/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.frontdoor

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.FrontDoorConfig
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

// Robolectric, as for the bug reporter's tests: the app's OkHttp (its Android variant) needs the Android framework.
class DefaultFrontDoorLookupTest : RobolectricTest() {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a domain file gives its server, the domain is lowercased and the input trimmed`() = runTest {
        server.enqueue(json(200, """{"v":1,"server":"theseis.chat.leveragers.com"}"""))
        val result = lookup("  Alice@Theseis.FR ")
        assertThat(result).isEqualTo(FrontDoorResult.Server("theseis.chat.leveragers.com"))
        assertThat(server.takeRequest().path).isEqualTo("/d/theseis.fr.json")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a domain file may name the pro server`() = runTest {
        server.enqueue(json(200, """{"v":1,"server":"chat.leveragers.com"}"""))
        assertThat(lookup("alice@example.com")).isEqualTo(FrontDoorResult.Server(FrontDoorConfig.PRO_SERVER))
    }

    @Test
    fun `an invalid domain file is a front door error`() = runTest {
        listOf(
            "not json",
            """["chat.leveragers.com"]""",
            """{"server":"chat.leveragers.com"}""",
            """{"v":2,"server":"chat.leveragers.com"}""",
            """{"v":"1","server":"chat.leveragers.com"}""",
            """{"v":1}""",
            """{"v":1,"server":42}""",
            """{"v":1,"server":"matrix.org"}""",
            """{"v":1,"server":"leveragers.com"}""",
            """{"v":1,"server":"chat.leveragers.com.example.org"}""",
            """{"v":1,"server":"https://chat.leveragers.com"}""",
            """{"v":1,"server":"Chat.Leveragers.com"}""",
        ).forEach { body ->
            server.enqueue(json(200, body))
            assertThat(lookup("alice@example.com")).isEqualTo(FrontDoorResult.Unreachable)
        }
    }

    @Test
    fun `an unexpected status of the domain file is a front door error`() = runTest {
        listOf(500, 403, 429, 301).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", "https://example.org/"))
            assertThat(lookup("alice@example.com")).isEqualTo(FrontDoorResult.Unreachable)
        }
        assertThat(server.requestCount).isEqualTo(4)
    }

    @Test
    fun `an unreachable front door is a front door error`() = runTest {
        val closedUrl = closedServerUrl()
        val result = lookup("alice@example.com", frontDoorBaseUrl = closedUrl)
        assertThat(result).isEqualTo(FrontDoorResult.Unreachable)
    }

    @Test
    fun `a slow front door is a front door error`() = runTest {
        server.enqueue(json(200, """{"v":1,"server":"chat.leveragers.com"}""").setHeadersDelay(2, TimeUnit.SECONDS))
        val result = lookup("alice@example.com", timeoutMillis = 200)
        assertThat(result).isEqualTo(FrontDoorResult.Unreachable)
    }

    @Test
    fun `without a domain file, the Microsoft tenant file gives the server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json(200, microsoftConfiguration("https://login.microsoftonline.com/F2611F1A-B034-4051-A574-CFF972C5FF25/v2.0")))
        server.enqueue(json(200, """{"v":1,"server":"theseis.chat.leveragers.com"}"""))
        val result = lookup("alice@theseis.fr")
        assertThat(result).isEqualTo(FrontDoorResult.Server("theseis.chat.leveragers.com"))
        assertThat(server.takeRequest().path).isEqualTo("/d/theseis.fr.json")
        assertThat(server.takeRequest().path).isEqualTo("/theseis.fr/v2.0/.well-known/openid-configuration")
        assertThat(server.takeRequest().path).isEqualTo("/t/f2611f1a-b034-4051-a574-cff972c5ff25.json")
    }

    @Test
    fun `without a domain file nor a tenant file, the pro server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json(200, microsoftConfiguration("https://login.microsoftonline.com/f2611f1a-b034-4051-a574-cff972c5ff25/v2.0/")))
        server.enqueue(MockResponse().setResponseCode(404))
        assertThat(lookup("alice@example.com")).isEqualTo(FrontDoorResult.Server(FrontDoorConfig.PRO_SERVER))
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `an unexpected answer for the tenant file is a front door error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json(200, microsoftConfiguration("https://login.microsoftonline.com/f2611f1a-b034-4051-a574-cff972c5ff25/v2.0")))
        server.enqueue(MockResponse().setResponseCode(500))
        assertThat(lookup("alice@example.com")).isEqualTo(FrontDoorResult.Unreachable)

        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json(200, microsoftConfiguration("https://login.microsoftonline.com/f2611f1a-b034-4051-a574-cff972c5ff25/v2.0")))
        server.enqueue(json(200, """{"v":1,"server":"matrix.org"}"""))
        assertThat(lookup("alice@example.com")).isEqualTo(FrontDoorResult.Unreachable)
    }

    @Test
    fun `any Microsoft failure gives the pro server`() = runTest {
        listOf(
            MockResponse().setResponseCode(400).setBody("""{"error":"invalid_tenant"}"""),
            json(200, "not json"),
            json(200, """{"token_endpoint":"https://login.microsoftonline.com/x/oauth2/v2.0/token"}"""),
            json(200, microsoftConfiguration("https://sts.windows.net/f2611f1a-b034-4051-a574-cff972c5ff25/")),
            json(200, microsoftConfiguration("https://login.microsoftonline.com/not-a-tenant-id/v2.0")),
            json(200, microsoftConfiguration("https://login.microsoftonline.com/f2611f1a-b034-4051-a574-cff972c5ff25/v2.0/x")),
        ).forEach { microsoftAnswer ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(microsoftAnswer)
            assertThat(lookup("alice@example.com")).isEqualTo(FrontDoorResult.Server(FrontDoorConfig.PRO_SERVER))
        }
        // Only the domain file and Microsoft were asked, never a tenant file.
        assertThat(server.requestCount).isEqualTo(12)
    }

    @Test
    fun `an unreachable Microsoft gives the pro server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = lookup("alice@example.com", microsoftBaseUrl = closedServerUrl())
        assertThat(result).isEqualTo(FrontDoorResult.Server(FrontDoorConfig.PRO_SERVER))
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a non-ASCII domain gives the pro server without any lookup`() = runTest {
        listOf("alice@exämple.fr", "alice@例え.jp", "alice@bücher.example").forEach { email ->
            assertThat(lookup(email)).isEqualTo(FrontDoorResult.Server(FrontDoorConfig.PRO_SERVER))
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an address without a valid domain is invalid, without any lookup`() = runTest {
        listOf(
            "",
            "   ",
            "alice",
            "alice@",
            "alice@localhost",
            "alice@example.",
            "alice@.example.com",
            "alice@-example.com",
            "alice@example-.com",
            "alice@exa_mple.com",
            "alice@example..com",
            "alice@example.com/x",
            "alice@exa mple.com",
            "alice@" + "a".repeat(64) + ".com",
        ).forEach { email ->
            assertThat(lookup(email)).isEqualTo(FrontDoorResult.InvalidEmail)
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `the domain is the part after the last at sign`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        lookup("\"a@b\"@example.com")
        assertThat(server.takeRequest().path).isEqualTo("/d/example.com.json")
    }

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun microsoftConfiguration(issuer: String) =
        """{"token_endpoint":"https://login.microsoftonline.com/x/oauth2/v2.0/token","issuer":"$issuer"}"""

    private fun closedServerUrl(): String {
        val closedServer = MockWebServer()
        closedServer.start()
        val url = closedServer.url("/").toString().removeSuffix("/")
        closedServer.shutdown()
        return url
    }

    private suspend fun TestScope.lookup(
        email: String,
        frontDoorBaseUrl: String = server.url("/").toString().removeSuffix("/"),
        microsoftBaseUrl: String = server.url("/").toString().removeSuffix("/"),
        timeoutMillis: Long = 5_000,
    ): FrontDoorResult {
        return DefaultFrontDoorLookup(
            okHttpClient = { OkHttpClient() },
            dispatchers = testCoroutineDispatchers(),
        ).lookup(
            email = email,
            frontDoorBaseUrl = frontDoorBaseUrl,
            microsoftBaseUrl = microsoftBaseUrl,
            timeoutMillis = timeoutMillis,
        )
    }
}
