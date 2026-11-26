/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.leveragers

import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.home.api.SpaceFirstRoomProvider
import io.element.android.features.home.impl.datasource.RoomListRoomSummaryFactory
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Leveragers: picks the room that opens in place of the page of a joined space, as in the iOS app (SpaceFirstRoom.swift).
 */
object SpaceFirstRoom {
    /**
     * The first joined room of the space in the home screen's order (Element Web's sections, then alphabetical),
     * a direct message only when the space has no other room, never an invite.
     */
    fun roomId(
        spaceFilter: SpaceServiceFilter,
        rooms: List<RoomListRoomSummary>,
        webSections: WebRoomListSections,
    ): RoomId? {
        // Without its rooms, the space would list every room.
        if (spaceFilter.descendants.isEmpty()) return null
        val joinedRooms = RoomListSection.sections(rooms, spaceFilter.descendants.toSet(), webSections)
            .flatMap { it.rooms }
            .filter { it.displayType == RoomSummaryDisplayType.ROOM }
        return (joinedRooms.firstOrNull { !it.isDirect } ?: joinedRooms.firstOrNull())?.roomId
    }
}

@ContributesBinding(SessionScope::class)
class DefaultSpaceFirstRoomProvider(
    private val client: MatrixClient,
    private val roomListRoomSummaryFactory: RoomListRoomSummaryFactory,
    private val webRoomListSectionsLoader: WebRoomListSectionsLoader,
) : SpaceFirstRoomProvider {
    override suspend fun firstRoomId(spaceId: RoomId): RoomId? = withTimeoutOrNull(LOAD_TIMEOUT) {
        val spaceFilter = client.spaceService.spaceFiltersFlow.first().firstOrNull { it.spaceRoom.roomId == spaceId }
            ?: return@withTimeoutOrNull null
        val rooms = client.roomListService.allRooms.summaries.first()
            .filterNot { it.info.isSpace }
            .map(roomListRoomSummaryFactory::create)
        val webSections = webRoomListSectionsLoader.cached() ?: WebRoomListSections.EMPTY
        SpaceFirstRoom.roomId(spaceFilter, rooms, webSections)
    }

    private companion object {
        val LOAD_TIMEOUT = 2.seconds
    }
}
