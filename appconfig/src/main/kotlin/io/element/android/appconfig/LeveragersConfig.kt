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

    /**
     * Whether polls can be created (composer menu) and their history opened (room details), as on our Element Web
     * where the poll button is hidden. Polls received from other clients are still shown and can be voted on.
     */
    const val CAN_CREATE_POLLS = false

    /**
     * Whether users can be blocked and rooms or messages reported. Our agents and colleagues share the same organisation.
     * Hides "Report" in the message menu, "Report room" in the room list menu and in room details, "Decline and block"
     * for invitations (room list and invite screen), "Block user" in DM details and member details, and the blocked
     * users list in the settings.
     */
    const val ALLOWS_BLOCKING_AND_REPORTING = false

    /**
     * Whether "Invite people" is offered in the details of a direct message. It would turn the conversation with an
     * agent into a group, which the web app does not offer either. Regular rooms keep "Invite", ruled by power levels.
     */
    const val ALLOWS_INVITING_FROM_DIRECT_MESSAGES = false

    /**
     * Whether rooms and spaces can be shared ("Share" in room details and in the space menu).
     * Our rooms only open from our apps: sharing their link makes no sense.
     */
    const val ALLOWS_SHARING_ROOMS = false

    /**
     * Whether the composer warns that media captions may not show in older apps.
     * Both our web and mobile apps show captions.
     */
    const val SHOWS_MEDIA_CAPTION_WARNING = false
}
