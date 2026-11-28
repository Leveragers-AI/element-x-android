/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.frontdoor

/**
 * Leveragers: finds the chat server of an email address before sign-in starts, through our front door
 * (FrontDoorConfig.BASE_URL). Same rules as our web and iOS apps, see LEVERAGERS.md.
 */
interface FrontDoorLookup {
    /**
     * @param email the address as typed; it is trimmed first.
     */
    suspend fun lookup(email: String): FrontDoorResult
}

sealed interface FrontDoorResult {
    /**
     * Sign in to this chat server (a Matrix server name, allowed by FrontDoorConfig.isAllowedServer).
     */
    data class Server(val serverName: String) : FrontDoorResult

    /**
     * The address has no valid domain: ask again.
     */
    data object InvalidEmail : FrontDoorResult

    /**
     * The front door could not be read (unreachable, too slow, refused, or an unexpected answer): try again, never
     * fall back to the pro server.
     */
    data object Unreachable : FrontDoorResult
}
