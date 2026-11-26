/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.leveragers

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter
import io.element.android.libraries.previewutils.room.aSpaceRoom
import org.junit.Test

class SpaceFirstRoomTest {
    @Test
    fun `the first room of the space in the home screen's order opens`() {
        val rooms = listOf(
            aRoomListRoomSummary(id = "!b:server", name = "Bravo"),
            aRoomListRoomSummary(id = "!other:server", name = "Alpha"),
            aRoomListRoomSummary(id = "!a:server", name = "Charlie"),
        )

        assertThat(SpaceFirstRoom.roomId(space("!a:server", "!b:server"), rooms, WebRoomListSections.EMPTY)).isEqualTo(RoomId("!b:server"))
    }

    @Test
    fun `a direct message only opens when the space has no other room`() {
        val rooms = listOf(
            aRoomListRoomSummary(id = "!dm:server", name = "Agent", isDirect = true),
            aRoomListRoomSummary(id = "!room:server", name = "Zulu"),
        )

        assertThat(SpaceFirstRoom.roomId(space("!room:server"), rooms, WebRoomListSections.EMPTY)).isEqualTo(RoomId("!room:server"))
        assertThat(SpaceFirstRoom.roomId(space("!unknown:server"), rooms, WebRoomListSections.EMPTY)).isEqualTo(RoomId("!dm:server"))
    }

    @Test
    fun `invites never open in place of the space`() {
        val rooms = listOf(
            aRoomListRoomSummary(id = "!invite:server", name = "Alpha", displayType = RoomSummaryDisplayType.INVITE),
            aRoomListRoomSummary(id = "!room:server", name = "Bravo"),
        )

        assertThat(SpaceFirstRoom.roomId(space("!invite:server", "!room:server"), rooms, WebRoomListSections.EMPTY))
            .isEqualTo(RoomId("!room:server"))
    }

    @Test
    fun `the space's page shows while its rooms aren't known, or when it has none`() {
        val rooms = listOf(aRoomListRoomSummary(id = "!room:server", name = "Alpha"))

        assertThat(SpaceFirstRoom.roomId(space(), rooms, WebRoomListSections.EMPTY)).isNull()
        assertThat(SpaceFirstRoom.roomId(space("!room:server"), emptyList(), WebRoomListSections.EMPTY)).isNull()
    }

    private fun space(vararg descendants: String) = SpaceServiceFilter(
        spaceRoom = aSpaceRoom(),
        level = 0,
        descendants = descendants.map(::RoomId),
    )
}
