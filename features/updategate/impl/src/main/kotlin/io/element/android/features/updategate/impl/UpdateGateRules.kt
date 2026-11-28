/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import androidx.compose.runtime.Immutable

/** What the update check shows. */
@Immutable
sealed interface UpdatePrompt {
    /** Nothing: the app as usual. */
    data object None : UpdatePrompt

    /** The blocking screen, instead of the app: this build is below the oldest one still allowed. */
    data class Blocking(val updateUrl: String) : UpdatePrompt

    /** The dialog, over the app: this build is below the recommended one. */
    data class Dialog(val updateUrl: String) : UpdatePrompt
}

/** What the phone keeps about the dialog. Times in milliseconds since the epoch. */
data class UpdateGateState(
    /** "Later" hides the dialog until this time. */
    val snoozedUntil: Long?,
    /** The first time the app saw its build below the recommended one; cleared once it no longer is. */
    val firstBehindAt: Long?,
)

/**
 * The rules of the update check, without any input or output, so that they can be tested alone.
 * An own build of null means the app could not read its versionCode: then nothing is shown.
 */
object UpdateGateRules {
    private const val HOUR_MILLIS = 60 * 60 * 1000L
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    /** The update file is read at most once in this time. */
    const val CHECK_INTERVAL_MILLIS = HOUR_MILLIS

    /** "Later" hides the dialog for this time... */
    const val SNOOZE_MILLIS = 3 * DAY_MILLIS

    /** ...and only for this time once the person has been behind for [LONG_BEHIND_MILLIS]. */
    const val SHORT_SNOOZE_MILLIS = DAY_MILLIS
    const val LONG_BEHIND_MILLIS = 14 * DAY_MILLIS

    /** Whether the update file should be read again. A clock set back counts as due. */
    fun isCheckDue(lastCheckAt: Long?, now: Long): Boolean {
        return lastCheckAt == null || now < lastCheckAt || now - lastCheckAt >= CHECK_INTERVAL_MILLIS
    }

    /**
     * What to show for the build [ownBuild] under [policy] (the last good copy of the update file, null without one).
     * The dialog is never shown twice in one launch ([dialogShownThisLaunch]) nor while "Later" holds; the blocking
     * screen ignores both. A new recommended build does not cancel "Later": [state] does not depend on [policy].
     */
    fun decide(
        ownBuild: Long?,
        policy: UpdatePolicy?,
        now: Long,
        state: UpdateGateState,
        dialogShownThisLaunch: Boolean,
    ): UpdatePrompt {
        if (ownBuild == null || policy == null) return UpdatePrompt.None
        if (ownBuild < policy.minBuild) return UpdatePrompt.Blocking(policy.updateUrl)
        if (ownBuild >= policy.recommendedBuild) return UpdatePrompt.None
        if (dialogShownThisLaunch) return UpdatePrompt.None
        val snoozedUntil = state.snoozedUntil
        if (snoozedUntil != null && now < snoozedUntil) return UpdatePrompt.None
        return UpdatePrompt.Dialog(policy.updateUrl)
    }

    /** [state] with the first date the app saw its build below the recommended one, cleared once it no longer is. */
    fun trackBehind(
        ownBuild: Long?,
        policy: UpdatePolicy?,
        now: Long,
        state: UpdateGateState,
    ): UpdateGateState {
        if (ownBuild == null || policy == null) return state
        val isBehind = ownBuild < policy.recommendedBuild
        return when {
            isBehind && state.firstBehindAt == null -> state.copy(firstBehindAt = now)
            !isBehind && state.firstBehindAt != null -> state.copy(firstBehindAt = null)
            else -> state
        }
    }

    /** [state] after "Later": 3 days, or 1 day once the person has been behind for 14 days. */
    fun later(now: Long, state: UpdateGateState): UpdateGateState {
        val firstBehindAt = state.firstBehindAt
        val isLongBehind = firstBehindAt != null && now - firstBehindAt >= LONG_BEHIND_MILLIS
        val snooze = if (isLongBehind) SHORT_SNOOZE_MILLIS else SNOOZE_MILLIS
        return state.copy(snoozedUntil = now + snooze)
    }
}
