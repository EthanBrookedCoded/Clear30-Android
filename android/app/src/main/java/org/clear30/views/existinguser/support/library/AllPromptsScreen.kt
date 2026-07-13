package org.clear30.views.existinguser.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramClairePrompt
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * AllPromptsScreen — the Claire prompts library (port of iOS `AllPromptsView`):
 * unlocked prompts filtered by program break (tabs, like the other libraries),
 * sectioned by stage with the latest stage first, laid out as a 2-column grid
 * of prompt cards. Tapping a card opens Claire pre-seeded with the prompt's
 * underlying text (pre-filled, not auto-sent — iOS `initialMessage`).
 */
@Composable
fun AllPromptsScreen(program: Program, onBack: () -> Unit, onOpenPrompt: (ProgramClairePrompt) -> Unit) {
    val tabs = remember(program.contentInfo.size) {
        buildLibraryTabs(program) { msgs -> msgs.flatMap { it.clairePrompts }.distinctBy { it.id } }
    }
    val defaultIndex = remember(tabs) { defaultLibraryTab(program, tabs) }
    var selected by remember(tabs) { mutableStateOf(defaultIndex) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1("Claire Prompts")
        }

        if (tabs.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(
                    "Claire prompts will appear here as your content unlocks.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
            return
        }

        if (tabs.size > 1) {
            LibraryTabRow(tabs.map { it.name }, selected, Clear30Gradients.claire) { selected = it }
        }
        val tab = tabs.getOrNull(selected) ?: tabs.first()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            // iOS renders stage sections latest-first (AllPromptsView reversed loop).
            tab.sections.reversed().forEach { section ->
                StageHeaderCard(section.stage, Clear30Gradients.claire)
                PromptGrid(section.items, onOpenPrompt)
            }
            Spacer(Modifier.size(Dimens.cardSpacing))
        }
    }
}

/** 2-column grid of prompt cards (iOS `GridView(columns: 2)`). */
@Composable
private fun PromptGrid(prompts: List<ProgramClairePrompt>, onOpen: (ProgramClairePrompt) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        prompts.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                rowItems.forEach { prompt ->
                    Box(Modifier.weight(1f).fillMaxHeight()) { PromptCard(prompt) { onOpen(prompt) } }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One prompt card (iOS `PromptCard`): sparkles + "Claire", title, "Start chat" pill. */
@Composable
private fun PromptCard(prompt: ProgramClairePrompt, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                Icon(sfSymbol("sparkles"), null, tint = Clear30Colors.claire1, modifier = Modifier.size(14.dp))
                TinyText("Claire", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            SmallText(prompt.title, maxLines = 4)
            Row(
                Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Colors.opacityGray)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TinyText("Start chat", color = Clear30Colors.claire1)
                Icon(sfSymbol("arrow.right"), null, tint = Clear30Colors.claire1, modifier = Modifier.size(11.dp))
            }
        }
    }
}
