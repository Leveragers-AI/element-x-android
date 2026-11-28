/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val FETCH_TIMEOUT_SECONDS = 8L

interface UpdatePolicyFetcher {
    /** The Android part of the update file, or null when it cannot be had: missing, network error, not valid. */
    suspend fun fetch(): UpdatePolicy?
}

@ContributesBinding(AppScope::class)
class DefaultUpdatePolicyFetcher(
    private val okHttpClient: () -> OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) : UpdatePolicyFetcher {
    private val client by lazy {
        okHttpClient().newBuilder()
            .callTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun fetch(): UpdatePolicy? = withContext(coroutineDispatchers.io) {
        runCatchingExceptions {
            val request = Request.Builder().url(UPDATE_FILE_URL).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) UpdatePolicy.fromUpdateFile(response.body.string()) else null
            }
        }
            .onFailure { Timber.w(it, "Update file not read") }
            .getOrNull()
    }
}
