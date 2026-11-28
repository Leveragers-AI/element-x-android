/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.element.android.features.updategate.api.UpdateGateEntryPoint
import io.element.android.libraries.architecture.bindings

/**
 * Leveragers: [content] behind the update check (see [UpdateGateEntryPoint.Render]). MainActivity wraps the whole
 * app in it.
 */
@Composable
fun UpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val updateGateEntryPoint = remember(context) { context.bindings<UpdateGateBindings>().updateGateEntryPoint() }
    updateGateEntryPoint.Render(content)
}

/** Leveragers: reads the update file again, at most once an hour. MainActivity calls it in onResume. */
fun Context.checkForAppUpdate() {
    bindings<UpdateGateBindings>().updateGateEntryPoint().check()
}
