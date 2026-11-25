/*
 * Copyright (c) 2026 Leveragers.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.colors

import androidx.compose.ui.graphics.Color
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsLight

/**
 * Leveragers: the palette of the "Leveragers" custom theme of our Element Web `config.json`, as in the iOS app
 * (LeveragersTheme.swift). The web theme has no dark variant: the app always uses the light theme.
 */
object LeveragersColors {
    private val cream = Color(0xFFFAF8F4)
    private val navy = Color(0xFF0F1627)
    private val navyHovered = Color(0xFF1D2840)
    private val navyPressed = Color(0xFF0A0F1C)
    private val subtleCream = Color(0xFFEFEBE5)
    private val creamSelected = Color(0xFFEDE9E2)
    private val creamBorder = Color(0xFFE5E0D7)
    private val lavender = Color(0xFFE4E3F3)
    private val warmGrey = Color(0xFF6A645B)
    private val gradientSubtle = Color(0xFFEBE7E0)

    /** Background of the messages received, a notch lighter than the web one. */
    val bubbleIncoming = Color(0xFFF1EDE6)

    /** Background of the messages sent. */
    val bubbleOutgoing = Color(0xFFE7EDF6)

    /** Compound light colours with the Leveragers palette. */
    val light: SemanticColors = compoundColorsLight.copy(
        // Canvas: cream background, slightly darker grouped lists.
        bgCanvasDefault = cream,
        bgCanvasDefaultLevel1 = cream,
        bgSubtlePrimary = Color(0xFFEBE7E0),
        bgSubtleSecondary = subtleCream,
        bgSubtleSecondaryLevel0 = subtleCream,
        // Actions and accent: the web theme swaps Element's green for navy ink.
        bgActionPrimaryRest = navy,
        bgActionPrimaryHovered = navyHovered,
        bgActionPrimaryPressed = navyPressed,
        bgActionSecondaryRest = cream,
        bgActionSecondaryHovered = Color(0xFFF0EDE6),
        bgActionSecondaryPressed = Color(0xFFEBE7E0),
        bgActionTertiaryHovered = Color(0xFFF4F1EB),
        bgActionTertiarySelected = creamSelected,
        bgAccentRest = navy,
        bgAccentHovered = navyHovered,
        bgAccentPressed = navyPressed,
        // The web theme replaces Element's blue and green palettes with navy and cream,
        // so success, info and badge colours follow the accent.
        bgAccentSelected = creamSelected,
        bgAccentSubtle = subtleCream,
        borderAccentSubtle = creamBorder,
        bgBadgeAccent = lavender,
        textBadgeAccent = navy,
        bgBadgeInfo = lavender,
        textBadgeInfo = navy,
        bgInfoSubtle = subtleCream,
        borderInfoSubtle = creamBorder,
        textInfoPrimary = navy,
        iconInfoPrimary = navy,
        bgSuccessRest = navy,
        bgSuccessHovered = navyHovered,
        bgSuccessPressed = navyPressed,
        bgSuccessSubtle = subtleCream,
        borderSuccessPrimary = navy,
        borderSuccessSubtle = creamBorder,
        textSuccessPrimary = navy,
        textActionSuccess = navy,
        iconSuccessPrimary = navy,
        // Gradients: navy for actions, warm cream fading out for the subtle ones (home screen bloom).
        gradientActionStop1 = Color(0xFF25224F),
        gradientActionStop2 = navyHovered,
        gradientActionStop3 = navy,
        gradientActionStop4 = Color(0xFF0A082B),
        gradientInfoStop1 = subtleCream,
        gradientInfoStop2 = cream,
        gradientSubtleStop1 = gradientSubtle.copy(alpha = 0.9f),
        gradientSubtleStop2 = gradientSubtle.copy(alpha = 0.7f),
        gradientSubtleStop3 = gradientSubtle.copy(alpha = 0.5f),
        gradientSubtleStop4 = gradientSubtle.copy(alpha = 0.3f),
        gradientSubtleStop5 = gradientSubtle.copy(alpha = 0.1f),
        gradientSubtleStop6 = gradientSubtle.copy(alpha = 0f),
        // Text and icons.
        textPrimary = navy,
        textSecondary = warmGrey,
        textActionPrimary = navy,
        textActionAccent = navy,
        textLinkExternal = navy,
        iconPrimary = navy,
        iconSecondary = warmGrey,
        iconTertiary = Color(0xFF867F74),
        iconAccentPrimary = navy,
        iconAccentTertiary = navy,
        // Borders and separators.
        borderInteractiveSecondary = creamBorder,
        borderInteractiveHovered = Color(0xFFD6CFC4),
        borderAccentPrimary = navy,
        separatorPrimary = creamBorder,
        separatorSecondary = Color(0xFFF0EDE6),
        // Avatars and usernames.
        bgDecorative1 = lavender,
        textDecorative1 = Color(0xFF14115B),
        bgDecorative2 = Color(0xFFE1E8DF),
        textDecorative2 = Color(0xFF44624A),
        bgDecorative3 = Color(0xFFF0E0D8),
        textDecorative3 = Color(0xFF8A4B35),
        bgDecorative4 = Color(0xFFE0E5EE),
        textDecorative4 = Color(0xFF3E5378),
        bgDecorative5 = Color(0xFFE9E0EA),
        textDecorative5 = Color(0xFF6A4870),
        bgDecorative6 = Color(0xFFEDE6DA),
        textDecorative6 = Color(0xFF6B5A3E),
    )
}
