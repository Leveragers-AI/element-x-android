/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption

import kotlinx.coroutines.flow.StateFlow

/**
 * Leveragers: sets up recovery without asking, as our Element Web does, at the first start after a sign-in that
 * created the account's encryption identity, and after "Reset identity". The new recovery key reaches Leveragers like
 * any key the app creates (RecoveryKeyEscrow.kt). Accounts that already had an identity are left alone.
 * Does nothing when LeveragersConfig.RECOVERY_KEY_RECIPIENT is empty.
 */
interface LeveragersRecoverySetup {
    /** True while recovery is being set up, or may be: Element's "Set up recovery" banner waits meanwhile. */
    val holdsBackRecoveryBanner: StateFlow<Boolean>

    /** To call once when the session starts, before its first verification state is known. */
    fun onSessionStarted()

    /** To call when "Reset identity" has completed. */
    fun onIdentityReset()
}
