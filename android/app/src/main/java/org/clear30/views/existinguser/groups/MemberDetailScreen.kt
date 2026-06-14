package org.clear30.views.existinguser.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.Clear30GroupMember
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.GiganticText
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * MemberDetailScreen — drill-in for a single [Clear30GroupMember]. Renders the
 * member's emoji + name as the hero, three stat cards (days checked in, days
 * clear, slip-ups), and a compact "last 30 days" dot-strip that visualizes
 * their recent consistency without a full calendar grid.
 *
 * Read-only; mirrors the iOS member-detail page intent. Fires
 * `openedGroupMember` with the member id for analytics.
 */
@Composable
fun MemberDetailScreen(
    member: Clear30GroupMember,
    group: Clear30Group,
    userInfo: UserInfo,
    onBack: () -> Unit,
) {
    LaunchedEffect(member.memberID) {
        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.openedGroupMember,
            mapOf(LogEventExtraDataType.ID to member.memberID),
        )
    }

    val isYou = member.memberID == userInfo.userID

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GiganticText(member.emoji)
                        Heading1(if (isYou) "You" else member.name)
                    }
            }
        }

        // Three-card stat grid — matches the Profile stat layout for visual
        // consistency across "me" and "them" views.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            StatCard("Checked in", member.daysCheckedIn.toString(), "days", Modifier.weight(1f))
            StatCard("Clear", member.daysSoberCount.toString(), "days", Modifier.weight(1f))
            StatCard("Slips", member.daysSmokedCount.toString(), "days", Modifier.weight(1f))
        }

        // Last-30 dot strip — read sober/smoked for the most recent 30 calendar
        // days the member has data for. Sober days fill green, smoked fill red,
        // missing days stay neutral so the strip honestly conveys consistency.
        Clear30Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Heading3("Last 30 days")
                    DotStrip(member)
                }
            }

        Spacer(Modifier.height(Dimens.cardSpacing))
        TinyText(
            "Joined ${if (member.joinDate != null) "recently" else "this group"}",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TinyText(title, color = Clear30Colors.text.copy(alpha = 0.5f))
            GiganticText(value)
            TinyText(unit, color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun DotStrip(member: Clear30GroupMember) {
    // We don't have a precise rolling-window over the member's dayInfo —
    // Clear30GroupMember.dayInfo is keyed by canonical date strings but the
    // server may have skipped days. Render up to the most-recent 30 entries
    // sorted lexicographically (yyyy-MM-dd sorts chronologically), which gives
    // a stable "their last entries" view without requiring a server cursor.
    val entries = member.dayInfo?.entries?.sortedBy { it.key }?.takeLast(30).orEmpty()
    if (entries.isEmpty()) {
        SmallText("No check-ins yet.", color = Clear30Colors.text.copy(alpha = 0.5f))
        return
    }
    val rows = entries.chunked(10)
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { entry ->
                val fill = when (entry.value.sober) {
                    true -> Clear30Colors.green
                    false -> Clear30Colors.red2
                    null -> Clear30Colors.opacityGrayFlattened
                }
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(fill),
                )
            }
            // Pad partial rows so the trailing dots align with the others.
            repeat(10 - row.size) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color.Transparent))
            }
        }
    }
}

/**
 * Leaderboard — the group roster sorted by daysCheckedIn. Renders the top three
 * as a podium row (#2 / #1 / #3 in stage order, taller center) and the rest
 * as ranked rows below. Tapping any row routes to [MemberDetailScreen] via
 * [onMemberTap].
 */
@Composable
fun GroupLeaderboard(
    group: Clear30Group,
    userInfo: UserInfo,
    onMemberTap: (Clear30GroupMember) -> Unit,
) {
    if (group.members.isEmpty()) return
    val sorted = group.members.sortedByDescending { it.daysCheckedIn }
    val top3 = sorted.take(3)
    val rest = sorted.drop(3)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Leaderboard")

        // Podium row — order is [#2, #1, #3] for the classic three-step look.
        // If we have fewer than 3 members we just lay them left-to-right.
        if (top3.size >= 3) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                PodiumColumn(rank = 2, member = top3[1], userInfo = userInfo, height = 110.dp, onMemberTap = onMemberTap, modifier = Modifier.weight(1f))
                PodiumColumn(rank = 1, member = top3[0], userInfo = userInfo, height = 150.dp, onMemberTap = onMemberTap, modifier = Modifier.weight(1f))
                PodiumColumn(rank = 3, member = top3[2], userInfo = userInfo, height = 90.dp, onMemberTap = onMemberTap, modifier = Modifier.weight(1f))
            }
        } else {
            top3.forEachIndexed { i, m -> RankRow(rank = i + 1, member = m, userInfo = userInfo, onTap = { onMemberTap(m) }) }
        }

        // Remaining members as standard rows starting at rank 4.
        rest.forEachIndexed { i, m -> RankRow(rank = i + 4, member = m, userInfo = userInfo, onTap = { onMemberTap(m) }) }
    }
}

@Composable
private fun PodiumColumn(
    rank: Int,
    member: Clear30GroupMember,
    userInfo: UserInfo,
    height: androidx.compose.ui.unit.Dp,
    onMemberTap: (Clear30GroupMember) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isYou = member.memberID == userInfo.userID
    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
    Column(
        modifier.padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Crown sits ABOVE the medal for #1 so the winner reads at a glance.
        if (rank == 1) SmallText("👑", color = Clear30Colors.text)
        SmallText(medal)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallText(member.emoji)
            SmallText(if (isYou) "You" else member.name, color = Clear30Colors.text)
        }
        TinyText("${member.daysCheckedIn} days", color = Clear30Colors.text.copy(alpha = 0.5f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        isYou -> Clear30Gradients.clear30
                        rank == 1 -> Clear30Gradients.clear30Bright
                        else -> Clear30Gradients.gray
                    }
                )
                .clickable { onMemberTap(member) },
            contentAlignment = Alignment.Center,
        ) {
            Heading3("#$rank", color = if (isYou || rank == 1) Color.White else Clear30Colors.text)
        }
    }
}

@Composable
private fun RankRow(rank: Int, member: Clear30GroupMember, userInfo: UserInfo, onTap: () -> Unit) {
    val isYou = member.memberID == userInfo.userID
    Clear30Card(
        modifier = Modifier.fillMaxWidth().clickable { onTap() },
        gradient = if (isYou) Clear30Gradients.clear30 else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            SmallText("#$rank", color = if (isYou) Color.White else Clear30Colors.text.copy(alpha = 0.5f))
            SmallText(member.emoji)
            SmallText(if (isYou) "You" else member.name, color = if (isYou) Color.White else Clear30Colors.text)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            TinyText(
                "${member.daysCheckedIn} days",
                color = if (isYou) Color.White.copy(alpha = 0.75f) else Clear30Colors.text.copy(alpha = 0.5f),
            )
        }
    }
}

