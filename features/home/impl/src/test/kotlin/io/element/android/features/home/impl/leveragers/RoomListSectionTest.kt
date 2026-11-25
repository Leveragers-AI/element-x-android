/*
 * Copyright (c) 2026 Leveragers.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.leveragers

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId
import org.junit.Test

class RoomListSectionTest {
    private val clientsTag = "element.io.section.clients"
    private val webSections = WebRoomListSections(
        orderedTags = listOf(WebRoomListSections.DIRECT_MESSAGES_TAG, clientsTag, WebRoomListSections.CHATS_TAG),
        customSectionNames = mapOf(clientsTag to "Clients"),
        roomTags = mapOf("!client:server" to setOf(clientsTag), "!favClient:server" to setOf(clientsTag)),
    )

    @Test
    fun `rooms are grouped like on Element Web and sorted alphabetically`() {
        val rooms = listOf(
            aRoomListRoomSummary(id = "!zeta:server", name = "zeta"),
            aRoomListRoomSummary(id = "!alpha:server", name = "Alpha"),
            aRoomListRoomSummary(id = "!dm:server", name = "Nagomi", isDirect = true),
            aRoomListRoomSummary(id = "!client:server", name = "Client"),
            aRoomListRoomSummary(id = "!favClient:server", name = "Favourite client", isFavorite = true),
            aRoomListRoomSummary(id = "!low:server", name = "Low", isLowPriority = true),
            aRoomListRoomSummary(id = "!invite:server", name = "Invite", isFavorite = true, displayType = RoomSummaryDisplayType.INVITE),
        )

        val sections = RoomListSection.sections(rooms, spaceRoomIds = null, webSections = webSections)

        assertThat(sections.map { it.kind }).containsExactly(
            RoomListSection.Kind.Invites,
            RoomListSection.Kind.Favourites,
            RoomListSection.Kind.DirectMessages,
            RoomListSection.Kind.Custom(clientsTag, "Clients"),
            RoomListSection.Kind.Rooms,
            RoomListSection.Kind.LowPriority,
        ).inOrder()
        assertThat(sections.map { section -> section.rooms.map { it.name } }).containsExactly(
            listOf("Invite"),
            listOf("Favourite client"),
            listOf("Nagomi"),
            listOf("Client"),
            listOf("Alpha", "zeta"),
            listOf("Low"),
        ).inOrder()
    }

    @Test
    fun `only the rooms of the space are kept, with direct messages and invites`() {
        val rooms = listOf(
            aRoomListRoomSummary(id = "!inSpace:server", name = "In space"),
            aRoomListRoomSummary(id = "!outside:server", name = "Outside"),
            aRoomListRoomSummary(id = "!dm:server", name = "Nagomi", isDirect = true),
            aRoomListRoomSummary(id = "!invite:server", name = "Invite", displayType = RoomSummaryDisplayType.INVITE),
        )

        val sections = RoomListSection.sections(rooms, spaceRoomIds = setOf(RoomId("!inSpace:server")), webSections = WebRoomListSections.EMPTY)

        assertThat(sections.flatMap { section -> section.rooms.map { it.name } }).containsExactly("Invite", "Nagomi", "In space").inOrder()
    }

    @Test
    fun `a space whose rooms are not known yet hides nothing`() {
        val rooms = listOf(aRoomListRoomSummary(id = "!room:server", name = "Room"))

        val sections = RoomListSection.sections(rooms, spaceRoomIds = emptySet(), webSections = WebRoomListSections.EMPTY)

        assertThat(sections.single().rooms.single().name).isEqualTo("Room")
    }
}
