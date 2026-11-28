/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import io.element.android.features.updategate.api.UpdateGateEntryPoint

@ContributesTo(AppScope::class)
interface UpdateGateBindings {
    fun updateGateEntryPoint(): UpdateGateEntryPoint
}
