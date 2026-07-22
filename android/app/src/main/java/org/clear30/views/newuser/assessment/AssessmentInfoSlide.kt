package org.clear30.views.newuser.assessment

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.clear30.R
import org.clear30.data.model.AffirmationCard
import org.clear30.data.model.AssessmentInfoData
import org.clear30.data.model.AssessmentInfoDataID
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.BreakReasonType
import org.clear30.data.model.getSingleOption
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading3
import org.clear30.views.components.Heading3Markdown
import org.clear30.views.components.HugeText
import org.clear30.views.components.softShadow
import org.clear30.views.components.SmallText
import org.clear30.views.components.SmallTextMarkdown
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * AssessmentInfoSlide — a faithful 1:1 port of `AssessmentInfoSlide2.swift`, the
 * single renderer iOS uses for EVERY `.information` slide (affirmations, social
 * proof, credibility, pain-point, dream-outcome, etc.).
 *
 * Layout mirrors the SwiftUI VStack exactly: a top "image / custom view" region,
 * a flexible spacer, then leading title / subtitle / body, then the primary
 * `TextIconButton` (a white pill with dark text + trailing icon) and an optional
 * `TinyTextButton`. iOS injects the rich slides through `data.customView`; we
 * dispatch on the slide's [AssessmentInfoDataID] to the same Compose components.
 *
 * The green-gradient background + white content color are supplied by the parent
 * ([AllAssessment]) for every info slide whose `overrideBackgroundGradient` is
 * not `false`, so the typed text composables here inherit white automatically.
 */
/**
 * Per-slide controller, the Compose equivalent of iOS `AssessmentInfoSlideViewModel`.
 * A custom view can hide the parent's primary CTA (e.g. the pain-point reveal keeps
 * it hidden until its final stage) by flipping [showNextButton].
 */
class InfoSlideController {
    var showNextButton by mutableStateOf(true)
}

val LocalInfoSlideController = compositionLocalOf { InfoSlideController() }

@Composable
fun AssessmentInfoSlide(
    data: AssessmentInfoData,
    // Null outside onboarding (new-break / mid-pilot / post-assessment reuse the
    // plain info layout without the onboarding VM's custom views).
    vm: AssessmentViewModel?,
    onGradient: Boolean,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null,
) {
    // welcomeTyping is a full-screen typing slide that auto-forwards — it owns
    // the whole frame (no spacer / button), exactly like the iOS customView.
    if (data.id == AssessmentInfoDataID.welcomeTyping) {
        WelcomeTypingSlide(data, onPrimary)
        return
    }

    val controller = remember(data.id) { InfoSlideController() }
    val custom = customViewFor(data, vm)
    // iOS renders title/subtitle below the customView only for the slides that
    // pair a custom view with copy (features / expectations). The text-owning
    // custom views (affirmation cards, social proof, …) render their own copy.
    val showBaseText = custom == null ||
        data.id == AssessmentInfoDataID.features ||
        data.id == AssessmentInfoDataID.expectations ||
        data.id == AssessmentInfoDataID.clear30Context ||
        // planPath and goalsAffirmation share the `goals_affirmation` raw (iOS
        // parity) — only the card-less plan-path variant shows the base copy.
        (data.id == AssessmentInfoDataID.planPath && data.affirmationCards == null)

    CompositionLocalProvider(LocalInfoSlideController provides controller) {
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        if (custom != null) {
            Box(Modifier.weight(1f).fillMaxWidth()) { custom() }
        } else if (data.systemImageName != null || data.imageName != null) {
            Spacer(Modifier.weight(1f))
            InfoImageArea(data, onGradient)
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (showBaseText) {
            if (data.title.isNotEmpty()) {
                Heading3Markdown(
                    data.title,
                    Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing / 2),
                )
            }
            if (data.subtitle.isNotEmpty()) {
                DefaultText(
                    data.subtitle,
                    Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing),
                )
            }
            if (data.body.isNotEmpty()) {
                SmallTextMarkdown(
                    data.body,
                    Modifier.fillMaxWidth(),
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
            }
        }

        val hasText = data.title.isNotEmpty() || data.subtitle.isNotEmpty() || data.body.isNotEmpty()
        // iOS gates the primary CTA via showNextButton — staged custom views
        // (e.g. the pain-point reveal) keep it hidden until their final stage.
        if (controller.showNextButton && data.primaryButtonText.isNotEmpty()) {
            InfoPrimaryButton(
                text = data.primaryButtonText,
                icon = data.primaryButtonIcon,
                modifier = Modifier.padding(top = Dimens.cardSpacing * (if (hasText) 2 else 1)),
                onClick = onPrimary,
            )
        }
        if (onSecondary != null && data.secondaryButtonText != null) {
            TinyTextButton(
                data.secondaryButtonText!!,
                Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing),
                onClick = onSecondary,
            )
        }
    }
    }
}

/**
 * Maps a named info slide to its rich Compose presentation (the equivalent of
 * iOS `data.customView`). Returns `null` for the plain image/title/body slides,
 * which fall through to the base layout above.
 */
@Composable
private fun customViewFor(data: AssessmentInfoData, vm: AssessmentViewModel?): (@Composable () -> Unit)? =
    if (vm == null) null
    else when (data.id) {
        AssessmentInfoDataID.socialProof -> ({ AssessmentSocialProof(vm.name) })
        AssessmentInfoDataID.credibility -> ({ AssessmentCredibility() })
        AssessmentInfoDataID.currentUseSummary -> ({ AssessmentPainPoint(vm.name, vm.responses) })
        AssessmentInfoDataID.clear30Recommendation -> ({
            AssessmentDreamOutcome(
                name = vm.name,
                breakReasons = chosenBreakReasons(vm),
                weeklySpend = weeklySpend(vm) ?: 0,
            )
        })
        // goalsAffirmation and planPath share the `goals_affirmation` raw —
        // the goals slide carries affirmationCards, the plan-path slides don't.
        AssessmentInfoDataID.goalsAffirmation -> ({
            if (data.affirmationCards != null) AffirmationCardsView(data) else PlanPathCard(data.badge)
        })
        AssessmentInfoDataID.triggersAffirmation -> ({ AffirmationCardsView(data) })
        AssessmentInfoDataID.whereYouAre -> ({ WhereYouAreSlide(vm.name) })
        AssessmentInfoDataID.whereYouGoing -> ({ WhereYouGoingSlide() })
        AssessmentInfoDataID.clear30Context -> ({ Clear30ContextEmojis() })
        else -> null
    }

/**
 * PlanPathCard — the goal "journey" illustration shown on the intro/affirmation
 * slide (iOS Plan Path): a white card with a goal pill ("Break" / "Quit" …) and
 * the winding milestone path. Gently bobs so the screen feels alive.
 */
@Composable
private fun PlanPathCard(badge: String?) {
    val transition = rememberInfiniteTransition(label = "planPath")
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Clear30Card(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .offset(y = (bob * 6f).dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                if (!badge.isNullOrEmpty()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Clear30Gradients.clear30)
                            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
                    ) {
                        SmallText(badge, color = Color.White)
                    }
                }
                Image(
                    painter = painterResource(R.drawable.plan_path),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .padding(horizontal = Dimens.cardSpacing),
                )
            }
        }
    }
}

/** The "fit Clear30 to you" emoji cluster (iOS clear30Context customView) — three
 *  overlapping white emoji circles, scaled up, centered above the title/body. */
@Composable
private fun Clear30ContextEmojis() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.scale(1.5f).size(width = 136.dp, height = 136.dp)) {
            EmojiCircle("☀️", Modifier.align(Alignment.TopStart).rotate(-2f))
            EmojiCircle(
                "📺",
                Modifier.align(Alignment.TopEnd)
                    .offset(y = 10.dp)
                    .rotate(2f)
                    .zIndex(1f),
            )
            EmojiCircle("🏋", Modifier.align(Alignment.BottomCenter).zIndex(2f))
        }
    }
}

/** A 75dp white circle with a centered emoji + soft shadow (iOS EmojiCircle). */
@Composable
private fun EmojiCircle(emoji: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(75.dp)
            .softShadow(Clear30Colors.shadow, cornerRadius = 75.dp / 2)
            .clip(CircleShape)
            .background(Clear30Colors.button),
        contentAlignment = Alignment.Center,
    ) {
        HugeText(emoji)
    }
}

/** "Thanks [name]!" lead-in to the pain-point chart (iOS whereYouAre custom view) — centered on the gradient. */
@Composable
private fun WhereYouAreSlide(name: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SmallText(
            "Thanks $name!",
            modifier = Modifier.fillMaxWidth(),
            color = LocalContentColor.current.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
        // One centered block (full width + centered text) so the long wrapping line
        // and the short line line up — matches iOS .multilineTextAlignment(.center).
        Heading3(
            "Based on data from millions of others,\nhere's where you're at",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/** "Now, this is where we'll take you" lead-in to the dream-outcome (iOS whereYouGoing). */
@Composable
private fun WhereYouGoingSlide() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Heading3(
            "Now, this is where\nwe'll take you",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/** Selected break-reason types, in chosen order (BREAK_REASON question). */
private fun chosenBreakReasons(vm: AssessmentViewModel): List<BreakReasonType> {
    val r = vm.responses[AssessmentQuestionID.BREAK_REASON.raw] ?: return emptyList()
    return r.responses.mapNotNull { idx -> r.question.options.getOrNull(idx)?.let { BreakReasonType.from(it) } }
}

/** Weekly cannabis spend in whole dollars (MONEY_SPENT question), or null. */
private fun weeklySpend(vm: AssessmentViewModel): Int? {
    val r = vm.responses[AssessmentQuestionID.MONEY_SPENT.raw] ?: return null
    val idx = r.responses.firstOrNull() ?: return null
    return r.question.options.getOrNull(idx)?.filter(Char::isDigit)?.toIntOrNull()
}

/**
 * The top image region — a centered SF Symbol inside a translucent circle (the
 * `Color.white.opacity(0.25)` halo from AssessmentInfoSlide2). Raster `imageName`
 * assets are not yet imported, so they are skipped with a marker.
 */
@Composable
private fun InfoImageArea(data: AssessmentInfoData, onGradient: Boolean) {
    // Raster asset (e.g. fair_trial_comparison) — resolve the iOS asset name to a
    // drawable by snake-casing it, mirroring the AssessmentImageChoice resolver.
    data.imageName?.let { raw ->
        val context = androidx.compose.ui.platform.LocalContext.current
        val resId = androidx.compose.runtime.remember(raw) {
            context.resources.getIdentifier(
                raw.lowercase().replace(Regex("[^a-z0-9]+"), "_"), "drawable", context.packageName,
            )
        }
        if (resId != 0) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(resId),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    val sym = data.systemImageName ?: return
    val haloColor = if (onGradient) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray
    val tint = if (onGradient) Color.White else Clear30Colors.green
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(data.systemImageSize.dp + Dimens.cardSpacing * 6) // iOS circle .padding(-3 * cardSpacing)
                .clip(CircleShape)
                .background(haloColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                sfSymbol(sym),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(data.systemImageSize.dp).rotate(data.systemImageRotation),
            )
        }
    }
}

/**
 * Primary CTA — a 1:1 port of `TextIconButton`: a full-width white card pill with
 * centered dark text + a trailing icon. (On the green-gradient info slides the
 * dark-on-white pill is the iOS look; the card supplies its own content color.)
 */
@Composable
private fun InfoPrimaryButton(text: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Clear30Card(modifier = modifier.fillMaxWidth().pressScale(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (text.isNotEmpty()) SmallText(text, maxLines = 1)
            if (icon.isNotBlank()) {
                Icon(sfSymbol(icon), contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Secondary CTA — a centered `TinyTextButton` that inherits the slide's content color. */
@Composable
private fun TinyTextButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.pressScale(onClick = onClick), contentAlignment = Alignment.Center) {
        TinyText(text)
    }
}

/** Welcome slide — types the title + body, then auto-forwards (welcomeTyping). */
@Composable
private fun WelcomeTypingSlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    TypingTextSlide(
        lines = listOf(
            TypingLine(data.title, TypingTextStyle.Heading3),
            TypingLine(data.body, TypingTextStyle.Small, alpha = 0.75f),
        ),
        centerText = true,
        onComplete = onPrimary,
    )
}

/**
 * AffirmationCardsView — the goals-affirmation slide (1:1 port of the iOS
 * `AffirmationCardsView`): a scroll of alternating-tilt [EmojiCard]s, an optional
 * bottom "Where You're Headed" card, then the leading title + body. The
 * pop-in animation (animatePopup) is disabled in this flow, so everything shows
 * at once — matching the `animatePopup: false` call site.
 */
@Composable
private fun AffirmationCardsView(data: AssessmentInfoData) {
    val cards = data.affirmationCards.orEmpty()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing * 2),
        ) {
            cards.forEachIndexed { index, card ->
                val even = index % 2 == 0
                EmojiCard(
                    card,
                    Modifier
                        .padding(
                            start = if (even) 0.dp else Dimens.horizontalPadding,
                            end = if (even) Dimens.horizontalPadding else 0.dp,
                        )
                        .rotate(if (even) -2f else 2f),
                )
            }
            if (!data.affirmationBottomText.isNullOrEmpty()) {
                Clear30Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        data.affirmationBottomLabel?.let {
                            TinyText(it, color = LocalContentColor.current.copy(alpha = 0.5f))
                        }
                        SmallText(data.affirmationBottomText!!)
                    }
                }
            }
        }
        if (data.title.isNotEmpty() || data.body.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                if (data.title.isNotEmpty()) Heading3Markdown(data.title, Modifier.fillMaxWidth())
                if (data.body.isNotEmpty()) {
                    SmallTextMarkdown(
                        data.body,
                        Modifier.fillMaxWidth(),
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

/** One affirmation card — a gradient emoji chip + title row, then a dim subtitle. */
@Composable
private fun EmojiCard(card: AffirmationCard, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(Dimens.cornerRadius / 2))
                        .background(Clear30Gradients.clear30),
                    contentAlignment = Alignment.Center,
                ) {
                    DefaultText(card.emoji, color = Color.White)
                }
                SmallText(card.title, Modifier.fillMaxWidth())
            }
            TinyText(card.subtitle, Modifier.fillMaxWidth(), color = LocalContentColor.current.copy(alpha = 0.5f))
        }
    }
}
