/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

/**
 * Leveragers: the email field of the start screen (LeveragersConfig.SIGNS_IN_WITH_EMAIL).
 */
data class EmailSignInState(
    val email: String,
    val error: EmailSignInError?,
    val isLookingUp: Boolean,
)

enum class EmailSignInError {
    /** The address has no valid domain. */
    InvalidEmail,

    /** The front door could not be read: try again, the email stays in the field. */
    FrontDoorUnreachable,
}
