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

    /**
     * Whether the settings are simplified: no user status under the profile, no "Media upload quality" (the default
     * quality is kept), no Labs, no developer options (which Element turns on by default outside release builds, and
     * unlocks with seven taps on the version number).
     */
    const val HAS_SIMPLIFIED_SETTINGS = true

    /**
     * Whether the app always uses the light theme, as our web app. When true, the "Appearance" setting is hidden and a
     * theme chosen before is no longer applied.
     */
    const val USES_LIGHT_THEME_ONLY = true

    /**
     * Whether the app uses the palette of our Element Web theme (LeveragersColors): cream background, navy ink instead
     * of Element's green, warm grey secondary text, softer message bubbles. Applied to the light theme only.
     */
    const val HAS_LEVERAGERS_PALETTE = true

    /**
     * Whether the home screen is simplified: a single list of rooms, without the Chats and Spaces tabs, the filters
     * (Unread, People, Favourites…) nor the space filter.
     */
    const val HAS_SIMPLIFIED_HOME_SCREEN = true

    /**
     * Whether the home screen groups the rooms into the sections of Element Web: Invites and Favourites, then the
     * sections created on the web, Direct messages and Rooms in the order chosen on the web, then Low priority, each
     * sorted alphabetically. The rooms are those of the first space (direct messages and invites are always shown).
     */
    const val HAS_WEB_ROOM_LIST_SECTIONS = true

    /**
     * Whether the user can share their location (composer menu, "Location sharing" in the settings).
     * The app also does not declare the location permissions (see app/src/main/AndroidManifest.xml), so Android
     * refuses any access to the location.
     */
    const val CAN_SHARE_LOCATION = false

    /**
     * Whether the start screen opens the sign-in page of our server by itself, once, as a tap on its button would
     * (first launch, or after signing out). Not when adding an account. If the page is closed, the start screen and
     * its button stay there to try again.
     */
    const val OPENS_SIGN_IN_AUTOMATICALLY = true

    /**
     * Whether creating a room is simplified: the first space the user can add rooms to is selected by default, and the
     * room is open to the members of its space (invite only without a space), without the access and address options.
     * A truly public room is not offered: our web app forbids it and the server refuses it. Creating a space is unchanged.
     */
    const val HAS_SIMPLIFIED_ROOM_CREATION = true
}
