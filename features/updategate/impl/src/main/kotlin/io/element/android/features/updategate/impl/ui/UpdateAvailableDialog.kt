/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.updategate.impl.R
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog

/**
 * The dialog over the app: "Update" opens the update page, "Later" hides it. Dismissing it (Back, a tap outside)
 * counts as "Later".
 */
@Composable
fun UpdateAvailableDialog(
    onUpdateClick: () -> Unit,
    onLaterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationDialog(
        modifier = modifier,
        title = stringResource(R.string.leveragers_update_available_title),
        content = stringResource(R.string.leveragers_update_available_message),
        submitText = stringResource(R.string.leveragers_update_action_update),
        cancelText = stringResource(R.string.leveragers_update_action_later),
        onSubmitClick = onUpdateClick,
        onDismiss = onLaterClick,
    )
}
