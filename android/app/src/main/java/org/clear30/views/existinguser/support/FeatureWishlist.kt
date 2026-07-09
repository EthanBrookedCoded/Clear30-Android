package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.FeatureIdea
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getFeatureIdeaVotes
import org.clear30.data.supabase.getFeatureIdeas
import org.clear30.data.supabase.removeFeatureIdeaVote
import org.clear30.data.supabase.submitFeatureIdeaVote
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading2
import org.clear30.views.components.IconButton
import org.clear30.views.components.LoadingIcon
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.colorFromHex

/**
 * FeatureWishlist — ported from FeatureIdeas.swift. Lists community feature ideas
 * (comms.feature_ideas) sorted by score; each can be up/down-voted
 * (comms.feature_idea_votes, optimistic with rollback on failure).
 */
@Composable
fun FeatureWishlist(userInfo: UserInfo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ideas by remember { mutableStateOf<List<FeatureIdea>>(emptyList()) }
    var votes by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }     // featureId -> -1/0/1
    var adjust by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }    // local score delta
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        SupabaseController.getFeatureIdeas().onSuccess { ideas = it }
        SupabaseController.getFeatureIdeaVotes(userInfo.userID).onSuccess { v -> votes = v.associate { it.featureIdeaID to it.vote } }
        loading = false
    }

    fun applyVote(id: Int, newVote: Int) {
        val prev = votes[id] ?: 0
        val target = if (prev == newVote) 0 else newVote // tapping the active arrow clears the vote
        val delta = target - prev
        votes = votes + (id to target)
        adjust = adjust + (id to ((adjust[id] ?: 0) + delta))
        scope.launch {
            val err = if (target == 0) SupabaseController.removeFeatureIdeaVote(userInfo.userID, id)
            else SupabaseController.submitFeatureIdeaVote(userInfo.userID, id, target)
            if (err != null) { // rollback
                votes = votes + (id to prev)
                adjust = adjust + (id to ((adjust[id] ?: 0) - delta))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2("Feature Wishlist")
        }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIcon() }
            ideas.isEmpty() -> Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText("No feature ideas yet.", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(top = Dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                items(ideas, key = { it.id }) { idea ->
                    FeatureIdeaCard(
                        idea = idea,
                        myVote = votes[idea.id] ?: 0,
                        score = idea.score + (adjust[idea.id] ?: 0),
                        onUp = { applyVote(idea.id, 1) },
                        onDown = { applyVote(idea.id, -1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureIdeaCard(idea: FeatureIdea, myVote: Int, score: Int, onUp: () -> Unit, onDown: () -> Unit) {
    val accent = runCatching { idea.hexColor?.let { colorFromHex(it) } }.getOrNull() ?: Clear30Colors.claire1
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing), verticalAlignment = Alignment.CenterVertically) {
            // Vote pill — accent-tinted, arrows light up with the user's vote.
            Column(
                Modifier.clip(RoundedCornerShape(Dimens.cornerRadius / 1.5f))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
            ) {
                SmallText("▲", color = if (myVote == 1) accent else Clear30Colors.text.copy(alpha = 0.35f), modifier = Modifier.clickable(onClick = onUp))
                SmallText(score.toString(), color = Clear30Colors.text)
                SmallText("▼", color = if (myVote == -1) Clear30Colors.red1 else Clear30Colors.text.copy(alpha = 0.35f), modifier = Modifier.clickable(onClick = onDown))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                idea.tag?.takeIf { it.isNotBlank() }?.let { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.15f))
                            .padding(horizontal = Dimens.cardSpacing / 2, vertical = 2.dp),
                    ) { TinyText(tag.uppercase(), color = accent, maxLines = 1) }
                }
                SmallText(idea.title, color = Clear30Colors.text)
                if (idea.body.isNotBlank()) TinyText(idea.body, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}
