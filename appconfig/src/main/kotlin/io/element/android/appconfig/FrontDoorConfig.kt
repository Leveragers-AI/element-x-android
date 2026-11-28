/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

/**
 * Leveragers: the front door, which tells the app the chat server of an email address before sign-in starts, and the
 * chat servers the app accepts (see LEVERAGERS.md, "Connexion par e-mail").
 */
object FrontDoorConfig {
    /**
     * The front door: one small file per enterprise domain (`d/<domain>.json`) or Microsoft tenant (`t/<tenant id>.json`),
     * `{"v":1,"server":"<chat server name>"}`.
     */
    const val BASE_URL = "https://login.leveragers.com"

    /**
     * The chat server of every pro-plan user, and of every address the front door has no file for.
     */
    const val PRO_SERVER = "chat.leveragers.com"

    private val ALLOWED_SERVER_REGEX = Regex(
        "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*\\.leveragers\\.com$"
    )

    /**
     * Whether the app may sign in to this chat server (a Matrix server name): the pro server, or a server named under
     * `.leveragers.com`.
     */
    fun isAllowedServer(serverName: String): Boolean {
        return serverName == PRO_SERVER || ALLOWED_SERVER_REGEX.matches(serverName)
    }
}
