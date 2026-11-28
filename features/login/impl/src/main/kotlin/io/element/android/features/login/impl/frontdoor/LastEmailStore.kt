/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.frontdoor

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.Inject
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first

/**
 * Leveragers: the last email address the start screen sent to the front door, kept on the device to pre-fill the field
 * at the next sign-in (after a sign-out, for instance). Nothing else is kept: no server, so a new sign-in always asks
 * the front door again.
 */
@Inject
class LastEmailStore(
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
) {
    private val store = preferenceDataStoreFactory.create("leveragers_sign_in")

    suspend fun get(): String? = store.data.first()[lastEmailKey]

    suspend fun set(email: String) {
        store.edit { prefs ->
            prefs[lastEmailKey] = email
        }
    }
}

private val lastEmailKey = stringPreferencesKey("last_email")
