package org.clear30.data.model

import androidx.compose.ui.graphics.Brush
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.colorFromHex

/**
 * Symptom info models — ported from SymptomInfos.swift.
 *
 * Swift used a `SymptomInfosDecoded` with dynamic coding keys to decode an
 * arbitrary `[String: SymptomInfo]` object; kotlinx deserializes a
 * `Map<String, SymptomInfo>` directly, so that helper is unnecessary.
 */
@Serializable
class SymptomInfos(
    var symptomInfos: MutableMap<String, SymptomInfo> = mutableMapOf(),
    var smsFrequency: Int = 0,
    var symptomInfosJSON: String? = null,
    var symptomMessagesJSON: String? = null,
) {
    companion object {
        const val STORE_KEY = "symptom_infos"

        /**
         * Built-in symptom set used when the backend hasn't populated symptom
         * infos yet (iOS pulls these from `get_symptom_infos`; on a fresh Android
         * account the store is empty, which left the Support → Symptom support
         * carousel hidden). These mirror the common cannabis-cessation symptoms
         * with a couple of concrete, actionable tips each so the cards are always
         * present and tappable.
         */
        fun builtIn(): SymptomInfos {
            fun tip(title: String, c1: String, c2: String, vararg sections: SymptomTipSection) =
                SymptomTip(title = title, sections = sections.toList(), color1 = c1, color2 = c2)
            fun sec(heading: String, content: String, example: String? = null) =
                SymptomTipSection(heading = heading, content = content, example = example)

            val map = linkedMapOf(
                "😤 Irritability" to SymptomInfo(
                    color1 = "FF8A65", color2 = "F4511E",
                    tips = listOf(
                        tip(
                            "Cool down fast", "FF8A65", "F4511E",
                            sec("Box breathing", "Inhale 4s, hold 4s, exhale 4s, hold 4s. Three rounds resets the stress response.", "Do it before you reply to that text."),
                            sec("Move it out", "A brisk 5-minute walk burns off the adrenaline behind the short fuse."),
                        ),
                        tip(
                            "Name it to tame it", "FF8A65", "F4511E",
                            sec("Label the feeling", "Quietly saying \"I'm irritable right now\" lowers its grip — it's a known withdrawal wave, not who you are."),
                        ),
                    ),
                    reddits = emptyMap(), prompts = mapOf("What set me off today?" to "Write what triggered the irritability and one kinder thing you could tell yourself."),
                ),
                "😰 Anxiety" to SymptomInfo(
                    color1 = "7E9BFF", color2 = "5B6CF4",
                    tips = listOf(
                        tip(
                            "Ground yourself", "7E9BFF", "5B6CF4",
                            sec("5-4-3-2-1", "Name 5 things you see, 4 you hear, 3 you can touch, 2 you smell, 1 you taste. It pulls you out of the spiral."),
                            sec("Longer exhale", "Breathe out twice as long as you breathe in for a minute — it flips on the calm nervous system."),
                        ),
                        tip(
                            "Right-size the worry", "7E9BFF", "5B6CF4",
                            sec("Ask: is it true now?", "Anxiety borrows from the future. Bring it back to what's actually happening this minute."),
                        ),
                    ),
                    reddits = emptyMap(), prompts = mapOf("What is my anxiety trying to tell me?" to "Name the worry, then one small thing in your control today."),
                ),
                "😴 Insomnia" to SymptomInfo(
                    color1 = "9E7BFF", color2 = "6C4FD0",
                    tips = listOf(
                        tip(
                            "Wind down", "9E7BFF", "6C4FD0",
                            sec("Screens off early", "Dim lights and put the phone down 30–60 min before bed so melatonin can rise."),
                            sec("Same time daily", "A consistent wake time resets your clock faster than chasing sleep at night."),
                        ),
                        tip(
                            "If you can't sleep", "9E7BFF", "6C4FD0",
                            sec("Don't fight it", "After ~20 min awake, get up and do something dull in low light, then return when sleepy. Sleep disruption fades within a couple of weeks."),
                        ),
                    ),
                    reddits = emptyMap(), prompts = mapOf("How can I make tonight calmer?" to "Plan one wind-down step you'll actually do tonight."),
                ),
                "🔥 Cravings" to SymptomInfo(
                    color1 = "FF7E7E", color2 = "E23B3B",
                    tips = listOf(
                        tip(
                            "Surf the urge", "FF7E7E", "E23B3B",
                            sec("Ride the wave", "Cravings peak and pass in about 15–20 minutes. Set a timer and let it crest — you don't have to act on it."),
                            sec("Change the scene", "Stand up, step outside, drink a glass of water. Breaking the cue often breaks the urge."),
                        ),
                        tip(
                            "Have a plan", "FF7E7E", "E23B3B",
                            sec("Pre-decide your move", "Pick one go-to action now (text a friend, a walk, a craving meditation) so you're not deciding mid-craving."),
                        ),
                    ),
                    reddits = emptyMap(), prompts = mapOf("What was I really needing?" to "Cravings often mask boredom, stress or loneliness — name what's underneath."),
                ),
                "🍽️ Low appetite" to SymptomInfo(
                    color1 = "80C97A", color2 = "4F9E58",
                    tips = listOf(
                        tip(
                            "Eat gently", "80C97A", "4F9E58",
                            sec("Small and often", "If big meals feel like too much, graze on easy snacks — fruit, nuts, yogurt — through the day."),
                            sec("Drink your calories", "A smoothie goes down easier than a plate when appetite is low."),
                        ),
                    ),
                    reddits = emptyMap(), prompts = emptyMap(),
                ),
                "🥱 Boredom" to SymptomInfo(
                    color1 = "FFD24F", color2 = "F0A93B",
                    tips = listOf(
                        tip(
                            "Fill the space", "FFD24F", "F0A93B",
                            sec("Reclaim the ritual", "Weed filled time. Line up replacements you look forward to — a show, a hobby, a walk, the gym."),
                            sec("Two-minute start", "Bored-restless? Start any small task for two minutes; momentum usually carries you."),
                        ),
                    ),
                    reddits = emptyMap(), prompts = mapOf("What would I love to have time for?" to "List a few things weed crowded out, and pick one for this week."),
                ),
            )
            return SymptomInfos(symptomInfos = map.toMutableMap())
        }
    }
}

@Serializable
data class SymptomInfo(
    val color1: String,
    val color2: String,
    val tips: List<SymptomTip>,
    val reddits: Map<String, String>,
    val prompts: Map<String, String>,
    var selected: Boolean? = null,
    var messages: List<SymptomInfoMessage>? = null,
) {
    fun getGradient(): Brush = Clear30Gradients.linear(
        listOf(colorFromHex(color1), colorFromHex(color2)),
        Clear30Gradients.bottomLeading, Clear30Gradients.topTrailing,
    )
}

@Serializable
data class SymptomTip(
    val title: String,
    val sections: List<SymptomTipSection>,
    val color1: String,
    val color2: String,
)

@Serializable
data class SymptomTipSection(
    val heading: String,
    val content: String,
    val example: String? = null,
)

@Serializable
data class SymptomInfoMessage(
    val message: String,
    var sent: Instant? = null,
)
