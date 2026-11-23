/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    const val APPLICATION_ID = "com.leveragers.agents"
    const val APPLICATION_NAME = "Agents"
    val METADATA_HOST_REVERSED: String? = "com.leveragers.agents"
    val OAUTH_CLIENT_URL_PATH: String? = null
    val URL_WEBSITE: String? = "https://leveragers.com"
    val URL_LOGO: String? = "https://agents.leveragers.com/vector-icons/1024.png"
    val URL_COPYRIGHT: String? = "https://github.com/Leveragers-AI/element-x-android"
    val URL_ACCEPTABLE_USE: String? = "https://www.leveragers.com/legal/mentions-legales"
    val URL_PRIVACY: String? = "https://www.leveragers.com/legal/politique-de-confidentialite"
    val URL_POLICY: String? = "https://www.leveragers.com/legal/politique-de-confidentialite"
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = null
    const val PUSH_CONFIG_INCLUDE_FIREBASE: Boolean = false
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH: Boolean = true
    val PUSHER_APP_ID_RELEASE: String? = null
    val PUSHER_APP_ID_DEBUG: String? = null
    val PUSHER_APP_ID_NIGHTLY: String? = null
}
