package org.clear30.data

/**
 * DailyLessons — the built-in 30-day Clear30 lesson set.
 *
 * The real daily content is served by the `program_get_messages` backend RPC,
 * but that only returns rows once the account is authenticated AND has a
 * submitted assessment — so in local dev (or before onboarding finishes) the
 * Today feed is empty. [ProgramMessageHandler.applyBuiltInLessons] buckets these
 * lessons into `Program.contentInfo` (one per program day, unlocking at 10:00)
 * as a FALLBACK, only for days the backend hasn't filled. Each lesson's `title`
 * leads with an emoji so [org.clear30.data.model.ProgramMessage.topicEmoji] /
 * `topicTitle` split it for the feed card. `{name}` is replaced with the user's
 * first name.
 */
object DailyLessons {

    data class Lesson(
        val day: Int,
        val title: String,
        val subtitle: String,
        val body: String,
    )

    val all: List<Lesson> = listOf(
        Lesson(
            0,
            "🌱 Welcome, {name}",
            "Day one of clearer.",
            "You just did the hardest part — deciding to start. The next 30 days aren't about willpower or being perfect; they're about giving your brain and body room to reset. Check in each day, read the day's lesson, and let the streak build itself. We've got you.",
        ),
        Lesson(
            1,
            "⏳ The first 24 hours",
            "What's happening right now.",
            "THC is fat-soluble, so it leaves your system slowly — but the mental shift starts almost immediately. You may notice you reach for it out of habit more than craving. That habit-loop is exactly what these 30 days will quiet down.",
        ),
        Lesson(
            2,
            "😴 Sleep might get weird",
            "It's temporary — and it's healing.",
            "Cannabis suppresses REM sleep, so when you stop, your brain rebounds with more of it. That can mean restless nights for a few days. Keep a consistent bedtime and skip screens late — your deep sleep is coming back online.",
        ),
        Lesson(
            3,
            "💭 Vivid dreams are back",
            "Your REM sleep is returning.",
            "If your dreams suddenly feel like full-length movies, that's the REM rebound — a sign your sleep architecture is recovering. Some people love it, some find it intense. Either way, it usually settles within a week or two.",
        ),
        Lesson(
            4,
            "😤 Feeling on edge?",
            "Irritability peaks around now.",
            "Days 3–5 are often the most irritable, because your brain is recalibrating without its usual buffer. This is the storm before the calm. A short walk, water, and naming the feeling (\"this is withdrawal, not me\") all take the edge off.",
        ),
        Lesson(
            5,
            "🍽️ Appetite shifts",
            "Your hunger signals are resetting.",
            "Without the appetite spike, you may eat less or notice real hunger again for the first time in a while. Try to eat regular, protein-forward meals — steady blood sugar makes cravings and mood swings much easier to ride out.",
        ),
        Lesson(
            6,
            "🎉 One week clear",
            "A real milestone.",
            "Seven days. Your short-term memory and reaction time are already sharpening, and the worst of the physical adjustment is behind you. Take a second to notice one thing that feels even slightly clearer than a week ago.",
        ),
        Lesson(
            7,
            "🌊 Cravings come in waves",
            "Ride them, don't fight them.",
            "A craving feels urgent but almost always passes within 15–20 minutes. Instead of bracing against it, surf it: notice it rise, breathe, do one small thing, and watch it fall. Every wave you ride teaches your brain it doesn't run the show.",
        ),
        Lesson(
            8,
            "🧠 Dopamine is rebalancing",
            "Why everything feels a little flat.",
            "Regular use turns down your brain's natural reward signal, so ordinary things can feel dull at first. This is your dopamine system healing — give it a couple of weeks and small pleasures (music, food, a good laugh) start to land properly again.",
        ),
        Lesson(
            9,
            "🥱 Boredom is the real trigger",
            "Name it to tame it.",
            "For a lot of people the urge isn't really about getting high — it's about filling an empty pocket of time. Line up a few \"instead of\" activities now: a playlist, a walk, a game, a text to a friend. Boredom loses its grip when you have a plan.",
        ),
        Lesson(
            10,
            "🔟 Double digits",
            "Ten days — momentum is real.",
            "You've proven you can do this for ten straight days. The neural pathways that scream for the old habit are already getting weaker each time you don't feed them. Keep going — it genuinely gets easier from here.",
        ),
        Lesson(
            11,
            "🔁 Rewire the routine",
            "Break the cue, not just the habit.",
            "Most use is glued to a cue — after work, before bed, with certain people. Pick your strongest cue and deliberately do something else in that exact moment today. You're not just resisting; you're writing a new default.",
        ),
        Lesson(
            12,
            "🪤 The \"just one\" trap",
            "Why moderation feels harder than a break.",
            "\"Just one\" reopens the whole loop and resets the progress your brain is making. A clean break is often easier than constant negotiation, because there's no decision to re-litigate every day. Protect the simplicity you've built.",
        ),
        Lesson(
            13,
            "🗣️ Telling people",
            "You don't owe anyone the full story.",
            "You can keep it simple: \"I'm taking a break.\" Real friends respect it; the ones who push are usually wrestling with their own use. Having one line ready makes social moments far less awkward.",
        ),
        Lesson(
            14,
            "🏅 Two weeks clear",
            "Halfway to 30.",
            "Fourteen days. By now sleep is usually settling, mood is steadier, and the cravings are less frequent. You're past the hump most people never get over. Notice how far \"day one you\" would be impressed.",
        ),
        Lesson(
            15,
            "🔍 Clarity is coming back",
            "The mental fog lifts.",
            "Around the two-week mark many people report sharper focus and a quieter mind. Conversations feel easier to follow; tasks feel less heavy. This is your working memory thanking you.",
        ),
        Lesson(
            16,
            "🌤️ Mood is stabilizing",
            "Fewer peaks, fewer crashes.",
            "Without the daily up-and-down, your baseline mood evens out. You might feel more emotionally \"present\" — including feelings you used to smooth over. That's not a setback; that's you, fully online, learning to handle things directly.",
        ),
        Lesson(
            17,
            "🤝 If you slip",
            "A slip is data, not failure.",
            "If it happens, don't spiral or wait for a \"perfect\" restart. Log it honestly, get curious about the trigger, and check in tomorrow. The people who succeed aren't the ones who never slip — they're the ones who don't quit after one.",
        ),
        Lesson(
            18,
            "💰 Count what you've kept",
            "Your savings are adding up.",
            "Tally what you'd normally spend in 18 days — money, yes, but also hours. Picture putting that toward something you actually want. Progress feels more real when you can see what it's buying you.",
        ),
        Lesson(
            19,
            "🏃 Move your body",
            "The high you already have.",
            "Exercise raises the same feel-good chemicals you're rebuilding, and it burns off restless craving energy fast. It doesn't need to be a workout — a brisk 10-minute walk counts. Motion is one of the most reliable craving-killers there is.",
        ),
        Lesson(
            20,
            "🌟 Twenty days strong",
            "Only ten to go.",
            "Two-thirds of the way. The habit that used to feel automatic now takes effort to even imagine. You're not white-knuckling anymore — you're becoming someone who just doesn't, by default.",
        ),
        Lesson(
            21,
            "🪞 The identity shift",
            "From \"quitting\" to \"don't\".",
            "There's a quiet flip that happens around three weeks: it stops being \"I'm trying to quit\" and becomes \"I don't really do that.\" That sentence is powerful — it removes the daily decision. Try it on today.",
        ),
        Lesson(
            22,
            "🧘 Stress without the crutch",
            "Building a real off-switch.",
            "Cannabis was likely your fast stress-relief button — so stress can feel sharper without it for a while. Breathing slowly (long exhales), a walk, or talking it out are slower but they actually resolve the stress instead of pausing it.",
        ),
        Lesson(
            23,
            "🎯 Sharper focus",
            "Deep work gets easier.",
            "With memory and motivation recovering, sustained attention comes back. Pick one thing you've been avoiding and give it 25 focused minutes today — you may be surprised how much more is in the tank.",
        ),
        Lesson(
            24,
            "❤️ Relationships",
            "Showing up more fully.",
            "Being fully present changes how you connect — more patience, more listening, more actually-there. If someone close shared this habit with you, this is a good week to do something together that doesn't involve it.",
        ),
        Lesson(
            25,
            "🛣️ Almost there",
            "Five days out.",
            "Day 25. Whatever brought you here, you've held the line for over three weeks. Don't coast — the finish line is exactly when old habits whisper \"you've proven your point, you can stop now.\" You're stronger than that whisper.",
        ),
        Lesson(
            26,
            "🧭 Plan for the triggers",
            "Name your top three.",
            "Think ahead: what are the three situations most likely to test you after day 30? Write the situation and your move for each. A plan made in calm beats a decision made in the moment, every time.",
        ),
        Lesson(
            27,
            "🙏 Notice what changed",
            "Gratitude locks in progress.",
            "Sleep, mood, money, clarity, pride — name a few things that are better than they were a month ago. Recognizing the wins is what turns a 30-day challenge into a lasting change.",
        ),
        Lesson(
            28,
            "📆 Two days left",
            "Decide what 31 looks like.",
            "You're almost at 30 — so what's next? Some people extend, some moderate intentionally, some stay done for good. There's no wrong answer, but choose it on purpose rather than drifting back. You've earned the right to decide.",
        ),
        Lesson(
            29,
            "🔭 Look back",
            "From day one to now.",
            "Scroll your calendar. Every clear day is a choice you made and kept. That version of you on day one didn't know if this was possible — and here you are, one day from proving it.",
        ),
        Lesson(
            30,
            "🏆 You did it, {name}",
            "30 days clear.",
            "Thirty days. Your sleep, memory, mood, and reward system have all had real time to recover — and you proved you're the one steering, not the habit. Whatever you choose next, you get to choose it from a clearer place. Proud of you.",
        ),
    )
}
