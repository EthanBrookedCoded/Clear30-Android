package org.clear30.views.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Lexend

/**
 * Buttons — ported from Buttons.swift (core set). The press behavior recreates
 * `DefaultButtonStyle`: scale to 0.9 while pressed (spring) + medium haptic on
 * tap, no opacity change (`NoOpacityChangeButtonStyle`).
 */

/** Press-to-shrink + haptic click, ported from DefaultButtonStyle. */
@Composable
fun Modifier.pressScale(haptic: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "pressScale")
    val hapticFeedback = LocalHapticFeedback.current
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null) {
            if (haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
}

/** DefaultButton — pill text button, gradient or solid (DefaultButtonTextStyle). */
@Composable
fun DefaultButton(
    title: String,
    gradient: Brush? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = gradient ?: Clear30Gradients.button
    Box(modifier.padding(16.dp).pressScale(onClick = onClick)) {
        Text(
            text = title,
            modifier = Modifier
                .clip(RoundedCornerShape(15.dp))
                .background(bg)
                .padding(horizontal = 25.dp, vertical = 15.dp),
            color = if (gradient != null) Color.White else Clear30Colors.text,
            fontFamily = Lexend,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
        )
    }
}

/** StretchedButton — full-width version (StretchedButtonTextStyle, corner 21). */
@Composable
fun StretchedButton(
    title: String,
    gradient: Brush? = null,
    weight: FontWeight = FontWeight.SemiBold,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = gradient ?: Clear30Gradients.button
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(bg)
            .padding(horizontal = 25.dp, vertical = 15.dp),
        color = if (gradient != null) Color.White else Clear30Colors.text,
        fontFamily = Lexend,
        fontWeight = weight,
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
    )
}

/** IconButton — tappable SF-symbol icon (IconButton). */
@Composable
fun IconButton(
    icon: String = "chevron.backward",
    height: androidx.compose.ui.unit.Dp = 20.dp,
    haptic: Boolean = true,
    padding: androidx.compose.ui.unit.Dp = 10.dp,
    tint: Color = Clear30Colors.text,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = sfSymbol(icon),
        contentDescription = icon,
        tint = tint,
        modifier = modifier.pressScale(haptic = haptic, onClick = onClick).padding(padding).size(height),
    )
}

/** GradientActionButton — full-width gradient card button with leading icon. */
@Composable
fun GradientActionButton(
    iconName: String,
    gradient: Brush,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Clear30Card(modifier = modifier.fillMaxWidth().pressScale(onClick = onClick), gradient = gradient) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Box(
                Modifier.size(36.dp)
                    .clip(RoundedCornerShape(Dimens.cornerRadius / 1.75f))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sfSymbol(iconName), contentDescription = title, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            SmallText(title, color = Color.White)
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Random blue/green gradient, ported from `getGradient()`. */
fun randomGradient(): Brush {
    val blueFirst = Math.random() <= 0.5
    val colors = if (blueFirst) listOf(Clear30Colors.blue, Clear30Colors.green)
    else listOf(Clear30Colors.green, Clear30Colors.blue)
    return Clear30Gradients.linear(colors, Clear30Gradients.topLeading, Clear30Gradients.bottomTrailing)
}
