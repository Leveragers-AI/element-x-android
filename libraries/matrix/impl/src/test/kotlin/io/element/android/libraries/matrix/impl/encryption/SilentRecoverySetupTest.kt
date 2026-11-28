/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.element.android.libraries.matrix.impl.encryption

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Leveragers: silent recovery setup at the first start after a sign-in that created the identity, and after
 * "Reset identity" (SilentRecoverySetup.kt).
 */
class SilentRecoverySetupTest {
    private val enableRecoveryCalls = mutableListOf<Pair<Boolean, String?>>()
    private val encryptionService = FakeEncryptionService(
        enableRecoveryLambda = { waitForBackupsToUpload, passphrase ->
            enableRecoveryCalls += waitForBackupsToUpload to passphrase
            Result.success("EsTc new key")
        },
    )
    private val verificationService = FakeSessionVerificationService()
    private val firstStarts = InMemoryFirstStartRecords()

    @Test
    fun newSignInThatCreatedTheIdentity() = runTest {
        val setup = aSilentRecoverySetup()
        setup.onSessionStarted()
        assertThat(setup.holdsBackRecoveryBanner.value).isTrue()
        settle()
        assertThat(firstStarts.decided).isEmpty()

        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        settle()
        assertThat(firstStarts.decided).containsExactly(USER_ID, DEVICE_ID)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        encryptionService.emitBackupState(BackupState.ENABLED)
        settle()

        assertThat(enableRecoveryCalls).containsExactly(false to null)
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
    }

    @Test
    fun newSignInOfAnAccountThatHasAnIdentity() = runTest {
        // The device starts "not verified", then the person verifies it: recovery is left to them.
        val setup = aSilentRecoverySetup()
        setup.onSessionStarted()
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.NotVerified)
        settle()
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
        assertThat(firstStarts.decided).containsExactly(USER_ID, DEVICE_ID)

        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        settle()
        assertThat(enableRecoveryCalls).isEmpty()

        // Nor at the next start of the app.
        aSilentRecoverySetup().onSessionStarted()
        settle()
        assertThat(enableRecoveryCalls).isEmpty()
    }

    @Test
    fun laterStartIsLeftAlone() = runTest {
        firstStarts.decided[USER_ID] = DEVICE_ID
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        val setup = aSilentRecoverySetup()
        setup.onSessionStarted()
        settle()
        assertThat(enableRecoveryCalls).isEmpty()
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
    }

    @Test
    fun newDeviceOfTheSameAccountIsDecidedAgain() = runTest {
        firstStarts.decided[USER_ID] = "PREVIOUSDEVICE"
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        aSilentRecoverySetup().onSessionStarted()
        settle()
        assertThat(enableRecoveryCalls).containsExactly(false to null)
        assertThat(firstStarts.decided).containsExactly(USER_ID, DEVICE_ID)
    }

    @Test
    fun sessionFromBeforeThisVersionOfTheApp() = runTest {
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        val setup = aSilentRecoverySetup(signedInAt = INSTALLED_AT - 1)
        setup.onSessionStarted()
        settle()
        assertThat(enableRecoveryCalls).isEmpty()
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
        assertThat(firstStarts.decided).containsExactly(USER_ID, DEVICE_ID)
    }

    @Test
    fun unknownSignInTime() = runTest {
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        aSilentRecoverySetup(signedInAt = null).onSessionStarted()
        settle()
        assertThat(enableRecoveryCalls).isEmpty()
    }

    @Test
    fun emptyRecipientIsNoOp() = runTest {
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        val setup = aSilentRecoverySetup(isEnabled = false)
        setup.onSessionStarted()
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
        setup.onIdentityReset()
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
        settle()
        assertThat(enableRecoveryCalls).isEmpty()
        assertThat(firstStarts.decided).isEmpty()
    }

    @Test
    fun recoveryAlreadySetUp() = runTest {
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.ENABLED)
        val setup = aSilentRecoverySetup()
        setup.onSessionStarted()
        settle()
        assertThat(enableRecoveryCalls).isEmpty()
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
    }

    @Test
    fun waitsForTheKeyBackupOfTheNewIdentity() = runTest {
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        encryptionService.emitBackupState(BackupState.CREATING)
        val setup = aSilentRecoverySetup()
        setup.onSessionStarted()
        settle()
        advanceTimeBy(1_000)
        assertThat(enableRecoveryCalls).isEmpty()
        assertThat(setup.holdsBackRecoveryBanner.value).isTrue()
        encryptionService.emitBackupState(BackupState.ENABLED)
        settle()
        assertThat(enableRecoveryCalls).containsExactly(false to null)
    }

    @Test
    fun setsUpRecoveryEvenIfTheBackupNeverComes() = runTest {
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        aSilentRecoverySetup().onSessionStarted()
        settle()
        assertThat(enableRecoveryCalls).isEmpty()
        advanceTimeBy(SilentRecoverySetup.BACKUP_WAIT_MILLIS)
        settle()
        assertThat(enableRecoveryCalls).containsExactly(false to null)
    }

    @Test
    fun identityReset() = runTest {
        val setup = aSilentRecoverySetup()
        setup.onIdentityReset()
        assertThat(setup.holdsBackRecoveryBanner.value).isTrue()
        settle()
        assertThat(enableRecoveryCalls).isEmpty()
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        settle()
        assertThat(enableRecoveryCalls).containsExactly(false to null)
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
    }

    @Test
    fun identityResetReportedTwice() = runTest {
        val setup = aSilentRecoverySetup()
        setup.onIdentityReset()
        setup.onIdentityReset()
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        settle()
        assertThat(enableRecoveryCalls).hasSize(1)
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
    }

    @Test
    fun failureReleasesTheBanner() = runTest {
        val failingService = FakeEncryptionService(enableRecoveryLambda = { _, _ -> Result.failure(IllegalStateException("no")) })
        failingService.emitRecoveryState(RecoveryState.DISABLED)
        val setup = aSilentRecoverySetup(encryption = failingService)
        setup.onIdentityReset()
        settle()
        assertThat(setup.holdsBackRecoveryBanner.value).isFalse()
    }

    @Test
    fun startedOncePerSession() = runTest {
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.DISABLED)
        val setup = aSilentRecoverySetup()
        setup.onSessionStarted()
        setup.onSessionStarted()
        settle()
        assertThat(enableRecoveryCalls).hasSize(1)
    }

    private fun TestScope.settle() {
        advanceTimeBy(10)
        runCurrent()
    }

    private fun TestScope.aSilentRecoverySetup(
        isEnabled: Boolean = true,
        signedInAt: Long? = INSTALLED_AT + 1,
        encryption: FakeEncryptionService = encryptionService,
    ) = SilentRecoverySetup(
        isEnabled = isEnabled,
        userId = USER_ID,
        deviceId = DEVICE_ID,
        sessionVerificationService = verificationService,
        encryptionService = encryption,
        firstStarts = firstStarts,
        signedInAt = { signedInAt },
        appInstalledAt = { INSTALLED_AT },
        sessionCoroutineScope = backgroundScope,
    )

    private class InMemoryFirstStartRecords : FirstStartRecords {
        val decided = mutableMapOf<String, String>()

        override suspend fun isDecided(userId: String, deviceId: String) = decided[userId] == deviceId

        override suspend fun setDecided(userId: String, deviceId: String) {
            decided[userId] = deviceId
        }
    }

    private companion object {
        const val USER_ID = "@essai:chat.leveragers.com"
        const val DEVICE_ID = "NEWDEVICE"
        const val INSTALLED_AT = 1_764_000_000_000L
    }
}
