/*
 * Copyright (c) 2026 Leveragers.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.leveragers

import io.element.android.libraries.core.data.tryOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Leveragers: the room list sections created in Element Web, read from the account so that the app shows the same
 * ones, as in the iOS app (WebRoomListSections.swift).
 *
 * Element Web stores the sections in its `im.vector.web.settings` account data (`RoomList.CustomSectionData` and
 * `RoomList.OrderedCustomSections`) and adds each room to a section with an `element.io.section.<id>` room tag.
 *
 * @property orderedTags the sections shown between Favourites and Low priority, in display order: custom section
 * tags, [CHATS_TAG] and [DIRECT_MESSAGES_TAG].
 * @property customSectionNames the names of the custom sections, by tag.
 * @property roomTags the custom section tags of each room, by room ID.
 * @property unreadableRoomIds the rooms whose tags couldn't be read this time, so their sections aren't known.
 */
@Serializable
data class WebRoomListSections(
    val orderedTags: List<String>,
    val customSectionNames: Map<String, String>,
    val roomTags: Map<String, Set<String>> = emptyMap(),
    val unreadableRoomIds: Set<String> = emptySet(),
) {
    companion object {
        const val SETTINGS_EVENT_TYPE = "im.vector.web.settings"
        const val CUSTOM_TAG_PREFIX = "element.io.section."

        /** Element Web's synthetic tag for the section holding every other room. */
        const val CHATS_TAG = "chats"

        /** Element Web's synthetic tag for the direct messages section. */
        const val DIRECT_MESSAGES_TAG = "im.vector.fake.direct"

        val EMPTY = WebRoomListSections(orderedTags = listOf(DIRECT_MESSAGES_TAG, CHATS_TAG), customSectionNames = emptyMap())

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Reads the sections from the content of the `im.vector.web.settings` account data, like Element Web does.
         */
        fun fromSettingsJson(settingsJson: String): WebRoomListSections {
            val settings = tryOrNull { json.parseToJsonElement(settingsJson) as? JsonObject } ?: JsonObject(emptyMap())
            val sectionData = settings["RoomList.CustomSectionData"] as? JsonObject ?: JsonObject(emptyMap())

            val names = buildMap {
                for ((tag, value) in sectionData) {
                    val section = value as? JsonObject ?: continue
                    val sectionTag = (section["tag"] as? JsonPrimitive)?.contentOrNull
                    val name = (section["name"] as? JsonPrimitive)?.contentOrNull
                    if (tag.startsWith(CUSTOM_TAG_PREFIX) && sectionTag == tag && name != null) {
                        put(tag, name)
                    }
                }
            }

            val storedOrder = (settings["RoomList.OrderedCustomSections"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
            val orderedTags = storedOrder.filter { it == CHATS_TAG || it == DIRECT_MESSAGES_TAG || it in names }.toMutableList()
            // Same defaults as Element Web: custom sections above Chats, direct messages above everything.
            if (CHATS_TAG !in orderedTags) {
                orderedTags.add(CHATS_TAG)
            }
            if (DIRECT_MESSAGES_TAG !in orderedTags) {
                orderedTags.add(0, DIRECT_MESSAGES_TAG)
            }
            return WebRoomListSections(orderedTags = orderedTags, customSectionNames = names)
        }

        /**
         * Keeps only the custom section tags from the content of a room's `m.tag` account data.
         */
        fun customTagsFromTagsJson(tagsJson: String): Set<String> {
            val content = tryOrNull { json.parseToJsonElement(tagsJson) as? JsonObject }
            val tags = content?.get("tags") as? JsonObject ?: return emptySet()
            return tags.keys.filter { it.startsWith(CUSTOM_TAG_PREFIX) }.toSet()
        }

        fun fromCache(cache: String): WebRoomListSections? = tryOrNull { json.decodeFromString<WebRoomListSections>(cache) }
    }

    fun toCache(): String = json.encodeToString(serializer(), copy(unreadableRoomIds = emptySet()))
}
