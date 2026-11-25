/*
 * Copyright (c) 2026 Leveragers.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

/**
 * Leveragers: switches for the Element features that "Agents by Leveragers" hides, as in the iOS app (AppSettings).
 * The code behind each switch stays in place, to keep rebases on new Element versions simple.
 */
object LeveragersConfig {
    /**
     * Whether "Invite friends to Agents" is offered on the "Start chat" screen.
     * Accounts on chat.leveragers.com are created by Leveragers: the shared link would lead to an app nobody can sign in to.
     */
    const val CAN_INVITE_FRIENDS_TO_APP = false

    /**
     * Whether "Join room by address" is offered on the "Start chat" screen.
     * Rooms on chat.leveragers.com are joined by invitation or through their space.
     */
    const val CAN_JOIN_ROOM_BY_ADDRESS = false
}
