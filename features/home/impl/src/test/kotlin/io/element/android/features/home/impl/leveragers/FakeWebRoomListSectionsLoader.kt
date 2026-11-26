/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.leveragers

import io.element.android.libraries.matrix.api.core.RoomId

class FakeWebRoomListSectionsLoader(
    private val cachedResult: () -> WebRoomListSections? = { WebRoomListSections.EMPTY },
    private val loadResult: (Collection<RoomId>) -> WebRoomListSections? = { WebRoomListSections.EMPTY },
) : WebRoomListSectionsLoader {
    override suspend fun cached(): WebRoomListSections? = cachedResult()
    override suspend fun load(joinedRoomIds: Collection<RoomId>): WebRoomListSections? = loadResult(joinedRoomIds)
}
