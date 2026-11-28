/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first

/** What the phone keeps: the last good copy of the update file, its last read, the dialog state. */
data class StoredUpdateGate(
    val policy: UpdatePolicy?,
    val lastCheckAt: Long?,
    val state: UpdateGateState,
)

interface UpdateGateStore {
    suspend fun read(): StoredUpdateGate

    suspend fun savePolicy(policy: UpdatePolicy)

    suspend fun saveLastCheckAt(lastCheckAt: Long)

    suspend fun saveState(state: UpdateGateState)
}

private val minBuildKey = longPreferencesKey("min_build")
private val recommendedBuildKey = longPreferencesKey("recommended_build")
private val updateUrlKey = stringPreferencesKey("update_url")
private val lastCheckAtKey = longPreferencesKey("last_check_at")
private val snoozedUntilKey = longPreferencesKey("snoozed_until")
private val firstBehindAtKey = longPreferencesKey("first_behind_at")

@ContributesBinding(AppScope::class)
class DefaultUpdateGateStore(
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
) : UpdateGateStore {
    private val store = preferenceDataStoreFactory.create("leveragers_update_gate")

    override suspend fun read(): StoredUpdateGate {
        val prefs = store.data.first()
        val minBuild = prefs[minBuildKey]
        val recommendedBuild = prefs[recommendedBuildKey]
        val updateUrl = prefs[updateUrlKey]
        return StoredUpdateGate(
            policy = if (minBuild != null && recommendedBuild != null && updateUrl != null) {
                UpdatePolicy(minBuild = minBuild, recommendedBuild = recommendedBuild, updateUrl = updateUrl)
            } else {
                null
            },
            lastCheckAt = prefs[lastCheckAtKey],
            state = UpdateGateState(
                snoozedUntil = prefs[snoozedUntilKey],
                firstBehindAt = prefs[firstBehindAtKey],
            ),
        )
    }

    override suspend fun savePolicy(policy: UpdatePolicy) {
        store.edit { prefs ->
            prefs[minBuildKey] = policy.minBuild
            prefs[recommendedBuildKey] = policy.recommendedBuild
            prefs[updateUrlKey] = policy.updateUrl
        }
    }

    override suspend fun saveLastCheckAt(lastCheckAt: Long) {
        store.edit { prefs ->
            prefs[lastCheckAtKey] = lastCheckAt
        }
    }

    override suspend fun saveState(state: UpdateGateState) {
        store.edit { prefs ->
            val snoozedUntil = state.snoozedUntil
            if (snoozedUntil != null) prefs[snoozedUntilKey] = snoozedUntil else prefs.remove(snoozedUntilKey)
            val firstBehindAt = state.firstBehindAt
            if (firstBehindAt != null) prefs[firstBehindAtKey] = firstBehindAt else prefs.remove(firstBehindAtKey)
        }
    }
}
