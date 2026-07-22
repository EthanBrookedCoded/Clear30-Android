package org.clear30.views.existinguser.support.slipped

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.AppMode
import org.clear30.data.model.PostTag
import org.clear30.data.model.Program
import org.clear30.data.model.SlipPlan
import org.clear30.data.model.UserInfo
import org.clear30.data.model.getCachedObject
import org.clear30.data.model.setCacheObject
import org.clear30.data.supabase.CreatePost
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.createCommunityPost
import org.clear30.data.supabase.getCommunityTags
import org.clear30.data.supabase.getUserID
import org.clear30.data.supabase.updateTriggerResponses
import org.clear30.data.supabase.updateYourWhy
import org.clear30.util.now
import org.clear30.views.components.Clear30Sheet
import org.clear30.views.components.ConfettiOverlay
import org.clear30.views.components.Heading3
import org.clear30.views.components.InlineVideoPlayer
import org.clear30.views.components.MultiLineOffWhiteInput
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.existinguser.community.communityProgramTagName
import org.clear30.views.existinguser.community.getDayTag
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * Inline activities for the post-slip ("I slipped") sheet — port of iOS
 * SlippedActivities.swift. Cards use a soft tint of the activity gradient with
 * dark text; the saturated gradient is used for the icon chip, the solidified
 * outline, and the primary CTA. Self-contained activities (plan, why,
 * affirmations, community) act in place; chat hands off via [SlippedCallbacks].
 */

/** Navigation hand-offs out of the sheet (iOS dismiss-then-navigate). */
class SlippedCallbacks(
    val onDismiss: () -> Unit,
    val onOpenClaire: () -> Unit,
    val onOpenDrFred: () -> Unit,
    val onOpenPeerSupport: () -> Unit,
)

private fun SlipActivity.logEngaged(loggingID: String) {
    Logger.logEvent(
        loggingID,
        LogEventType.slippedActivityEngaged,
        mapOf(LogEventExtraDataType.TYPE to id.rawValue),
    )
}

/** Renders the inline view for a given activity id. */
@Composable
fun SlippedActivityView(
    id: SlipActivityID,
    userInfo: UserInfo,
    program: Program,
    callbacks: SlippedCallbacks,
) {
    val activity = SlipActivity.meta(id)
    when (id) {
        SlipActivityID.PLAN -> SlipPlanActivity(activity, userInfo)
        SlipActivityID.TALK -> SlipTalkActivity(activity, userInfo, callbacks)
        SlipActivityID.WHY -> SlipWhyActivity(activity, userInfo)
        SlipActivityID.SELF_TALK -> SlipAffirmationActivity(activity, userInfo)
        SlipActivityID.TESTIMONIAL -> SlipTestimonialActivity(activity, userInfo)
        SlipActivityID.COMMUNITY -> SlipCommunityActivity(activity, userInfo, program)
    }
}

// MARK: - Shell (tinted card)

@Composable
private fun SlippedActivityShell(
    activity: SlipActivity,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().cardStyle(gradient = activity.tintGradient),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).alpha(0.5f).clip(CircleShape).background(activity.gradient),
                contentAlignment = Alignment.Center,
            ) { Heading3(activity.icon) }

            Column(Modifier.weight(1f)) {
                SmallText(title ?: activity.title)
                if (subtitle != null) TinyText(subtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
            }

            trailing?.invoke()
        }

        content()
    }
}

/**
 * Card with a soft outline in the activity's own gradient — the "solidified"
 * callout used after saving a plan / why and to frame an affirmation.
 */
@Composable
private fun SlipOutlinedCard(
    gradient: androidx.compose.ui.graphics.Brush,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().cardStyle(
            color = Clear30Colors.button,
            shadowColor = Color.Transparent,
            outlineGradient = gradient,
            outlineOpacity = 0.5f,
        ),
    ) { content() }
}

/** iOS TinyTextButton — plain tiny text + optional icon, no background. */
@Composable
private fun TinyTextActionButton(
    text: String,
    icon: String? = null,
    opacity: Float = 0.5f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.alpha(opacity).pressScale(onClick = onClick).padding(vertical = Dimens.cardSpacing / 3),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TinyText(text)
        if (icon != null) {
            Icon(sfSymbol(icon), contentDescription = null, tint = Clear30Colors.text, modifier = Modifier.size(11.dp))
        }
    }
}

// MARK: - 1. Make a plan (if-then)

@Composable
private fun SlipPlanActivity(activity: SlipActivity, userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    var ifText by remember { mutableStateOf("") }
    var feelingText by remember { mutableStateOf("") }
    var thenText by remember { mutableStateOf("") }
    var plans by remember { mutableStateOf(userInfo.getCachedObject<List<SlipPlan>>(SlipPlan.CACHE_KEY) ?: emptyList()) }
    var justSaved by remember { mutableStateOf<SlipPlan?>(null) }
    var showPrevious by remember { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }

    val saveDisabled = ifText.isBlank() || feelingText.isBlank() || thenText.isBlank()

    Box {
        SlippedActivityShell(activity, subtitle = "So you don't have to decide in the moment") {
            val saved = justSaved
            if (saved != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    PlanLine(activity, "Next time, if…", saved.ifText)
                    saved.feelingText?.takeIf { it.isNotEmpty() }?.let { PlanLine(activity, "and I'm feeling…", it) }
                    PlanLine(activity, "then I'll…", saved.thenText)
                    TinyTextActionButton("Make another plan", icon = "plus") { justSaved = null }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    LabeledField("Next time, if…", ifText, "Trigger") { ifText = it }
                    LabeledField("and I'm feeling…", feelingText, "Feeling") { feelingText = it }
                    LabeledField("then I'll…", thenText, "Action") { thenText = it }

                    if (!saveDisabled) {
                        StretchedButton("Save plan", gradient = activity.gradient) {
                            val plan = SlipPlan(
                                ifText = ifText.trim(),
                                feelingText = feelingText.trim(),
                                thenText = thenText.trim(),
                                date = SlipPlan.displayDate(now()),
                                createdAt = now(),
                            )
                            val updated = plans + plan
                            plans = updated
                            userInfo.setCacheObject(SlipPlan.CACHE_KEY, updated)
                            activity.logEngaged(userInfo.loggingID)
                            Haptics.successHeavy()
                            confetti++
                            justSaved = plan
                            ifText = ""; feelingText = ""; thenText = ""
                            scope.launch {
                                runCatching { Clear30Store.save(userInfo) }
                                SupabaseController.updateTriggerResponses(updated.map { it.toData() })
                            }
                        }
                    }

                    if (plans.isNotEmpty()) {
                        TinyTextActionButton("See previous plans", icon = "chevron.right") { showPrevious = true }
                    }
                }
            }
        }

        if (confetti > 0) key(confetti) { ConfettiOverlay(Modifier.fillMaxWidth(), count = 100) }
    }

    if (showPrevious) {
        SlipPreviousPlansSheet(plans.reversed()) { showPrevious = false }
    }
}

@Composable
private fun LabeledField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        TinyText(label, color = Clear30Colors.text.copy(alpha = 0.5f))
        MultiLineOffWhiteInput(value, onChange, placeholder = placeholder, smallText = true)
    }
}

@Composable
private fun PlanLine(activity: SlipActivity, label: String, text: String) {
    SlipOutlinedCard(activity.gradient) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
            TinyText(label, color = Clear30Colors.text.copy(alpha = 0.5f))
            SmallText(text)
        }
    }
}

@Composable
private fun SlipPreviousPlansSheet(plans: List<SlipPlan>, onDismiss: () -> Unit) {
    // Own padding (bottom cardSpacing * 2 inside the scroll) instead of the
    // standard sheet content padding.
    Clear30Sheet(onDismiss = onDismiss, skipPartiallyExpanded = false, contentPadding = false) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(bottom = Dimens.cardSpacing * 2),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Heading3("Previous plans")
            plans.forEach { plan ->
                Column(
                    Modifier.fillMaxWidth().cardStyle(color = Clear30Colors.opacityGray, shadowColor = Color.Transparent),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        TinyText("If ${plan.ifText}", Modifier.weight(1f), color = Clear30Colors.text.copy(alpha = 0.5f))
                        TinyText(plan.date, color = Clear30Colors.text.copy(alpha = 0.25f))
                    }
                    plan.feelingText?.takeIf { it.isNotEmpty() }?.let {
                        TinyText("and feeling $it", color = Clear30Colors.text.copy(alpha = 0.5f))
                    }
                    SmallText("→ ${plan.thenText}")
                }
            }
        }
    }
}

// MARK: - 2. Talk to someone (launches existing chats)

@Composable
private fun SlipTalkActivity(activity: SlipActivity, userInfo: UserInfo, callbacks: SlippedCallbacks) {
    SlippedActivityShell(activity, subtitle = "We're here for you, no judgment") {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            PersonRow(activity, image = org.clear30.R.drawable.fred, name = "Dr. Fred", role = "Addiction specialist") {
                activity.logEngaged(userInfo.loggingID)
                callbacks.onDismiss(); callbacks.onOpenDrFred()
            }
            if (userInfo.mode != AppMode.ADOLESCENT) {
                PersonRow(activity, image = org.clear30.R.drawable.gerad, name = "Gerad", role = "Your accountability buddy") {
                    activity.logEngaged(userInfo.loggingID)
                    callbacks.onDismiss(); callbacks.onOpenPeerSupport()
                }
            }
            PersonRow(activity, systemImage = "sparkles", name = "Claire", role = "AI coach · here anytime") {
                activity.logEngaged(userInfo.loggingID)
                callbacks.onDismiss(); callbacks.onOpenClaire()
            }
        }
    }
}

@Composable
private fun PersonRow(
    activity: SlipActivity,
    image: Int? = null,
    systemImage: String? = null,
    name: String,
    role: String,
    onClick: () -> Unit,
) {
    Box(Modifier.pressScale(onClick = onClick)) {
        SlipOutlinedCard(activity.gradient) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().alpha(0.5f).clip(CircleShape).background(activity.gradient))
                    if (image != null) {
                        Image(
                            painterResource(image), contentDescription = name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else if (systemImage != null) {
                        Icon(sfSymbol(systemImage), contentDescription = null, tint = Clear30Colors.meditation1, modifier = Modifier.size(18.dp))
                    }
                }

                Column(Modifier.weight(1f)) {
                    SmallText(name)
                    TinyText(role, color = Clear30Colors.text.copy(alpha = 0.5f))
                }

                Icon(
                    sfSymbol("chevron.right"), contentDescription = null,
                    tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

// MARK: - 3. Revisit your why

@Composable
private fun SlipWhyActivity(activity: SlipActivity, userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(userInfo.userWhy.orEmpty().isBlank()) }
    var draft by remember { mutableStateOf(userInfo.userWhy.orEmpty()) }
    var confetti by remember { mutableIntStateOf(0) }
    var savedWhy by remember { mutableStateOf(userInfo.userWhy.orEmpty()) }

    val showSaved = !editing && savedWhy.isNotBlank()

    Box {
        SlippedActivityShell(
            activity,
            title = if (showSaved) activity.title else "Set your why",
            trailing = if (showSaved) {
                {
                    TinyTextActionButton("Edit") {
                        draft = savedWhy
                        editing = true
                    }
                }
            } else null,
        ) {
            if (showSaved) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    SlipOutlinedCard(activity.gradient) { Heading3("“$savedWhy”") }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                        TinyText("🔊")
                        TinyText(
                            "When said out loud, it'll come back to you when you need it.",
                            color = Clear30Colors.text.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    SmallText("What's your motivation?", color = Clear30Colors.text.copy(alpha = 0.5f))
                    MultiLineOffWhiteInput(draft, { draft = it }, placeholder = "Your why", smallText = true)
                    if (draft.isNotBlank()) {
                        StretchedButton("Save my why", gradient = activity.gradient) {
                            val text = draft.trim()
                            userInfo.userWhy = text.ifEmpty { null }
                            savedWhy = text
                            editing = false
                            activity.logEngaged(userInfo.loggingID)
                            Haptics.successHeavy()
                            confetti++
                            scope.launch {
                                runCatching { Clear30Store.save(userInfo) }
                                SupabaseController.updateYourWhy(text)
                            }
                        }
                    }
                }
            }
        }

        if (confetti > 0) key(confetti) { ConfettiOverlay(Modifier.fillMaxWidth(), count = 100) }
    }
}

// MARK: - 4. Affirmations

@Composable
private fun SlipAffirmationActivity(activity: SlipActivity, userInfo: UserInfo) {
    var index by remember { mutableIntStateOf(SlipAffirmations.all.indices.random()) }

    LaunchedEffect(Unit) { activity.logEngaged(userInfo.loggingID) }

    SlippedActivityShell(activity, subtitle = "When said out loud, it'll come back to you when you need it") {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            AnimatedContent(targetState = index, label = "affirmation") { i ->
                SlipOutlinedCard(activity.gradient) { Heading3("“${SlipAffirmations.all[i]}”") }
            }
            TinyTextActionButton("Show another", icon = "arrow.clockwise") {
                index = (index + 1) % SlipAffirmations.all.size
            }
        }
    }
}

// MARK: - 5. Watch a story (video carousel)

@Composable
private fun SlipTestimonialActivity(activity: SlipActivity, userInfo: UserInfo) {
    // Shuffled once per presentation so the story order varies each time.
    val stories = remember { SlipStories.all.shuffled() }

    LaunchedEffect(Unit) { activity.logEngaged(userInfo.loggingID) }

    SlippedActivityShell(activity, subtitle = "Someone who's been exactly here") {
        // iOS autoplays the focused page in a vertical-feed carousel; Android's
        // InlineVideoPlayer is tap-to-play, which suits a horizontal rail.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            items(stories.size) { i ->
                Box(Modifier.fillParentMaxWidth(0.85f)) {
                    InlineVideoPlayer(uri = stories[i])
                }
            }
        }
    }
}

// MARK: - 6. Reach out to the community (inline compose)

@Composable
private fun SlipCommunityActivity(activity: SlipActivity, userInfo: UserInfo, program: Program) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var postBody by remember { mutableStateOf("") }
    var posted by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }
    var communityTags by remember { mutableStateOf<List<PostTag.Tag>>(emptyList()) }

    // Tags load in the background; the post auto-tags with whatever resolved
    // by submit time (program tag + day tag — iOS CreatePostView.setup()).
    LaunchedEffect(Unit) {
        SupabaseController.getCommunityTags().onSuccess { communityTags = it }
    }

    val postDisabled = title.isBlank() || postBody.isBlank()

    Box {
        SlippedActivityShell(activity, subtitle = "Others are here for you") {
            if (posted) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    SlipOutlinedCard(activity.gradient) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                            SmallText(title)
                            TinyText(postBody, color = Clear30Colors.text.copy(alpha = 0.5f))
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(sfSymbol("checkmark.circle.fill"), contentDescription = null, tint = Clear30Colors.green, modifier = Modifier.size(16.dp))
                        TinyText("Shared with the community", color = Clear30Colors.text.copy(alpha = 0.5f))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    MultiLineOffWhiteInput(title, { title = it }, placeholder = "Title", smallText = true)
                    MultiLineOffWhiteInput(postBody, { postBody = it }, placeholder = "I feel…", smallText = true)

                    if (posting) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    } else if (!postDisabled) {
                        StretchedButton("Post to community", gradient = activity.gradient) {
                            posting = true
                            Haptics.successHeavy()
                            activity.logEngaged(userInfo.loggingID)
                            scope.launch {
                                // Auto tags: program (or guardian) + day — same as CreatePostView.setup().
                                val tags = buildList {
                                    if (userInfo.mode == AppMode.ADOLESCENT) {
                                        communityTags.firstOrNull { it.type == "program" && it.name == "Adolescent" }?.let { add(it.name) }
                                    } else {
                                        communityTags.firstOrNull { it.type == "program" && it.name == program.communityProgramTagName }?.let { add(it.name) }
                                    }
                                    communityTags.getDayTag(program)?.let { add(it.name) }
                                }
                                val uid = SupabaseController.getUserID() ?: userInfo.userID
                                Logger.logEvent(userInfo.loggingID, LogEventType.createdCommunityPost)
                                val err = SupabaseController.createCommunityPost(
                                    CreatePost(
                                        p_title = title.trim(),
                                        p_content_type = "text",
                                        p_body = postBody.trim(),
                                        p_user_id = uid,
                                        p_tags = tags,
                                    ),
                                )
                                posting = false
                                if (err == null) {
                                    confetti++
                                    posted = true
                                } else {
                                    org.clear30.data.AlertHandler.info("Couldn't post", err.message)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (confetti > 0) key(confetti) { ConfettiOverlay(Modifier.fillMaxWidth(), count = 100) }
    }
}
