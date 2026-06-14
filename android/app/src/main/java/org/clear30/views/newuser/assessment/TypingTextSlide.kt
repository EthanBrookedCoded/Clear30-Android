package org.clear30.views.newuser.assessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/** One line of a [TypingTextSlide], with its style + opacity (iOS `TypingLine`). */
data class TypingLine(val text: String, val style: TypingTextStyle, val alpha: Float = 1f)

enum class TypingTextStyle { Heading3, Body, Small }

/**
 * TypingTextSlide — types each line out character-by-character (with a light
 * haptic per character), then auto-forwards via [onComplete]. Ported from
 * TypingTextSlide.swift, decoupled from the iOS page-focus plumbing: `AllAssessment`
 * only composes the current slide, so typing starts on first appearance.
 */
@Composable
fun TypingTextSlide(
    lines: List<TypingLine>,
    centerText: Boolean = true,
    onComplete: (() -> Unit)? = null,
) {
    val revealed = remember { mutableStateListOf(*Array(lines.size) { 0 }) }
    var visibleCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(INITIAL_DELAY)
        for (i in lines.indices) {
            visibleCount = i + 1
            val text = lines[i].text
            for (c in 1..text.length) {
                revealed[i] = c
                Haptics.lightImpact()
                delay(CHAR_DELAY)
            }
            delay(LINE_DELAY)
        }
        delay(COMPLETION_DELAY)
        onComplete?.invoke()
    }

    val align = if (centerText) Alignment.CenterHorizontally else Alignment.Start
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.horizontalPadding * 2),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        horizontalAlignment = align,
    ) {
        Spacer(Modifier.weight(1f))
        for (i in 0 until visibleCount) {
            val line = lines[i]
            val shown = line.text.take(revealed[i])
            val color = Clear30Colors.text.copy(alpha = line.alpha)
            when (line.style) {
                TypingTextStyle.Heading3 -> Heading3(shown, color = color)
                TypingTextStyle.Body -> DefaultText(shown, color = color)
                TypingTextStyle.Small -> SmallText(shown, color = color)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

private const val INITIAL_DELAY = 1000L
private const val CHAR_DELAY = 50L
private const val LINE_DELAY = 200L
private const val COMPLETION_DELAY = 1000L
