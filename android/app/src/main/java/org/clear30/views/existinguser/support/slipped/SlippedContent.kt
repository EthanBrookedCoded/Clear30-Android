package org.clear30.views.existinguser.support.slipped

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.clear30.views.theme.Clear30Colors

/**
 * Content pools for the post-slip ("I slipped") sheet — 1:1 port of iOS
 * SlippedContent.swift. Header copy (emoji + title + subtitle + callout) and
 * the hero activity are picked at random per presentation. Local for v1; a
 * remote source can replace the pools without changing the views.
 */

/** The user's goal, from the "What brings you here" assessment answer. */
enum class SlipGoal { QUITTING, MODERATING, GENERAL }

data class SlipCopy(
    val emoji: String,
    val title: String,
    val subtitle: String,
    /** The rotating callout line — stats, normalization, reframes. */
    val callout: String,
) {
    companion object {
        /** General — works regardless of goal (and for "explore / don't know"). */
        val general = listOf(
            SlipCopy(
                "🌱", "You're here",
                "Smoking is a moment, not a setback.",
                "Using doesn't decide what's next; your response does. (Marlatt & Gordon)",
            ),
            SlipCopy(
                "🤍", "We've got you",
                "Showing up is the part that counts.",
                "~75% of people who set out to change a substance habit get there, most over several tries. (US national study)",
            ),
            SlipCopy(
                "⚓", "Nothing's undone",
                "Smoking doesn't erase things; it helps you understand your triggers.",
                "Most people who resolve a substance problem do it in about 2 serious attempts. (Recovery Research Institute)",
            ),
            SlipCopy(
                "🫶", "Today is one day. Yesterday was one day.",
                "Smoking is something you did, not who you are.",
                "Change isn't linear; the people who get there know it's not a straight line.",
            ),
        )

        /** Quitting / breaking. */
        val quitting = listOf(
            SlipCopy(
                "🌱", "So you smoked",
                "It happens. Be easy on yourself.",
                "People quitting cannabis average 3–7 serious attempts before it sticks. (cannabis cessation research)",
            ),
            SlipCopy(
                "🤗", "You're okay",
                "Quitting's hard. One slip doesn't change that you're doing it.",
                "~3 in 4 people slip at some point during a quit attempt. (young-adult cannabis study)",
            ),
            SlipCopy(
                "🤍", "We're here for you",
                "Whenever you're ready to get back on the Clear30 path, we are with you.",
                "Most people who quit successfully have setbacks. Change happens when we don't give up.",
            ),
        )

        /** Moderating / cutting down. */
        val moderating = listOf(
            SlipCopy(
                "🌱", "It happens",
                "Nobody does this perfectly.",
                "Cutting back is a scribble that trends down, not a straight line.",
            ),
            SlipCopy(
                "⚓", "You're still in this",
                "Smoking doesn't cancel your progress.",
                "Most people get there by zig-zagging toward it. The key is persistence in the face of setbacks. (US national study)",
            ),
            SlipCopy(
                "🫶", "Using does not have to be the trend",
                "It does not have to undo your direction.",
                "Be as kind to yourself now as you'd be to a friend who used. (Neff, self-compassion)",
            ),
        )

        fun variants(goal: SlipGoal): List<SlipCopy> = when (goal) {
            SlipGoal.QUITTING -> quitting
            SlipGoal.MODERATING -> moderating
            SlipGoal.GENERAL -> general
        }

        fun random(goal: SlipGoal): SlipCopy = variants(goal).randomOrNull() ?: general[0]
    }
}

// MARK: - Activities

enum class SlipActivityID(val rawValue: String) {
    PLAN("plan"),
    TALK("talk"),
    WHY("why"),
    SELF_TALK("selfTalk"),
    TESTIMONIAL("testimonial"),
    COMMUNITY("community"),
}

data class SlipActivity(
    val id: SlipActivityID,
    val icon: String,
    val title: String,
    val blurb: String,
    /** [start, end] feature colors — saturated gradient for icon chip / CTA / outline. */
    val colors: List<Color>,
) {
    val gradient: Brush get() = Brush.linearGradient(colors)

    /**
     * Soft, opaque wash of the feature colors over the card surface — blends
     * toward the card color (not white) so it reads in light and dark mode and
     * the card shadow stays a clean silhouette (iOS `tintGradient`).
     */
    val tintGradient: Brush
        get() = Brush.linearGradient(colors.map { lerp(it, Clear30Colors.button, 0.88f) })

    companion object {
        val all = listOf(
            SlipActivity(
                SlipActivityID.PLAN, "🧩", "Make a plan for next time",
                "If-then, so you don't decide in the moment",
                listOf(Clear30Colors.blue, Clear30Colors.green),
            ),
            SlipActivity(
                SlipActivityID.TALK, "💬", "Talk to someone",
                "Dr. Fred, Gerad, or Claire",
                listOf(Clear30Colors.meditation1, Clear30Colors.meditation2),
            ),
            SlipActivity(
                SlipActivityID.WHY, "🎯", "Revisit your why",
                "The reason you started this break",
                listOf(Clear30Colors.journal1, Clear30Colors.journal2),
            ),
            SlipActivity(
                SlipActivityID.SELF_TALK, "🗣️", "Affirmations",
                "An affirmation to repeat out loud",
                listOf(Color(0xFFF4BA66), Color(0xFFFFC685)),
            ),
            SlipActivity(
                SlipActivityID.TESTIMONIAL, "🎬", "Watch a story",
                "Someone who's been exactly here",
                listOf(Clear30Colors.red1, Clear30Colors.red2),
            ),
            SlipActivity(
                SlipActivityID.COMMUNITY, "👥", "Reach out to the community",
                "Post or reply, you're not alone",
                listOf(Color(0xFFA32EB8), Color(0xFFB93FCF)),
            ),
        )

        fun random(): SlipActivity = all.random()

        fun meta(id: SlipActivityID): SlipActivity = all.first { it.id == id }
    }
}

// MARK: - Testimonials ("watch a story")

/** Video stories shown in the testimonial carousel. */
object SlipStories {
    val all: List<String> = (1..8).map { "https://m.clear30.org/testimonials/$it.mp4" }
}

// MARK: - Affirmations (static pool, v1)

object SlipAffirmations {
    val all = listOf(
        "Using is a moment, not a verdict on who I am.",
        "I've gotten through cravings before. I can do it again.",
        "Coming back is the strong part, and I am doing it.",
        "I'm not starting over. I'm continuing.",
        "The past is the past. I'm the person who chooses to continue.",
        "I don't need a perfect run. I need to keep showing up.",
        "I will count all the days I have tried to be better because they are what matter.",
        "If I shift my mindset to growth, that changes everything.",
        "Being here is proof I haven't given up. That matters.",
        "This is one moment. I get to decide what the next one is.",
        "I'm learning what works for me, and that takes a few tries.",
        "The urge to continue into regular use has passed before. It will pass again.",
    )
}
