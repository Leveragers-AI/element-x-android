/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import io.element.android.libraries.designsystem.R

/**
 * Leveragers: Sora, the display font of the Leveragers brand (already on the start screen of our Element Web), for
 * titles, as in the iOS app. A variable font (weights 100 to 800), under the SIL Open Font License 1.1
 * (see libraries/designsystem/fonts/OFL-Sora.txt).
 */
val SoraFontFamily = FontFamily(
    soraFont(FontWeight.Normal),
    soraFont(FontWeight.Medium),
    soraFont(FontWeight.SemiBold),
    soraFont(FontWeight.Bold),
)

private fun soraFont(weight: FontWeight) = Font(
    resId = R.font.sora,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * Leveragers: a title in Sora, semi bold by default.
 */
fun leveragersDisplayTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.SemiBold,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    fontFamily = SoraFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    letterSpacing = letterSpacing,
)
