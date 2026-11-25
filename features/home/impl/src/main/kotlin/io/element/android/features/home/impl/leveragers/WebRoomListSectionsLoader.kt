/*
 * Copyright (c) 2026 Leveragers.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.leveragers

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Leveragers: reads the Element Web room list sections of the account and the section tags of its rooms, and keeps
 * the last ones read for the next launches, as in the iOS app.
 */
interface WebRoomListSectionsLoader {
    /** The sections read at the last launch, if any. */
    suspend fun cached(): WebRoomListSections?

    /**
     * Reads the sections and the section tags of the given joined rooms, or null if the sections can't be read.
     */
    suspend fun load(joinedRoomIds: Collection<RoomId>): WebRoomListSections?
}

@ContributesBinding(SessionScope::class)
class DefaultWebRoomListSectionsLoader(
    private val client: MatrixClient,
    private val sessionStore: SessionStore,
    private val dispatchers: CoroutineDispatchers,
    @ApplicationContext private val context: Context,
) : WebRoomListSectionsLoader {
    private val cachePreferences by lazy { context.getSharedPreferences(CACHE_PREFERENCES_NAME, Context.MODE_PRIVATE) }

    override suspend fun cached(): WebRoomListSections? = withContext(dispatchers.io) {
        cachePreferences.getString(client.sessionId.value, null)?.let(WebRoomListSections::fromCache)
    }

    override suspend fun load(joinedRoomIds: Collection<RoomId>): WebRoomListSections? = withContext(dispatchers.io) {
        val settingsJson = client.getAccountData(WebRoomListSections.SETTINGS_EVENT_TYPE)
            .onFailure { Timber.e(it, "Failed loading the Element Web room list sections") }
            .getOrElse { return@withContext null }
        val sections = settingsJson?.let(WebRoomListSections::fromSettingsJson) ?: WebRoomListSections.EMPTY
        if (sections.customSectionNames.isEmpty()) {
            return@withContext sections.also(::save)
        }

        // The SDK drops the room tags that aren't `m.*` or `u.*`, so ask the homeserver for each room's tags.
        var fetched = fetchCustomSectionTags(joinedRoomIds)
        // These requests don't go through the SDK, which renews the access token when the homeserver refuses it.
        // So let an SDK request renew it, then try those rooms once more with the new one.
        if (fetched.unauthorizedRoomIds.isNotEmpty()) {
            Timber.i("The access token was refused for ${fetched.unauthorizedRoomIds.size} rooms' tags, retrying once.")
            client.getUserProfile()
            val retried = fetchCustomSectionTagsById(fetched.unauthorizedRoomIds)
            fetched = FetchedCustomSectionTags(
                roomTags = fetched.roomTags + retried.roomTags,
                unauthorizedRoomIds = retried.unauthorizedRoomIds,
                failedRoomIds = fetched.failedRoomIds + retried.failedRoomIds,
            )
        }
        sections.copy(
            roomTags = fetched.roomTags,
            unreadableRoomIds = fetched.unauthorizedRoomIds + fetched.failedRoomIds,
        ).also(::save)
    }

    private fun save(sections: WebRoomListSections) {
        cachePreferences.edit { putString(client.sessionId.value, sections.toCache()) }
    }

    private data class FetchedCustomSectionTags(
        val roomTags: Map<String, Set<String>> = emptyMap(),
        /** The homeserver refused the access token (it may have just expired). */
        val unauthorizedRoomIds: Set<String> = emptySet(),
        /** Any other failure. */
        val failedRoomIds: Set<String> = emptySet(),
    )

    private sealed interface CustomSectionTagsResponse {
        data class Tags(val tags: Set<String>) : CustomSectionTagsResponse
        data object Unauthorized : CustomSectionTagsResponse
        data object Failed : CustomSectionTagsResponse
    }

    private suspend fun fetchCustomSectionTags(roomIds: Collection<RoomId>): FetchedCustomSectionTags = fetchCustomSectionTagsById(roomIds.map { it.value })

    private suspend fun fetchCustomSectionTagsById(roomIds: Collection<String>): FetchedCustomSectionTags = coroutineScope {
        val session = sessionStore.getSession(client.sessionId.value)
            ?: return@coroutineScope FetchedCustomSectionTags(failedRoomIds = roomIds.toSet())
        // A few requests at a time.
        val semaphore = Semaphore(MAXIMUM_CONCURRENT_TAGS_REQUESTS)
        val responses = roomIds.map { roomId ->
            async {
                semaphore.withPermit {
                    roomId to fetchCustomSectionTags(roomId, session.homeserverUrl, session.userId, session.accessToken)
                }
            }
        }.awaitAll()
        FetchedCustomSectionTags(
            roomTags = responses.mapNotNull { (roomId, response) ->
                (response as? CustomSectionTagsResponse.Tags)?.tags?.takeIf { it.isNotEmpty() }?.let { roomId to it }
            }.toMap(),
            unauthorizedRoomIds = responses.filter { it.second == CustomSectionTagsResponse.Unauthorized }.map { it.first }.toSet(),
            failedRoomIds = responses.filter { it.second == CustomSectionTagsResponse.Failed }.map { it.first }.toSet(),
        )
    }

    private suspend fun fetchCustomSectionTags(
        roomId: String,
        homeserverUrl: String,
        userId: String,
        accessToken: String,
    ): CustomSectionTagsResponse = withContext(dispatchers.io) {
        // Not through the app's HTTP client, whose logs could show the access token.
        val url = Uri.parse(homeserverUrl).buildUpon()
            .appendEncodedPath("_matrix/client/v3/user")
            .appendPath(userId)
            .appendPath("rooms")
            .appendPath(roomId)
            .appendPath("tags")
            .build()
            .toString()
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                when (val statusCode = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> CustomSectionTagsResponse.Tags(
                        WebRoomListSections.customTagsFromTagsJson(connection.inputStream.bufferedReader().use { it.readText() })
                    )
                    HttpURLConnection.HTTP_UNAUTHORIZED -> CustomSectionTagsResponse.Unauthorized
                    else -> {
                        Timber.w("Failed loading the tags of a room with status $statusCode")
                        CustomSectionTagsResponse.Failed
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed loading the tags of a room")
            CustomSectionTagsResponse.Failed
        }
    }

    private companion object {
        const val CACHE_PREFERENCES_NAME = "leveragers_web_room_list_sections"
        const val MAXIMUM_CONCURRENT_TAGS_REQUESTS = 6
        const val TIMEOUT_MILLIS = 30_000
    }
}
