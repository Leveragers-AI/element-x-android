/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag

/**
 * Leveragers: the email field and "Continue" of the start screen, in place of Element's sign-in button.
 * The tags are Element's own login ones, which the emulator test (Maestro) finds.
 */
@Composable
fun EmailSignInForm(
    state: EmailSignInState,
    isSigningIn: Boolean,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusy = state.isLookingUp || isSigningIn
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextField(
            value = state.email,
            onValueChange = { value -> onEmailChange(value.filterNot { it == '\n' || it == '\r' }) },
            label = stringResource(R.string.leveragers_sign_in_email_label),
            supportingText = when (state.error) {
                EmailSignInError.InvalidEmail -> stringResource(R.string.leveragers_sign_in_invalid_email)
                EmailSignInError.FrontDoorUnreachable -> stringResource(R.string.leveragers_sign_in_front_door_error)
                null -> null
            },
            validity = if (state.error != null) TextFieldValidity.Invalid else TextFieldValidity.None,
            enabled = !isBusy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.loginEmailUsername),
        )
        Button(
            text = stringResource(R.string.leveragers_sign_in_continue),
            showProgress = isBusy,
            enabled = isBusy || state.email.isNotBlank(),
            onClick = submit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.loginContinue),
        )
    }
}
