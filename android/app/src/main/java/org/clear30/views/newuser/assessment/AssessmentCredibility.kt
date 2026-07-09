package org.clear30.views.newuser.assessment

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.clear30.R
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.softShadow
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import org.clear30.views.theme.Lexend
import androidx.compose.ui.unit.sp

// MARK: - Models

/** Ported from `CredibilityBoardMember` — name + optional citation + portrait drawable. */
private data class CredibilityBoardMember(
    val name: String,
    val citations: CredibilityCitation? = null,
    val imageRes: Int,
)

/** Ported from `CredibilityCitation` — a citation count + scholar/research link. */
private data class CredibilityCitation(
    val number: String,
    val link: String,
)

/** Ported from `CredibilityLogo` — a partner-org tile (title + logo). */
private data class CredibilityLogo(
    val title: String,
    val label: String,
)

/**
 * AssessmentCredibility — ported 1:1 from AssessmentCredibility.swift.
 *
 * Renders on the green gradient assessment background (white content color is
 * already provided via LocalContentColor by the parent), so this view sets NO
 * background. Layout: centered heading → an expert card (Dr. Fred row + a
 * horizontally-scrolling board-of-experts strip) → the "150+ techniques" program
 * info row → an auto-scrolling partner-logo strip.
 *
 * The board-member portraits exist as drawables (fred_2, alan, tom, kamala,
 * stephen, marcia, lauren). The partner logos (Harvard, NIH, UMich, NIDA, MLB)
 * are imported and rendered from drawable resources (text label is a fallback).
 */
@Composable
fun AssessmentCredibility(modifier: Modifier = Modifier) {

    val fredCitation = CredibilityCitation(
        number = "5674",
        link = "https://scholar.google.com/citations?user=9VJFk0gAAAAJ&hl=en",
    )

    val boardMembers = listOf(
        CredibilityBoardMember(
            name = "Dr. Alan Budney, PhD",
            citations = CredibilityCitation("16,236", "https://www.researchgate.net/profile/Alan-Budney"),
            imageRes = R.drawable.alan,
        ),
        CredibilityBoardMember(
            name = "Dr. A. Thomas McLellan, PhD",
            citations = CredibilityCitation("35,609", "https://research.com/u/a-thomas-mclellan"),
            imageRes = R.drawable.tom,
        ),
        CredibilityBoardMember(name = "Dr. Kamala Génecé, PhD", imageRes = R.drawable.kamala),
        CredibilityBoardMember(name = "Stephen D'Antonio", imageRes = R.drawable.stephen),
        CredibilityBoardMember(name = "Marcia Lee Taylor", imageRes = R.drawable.marcia),
        CredibilityBoardMember(name = "Lauren Johnson", imageRes = R.drawable.lauren),
    )

    // Partner logos are imported (drawable-xxxhdpi: harvard/nih/umich/nida/mlb) and
    // wired via [logoDrawable]; LogoScroller renders the image, falling back to the
    // label text only if a drawable is ever missing.
    val logos = listOf(
        CredibilityLogo(title = "Peer Reviewed Paper By", label = "Harvard"),
        CredibilityLogo(title = "Research Funding By", label = "NIH"),
        CredibilityLogo(title = "Research Partner", label = "U Michigan"),
        CredibilityLogo(title = "Research Funding By", label = "NIDA"),
        CredibilityLogo(title = "Presented For", label = "MLB"),
    )

    // ScrollView { ... }.fadeOut(10).padding(.horizontal, -scrollShadowFix)
    // TODO(port): omit the iOS top/bottom fadeOut scrim (cosmetic edge fade).
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Dimens.headingTopPadding)
            .padding(horizontal = Dimens.scrollShadowFix),
    ) {
        Heading(Modifier.padding(bottom = Dimens.cardSpacing * 2))

        ExpertCard(fredCitation, boardMembers, Modifier.padding(bottom = Dimens.cardSpacing))

        ProgramInfo(Modifier.padding(bottom = Dimens.cardSpacing * 4))

        // iOS gates the logos behind `isPageFocused`; we render them eagerly.
        LogoScroller(logos)
    }
}

@Composable
private fun Heading(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.cardSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SmallText(
            "This isn’t just an app.",
            color = LocalContentColor.current.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = Dimens.cardSpacing / 2),
        )
        // multilineTextAlignment(.center)
        Heading3(
            "The only program built by experts...",
            modifier = Modifier
                .padding(bottom = Dimens.cardSpacing)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ExpertCard(
    fredCitation: CredibilityCitation,
    boardMembers: List<CredibilityBoardMember>,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Clear30Card(modifier = modifier.fillMaxWidth()) {
        Column {
            // Fred row
            Row(
                Modifier.padding(bottom = Dimens.cardSpacing),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                verticalAlignment = Alignment.Top,
            ) {
                Image(
                    painter = painterResource(R.drawable.fred_2),
                    contentDescription = "Dr. Fred Muench",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(60.dp),
                )
                Column(Modifier.weight(1f)) {
                    SmallText("Dr. Fred Muench, PhD", color = Clear30Colors.text)
                    TinyText(
                        "Program Creator & Addiction Specialist",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = Dimens.cardSpacing / 4),
                    )
                    // Citations pill — clear30 gradient capsule, white content.
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Clear30Gradients.clear30)
                            .padding(
                                horizontal = Dimens.cardSpacing / 2,
                                vertical = Dimens.cardSpacing / 4,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveCircle(Modifier.padding(end = Dimens.cardSpacing / 4))
                        TinyText("${fredCitation.number} ", color = Color.White)
                        UnderlinedTinyText(
                            "research citations",
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.pressScale {
                                uriHandler.openUri(fredCitation.link)
                            },
                        )
                    }
                }
            }

            // Board members header
            MiniText(
                "Our Board of Behavioral Health Experts",
                color = Clear30Colors.text.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = Dimens.cardSpacing / 2),
            )

            // Board members — horizontal strip of small opacity-gray tiles.
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                boardMembers.forEach { member ->
                    BoardMemberTile(member, uriHandler)
                }
            }
        }
    }
}

@Composable
private fun BoardMemberTile(
    member: CredibilityBoardMember,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    // CardStyle(color: .clear30OpacityGray, padding: false) + custom inset.
    Row(
        Modifier
            .cardStyle(color = Clear30Colors.opacityGray, padding = false)
            .padding(
                horizontal = Dimens.cardSpacing * 3 / 4,
                vertical = Dimens.cardSpacing / 2,
            ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(member.imageRes),
            contentDescription = member.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(25.dp),
        )
        Column {
            TinyText(member.name, color = Clear30Colors.text)
            member.citations?.let { citations ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniText("${citations.number} ", color = Clear30Colors.text.copy(alpha = 0.5f))
                    UnderlinedMiniText(
                        "research citations",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                        modifier = Modifier.pressScale {
                            uriHandler.openUri(citations.link)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramInfo(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        // 150+ badge — white circle with a soft shadow + gradient "150+" tilted -3°.
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(80.dp)
                    .softShadow(Clear30Colors.shadow, cornerRadius = 40.dp)
                    .clip(CircleShape)
                    .background(Clear30Colors.button),
            )
            // Gradient-filled "150+" — gradient text via SpanStyle brush.
            GradientHeading3("150+", Modifier.rotate(-3f))
        }
        Column(Modifier.weight(1f)) {
            SmallText("Proven cognitive techniques for lasting change")
            TinyText(
                "CBT, ACT, Resilience training, DBT, Cognitive Restructuring...",
                color = LocalContentColor.current.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun LogoScroller(logos: List<CredibilityLogo>) {
    // iOS auto-scrolls this strip forever (25s linear). We render the final
    // visible state as a static horizontally-scrollable row.
    // TODO(port): infinite auto-scroll marquee animation omitted.
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        logos.forEach { logo ->
            Column(
                Modifier.width(200.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TinyText(
                    logo.title,
                    color = LocalContentColor.current.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = Dimens.cardSpacing),
                )
                val logoRes = logoDrawable(logo.label)
                Box(
                    Modifier.height(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (logoRes != 0) {
                        Image(
                            painter = painterResource(logoRes),
                            contentDescription = logo.label,
                            modifier = Modifier.height(30.dp),
                        )
                    } else {
                        SmallText(logo.label)
                    }
                }
            }
        }
    }
}

// MARK: - Helpers

/** Maps a partner label to its imported logo drawable (rasterized from the iOS SVGs). */
private fun logoDrawable(label: String): Int = when (label) {
    "Harvard" -> R.drawable.harvard
    "NIH" -> R.drawable.nih
    "U Michigan" -> R.drawable.umich
    "NIDA" -> R.drawable.nida
    "MLB" -> R.drawable.mlb
    else -> 0
}

/**
 * LiveCircle — a small "live" indicator dot. iOS uses a pulsing green ring; we
 * render a static green-on-white dot (the pill it sits in is already animated by
 * its gradient). TODO(port): pulse animation omitted.
 */
@Composable
private fun LiveCircle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(15.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Clear30Colors.green),
        )
    }
}

/** TinyText with an underline — for the tappable "research citations" link. */
@Composable
private fun UnderlinedTinyText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = Lexend,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        textDecoration = TextDecoration.Underline,
    )
}

/** MiniText with an underline — board-member citation link variant. */
@Composable
private fun UnderlinedMiniText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = Lexend,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        textDecoration = TextDecoration.Underline,
    )
}

/** Heading3 (22sp Medium) painted with the clear30 gradient — the "150+" badge. */
@Composable
private fun GradientHeading3(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = Lexend,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        style = androidx.compose.ui.text.TextStyle(brush = Clear30Gradients.clear30),
    )
}
