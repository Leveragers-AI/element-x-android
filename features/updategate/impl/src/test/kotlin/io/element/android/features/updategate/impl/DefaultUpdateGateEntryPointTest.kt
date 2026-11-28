/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.preferences.test.FakePreferenceDataStoreFactory
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val OWN_BUILD = 261100150L
private const val UPDATE_URL = "https://play.google.com/store/apps/details?id=com.leveragers.agents"
private const val NOW = 1_800_000_000_000L
private const val HOUR = 60 * 60 * 1000L
private const val DAY = 24 * HOUR

class DefaultUpdateGateEntryPointTest {
    private val clock = FakeSystemClock(epochMillisResult = NOW)
    private val store = DefaultUpdateGateStore(FakePreferenceDataStoreFactory())
    private val fetcher = FakeUpdatePolicyFetcher()

    /** A new launch of the app: same phone storage, new in-memory state. */
    private fun TestScope.launchApp() = DefaultUpdateGateEntryPoint(
        store = store,
        fetcher = fetcher,
        buildMeta = aBuildMeta(versionCode = OWN_BUILD),
        systemClock = clock,
        appCoroutineScope = backgroundScope,
    )

    @Test
    fun `without the file nor a copy of it, the app shows as usual`() = runTest {
        val app = launchApp()
        assertThat(app.promptFlow.value).isNull()
        app.refresh()
        assertThat(fetcher.fetchCount).isEqualTo(1)
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.None)
    }

    @Test
    fun `a blocked person stays blocked offline`() = runTest {
        fetcher.result = UpdatePolicy(minBuild = OWN_BUILD + 10, recommendedBuild = OWN_BUILD + 10, updateUrl = UPDATE_URL)
        launchApp().refresh()

        fetcher.result = null
        clock.epochMillisResult = NOW + DAY
        val app = launchApp()
        app.refresh()
        assertThat(fetcher.fetchCount).isEqualTo(2)
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.Blocking(UPDATE_URL))
    }

    @Test
    fun `the file is read at most once an hour, across launches`() = runTest {
        launchApp().refresh()
        clock.epochMillisResult = NOW + HOUR - 1
        launchApp().refresh()
        assertThat(fetcher.fetchCount).isEqualTo(1)
        clock.epochMillisResult = NOW + HOUR
        launchApp().refresh()
        assertThat(fetcher.fetchCount).isEqualTo(2)
    }

    @Test
    fun `Later hides the dialog for this launch and the next 3 days`() = runTest {
        fetcher.result = UpdatePolicy(minBuild = 0, recommendedBuild = OWN_BUILD + 10, updateUrl = UPDATE_URL)
        val app = launchApp()
        app.refresh()
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.Dialog(UPDATE_URL))

        app.onDialogLater()?.join()
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.None)
        assertThat(store.read().state).isEqualTo(UpdateGateState(snoozedUntil = NOW + 3 * DAY, firstBehindAt = NOW))

        clock.epochMillisResult = NOW + 3 * DAY - 1
        val nextLaunch = launchApp()
        nextLaunch.refresh()
        assertThat(nextLaunch.promptFlow.value).isEqualTo(UpdatePrompt.None)

        clock.epochMillisResult = NOW + 3 * DAY
        val laterLaunch = launchApp()
        laterLaunch.refresh()
        assertThat(laterLaunch.promptFlow.value).isEqualTo(UpdatePrompt.Dialog(UPDATE_URL))
    }

    @Test
    fun `after Update, the dialog comes back only at the next launch`() = runTest {
        fetcher.result = UpdatePolicy(minBuild = 0, recommendedBuild = OWN_BUILD + 10, updateUrl = UPDATE_URL)
        val app = launchApp()
        app.refresh()
        app.onDialogUpdate()
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.None)

        clock.epochMillisResult = NOW + HOUR
        app.refresh()
        assertThat(fetcher.fetchCount).isEqualTo(2)
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.None)

        val nextLaunch = launchApp()
        nextLaunch.refresh()
        assertThat(nextLaunch.promptFlow.value).isEqualTo(UpdatePrompt.Dialog(UPDATE_URL))
    }

    @Test
    fun `a dialog on screen stays when the file is read again`() = runTest {
        fetcher.result = UpdatePolicy(minBuild = 0, recommendedBuild = OWN_BUILD + 10, updateUrl = UPDATE_URL)
        val app = launchApp()
        app.refresh()
        clock.epochMillisResult = NOW + HOUR
        app.refresh()
        assertThat(fetcher.fetchCount).isEqualTo(2)
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.Dialog(UPDATE_URL))
    }

    @Test
    fun `a raised minimum build turns the dialog into the blocking screen`() = runTest {
        fetcher.result = UpdatePolicy(minBuild = 0, recommendedBuild = OWN_BUILD + 10, updateUrl = UPDATE_URL)
        val app = launchApp()
        app.refresh()
        fetcher.result = UpdatePolicy(minBuild = OWN_BUILD + 10, recommendedBuild = OWN_BUILD + 10, updateUrl = UPDATE_URL)
        clock.epochMillisResult = NOW + HOUR
        app.refresh()
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.Blocking(UPDATE_URL))
    }

    @Test
    fun `an unreadable own build shows nothing`() = runTest {
        fetcher.result = UpdatePolicy(minBuild = OWN_BUILD + 10, recommendedBuild = OWN_BUILD + 10, updateUrl = UPDATE_URL)
        val app = DefaultUpdateGateEntryPoint(
            store = store,
            fetcher = fetcher,
            buildMeta = aBuildMeta(versionCode = 0),
            systemClock = clock,
            appCoroutineScope = backgroundScope,
        )
        app.refresh()
        assertThat(app.promptFlow.value).isEqualTo(UpdatePrompt.None)
    }

    private fun aBuildMeta(versionCode: Long) = BuildMeta(
        buildType = BuildType.RELEASE,
        isDebuggable = false,
        applicationName = "Agents",
        productionApplicationName = "Agents",
        desktopApplicationName = "Agents",
        applicationId = "com.leveragers.agents",
        isEnterpriseBuild = false,
        lowPrivacyLoggingEnabled = false,
        versionName = "26.11.0",
        versionCode = versionCode,
        gitRevision = "test",
        gitBranchName = "test",
        flavorDescription = "GooglePlay",
        flavorShortDescription = "G",
    )
}
