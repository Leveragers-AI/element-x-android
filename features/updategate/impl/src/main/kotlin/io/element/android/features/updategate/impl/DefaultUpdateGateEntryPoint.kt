/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.updategate.api.UpdateGateEntryPoint
import io.element.android.features.updategate.impl.ui.UpdateAvailableDialog
import io.element.android.features.updategate.impl.ui.UpdateGateTheme
import io.element.android.features.updategate.impl.ui.UpdateRequiredView
import io.element.android.libraries.androidutils.system.openUrlInExternalApp
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultUpdateGateEntryPoint(
    private val store: UpdateGateStore,
    private val fetcher: UpdatePolicyFetcher,
    private val buildMeta: BuildMeta,
    private val systemClock: SystemClock,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : UpdateGateEntryPoint {
    private val mutex = Mutex()

    /** Null until the copy kept on the phone has been read. */
    private val prompt = MutableStateFlow<UpdatePrompt?>(null)
    internal val promptFlow: StateFlow<UpdatePrompt?> = prompt

    /** In memory only: a new launch of the app may show the dialog again. */
    private var dialogShownThisLaunch = false

    override fun check() {
        appCoroutineScope.launch { refresh() }
    }

    @Composable
    override fun Render(content: @Composable () -> Unit) {
        val shownPrompt by prompt.collectAsState()
        val context = LocalContext.current
        val currentPrompt = shownPrompt
        if (currentPrompt is UpdatePrompt.Blocking) {
            UpdateGateTheme(applySystemBarsUpdate = true) {
                UpdateRequiredView(
                    onUpdateClick = { context.openUrlInExternalApp(currentPrompt.updateUrl) },
                )
            }
        } else if (currentPrompt != null) {
            // The content stays in the same place with or without the dialog, so that the dialog leaves the app as is.
            content()
            if (currentPrompt is UpdatePrompt.Dialog) {
                UpdateGateTheme(applySystemBarsUpdate = false) {
                    UpdateAvailableDialog(
                        onUpdateClick = {
                            context.openUrlInExternalApp(currentPrompt.updateUrl)
                            onDialogUpdate()
                        },
                        onLaterClick = { onDialogLater() },
                    )
                }
            }
        }
    }

    internal suspend fun refresh() {
        mutex.withLock {
            runCatchingExceptions { refreshLocked() }
                .onFailure { Timber.w(it, "Update check failed") }
            // Without a decision, the app as usual: the check never keeps anyone out of the app by mistake.
            if (prompt.value == null) prompt.value = UpdatePrompt.None
        }
    }

    private suspend fun refreshLocked() {
        val saved = store.read()
        // First the copy kept on the phone, without waiting for the network.
        if (prompt.value == null) publish(saved)
        val now = systemClock.epochMillis()
        if (!UpdateGateRules.isCheckDue(saved.lastCheckAt, now)) return
        store.saveLastCheckAt(now)
        // Missing or unreadable file, network error: the last good copy stays.
        val policy = fetcher.fetch() ?: return
        store.savePolicy(policy)
        publish(store.read())
    }

    private suspend fun publish(saved: StoredUpdateGate) {
        val now = systemClock.epochMillis()
        val ownBuild = buildMeta.versionCode.takeIf { it > 0 }
        val state = UpdateGateRules.trackBehind(ownBuild, saved.policy, now, saved.state)
        if (state != saved.state) store.saveState(state)
        // The dialog already on screen is not a second showing: it stays until answered.
        val isDialogShownBefore = dialogShownThisLaunch && prompt.value !is UpdatePrompt.Dialog
        val decision = UpdateGateRules.decide(ownBuild, saved.policy, now, state, isDialogShownBefore)
        if (decision is UpdatePrompt.Dialog) dialogShownThisLaunch = true
        prompt.value = decision
    }

    /** "Update" in the dialog: the update page is open, the dialog goes until the next launch. */
    internal fun onDialogUpdate() {
        if (prompt.value is UpdatePrompt.Dialog) prompt.value = UpdatePrompt.None
    }

    /** "Later" in the dialog (or the dialog dismissed): hidden for 3 days, or 1 day after 14 days behind. */
    internal fun onDialogLater(): Job? {
        if (prompt.value !is UpdatePrompt.Dialog) return null
        prompt.value = UpdatePrompt.None
        return appCoroutineScope.launch {
            mutex.withLock {
                runCatchingExceptions {
                    val saved = store.read()
                    store.saveState(UpdateGateRules.later(systemClock.epochMillis(), saved.state))
                }.onFailure { Timber.w(it, "Update snooze not saved") }
            }
        }
    }
}
