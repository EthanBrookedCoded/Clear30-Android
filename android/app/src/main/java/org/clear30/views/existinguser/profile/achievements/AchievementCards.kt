package org.clear30.views.existinguser.profile.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.data.model.AchievementDefinition
import org.clear30.data.model.AchievementRarity
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.MiniText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.colorFromHex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Rarity helpers (ports of AchievementDataAbstracted.computedRarity / color1 /
// color2 / gradient). `rarity` is null until joined, so fall back to the iOS
// blue→green "Common" with a circle glyph.
// ─────────────────────────────────────────────────────────────────────────────

val AchievementDefinition.computedRarity: AchievementRarity
    get() = rarity ?: AchievementRarity(
        id = 1,
        name = "Common",
        gradientStart = "#5BB4A9",
        gradientEnd = "#80C97A",
        displayOrder = 1,
        sfSymbol = "circle.fill",
    )

val AchievementRarity.color1: Color get() = colorFromHex(gradientStart)
val AchievementRarity.color2: Color get() = colorFromHex(gradientEnd)

/** bottomLeading → topTrailing, matching `AchievementRarity.gradient`. */
val AchievementRarity.gradient: Brush
    get() = Clear30Gradients.linear(listOf(color1, color2), Clear30Gradients.bottomLeading, Clear30Gradients.topTrailing)

/** Subtle white sheen used for glyphs / outlines on gradient-filled cards. */
val whiteGradient: Brush = Clear30Gradients.linear(
    listOf(Color.White, Color(0xFFF1F4F4)), Clear30Gradients.bottomLeading, Clear30Gradients.topTrailing,
)

/** `Color.darker(by:)` — lerp toward black by [fraction] (0.1 == 10% darker). */
fun Color.darker(fraction: Float): Color = lerp(this, Color.Black, fraction)

// ─────────────────────────────────────────────────────────────────────────────
// Rarity glyph — the circle / diamond / hexagon / seal shape that distinguishes
// each rarity. Drawn with a Path so it fills with any Brush (white sheen on a
// gradient card, gray when locked) and stays crisp at any size.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RarityGlyph(glyph: String, brush: Brush, size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        drawPath(rarityPath(glyph, this.size.width, this.size.height), brush)
    }
}

private fun DrawScope.rarityPath(glyph: String, w: Float, h: Float): Path {
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) / 2f
    return when {
        glyph.contains("diamond") -> Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
        }
        glyph.contains("hexagon") -> polygon(cx, cy, r, sides = 6, rotationDeg = 90f)
        glyph.contains("seal") -> seal(cx, cy, r, lobes = 11)
        else -> Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }
    }
}

private fun polygon(cx: Float, cy: Float, r: Float, sides: Int, rotationDeg: Float): Path = Path().apply {
    val rot = rotationDeg * PI.toFloat() / 180f
    for (i in 0 until sides) {
        val a = rot + 2f * PI.toFloat() * i / sides
        val x = cx + r * cos(a); val y = cy - r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A wax-seal / scalloped badge — alternating outer/inner radii (legendary). */
private fun seal(cx: Float, cy: Float, r: Float, lobes: Int): Path = Path().apply {
    val steps = lobes * 2
    val inner = r * 0.82f
    for (i in 0 until steps) {
        val a = -PI.toFloat() / 2f + 2f * PI.toFloat() * i / steps
        val rad = if (i % 2 == 0) r else inner
        val x = cx + rad * cos(a); val y = cy + rad * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// ─────────────────────────────────────────────────────────────────────────────
// Gradient-filled icon — the achievement's own SF symbol tinted with a brush
// (rarity gradient on earned cards, white sheen on new cards). Forces an
// offscreen layer so SrcAtop blends the brush only over the glyph.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GradientIcon(symbol: String, brush: Brush, size: Dp, modifier: Modifier = Modifier) {
    Icon(
        imageVector = sfSymbol(symbol),
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(size)
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
            },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Pills (CardStyle-backed) for names / rarity labels / "New ›".
// ─────────────────────────────────────────────────────────────────────────────

private val pillShape = RoundedCornerShape(Dimens.cornerRadius / 1.75f)

@Composable
private fun Pill(background: Brush, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(pillShape).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Rarity badge — glyph + name. On a gradient card the badge is white-filled with
 * the rarity gradient text/glyph; standalone (section header) it's gradient-filled
 * with a white glyph/text.
 */
@Composable
fun AchievementBadge(
    rarity: AchievementRarity,
    gradientBackground: Boolean,
    small: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bg = if (gradientBackground) rarity.gradient else SolidColor(Color.White)
    val fgBrush = if (gradientBackground) whiteGradient else rarity.gradient
    val fgColor = if (gradientBackground) Color.White else rarity.color1
    Pill(background = bg, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 8),
        ) {
            RarityGlyph(rarity.glyph, fgBrush, size = if (small) 9.dp else 13.dp)
            if (small) MiniText(rarity.name, color = fgColor) else TinyText(rarity.name, color = fgColor)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card states (ports of AchievementCard / AchievementNewCard /
// AchievementPlaceholderCard). All square (aspectRatio 1).
// ─────────────────────────────────────────────────────────────────────────────

/** Earned + already viewed: white card, rarity-outlined, gradient icon + name pill. */
@Composable
fun AchievementCard(def: AchievementDefinition, modifier: Modifier = Modifier, iconSize: Dp = 50.dp) {
    val rarity = def.computedRarity
    Clear30Card(
        modifier = modifier.aspectRatio(1f),
        outlineGradient = rarity.gradient,
        outlineOpacity = 0.5f,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                GradientIcon(def.sfSymbol, rarity.gradient, iconSize)
            }
            // Chip tinted with the RARITY color at 0.25 (Thatcher 2026-07-21) —
            // not the neutral gray.
            Pill(
                background = Brush.linearGradient(
                    listOf(rarity.color1.copy(alpha = 0.25f), rarity.color2.copy(alpha = 0.25f)),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TinyText(def.name, color = rarity.color1, maxLines = 1)
            }
        }
    }
}

/** Earned + NEW: rarity-gradient tile, white rarity glyph + name, colored glow, "New ›". */
@Composable
fun AchievementNewCard(def: AchievementDefinition, modifier: Modifier = Modifier, glyphSize: Dp = 60.dp) {
    val rarity = def.computedRarity
    Box(modifier.aspectRatio(1f)) {
        Clear30Card(
            modifier = Modifier.fillMaxSize(),
            gradient = rarity.gradient,
            shadowColor = rarity.color1.darker(0.1f).copy(alpha = 0.75f),
            outlineGradient = whiteGradient,
            outlineOpacity = 0.5f,
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RarityGlyph(rarity.glyph, whiteGradient, glyphSize)
                }
                Pill(background = SolidColor(Color.White.copy(alpha = 0.25f)), modifier = Modifier.fillMaxWidth()) {
                    TinyText(rarity.name, color = Color.White, maxLines = 1)
                }
            }
        }
        // "New ›" badge, rotated like a sticker in the top-trailing corner.
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).rotate(-3f)
                .clip(pillShape).background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
            ) {
                TinyText("New", color = rarity.color1)
                // iOS draws the raw chevron glyph 4pt wide (~7pt tall); the Material
                // vector letterboxes its glyph, so center an oversized icon in a
                // glyph-sized box to match without inflating the row.
                Box(Modifier.size(width = 5.dp, height = 8.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        sfSymbol("chevron.right"),
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.requiredSize(15.dp),
                    )
                }
            }
        }
    }
}

/** Locked: plain card, gray rarity glyph + the criteria description. */
@Composable
fun AchievementPlaceholderCard(
    def: AchievementDefinition,
    modifier: Modifier = Modifier,
    timeUntil: String? = null,
    glyphSize: Dp = 50.dp,
) {
    val rarity = def.computedRarity
    Box(modifier.aspectRatio(1f)) {
        Clear30Card(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RarityGlyph(rarity.glyph, SolidColor(Clear30Colors.opacityGray), glyphSize)
                }
                Pill(background = SolidColor(Clear30Colors.opacityGray), modifier = Modifier.fillMaxWidth()) {
                    MiniText(def.description, color = Clear30Colors.text.copy(alpha = 0.25f))
                }
            }
        }
        if (timeUntil != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).rotate(-3f)
                .clip(pillShape).background(rarity.color1).padding(horizontal = 7.dp, vertical = 4.dp)) {
                MiniText(timeUntil, color = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compact variants for the Profile mini-display row (icon only, no name pill).
// ─────────────────────────────────────────────────────────────────────────────

/** Earned + viewed, compact: gradient-outlined white tile with the gradient icon. */
@Composable
fun AchievementIconCard(def: AchievementDefinition, modifier: Modifier = Modifier) {
    val rarity = def.computedRarity
    Clear30Card(
        modifier = modifier.aspectRatio(1f),
        outlineGradient = rarity.gradient,
        outlineOpacity = 0.5f,
        padding = false,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GradientIcon(def.sfSymbol, rarity.gradient, 22.dp)
        }
    }
}

/** Earned + new, compact: gradient tile with the white rarity glyph + glow + "New". */
@Composable
fun AchievementNewIconCard(def: AchievementDefinition, modifier: Modifier = Modifier) {
    val rarity = def.computedRarity
    Box(modifier.aspectRatio(1f)) {
        Clear30Card(
            modifier = Modifier.fillMaxSize(),
            gradient = rarity.gradient,
            shadowColor = rarity.color1.darker(0.1f).copy(alpha = 0.75f),
            outlineGradient = whiteGradient,
            outlineOpacity = 0.5f,
            padding = false,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RarityGlyph(rarity.glyph, whiteGradient, 22.dp)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp).rotate(-3f)
                .clip(pillShape).background(Color.White).padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MiniText("New", color = rarity.color1)
                Icon(sfSymbol("chevron.right"), contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(7.dp))
            }
        }
    }
}

/** "120+ More" tail card for the mini-display. */
@Composable
fun AchievementMoreCard(count: Int, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            org.clear30.views.components.SmallText("$count+", color = Clear30Colors.text.copy(alpha = 0.5f))
            TinyText("More", color = Clear30Colors.text.copy(alpha = 0.25f))
        }
    }
}
