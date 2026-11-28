/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

class FakeUpdatePolicyFetcher(
    var result: UpdatePolicy? = null,
) : UpdatePolicyFetcher {
    var fetchCount = 0
        private set

    override suspend fun fetch(): UpdatePolicy? {
        fetchCount++
        return result
    }
}
