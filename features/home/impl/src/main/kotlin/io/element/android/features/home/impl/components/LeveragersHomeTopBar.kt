/*
 * Copyright (c) 2026 Leveragers.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.appconfig.ApplicationConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.modifiers.backgroundVerticalGradient
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.theme.leveragersDisplayTextStyle
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Leveragers: the header of the simplified home screen, like our Voice app and the iOS app: the Leveragers logo next
 * to the app name and its signature, then the settings and new room buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeveragersHomeTopBar(
    onOpenSettings: () -> Unit,
    onStartChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    TopAppBar(
        modifier = modifier
            .backgroundVerticalGradient()
            .statusBarsPadding()
            .padding(contentPadding),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.leveragers_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // The same sizes as Voice's header.
                    Text(
                        text = ApplicationConfig.PRODUCTION_APPLICATION_NAME,
                        style = leveragersDisplayTextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
                        color = ElementTheme.colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = stringResource(R.string.screen_roomlist_brand_tagline),
                        style = leveragersDisplayTextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        },
        actions = {
            // The same settings icon as Element Web.
            RoundHeaderButton(
                imageVector = CompoundIcons.SettingsSolid(),
                contentDescription = stringResource(CommonStrings.common_settings),
                backgroundColor = ElementTheme.colors.bgSubtleSecondary,
                iconTint = ElementTheme.colors.iconPrimary,
                iconSize = 20.dp,
                onClick = onOpenSettings,
            )
            Spacer(Modifier.width(8.dp))
            RoundHeaderButton(
                imageVector = CompoundIcons.Plus(),
                contentDescription = stringResource(CommonStrings.action_start_chat),
                backgroundColor = ElementTheme.colors.bgActionPrimaryRest,
                iconTint = ElementTheme.colors.iconOnSolidPrimary,
                iconSize = 24.dp,
                onClick = onStartChatClick,
            )
            Spacer(Modifier.width(12.dp))
        },
        windowInsets = WindowInsets(left = 4.dp),
    )
}

@Composable
private fun RoundHeaderButton(
    imageVector: ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    iconTint: Color,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Leveragers: the room list search, floating at the bottom of the simplified home screen, as in the iOS app.
 */
@Composable
fun LeveragersHomeSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.Search(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(CommonStrings.action_search),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun LeveragersHomeTopBarPreview() = ElementPreview {
    LeveragersHomeTopBar(
        onOpenSettings = {},
        onStartChatClick = {},
    )
}
