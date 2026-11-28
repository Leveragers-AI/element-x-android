/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.test.encryption

import io.element.android.libraries.matrix.api.encryption.LeveragersRecoverySetup
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLeveragersRecoverySetup(
    override val holdsBackRecoveryBanner: MutableStateFlow<Boolean> = MutableStateFlow(false),
) : LeveragersRecoverySetup {
    var sessionStartedCount = 0
        private set
    var identityResetCount = 0
        private set

    override fun onSessionStarted() {
        sessionStartedCount++
    }

    override fun onIdentityReset() {
        identityResetCount++
    }
}
