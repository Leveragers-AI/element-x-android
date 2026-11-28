/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.frontdoor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.appconfig.FrontDoorConfig
import io.element.android.features.login.impl.BuildConfig
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Leveragers: the front door lookup, with the HTTP client of the app.
 *
 * 1. Domain = the part after the last "@", lowercased. Any non-ASCII character: the pro server, no lookup. Not a valid
 *    domain name: [FrontDoorResult.InvalidEmail].
 * 2. `d/<domain>.json`: 200 with a valid body gives the server; 404 goes on to step 3; anything else is
 *    [FrontDoorResult.Unreachable].
 * 3. Microsoft's public discovery for the domain gives its tenant id, then `t/<tenant id>.json`: 200 with a valid body
 *    gives the server, 404 the pro server, anything else [FrontDoorResult.Unreachable]. Any Microsoft failure: the pro
 *    server.
 *
 * A valid body is `{"v":1,"server":"<name>"}` with a server allowed by FrontDoorConfig.isAllowedServer. Each request
 * has 8 seconds, and redirects are not followed.
 */
@ContributesBinding(AppScope::class)
class DefaultFrontDoorLookup(
    private val okHttpClient: () -> OkHttpClient,
    private val dispatchers: CoroutineDispatchers,
) : FrontDoorLookup {
    override suspend fun lookup(email: String): FrontDoorResult = lookup(
        email = email,
        // The emulator test build reads a front door on the CI runner; every other build reads the real one.
        frontDoorBaseUrl = BuildConfig.LEVERAGERS_FRONT_DOOR_TEST_URL.ifEmpty { FrontDoorConfig.BASE_URL },
        microsoftBaseUrl = MICROSOFT_BASE_URL,
        timeoutMillis = TIMEOUT_MILLIS,
    )

    internal suspend fun lookup(
        email: String,
        frontDoorBaseUrl: String,
        microsoftBaseUrl: String,
        timeoutMillis: Long,
    ): FrontDoorResult = withContext(dispatchers.io) {
        val domain = email.trim().substringAfterLast('@', missingDelimiterValue = "").lowercase()
        if (domain.any { it.code > MAX_ASCII_CODE }) {
            return@withContext FrontDoorResult.Server(FrontDoorConfig.PRO_SERVER)
        }
        if (!DOMAIN_REGEX.matches(domain)) {
            return@withContext FrontDoorResult.InvalidEmail
        }
        val client = okHttpClient().newBuilder()
            .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        when (val domainAnswer = client.fetch("$frontDoorBaseUrl/d/$domain.json")) {
            is HttpAnswer.Found -> domainAnswer.body.toFrontDoorResult()
            HttpAnswer.NotFound -> client.lookUpMicrosoftTenant(
                domain = domain,
                frontDoorBaseUrl = frontDoorBaseUrl,
                microsoftBaseUrl = microsoftBaseUrl,
            )
            HttpAnswer.Failed -> FrontDoorResult.Unreachable
        }
    }

    private fun OkHttpClient.lookUpMicrosoftTenant(
        domain: String,
        frontDoorBaseUrl: String,
        microsoftBaseUrl: String,
    ): FrontDoorResult {
        val tenantId = microsoftTenantId(domain, microsoftBaseUrl)
            ?: return FrontDoorResult.Server(FrontDoorConfig.PRO_SERVER)
        return when (val tenantAnswer = fetch("$frontDoorBaseUrl/t/$tenantId.json")) {
            is HttpAnswer.Found -> tenantAnswer.body.toFrontDoorResult()
            HttpAnswer.NotFound -> FrontDoorResult.Server(FrontDoorConfig.PRO_SERVER)
            HttpAnswer.Failed -> FrontDoorResult.Unreachable
        }
    }

    /**
     * The Microsoft tenant id of the domain (lowercase), from Microsoft's public discovery, or null on any failure.
     */
    private fun OkHttpClient.microsoftTenantId(domain: String, microsoftBaseUrl: String): String? {
        val answer = fetch("$microsoftBaseUrl/$domain/v2.0/.well-known/openid-configuration") as? HttpAnswer.Found
            ?: return null
        val issuer = answer.body.parseJsonObject()?.stringOrNull("issuer") ?: return null
        return ISSUER_REGEX.matchEntire(issuer)?.groupValues?.get(1)?.lowercase()
    }

    private fun String.toFrontDoorResult(): FrontDoorResult {
        val json = parseJsonObject()
        val version = json?.get("v") as? JsonPrimitive
        val server = json?.stringOrNull("server")
        return if (version != null && !version.isString && version.content == "1" &&
            server != null && FrontDoorConfig.isAllowedServer(server)
        ) {
            FrontDoorResult.Server(server)
        } else {
            Timber.w("Front door: unexpected answer")
            FrontDoorResult.Unreachable
        }
    }

    private fun OkHttpClient.fetch(url: String): HttpAnswer {
        val request = Request.Builder().url(url).get().build()
        return try {
            newCall(request).execute().use { response ->
                when (response.code) {
                    HTTP_OK -> HttpAnswer.Found(response.body.string())
                    HTTP_NOT_FOUND -> HttpAnswer.NotFound
                    else -> HttpAnswer.Failed
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Front door: request failed")
            HttpAnswer.Failed
        }
    }

    private fun String.parseJsonObject(): JsonObject? = try {
        Json.parseToJsonElement(this) as? JsonObject
    } catch (e: IllegalArgumentException) {
        // SerializationException (malformed JSON) is an IllegalArgumentException.
        null
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private sealed interface HttpAnswer {
        data class Found(val body: String) : HttpAnswer
        data object NotFound : HttpAnswer
        data object Failed : HttpAnswer
    }
}

private const val MICROSOFT_BASE_URL = "https://login.microsoftonline.com"
private const val TIMEOUT_MILLIS = 8_000L
private const val MAX_ASCII_CODE = 0x7F
private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404

private val DOMAIN_REGEX = Regex(
    "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
)

private val ISSUER_REGEX = Regex(
    "^https://login\\.microsoftonline\\.com/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/v2\\.0/?$",
    RegexOption.IGNORE_CASE,
)
