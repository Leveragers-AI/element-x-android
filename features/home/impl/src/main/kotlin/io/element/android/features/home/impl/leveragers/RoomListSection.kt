/*
 * Copyright (c) 2026 Leveragers.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.leveragers

import androidx.compose.runtime.Immutable
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.text.Collator

/**
 * Leveragers: a group of rooms on the home screen, in the same order as the Element Web room list.
 */
@Immutable
data class RoomListSection(
    val kind: Kind,
    val rooms: ImmutableList<RoomListRoomSummary>,
) {
    sealed interface Kind {
        data object Invites : Kind
        data object Favourites : Kind
        data object DirectMessages : Kind
        data object Rooms : Kind
        data object LowPriority : Kind

        /** A section created in Element Web. */
        data class Custom(val tag: String, val name: String) : Kind
    }

    companion object {
        /**
         * Groups the rooms of the given space into Element Web's sections, each sorted alphabetically.
         * Invites and direct messages are shown whatever the space, as they don't belong to one.
         *
         * @param rooms the rooms of the home screen.
         * @param spaceRoomIds the rooms of the selected space, or null to keep every room.
         * @param webSections the sections of Element Web.
         */
        fun sections(
            rooms: List<RoomListRoomSummary>,
            spaceRoomIds: Set<RoomId>?,
            webSections: WebRoomListSections,
        ): ImmutableList<RoomListSection> {
            val kinds = orderedKinds(webSections)
            val grouped = rooms
                .filter { it.isIn(spaceRoomIds) }
                .groupBy { it.kind(kinds, webSections) }
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            return kinds.mapNotNull { kind ->
                grouped[kind]
                    ?.takeIf { it.isNotEmpty() }
                    ?.sortedWith { first, second -> collator.compare(first.name.orEmpty(), second.name.orEmpty()) }
                    ?.let { RoomListSection(kind, it.toImmutableList()) }
            }.toImmutableList()
        }

        // Invites and Favourites are pinned at the top and Low priority at the bottom, as on Element Web.
        private fun orderedKinds(webSections: WebRoomListSections): List<Kind> {
            val reorderable = webSections.orderedTags.mapNotNull { tag ->
                when (tag) {
                    WebRoomListSections.CHATS_TAG -> Kind.Rooms
                    WebRoomListSections.DIRECT_MESSAGES_TAG -> Kind.DirectMessages
                    else -> webSections.customSectionNames[tag]?.let { Kind.Custom(tag, it) }
                }
            }
            return listOf(Kind.Invites, Kind.Favourites) + reorderable + Kind.LowPriority
        }

        // A space whose rooms aren't known yet (its state is still syncing) hides nothing, rather than every room.
        private fun RoomListRoomSummary.isIn(spaceRoomIds: Set<RoomId>?): Boolean {
            if (spaceRoomIds.isNullOrEmpty() || displayType != RoomSummaryDisplayType.ROOM || isDirect) return true
            return roomId in spaceRoomIds
        }

        // Same rule as Element Web: an invite wins, then the first section in display order whose tag the room carries,
        // then direct messages, then Rooms.
        private fun RoomListRoomSummary.kind(kinds: List<Kind>, webSections: WebRoomListSections): Kind {
            if (displayType == RoomSummaryDisplayType.INVITE || displayType == RoomSummaryDisplayType.KNOCKED) {
                return Kind.Invites
            }
            val customTags = webSections.roomTags[roomId.value].orEmpty()
            val taggedKind = kinds.firstOrNull { kind ->
                when (kind) {
                    Kind.Favourites -> isFavorite
                    Kind.LowPriority -> isLowPriority
                    is Kind.Custom -> kind.tag in customTags
                    Kind.Invites, Kind.DirectMessages, Kind.Rooms -> false
                }
            }
            return taggedKind ?: if (isDirect) Kind.DirectMessages else Kind.Rooms
        }
    }
}
