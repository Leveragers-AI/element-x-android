/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.api

import androidx.compose.runtime.Composable

/**
 * Leveragers: the app update check. The update file of login.leveragers.com gives the oldest build that may still be
 * used and the build we recommend: below the first, a blocking screen; below the second, a dialog.
 */
interface UpdateGateEntryPoint {
    /**
     * Reads the update file again, at most once an hour. Called each time the app comes to the foreground, launch
     * included.
     */
    fun check()

    /**
     * Shows [content], or the blocking screen instead of it, or the dialog over it. Shows nothing until the copy of
     * the file kept on the phone has been read, so that a blocked person never reaches the app.
     */
    @Composable
    fun Render(content: @Composable () -> Unit)
}
