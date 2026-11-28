/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import android.content.Context
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.LeveragersConfig
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.coroutine.mapState
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.LeveragersRecoverySetup
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val SETUP_LOG_TAG = "SilentRecoverySetup"

/**
 * Leveragers: recovery set up without asking (see [LeveragersRecoverySetup]).
 *
 * "First start after a sign-in that created the identity": this device's first session start (no decision recorded for
 * its device id in [firstStarts]) after a sign-in made with the installed version of the app (sign-in time, from the
 * session store, not before the app's install or update time), whose first known verification state is "verified".
 * The SDK creates the identity at sign-in when the account has none, and the device is then verified at once; an
 * account that already has an identity starts "not verified" and is left alone, even after the person verifies the
 * device. The decision is recorded as soon as that first state is known, so a later start never acts.
 *
 * Recovery is then enabled only if it is disabled, through [EncryptionService.enableRecovery], which sends the copy of
 * the new key (RustEncryptionService, RecoveryKeyEscrow.kt).
 */
class SilentRecoverySetup(
    private val isEnabled: Boolean,
    private val userId: String,
    private val deviceId: String,
    private val sessionVerificationService: SessionVerificationService,
    private val encryptionService: EncryptionService,
    private val firstStarts: FirstStartRecords,
    /** Time of this session's sign-in (ms), if known. */
    private val signedInAt: suspend () -> Long?,
    /** Time the installed version of the app was installed or updated (ms), if known. */
    private val appInstalledAt: suspend () -> Long?,
    private val sessionCoroutineScope: CoroutineScope,
    private val backupWaitMillis: Long = BACKUP_WAIT_MILLIS,
) : LeveragersRecoverySetup {
    private val runningSetups = MutableStateFlow(0)
    private val started = AtomicBoolean(false)
    private val isSettingUpAfterReset = AtomicBoolean(false)

    override val holdsBackRecoveryBanner: StateFlow<Boolean> = runningSetups.mapState { it > 0 }

    override fun onSessionStarted() {
        if (!isEnabled || !started.compareAndSet(false, true)) return
        // Listen right away, so that this start's first verification state can't be missed.
        val firstVerifiedStatus = sessionCoroutineScope.async(start = CoroutineStart.UNDISPATCHED) {
            sessionVerificationService.sessionVerifiedStatus.first { it != SessionVerifiedStatus.Unknown }
        }
        runHoldingBackBanner {
            try {
                setUpAtFirstStartAfterSignIn(firstVerifiedStatus)
            } finally {
                firstVerifiedStatus.cancel()
            }
        }
    }

    override fun onIdentityReset() {
        // Once at a time: the reset screen may report the same reset twice (screen rebuilt while waiting).
        if (!isEnabled || !isSettingUpAfterReset.compareAndSet(false, true)) return
        runHoldingBackBanner {
            try {
                Timber.tag(SETUP_LOG_TAG).i("Identity reset: setting up recovery")
                setUpIfRecoveryIsDisabled()
            } finally {
                isSettingUpAfterReset.set(false)
            }
        }
    }

    private fun runHoldingBackBanner(block: suspend () -> Unit) {
        runningSetups.update { it + 1 }
        sessionCoroutineScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(SETUP_LOG_TAG).w("Recovery not set up: ${e.javaClass.simpleName}")
            } finally {
                runningSetups.update { it - 1 }
            }
        }
    }

    private suspend fun setUpAtFirstStartAfterSignIn(firstVerifiedStatus: Deferred<SessionVerifiedStatus>) {
        if (firstStarts.isDecided(userId, deviceId)) return
        val signedInAt = signedInAt()
        val installedAt = appInstalledAt()
        if (signedInAt == null || installedAt == null || signedInAt < installedAt) {
            // A session from before this version of the app: not a sign-in this code has seen from its start.
            firstStarts.setDecided(userId, deviceId)
            return
        }
        val status = firstVerifiedStatus.await()
        firstStarts.setDecided(userId, deviceId)
        if (status != SessionVerifiedStatus.Verified) {
            Timber.tag(SETUP_LOG_TAG).i("New sign-in of an account that has an identity: recovery left to the person")
            return
        }
        Timber.tag(SETUP_LOG_TAG).i("New sign-in that created the identity: setting up recovery")
        // The SDK creates the key backup right after the identity: let it finish, so that recovery uses that backup
        // instead of creating a second one.
        withTimeoutOrNull(backupWaitMillis) {
            encryptionService.backupStateStateFlow.first { it == BackupState.ENABLED }
        }
        setUpIfRecoveryIsDisabled()
    }

    private suspend fun setUpIfRecoveryIsDisabled() {
        val recoveryState = encryptionService.recoveryStateStateFlow.first {
            it != RecoveryState.UNKNOWN && it != RecoveryState.WAITING_FOR_SYNC
        }
        if (recoveryState != RecoveryState.DISABLED) {
            Timber.tag(SETUP_LOG_TAG).i("Recovery is $recoveryState: nothing to set up")
            return
        }
        encryptionService.enableRecovery(waitForBackupsToUpload = false)
            .onSuccess { Timber.tag(SETUP_LOG_TAG).i("Recovery set up") }
            .onFailure { Timber.tag(SETUP_LOG_TAG).w("Recovery not set up: ${it.javaClass.simpleName}") }
    }

    companion object {
        /** How long to wait for the key backup the SDK creates with a new identity. */
        const val BACKUP_WAIT_MILLIS = 60_000L
    }
}

/** Which device of each account has had its first start after sign-in decided. */
interface FirstStartRecords {
    suspend fun isDecided(userId: String, deviceId: String): Boolean
    suspend fun setDecided(userId: String, deviceId: String)
}

/** [FirstStartRecords] in the app's private preferences: one device id per account, the last one decided. */
class SharedPreferencesFirstStartRecords(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : FirstStartRecords {
    private val preferences by lazy { context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }

    override suspend fun isDecided(userId: String, deviceId: String): Boolean = withContext(ioDispatcher) {
        preferences.getString(userId, null) == deviceId
    }

    override suspend fun setDecided(userId: String, deviceId: String) {
        withContext(ioDispatcher) {
            // Written at once: a start that follows a crash must find it.
            preferences.edit().putString(userId, deviceId).commit()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "leveragers_recovery_setup"
    }
}

/** The app's [LeveragersRecoverySetup], one per session. */
@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultLeveragersRecoverySetup(
    matrixClient: MatrixClient,
    sessionStore: SessionStore,
    @ApplicationContext context: Context,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
    dispatchers: CoroutineDispatchers,
) : LeveragersRecoverySetup by SilentRecoverySetup(
    isEnabled = LeveragersConfig.RECOVERY_KEY_RECIPIENT.isNotBlank(),
    userId = matrixClient.sessionId.value,
    deviceId = matrixClient.deviceId.value,
    sessionVerificationService = matrixClient.sessionVerificationService,
    encryptionService = matrixClient.encryptionService,
    firstStarts = SharedPreferencesFirstStartRecords(context, dispatchers.io),
    signedInAt = { sessionStore.getSession(matrixClient.sessionId.value)?.loginTimestamp?.time },
    appInstalledAt = {
        withContext(dispatchers.io) {
            runCatchingExceptions {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            }.getOrNull()
        }
    },
    sessionCoroutineScope = sessionCoroutineScope,
)
