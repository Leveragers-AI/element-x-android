/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import io.element.android.libraries.preferences.test.FakePreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import androidx.datastore.preferences.core.PreferenceDataStoreFactory as AndroidPreferenceDataStoreFactory

class DefaultUpdateGateStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** A store on [file], so that a second store can read what a first one wrote, as after a restart of the app. */
    private class FilePreferenceDataStoreFactory(
        private val file: File,
        private val scope: CoroutineScope,
    ) : PreferenceDataStoreFactory {
        override fun create(name: String): DataStore<Preferences> {
            return AndroidPreferenceDataStoreFactory.create(scope = scope) { file }
        }
    }

    @Test
    fun `a new install keeps nothing`() = runTest {
        val store = DefaultUpdateGateStore(FakePreferenceDataStoreFactory())
        assertThat(store.read()).isEqualTo(
            StoredUpdateGate(
                policy = null,
                lastCheckAt = null,
                state = UpdateGateState(snoozedUntil = null, firstBehindAt = null),
            )
        )
    }

    @Test
    fun `what is saved is read again after a restart`() = runTest {
        val file = File(temporaryFolder.root, "leveragers_update_gate.preferences_pb")
        val policy = UpdatePolicy(
            minBuild = 261100130,
            recommendedBuild = 261100150,
            updateUrl = "https://play.google.com/store/apps/details?id=com.leveragers.agents",
        )
        val state = UpdateGateState(snoozedUntil = 2_000L, firstBehindAt = 500L)

        val firstScope = CoroutineScope(Dispatchers.IO + Job())
        val first = DefaultUpdateGateStore(FilePreferenceDataStoreFactory(file, firstScope))
        first.savePolicy(policy)
        first.saveLastCheckAt(1_000L)
        first.saveState(state)
        firstScope.coroutineContext.job.cancelAndJoin()

        val secondScope = CoroutineScope(Dispatchers.IO + Job())
        val second = DefaultUpdateGateStore(FilePreferenceDataStoreFactory(file, secondScope))
        assertThat(second.read()).isEqualTo(StoredUpdateGate(policy = policy, lastCheckAt = 1_000L, state = state))
        secondScope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun `a newer copy of the file replaces the older one`() = runTest {
        val store = DefaultUpdateGateStore(FakePreferenceDataStoreFactory())
        store.savePolicy(UpdatePolicy(minBuild = 0, recommendedBuild = 0, updateUrl = "https://example.com/a"))
        val newer = UpdatePolicy(minBuild = 261100130, recommendedBuild = 261100150, updateUrl = "https://example.com/b")
        store.savePolicy(newer)
        assertThat(store.read().policy).isEqualTo(newer)
    }

    @Test
    fun `dates cleared in the state are removed`() = runTest {
        val store = DefaultUpdateGateStore(FakePreferenceDataStoreFactory())
        store.saveState(UpdateGateState(snoozedUntil = 2_000L, firstBehindAt = 500L))
        store.saveState(UpdateGateState(snoozedUntil = 2_000L, firstBehindAt = null))
        assertThat(store.read().state).isEqualTo(UpdateGateState(snoozedUntil = 2_000L, firstBehindAt = null))
        store.saveState(UpdateGateState(snoozedUntil = null, firstBehindAt = null))
        assertThat(store.read().state).isEqualTo(UpdateGateState(snoozedUntil = null, firstBehindAt = null))
    }
}
