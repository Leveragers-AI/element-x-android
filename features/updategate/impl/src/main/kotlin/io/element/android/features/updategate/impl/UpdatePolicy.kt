/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/**
 * The update file, on the front door of the sign-in (same file for the iOS app), written from hub2 by the pool's
 * `front-door` command.
 */
internal const val UPDATE_FILE_URL = "https://login.leveragers.com/app/agents.json"

/**
 * The Android part of the update file. [minBuild] and [recommendedBuild] are versionCodes, compared with the app's
 * own; [updateUrl] is the page that installs the update.
 */
data class UpdatePolicy(
    val minBuild: Long,
    val recommendedBuild: Long,
    val updateUrl: String,
) {
    companion object {
        /**
         * The Android part of the update file [json]
         * (`{"v":1,"ios":{…},"android":{"min_build":N,"recommended_build":N,"update_url":"https://…"}}`), or null
         * when the file is not valid: `v` other than 1, a threshold that is not a non-negative integer, an address that
         * is not https, no Android part, not JSON.
         */
        fun fromUpdateFile(json: String): UpdatePolicy? = runCatchingExceptions {
            val file = Json.parseToJsonElement(json) as? JsonObject ?: return null
            if (file["v"].toNonNegativeLong() != 1L) return null
            val android = file["android"] as? JsonObject ?: return null
            val minBuild = android["min_build"].toNonNegativeLong() ?: return null
            val recommendedBuild = android["recommended_build"].toNonNegativeLong() ?: return null
            val updateUrl = (android["update_url"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            if (!updateUrl.isHttpsUrl()) return null
            UpdatePolicy(
                minBuild = minBuild,
                recommendedBuild = recommendedBuild,
                updateUrl = updateUrl,
            )
        }.getOrNull()
    }
}

/** A JSON integer (not a string, not a decimal) of 0 or more. */
private fun JsonElement?.toNonNegativeLong(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    return primitive.content.toLongOrNull()?.takeIf { it >= 0 }
}

private fun String.isHttpsUrl(): Boolean = runCatchingExceptions {
    val uri = URI(this)
    uri.scheme == "https" && !uri.host.isNullOrEmpty()
}.getOrDefault(false)
