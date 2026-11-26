/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.leveragers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebRoomListSectionsTest {
    @Test
    fun `sections are read from the Element Web settings in their order`() {
        val sections = WebRoomListSections.fromSettingsJson(
            """
            {
              "RoomList.CustomSectionData": {
                "element.io.section.a": {"tag": "element.io.section.a", "name": "Clients"},
                "element.io.section.b": {"tag": "element.io.section.b", "name": "Projets"},
                "element.io.section.c": {"tag": "element.io.section.other", "name": "Mismatch"}
              },
              "RoomList.OrderedCustomSections": ["element.io.section.b", "chats", "element.io.section.a", "element.io.section.c"]
            }
            """.trimIndent()
        )
        assertThat(sections.customSectionNames).containsExactly("element.io.section.a", "Clients", "element.io.section.b", "Projets")
        assertThat(sections.orderedTags).containsExactly(
            WebRoomListSections.DIRECT_MESSAGES_TAG,
            "element.io.section.b",
            WebRoomListSections.CHATS_TAG,
            "element.io.section.a",
        ).inOrder()
    }

    @Test
    fun `without settings, direct messages come before rooms`() {
        val sections = WebRoomListSections.fromSettingsJson("{}")
        assertThat(sections).isEqualTo(WebRoomListSections.EMPTY)
        assertThat(WebRoomListSections.fromSettingsJson("not json")).isEqualTo(WebRoomListSections.EMPTY)
    }

    @Test
    fun `only the custom section tags of a room are kept`() {
        val tags = WebRoomListSections.customTagsFromTagsJson(
            """{"tags": {"m.favourite": {"order": 0.5}, "element.io.section.a": {}, "u.work": {}}}"""
        )
        assertThat(tags).containsExactly("element.io.section.a")
        assertThat(WebRoomListSections.customTagsFromTagsJson("{}")).isEmpty()
    }

    @Test
    fun `the cache keeps the sections but not the unreadable rooms`() {
        val sections = WebRoomListSections(
            orderedTags = listOf("element.io.section.a", WebRoomListSections.CHATS_TAG),
            customSectionNames = mapOf("element.io.section.a" to "Clients"),
            roomTags = mapOf("!room:server" to setOf("element.io.section.a")),
            unreadableRoomIds = setOf("!other:server"),
        )
        assertThat(WebRoomListSections.fromCache(sections.toCache())).isEqualTo(sections.copy(unreadableRoomIds = emptySet()))
        assertThat(WebRoomListSections.fromCache("broken")).isNull()
    }
}
