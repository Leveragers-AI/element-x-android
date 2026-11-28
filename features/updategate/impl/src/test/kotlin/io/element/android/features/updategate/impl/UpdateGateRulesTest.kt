/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val UPDATE_URL = "https://play.google.com/store/apps/details?id=com.leveragers.agents"
private const val NOW = 1_800_000_000_000L
private const val HOUR = 60 * 60 * 1000L
private const val DAY = 24 * HOUR

class UpdateGateRulesTest {
    private val noState = UpdateGateState(snoozedUntil = null, firstBehindAt = null)

    private fun policy(minBuild: Long, recommendedBuild: Long) = UpdatePolicy(
        minBuild = minBuild,
        recommendedBuild = recommendedBuild,
        updateUrl = UPDATE_URL,
    )

    private fun decide(
        ownBuild: Long?,
        policy: UpdatePolicy?,
        state: UpdateGateState = noState,
        now: Long = NOW,
        dialogShownThisLaunch: Boolean = false,
    ) = UpdateGateRules.decide(ownBuild, policy, now, state, dialogShownThisLaunch)

    @Test
    fun `nothing without a copy of the file or without the own build`() {
        assertThat(decide(ownBuild = 261100130, policy = null)).isEqualTo(UpdatePrompt.None)
        assertThat(decide(ownBuild = null, policy = policy(261100200, 261100300))).isEqualTo(UpdatePrompt.None)
    }

    @Test
    fun `thresholds at zero show nothing`() {
        assertThat(decide(ownBuild = 261100132, policy = policy(0, 0))).isEqualTo(UpdatePrompt.None)
    }

    @Test
    fun `below the minimum build, the blocking screen, whatever the dialog state`() {
        val policy = policy(minBuild = 261100150, recommendedBuild = 261100200)
        val blocking = UpdatePrompt.Blocking(UPDATE_URL)
        assertThat(decide(ownBuild = 261100149, policy = policy)).isEqualTo(blocking)
        assertThat(
            decide(
                ownBuild = 261100149,
                policy = policy,
                state = UpdateGateState(snoozedUntil = NOW + DAY, firstBehindAt = NOW - DAY),
                dialogShownThisLaunch = true,
            )
        ).isEqualTo(blocking)
    }

    @Test
    fun `from the minimum build and below the recommended one, the dialog`() {
        val policy = policy(minBuild = 261100150, recommendedBuild = 261100200)
        assertThat(decide(ownBuild = 261100150, policy = policy)).isEqualTo(UpdatePrompt.Dialog(UPDATE_URL))
        assertThat(decide(ownBuild = 261100199, policy = policy)).isEqualTo(UpdatePrompt.Dialog(UPDATE_URL))
    }

    @Test
    fun `from the recommended build, nothing`() {
        val policy = policy(minBuild = 261100150, recommendedBuild = 261100200)
        assertThat(decide(ownBuild = 261100200, policy = policy)).isEqualTo(UpdatePrompt.None)
        assertThat(decide(ownBuild = 261100204, policy = policy)).isEqualTo(UpdatePrompt.None)
    }

    @Test
    fun `the dialog is never shown twice in one launch`() {
        assertThat(
            decide(ownBuild = 261100150, policy = policy(0, 261100200), dialogShownThisLaunch = true)
        ).isEqualTo(UpdatePrompt.None)
    }

    @Test
    fun `the dialog waits for the end of Later`() {
        val policy = policy(0, 261100200)
        val snoozed = UpdateGateState(snoozedUntil = NOW + 1, firstBehindAt = NOW - DAY)
        assertThat(decide(ownBuild = 261100150, policy = policy, state = snoozed)).isEqualTo(UpdatePrompt.None)
        assertThat(decide(ownBuild = 261100150, policy = policy, state = snoozed, now = NOW + 1))
            .isEqualTo(UpdatePrompt.Dialog(UPDATE_URL))
    }

    @Test
    fun `a new recommended build does not cancel Later`() {
        val snoozed = UpdateGateRules.later(NOW, UpdateGateState(snoozedUntil = null, firstBehindAt = NOW))
        assertThat(decide(ownBuild = 261100150, policy = policy(0, 261100200), state = snoozed, now = NOW + HOUR))
            .isEqualTo(UpdatePrompt.None)
        assertThat(decide(ownBuild = 261100150, policy = policy(0, 261100300), state = snoozed, now = NOW + 2 * HOUR))
            .isEqualTo(UpdatePrompt.None)
    }

    @Test
    fun `the first date behind is kept from the first time the app sees it`() {
        val policy = policy(0, 261100200)
        val first = UpdateGateRules.trackBehind(261100150, policy, NOW, noState)
        assertThat(first).isEqualTo(UpdateGateState(snoozedUntil = null, firstBehindAt = NOW))
        assertThat(UpdateGateRules.trackBehind(261100150, policy(0, 261100300), NOW + DAY, first)).isEqualTo(first)
    }

    @Test
    fun `the first date behind is cleared once the app is no longer behind`() {
        val behind = UpdateGateState(snoozedUntil = NOW + DAY, firstBehindAt = NOW - DAY)
        assertThat(UpdateGateRules.trackBehind(261100200, policy(0, 261100200), NOW, behind))
            .isEqualTo(UpdateGateState(snoozedUntil = NOW + DAY, firstBehindAt = null))
    }

    @Test
    fun `the first date behind does not change without a copy of the file or without the own build`() {
        val behind = UpdateGateState(snoozedUntil = null, firstBehindAt = NOW - DAY)
        assertThat(UpdateGateRules.trackBehind(261100200, null, NOW, behind)).isEqualTo(behind)
        assertThat(UpdateGateRules.trackBehind(null, policy(0, 261100300), NOW, noState)).isEqualTo(noState)
    }

    @Test
    fun `Later hides the dialog for 3 days`() {
        assertThat(UpdateGateRules.later(NOW, noState).snoozedUntil).isEqualTo(NOW + 3 * DAY)
        val behindFor13Days = UpdateGateState(snoozedUntil = null, firstBehindAt = NOW - 14 * DAY + 1)
        assertThat(UpdateGateRules.later(NOW, behindFor13Days).snoozedUntil).isEqualTo(NOW + 3 * DAY)
    }

    @Test
    fun `Later hides the dialog for 1 day once behind for 14 days`() {
        val behindFor14Days = UpdateGateState(snoozedUntil = NOW - DAY, firstBehindAt = NOW - 14 * DAY)
        assertThat(UpdateGateRules.later(NOW, behindFor14Days))
            .isEqualTo(UpdateGateState(snoozedUntil = NOW + DAY, firstBehindAt = NOW - 14 * DAY))
    }

    @Test
    fun `the file is read at most once an hour`() {
        assertThat(UpdateGateRules.isCheckDue(lastCheckAt = null, now = NOW)).isTrue()
        assertThat(UpdateGateRules.isCheckDue(lastCheckAt = NOW, now = NOW + HOUR - 1)).isFalse()
        assertThat(UpdateGateRules.isCheckDue(lastCheckAt = NOW, now = NOW + HOUR)).isTrue()
        // A clock set back does not stop the reads.
        assertThat(UpdateGateRules.isCheckDue(lastCheckAt = NOW, now = NOW - 1)).isTrue()
    }
}
