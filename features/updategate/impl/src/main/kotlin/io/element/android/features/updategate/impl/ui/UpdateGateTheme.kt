/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl.ui

import androidx.compose.runtime.Composable
import io.element.android.compound.colors.LeveragersColors
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.Theme

/**
 * The theme of the app (always light, Leveragers palette) for the blocking screen and the dialog, which are drawn
 * outside the app's own theme.
 */
@Composable
internal fun UpdateGateTheme(
    applySystemBarsUpdate: Boolean,
    content: @Composable () -> Unit,
) {
    ElementTheme(
        theme = Theme.Light,
        applySystemBarsUpdate = applySystemBarsUpdate,
        compoundLight = LeveragersColors.light,
        content = content,
    )
}
