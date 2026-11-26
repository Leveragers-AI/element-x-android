/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.typing

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow

private val bubbleHeight = 28.dp
private val bubbleVerticalPadding = 4.dp

/**
 * Leveragers: "… is typing" as on Element Desktop, the avatars of the people typing then a pill with Element Web's
 * animated dots, without the names, which screen readers still read, as in the iOS app.
 * Once shown, its room stays so that the timeline doesn't jump each time someone types.
 */
@Composable
internal fun TypingBubble(
    typingMembers: ImmutableList<TypingRoomMember>,
    visible: Boolean,
    reserveSpace: Boolean,
    accessibilityText: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (visible || reserveSpace) bubbleHeight + bubbleVerticalPadding * 2 else 0.dp),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
        ) {
            // Remember the last members to keep the avatars while fading out.
            val members = remember(typingMembers) { typingMembers }
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, top = bubbleVerticalPadding, bottom = bubbleVerticalPadding)
                    .semantics(mergeDescendants = true) { contentDescription = accessibilityText },
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypingAvatars(members)
                Box(
                    modifier = Modifier
                        .height(bubbleHeight)
                        .background(ElementTheme.colors.messageFromOtherBackground, CircleShape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TypingDots()
                }
            }
        }
    }
}

@Composable
private fun TypingAvatars(members: ImmutableList<TypingRoomMember>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        members.take(3).forEach { member ->
            Avatar(
                modifier = Modifier
                    .background(ElementTheme.colors.bgCanvasDefault, CircleShape)
                    .padding(1.dp),
                avatarData = AvatarData(
                    id = member.userId,
                    name = member.disambiguatedDisplayName,
                    url = member.avatarUrl,
                    size = AvatarSize.TypingIndicator,
                ),
                avatarType = AvatarType.User,
            )
        }
    }
}

// Element Web's `lv-frappe` animation: dots 6 dp wide, 10 dp apart, a wave every 1.33 s that lifts, enlarges and
// darkens each dot in turn. The dots stay still when the animations are turned off.
private const val DOTS_PERIOD_MILLIS = 1330
private val dotPeaks = listOf(0.5f, 2f / 3f, 5f / 6f)
private val restingOpacities = listOf(0.35f, 0.6f, 0.85f)

@Composable
private fun TypingDots() {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val phase = if (animationsEnabled) {
        rememberInfiniteTransition(label = "typingDots").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(DOTS_PERIOD_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "typingDotsPhase",
        )
    } else {
        null
    }
    val color = ElementTheme.colors.textPrimary
    val liftDistance = with(LocalDensity.current) { 1.5.dp.toPx() }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        val currentPhase = phase?.value
                        if (currentPhase == null) {
                            alpha = restingOpacities[index]
                        } else {
                            val lift = lift(currentPhase, dotPeaks[index])
                            alpha = 0.45f + 0.55f * lift
                            scaleX = 1 + 0.25f * lift
                            scaleY = scaleX
                            translationY = liftDistance * (0.5f - lift)
                        }
                    }
                    .background(color, CircleShape)
            )
        }
    }
}

/** How far a dot is in its wave, from 0 (resting) to 1 (top), matching the keyframes of Element Web. */
private fun lift(phase: Float, peak: Float): Float {
    val distance = abs(phase - peak).let { min(it, 1 - it) }
    return ((1 + cos(2 * PI * distance)) / 2).pow(1.5).toFloat()
}
