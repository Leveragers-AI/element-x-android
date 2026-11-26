/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.api

import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Leveragers: the room that opens in place of the page of a joined space, as on Element Web and in the iOS app.
 */
fun interface SpaceFirstRoomProvider {
    /**
     * The first room of the space in the home screen's order, a direct message only when the space has no other room.
     * Null when the space or its rooms aren't known yet, or it has none: the space's page shows as before.
     */
    suspend fun firstRoomId(spaceId: RoomId): RoomId?
}
