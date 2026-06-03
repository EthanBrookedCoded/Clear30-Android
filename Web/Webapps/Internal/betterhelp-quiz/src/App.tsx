import { useState } from 'react';

// Intro Page Component  
function QuizIntro({ onStartQuiz }: { onStartQuiz: () => void }) {
  return (
    <div className="min-h-screen bg-white flex flex-col" style={{ padding: '25px' }}>
      {/* Headline at top */}
      <h1
        className="text-[32px] font-medium leading-tight mb-4 mt-8 text-center"
        style={{ fontFamily: 'Lexend, sans-serif' }}
      >
        Would Therapy Help You?
      </h1>

      {/* Image aligned to top */}
      <div>
        <img
          src="https://m.clear30.org/images/betterhelp_flower.jpeg"
          alt="Therapy journey illustration"
          className="w-full max-w-md scale-75 rounded-[21px] mt-0 mb-6 mx-auto block"
        />
      </div>


      {/* Fixed bottom section */}
      <div className="fixed bottom-0 left-0 right-0 bg-white pt-6 pb-8" style={{ paddingLeft: '25px', paddingRight: '25px' }}>
        <div className="max-w-2xl mx-auto">
          <p
            className="text-[17px] text-black/50 leading-tight mb-4 text-center"
            style={{ fontFamily: 'Lexend, sans-serif' }}
          >
            Take the 2 min quiz to find out ➡️
          </p>

          <button
            onClick={onStartQuiz}
            className="w-full px-4 py-3 text-[17px] text-white rounded-[21px] active:scale-95 transition-transform"
            style={{
              fontFamily: 'Lexend, sans-serif',
              background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
              boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
            }}
          >
            Start Quiz
          </button>

          <p
            className="text-[14px] text-black/50 text-center mt-6 leading-tight"
            style={{ fontFamily: 'Lexend, sans-serif' }}
          >
            Need immediate support? Call or text <span className="font-medium text-black/50">988</span> anytime to talk with someone who can help.
          </p>
        </div>
      </div>
    </div>
  );
}

// Quiz data from spreadsheets
const QUESTIONS = [
  {
    id: 1,
    type: 'Clarify',
    text: 'What brought you here today?',
    options: [
      {
        id: 'Q1_A1', text: '🤔 I\'ve been struggling and wondering if therapy could help', affirmation: `**You're not alone in wondering this.**

*A lot of people sit with this question for months or even years.*

The fact that you're here exploring it says something important — **you're ready to at least consider what could help.**` },
      {
        id: 'Q1_A2', text: '💭 I\'m curious but not sure therapy is for me', affirmation: `**That's honest.**

*Most people who benefit from therapy started exactly where you are* — curious but skeptical.

The fact that you're open to exploring it is already a **really good sign.**` },
      {
        id: 'Q1_A3', text: '💬 Someone suggested I look into therapy', affirmation: `It can feel weird when someone else plants the idea.

**But here's the thing:** *the people closest to us often see patterns we're too close to notice.*

Worth exploring what they might be picking up on.` },
      {
        id: 'Q1_A4', text: '🌱 I want to grow/improve myself', affirmation: `**That's a mature reason to be here.**

Most people think therapy is only for when things are falling apart, but the people who get the most out of it are the ones using it as a **growth tool** — which is *exactly* how you're thinking about it.` },
      {
        id: 'Q1_A5', text: '🔍 Just exploring options', affirmation: `**Smart approach.**

You're doing research before making a decision, which is exactly what you *should* be doing.

Therapy isn't for everyone, and it's worth figuring out if it makes sense for where you're at right now.` },
    ]
  },
  {
    id: 2,
    type: 'Label',
    text: 'Which of these feels most true for you right now?',
    options: [
      {
        id: 'Q2_A1', text: '🔄 I feel stuck or like I\'m going in circles', affirmation: `**That makes total sense.**

When you feel stuck, it's usually because you're trying to solve something with the *same thinking that got you here.*

The hard part is that most of us were never taught how to actually *break these patterns* — we just keep trying to push through.` },
      {
        id: 'Q2_A2', text: '😰 I\'m dealing with stress/anxiety that won\'t go away', affirmation: `**That makes total sense.**

When stress and anxiety stick around, it's often your mind's way of telling you *something needs attention.*

The hard part is that most of us weren't taught how to actually **process this stuff** — we just try to manage it and keep going.` },
      {
        id: 'Q2_A3', text: '⏸️ Something happened and I can\'t seem to move past it', affirmation: `**That makes total sense.**

When something big happens and you can't move past it, it doesn't mean you're weak or dramatic — it usually means you haven't had the *right tools to process what happened.*

Most of us were **never taught how to do that.**` },
      {
        id: 'Q2_A4', text: '💔 My relationships feel harder than they should', affirmation: `**That makes total sense.**

When relationships feel harder than they should, it's often because *patterns from our past* are showing up in the present.

The hard part is that most of us can't see our own patterns clearly — **we're too close to them.**` },
      {
        id: 'Q2_A5', text: '😶 I\'m functioning fine but not feeling good', affirmation: `**That makes total sense.**

You can be "fine" on paper and still not *feel good* — and that gap is exhausting.

Most people wait until things are really bad to get help, but this in-between place is exactly when **support can make the biggest difference.**` },
      {
        id: 'Q2_A6', text: '🤷 I know something\'s off but can\'t name it', affirmation: `**That makes total sense.**

Sometimes the hardest part is knowing something's off but not having the words for it.

That vague discomfort is often your mind saying something needs attention — you just need help figuring out *what* that is.` },
    ]
  },
  {
    id: 3,
    type: 'Overview Pain',
    text: 'How long have you been dealing with this?',
    options: [
      {
        id: 'Q3_A1', text: '📅 A few weeks', affirmation: `**A few weeks is actually a really good time to look at this.**

Most people wait until it's been months or years, but *catching patterns early* makes them so much easier to shift.

In a lot of ways, **you're ahead of the curve.**` },
      {
        id: 'Q3_A2', text: '🗓️ A few months', affirmation: `A few months is long enough to know this isn't just a rough week.

**The longer you carry this on your own, the more energy it steals** just to get through the day.

This isn't about weakness — it's about not having the *tools or support yet.*` },
      {
        id: 'Q3_A3', text: '⏳ 6 months to a year', affirmation: `**That's a long time to carry this.**

After 6+ months, your brain basically learns to operate this way — it starts to feel *normal*, even though it's draining you every day.

**You really don't have to keep doing this alone.**` },
      {
        id: 'Q3_A4', text: '📆 More than a year', affirmation: `**That's a long time to carry this.**

At this point, you've probably gotten really good at functioning despite it — but that doesn't mean it isn't taking a toll.

The longer patterns go unaddressed, the more they quietly shape *how you see everything.* **You deserve support.**` },
      {
        id: 'Q3_A5', text: '🕰️ Years, honestly', affirmation: `**Years is a really long time to deal with something on your own.**

Eventually it stops feeling like "a problem" and starts feeling like *just who you are* — but that's not the whole story.

The fact that you're here suggests **you know there's another way to feel.**` },
    ]
  },
  {
    id: 4,
    type: 'Overview Pain',
    text: 'What have you already tried?',
    options: [
      {
        id: 'Q4_A1', text: '👥 Talking to friends/family', affirmation: `**You've been trying.**

Friends and family can help, but they're not trained to actually help you *shift patterns* — they can listen, relate, and support, but that's different from having a guide.

There's nothing wrong with what you've been doing; you just haven't had access to the **right kind of support** yet.` },
      {
        id: 'Q4_A2', text: '📚 Self-help books, podcasts, videos', affirmation: `**You've been trying.**

Self-help content is great for learning, but it can't give you *personalized feedback* or catch your blind spots.

There's nothing wrong with your effort — you just need someone who can actually work with **your specific situation.**` },
      {
        id: 'Q4_A3', text: '🧘 Meditation, exercise, journaling', affirmation: `**You've been trying.**

Those tools can help manage symptoms, but they don't always touch *the root* of what's going on.

It's kind of like treating pain with Advil — it helps, but it doesn't **heal the injury.** Sometimes you need something that goes deeper.` },
      {
        id: 'Q4_A4', text: '💪 Just trying to push through/ignore it', affirmation: `**You've been trying.**

Pushing through works for a while, but ignoring something doesn't make it disappear — it just shows up in *other ways.*

**The fact that you're here means some part of you knows it's time to actually deal with this.**` },
      {
        id: 'Q4_A5', text: '🤷‍♂️ Nothing really, I don\'t know where to start', affirmation: `**That's completely understandable.**

When you don't know where to start, it's easy to stay stuck in place — that doesn't mean you don't care or you're avoiding it.

**You're here now, which means you're ready** to figure out what actually helps. *That first step is often the hardest one.*` },
      {
        id: 'Q4_A6', text: '🔄 Therapy before (but it didn\'t work out)', affirmation: `**You've been trying.**

If therapy didn't work before, it's usually a *fit issue* — wrong therapist, wrong style, or wrong timing.

It doesn't mean therapy can't work for you; it just means you haven't found the **right match** yet, and that's something we can change.` },
    ]
  },
  {
    id: 5,
    type: 'Sell Vacation',
    text: 'If you woke up 6 months from now and this was better, what would be different?',
    options: [
      {
        id: 'Q5_A1', text: '☁️ I\'d feel lighter/less weighed down', affirmation: `**That's possible.**

What you just described isn't some fantasy version of you — it's you without this weight taking up so much *mental space.*

Most people don't realize how much energy they spend carrying this stuff until they finally get **support to put it down.**` },
      {
        id: 'Q5_A2', text: '🧘‍♀️ I\'d handle stress without it taking over', affirmation: `**That's possible.**

What you just described isn't some idealized version of you — it's you with *real tools* to manage stress instead of just surviving it.

Most people don't realize how much energy anxiety takes until they learn how to **process it differently.**` },
      {
        id: 'Q5_A3', text: '💕 My relationships would feel easier', affirmation: `**That's possible.**

What you just described isn't some imaginary version of you — it's you without *old patterns* getting in the way of connection.

Most people don't realize how much their past shapes their present relationships until they get help **seeing — and slowly shifting — those patterns.**` },
      {
        id: 'Q5_A4', text: '✨ I\'d actually enjoy things again', affirmation: `**That's possible.**

What you just described isn't some distant version of you — it's you without this *fog* making everything feel muted.

Most people don't realize how much they've dimmed their own experience until they get support to start **turning the lights back on.**` },
      {
        id: 'Q5_A5', text: '🎯 I\'d feel more like myself', affirmation: `**That's possible.**

What you just described isn't a whole new person — it's actually just *you*, without all the layers that have been covering up who you are.

Most people don't realize how far they've drifted from themselves until they get help **finding their way back.**` },
      {
        id: 'Q5_A6', text: '🧠 I\'d stop overthinking everything', affirmation: `**That's possible.**

What you just described isn't unrealistic — it's you without your mind *running in circles* all the time.

Most people don't realize how exhausting overthinking is until they learn how to **quiet it down and trust themselves more.**` },
    ]
  },
];

// Results logic from spreadsheet
const RESULTS_LOGIC = {
  whatYoureDealing: {
    'Q2_A1+Q3_A1': `It sounds like you're dealing with a **pattern of feeling stuck** that's been going on for a few weeks.
 

 This usually happens when you're trying to solve something with the *same approach over and over*. 
 

 **It's not that you're not trying** — it's that you need a different perspective to see what you can't see on your own.`,
    'Q2_A1+Q3_A2': `It sounds like you're dealing with a **pattern of feeling stuck** that's been going on for a few months.
 

 This usually happens when you're trying to solve something with the *same approach over and over*. 
 

 **It's not that you're not trying** — it's that you need a different perspective to see what you can't see on your own.`,
    'Q2_A1+Q3_A3': `It sounds like you're dealing with a **pattern of feeling stuck** that's been going on for 6 months to a year.
 

 This usually happens when you're trying to solve something with the *same approach over and over* - and at this point, *your brain has learned to operate this way*. It's *become your new normal*, even though it's costing you energy every day.`,
    'Q2_A1+Q3_A4': `It sounds like you're dealing with a **pattern of feeling stuck** that's been going on for more than a year.
 

 This usually happens when you're trying to solve something with the *same approach over and over* - and at this point, it's *shaped how you think and feel* about everything. You've probably gotten good at functioning despite it, but that doesn't mean it's not taking a toll.`,
    'Q2_A1+Q3_A5': `It sounds like you're dealing with a **pattern of feeling stuck** that's been going on for years.
 

 At this point, it probably doesn't even feel like a problem anymore - it *just feels like who you are*.
 

 **But it's not.** It's a pattern you've been trying to solve with the same thinking, and you need a different perspective to see what you can't see on your own.`,
    'Q2_A2+Q3_A1': `It sounds like you're dealing with **stress and anxiety** that's been present for a few weeks. When **stress and anxiety** show up like this, it's usually your mind's way of telling you something needs attention - and catching it early makes it easier to address.`,
    'Q2_A2+Q3_A2': `It sounds like you're dealing with **stress and anxiety** that's been present for a few months.
 

 When stress sticks around this long, it's not just about 'calming down' - there's *usually something underneath it* that needs to be understood and processed.
 

 **And that's hard to do on your own.**`,
    'Q2_A2+Q3_A3': `It sounds like you're dealing with **stress and anxiety** that's been present for 6 months to a year.
 

 When stress sticks around this long, it's not just about managing symptoms - your nervous system has learned to operate in this heightened state. There's *usually something underneath it* that needs to be understood and processed, and that's hard to see on your own.`,
    'Q2_A2+Q3_A4': `It sounds like you're dealing with **stress and anxiety** that's been present for more than a year.
 

 At this point, this heightened state has *become your baseline* - you've probably forgotten what it feels like to not be stressed.
 

 When stress persists this long, there's *usually something underneath it* that needs professional help to understand and process.`,
    'Q2_A2+Q3_A5': `It sounds like you're dealing with **stress and anxiety** that's been present for years.
 

 At this point, being stressed probably *just feels like your personality* - but it's not.
 

 When stress persists this long, there's *usually something underneath it* that needs professional help to understand and process.
 

 **You don't have to keep living this way.**`,
    'Q2_A3+Q3_A1': `It sounds like something significant happened a few weeks ago that you haven't been able to fully process. When we can't move past something, it's not about being weak - it's about not having the right tools or support to work through it properly. And the earlier you address it, the easier it is to process.`,
    'Q2_A3+Q3_A2': `It sounds like something significant happened a few months ago that you haven't been able to fully process. When we can't move past something for this long, it's usually because we're missing the tools or support to work through it properly. And that's not something most people know how to do on their own.`,
    'Q2_A3+Q3_A3': `It sounds like something significant happened 6 months to a year ago that you still haven't been able to fully process. At this point, it's probably affecting more areas of your life than just the original event - that's what happens when we don't have the right tools or support to work through difficult experiences.`,
    'Q2_A3+Q3_A4': `It sounds like something significant happened more than a year ago that you still haven't been able to fully process. At this point, it's probably influencing how you see everything - your relationships, your choices, your sense of safety. When trauma or difficult experiences go unprocessed this long, they shape your whole worldview. And that's not something you can fix alone.`,
    'Q2_A3+Q3_A5': `It sounds like something significant happened years ago that you still haven't been able to fully process. At this point, it's woven into how you see everything - your relationships, your choices, your sense of who you are. When trauma or difficult experiences go unprocessed for years, they become part of your foundation. And that's not something you can untangle on your own.`,
    'Q2_A4+Q3_A1': `It sounds like you're dealing with **relationship patterns** that have been showing up for a few weeks.
 

 When relationships feel harder than they should, it's usually *old patterns from your past* showing up - and catching them early makes them easier to shift.`,
    'Q2_A4+Q3_A2': `It sounds like you're dealing with **relationship patterns** that have been showing up for a few months.
 

 When relationships consistently feel harder than they should, it's usually *old patterns from your past* playing out in the present - and those are hard to see without help.`,
    'Q2_A4+Q3_A3': `It sounds like you're dealing with **relationship patterns** that have been showing up for 6 months to a year.
 

 When relationships consistently feel harder than they should for this long, it's usually *old patterns from your past* playing out in the present.
 

 At this point, these patterns have probably affected multiple relationships - and they're hard to see on your own because you're living inside them.`,
    'Q2_A4+Q3_A4': `It sounds like you're dealing with **relationship patterns** that have been showing up for more than a year.
 

 At this point, these patterns have probably *shaped most of your significant relationships*.
 

 When relationships consistently feel harder than they should for this long, it's *old patterns from your past* that you can't see clearly because you're too close to them.
 

 **That's why you need outside perspective.**`,
    'Q2_A4+Q3_A5': `It sounds like you're dealing with **relationship patterns** that have been showing up for years.
 

 At this point, these patterns probably feel like *'just how relationships are'* for you - but they're not. They're *old patterns from your past* that have been running on autopilot.
 

 **And you can't see them clearly because you're living inside them.**`,
    'Q2_A5+Q3_A1': `It sounds like you're dealing with a **gap between functioning fine and actually feeling good** - and it's been there for a few weeks.
 

 Things aren't falling apart, but you're not thriving either. That middle zone is exhausting to live in, even if it doesn't look like a 'problem' from the outside.`,
    'Q2_A5+Q3_A2': `It sounds like you're dealing with a **gap between functioning fine and actually feeling good** - and it's been there for a few months.
 

 Things aren't falling apart, but you're not thriving either. That middle zone is exhausting to live in, and most people don't realize how much energy it takes to maintain that until they actually feel better.`,
    'Q2_A5+Q3_A3': `It sounds like you're dealing with a **gap between functioning fine and actually feeling good** - and it's been there for 6 months to a year. At this point, *'fine but not good'* has probably become your normal.
 

 Things aren't falling apart, but you're not thriving - and that middle zone is exhausting to live in.
 

 **You don't need things to be falling apart to deserve support.**`,
    'Q2_A5+Q3_A4': `It sounds like you're dealing with a **gap between functioning fine and actually feeling good** - and it's been there for more than a year.
 

 At this point, you've probably forgotten what *'thriving'* feels like. You're maintaining, but not living fully - and that takes an enormous amount of energy that most people don't see.`,
    'Q2_A5+Q3_A5': `It sounds like you're dealing with a **gap between functioning fine and actually feeling good** - and it's been there for years. At this point, *'fine but not good'* probably feels like your personality, not a problem.
 

 **But you deserve more than just maintaining.** You deserve to actually feel good, not just function.`,
    'Q2_A6+Q3_A1': `It sounds like you're dealing with **something you can't quite name** that's been present for a few weeks.
 

 That vague discomfort is your mind's way of telling you something needs attention - and addressing it early, even before you can fully name it, is actually the smart move.`,
    'Q2_A6+Q3_A2': `It sounds like you're dealing with **something you can't quite name** that's been present for a few months.
 

 That vague discomfort is your mind's way of telling you something needs attention - you just need help figuring out what exactly that is.
 

 **And that's hard to do alone when you can't even name the problem.**`,
    'Q2_A6+Q3_A3': `It sounds like you're dealing with **something you can't quite name** that's been present for 6 months to a year.
 

 When something feels off for this long but you can't identify what it is, that's usually because you're too close to see it clearly.
 

 **You need outside perspective to help you name what's actually going on.**`,
    'Q2_A6+Q3_A4': `It sounds like you're dealing with **something you can't quite name** that's been present for more than a year.
 

 At this point, that vague discomfort has probably *become your baseline* - you're so used to it that you can't see it clearly anymore.
 

 **You need professional help to identify what's actually going on.**`,
    'Q2_A6+Q3_A5': `It sounds like you're dealing with **something you can't quite name** that's been present for years.
 

 At this point, that feeling of *'something's off'* might just feel like background noise - but it's been taking up mental space for years.
 

 **You need professional help to identify and address what's actually underneath that feeling.**`,
  },
  whatYouNeed: {
    'Q2_A1+Q5_ANY': `**You need to understand** *why you keep hitting the same walls* - *the patterns in how you think* and approach problems that keep you stuck.
 

 **You also need** *new tools and perspectives* to break out of the loop you're in.`,
    'Q2_A2+Q5_A1': `**You need to understand** *what's actually driving* the stress and anxiety - *what's underneath it* - not just ways to manage the symptoms.
 

 **You also need** tools to help you *feel lighter* instead of carrying this weight all the time.`,
    'Q2_A2+Q5_A2': `**You need to understand** *what's actually driving* the stress and anxiety - *what's underneath it* - not just ways to manage the symptoms.
 

 **You also need** tools to actually *regulate your nervous system* so stress doesn't take over your life.`,
    'Q2_A2+Q5_A3': `**You need to understand** *what's actually driving* the stress and anxiety - *what's underneath it* - not just ways to manage the symptoms.
 

 **You also need** to see how stress might be affecting your relationships in ways you haven't noticed.`,
    'Q2_A2+Q5_A4': `**You need to understand** *what's actually driving* the stress and anxiety - *what's underneath it* - not just ways to manage the symptoms.
 

 **You also need** to work through whatever's been keeping you from *actually enjoying* life instead of just surviving it.`,
    'Q2_A2+Q5_A5': `**You need to understand** *what's actually driving* the stress and anxiety - *what's underneath it* - not just ways to manage the symptoms.
 

 **You also need** to reconnect with who you are underneath all the stress that's been covering you up.`,
    'Q2_A2+Q5_A6': `**You need to understand** *what's actually driving* the stress and anxiety - *what's underneath it* - not just ways to manage the symptoms.
 

 **You also need** tools to quiet the overthinking that's probably making the anxiety worse.`,
    'Q2_A3+Q5_ANY': `**You need help processing** *what happened in a structured way* - not just talking about it, but actually working through it so it doesn't keep replaying.
 

 **You also need** tools to *move forward* instead of staying stuck in the past.`,
    'Q2_A4+Q5_A1': `**You need to understand** *the patterns you can't see* on your own - the ways you show up in relationships that make things harder without realizing it.
 

 **You also need** tools to help you *feel lighter* in your connections instead of carrying so much weight.`,
    'Q2_A4+Q5_A2': `**You need to understand** *the patterns you can't see* on your own - the ways you show up in relationships that make things harder without realizing it.
 

 **You also need** tools to handle relationship stress without it completely taking over.`,
    'Q2_A4+Q5_A3': `**You need to understand** *the patterns you can't see* on your own - the ways you show up in relationships that make things harder without realizing it.
 

 **Once you can see these patterns, you can *actually shift them* and make connection feel natural instead of exhausting.**`,
    'Q2_A4+Q5_A4': `**You need to understand** *the patterns you can't see* on your own - the ways you show up in relationships that make things harder without realizing it.
 

 **You also need** to figure out what's been keeping you from *actually enjoying* your relationships instead of just managing them.`,
    'Q2_A4+Q5_A5': `**You need to understand** *the patterns you can't see* on your own - the ways you show up in relationships that make things harder without realizing it.
 

 **You also need** to figure out how your relationships have been pulling you away from who you actually are.`,
    'Q2_A4+Q5_A6': `**You need to understand** *the patterns you can't see* on your own - the ways you show up in relationships that make things harder without realizing it.
 

 **You also need** tools to quiet the overthinking that's probably making you second-guess everything you do and say.`,
    'Q2_A5+Q5_ANY': `**You need to understand** *what's creating the gap* between functioning and feeling good - what you're missing that would *actually make you thrive* instead of just survive.
 

 **You also need** permission to prioritize feeling good, not just being productive.`,
    'Q2_A6+Q5_ANY': `**You need help naming** *what's actually going on* - turning that vague discomfort into something concrete you can actually work on.
 

 **Once you can name it, you can address it.** But that's almost impossible to do alone when you can't even see the problem clearly.`,
  },
  therapyOpening: {
    'Q3_A1+Q4_A1': `**Based on what you told us: Yes.** You've been dealing with this for a *few weeks* and you've been talking to friends and family about it.
 

 The fact that you're *catching this early* and *seeking the right support* means you can address it before it becomes a bigger pattern.
 

 **Friends care, but they can't give you the tools that actually create change.**`,
    'Q3_A1+Q4_A2': `**Based on what you told us: Yes.** You've been dealing with this for a *few weeks* and you've been consuming self-help content.
 

 The fact that you're *catching this early* and *seeking the right support* means you can address it before it becomes a bigger pattern.
 

 **You've learned enough - now you need personalized help applying it.**`,
    'Q3_A1+Q4_A3': `**Based on what you told us: Yes.** You've been dealing with this for a *few weeks* and you've been using meditation, exercise, and journaling.
 

 The fact that you're *catching this early* and *seeking the right support* means you can address it before it becomes a bigger pattern.
 

 **Those tools help manage symptoms, but you need something that goes deeper.**`,
    'Q3_A1+Q4_A4': `**Based on what you told us: Yes.** You've been dealing with this for a *few weeks* and you've been trying to push through it.
 

 The fact that you're *catching this early* and seeking help before pushing through makes it worse shows real self-awareness.
 

 **Pushing through only works for so long.**`,
    'Q3_A1+Q4_A5': `**Based on what you told us: Yes.** You've been dealing with this for a *few weeks* and you haven't known where to start.
 

 The fact that you're *catching this early* and seeking guidance before it becomes a bigger pattern is smart.
 

 **When you don't know where to start, *professional guidance* makes all the difference.**`,
    'Q3_A1+Q4_A6': `**Based on what you told us: Yes.** You've been dealing with this for a *few weeks* and you've tried therapy before.
 

 The fact that you're *catching this early* and you're open to trying again with better fit shows growth.
 

 **Now you know what to look for in a therapist.**`,
    'Q3_A2+Q4_A1': `**Based on what you told us: Yes.** You've been dealing with this for a *few months* and you've been talking to friends and family - *that's long enough* to know willpower alone isn't going to solve it.
 

 **Friends care, but they can't give you the structured support or tools that actually create change.**`,
    'Q3_A2+Q4_A2': `**Based on what you told us: Yes.** You've been dealing with this for a *few months* and you've been consuming self-help content - *that's long enough* to know information alone isn't solving it.
 

 **You've learned enough - now you need someone who can actually work with your specific situation.**`,
    'Q3_A2+Q4_A3': `**Based on what you told us: Yes.** You've been dealing with this for a *few months* and you've been using meditation, exercise, and journaling - *that's long enough* to know symptom management isn't solving it.
 

 **Those tools help you cope, but you need something that goes deeper to actually address the root.**`,
    'Q3_A2+Q4_A4': `**Based on what you told us: Yes.** You've been dealing with this for a *few months* by trying to push through it - *that's long enough* to know that approach isn't working.
 

 **Pushing through has gotten you this far, but it's also kept you stuck.
 

 **You need a different approach.`,
    'Q3_A2+Q4_A5': `**Based on what you told us: Yes.** You've been dealing with this for a *few months* and you haven't known where to start - *that's long enough* that you need *professional guidance* to break the pattern.
 

 **When you don't know what to do, having someone trained to guide you makes all the difference.**`,
    'Q3_A2+Q4_A6': `**Based on what you told us: Yes.** You've been dealing with this for a *few months* and you've tried therapy before - *that's long enough* to know you need support, and now you know what didn't work last time.
 

 **That makes finding the right fit way easier this time.**`,
    'Q3_A3+Q4_ANY': `**Based on what you told us: Yes.** You've been dealing with this for *6 months to a year* - at that point, your brain has learned to operate this way.
 

 **It's become your new normal, even though it's costing you energy every day.** You need *professional support* to actually shift this pattern.`,
    'Q3_A4+Q4_ANY': `**Based on what you told us: Yes.** You've been carrying this for *more than a year* - at this point, it's shaped how you think and feel about everything.
 

 **You've probably gotten really good at functioning despite it, but you deserve more than just functioning.** You need support to actually address this.`,
    'Q3_A5+Q4_ANY': `**Based on what you told us: Yes.** You've been carrying this for *years* - *that's long enough*. At this point, it probably doesn't feel like a problem anymore, it just feels like who you are.
 

 **But it doesn't have to be.** You need *professional support* to actually shift patterns that have been running for this long.`,
  },
  therapyOutcomeBridge: {
    'Q5_A1': `What you're looking for — **feeling lighter and less weighed down** — is *exactly* what good therapy helps with.
 

 It teaches you how to *actually put down* what you've been carrying instead of just trying to be strong enough to keep holding it.`,
    'Q5_A2': `What you're looking for — **handling stress without it taking over** — is *exactly* what good therapy helps with.
 

 It teaches you tools to *actually regulate* your nervous system and *process stress differently*, not just push through it.`,
    'Q5_A3': `What you're looking for — **relationships that feel easier** — is *exactly* what good therapy helps with.
 

 It helps you *see and shift* the patterns that make connection harder than it needs to be, so relationships can feel natural instead of exhausting.`,
    'Q5_A4': `What you're looking for — **actually enjoying things again** — is *exactly* what good therapy helps with.
 

 It helps you work through whatever's been dimming your experience so you can *actually feel things fully* instead of just going through the motions.`,
    'Q5_A5': `What you're looking for — **feeling more like yourself** — is *exactly* what good therapy helps with.
 

 It helps you *clear away* the patterns and pain that have been covering up who you really are, so you can reconnect with yourself.`,
    'Q5_A6': `What you're looking for — **stopping the overthinking** — is *exactly* what good therapy helps with.
 

 It teaches you how to *quiet your mind* and *trust yourself more* instead of getting stuck in loops that go nowhere.`,
  },
  therapyValidation: {
    'Q1_A1': `**You said** you've been struggling and wondering if therapy could help.
 

 **That wondering is you already knowing the answer** — you just needed someone to confirm it makes sense.
 

 **It does.**`,
    'Q1_A2': `**You said** you're curious but not sure.
 

 **Here's the thing:** you don't need to be 100% sure to start. You just need to be open to it working — *which you clearly are*, or you wouldn't be here.`,
    'Q1_A3': `**You said** someone suggested you look into this.
 

 They might have seen *something* you're too close to see. Whether they're right or not, you're here doing your own research — which means you're ready to find out for yourself.`,
    'Q1_A4': `**You said** you want to grow and improve.
 

 **That's exactly the mindset that makes therapy most effective.**
 

 You're not waiting for things to fall apart - you're being proactive about your mental health.
 

 **That's rare and smart.**`,
    'Q1_A5': `**You said** you're just exploring options.
 

 **You've explored.** Based on what you shared, therapy is the option that makes the most sense. Now it's just about whether you're ready to try it.`,
  },
};


const FAQ_DATA = [
  {
    question: "What if I'm not 'bad enough' for therapy?",
    answer: "You don't go to the gym only when you're injured. Therapy works best when you're functional but want to feel better - which is exactly where you are.",
    personalizedAddition: (answers: Record<string, string>) => {
      if (answers.q2 === 'Q2_A5') {
        return " You told us you're functioning fine but not feeling good - that's the exact sweet spot for therapy to make the biggest impact.";
      }
      return "";
    }
  },
  {
    question: "What if I get a therapist who doesn't get me?",
    answer: "You can switch therapists anytime until you find the right fit. Most people find their person within 1-2 tries.",
    personalizedAddition: (answers: Record<string, string>) => {
      if (answers.q4 === 'Q4_A6') {
        return " Since therapy didn't work out for you before, you now know what to look for in a therapist - that makes finding the right fit way easier the second time.";
      }
      return "";
    }
  },
  {
    question: "What if it doesn't work?",
    answer: "Based on what you shared, [PROBLEM] is exactly what therapy's designed for. The people it doesn't work for are usually the ones who aren't ready or don't find the right therapist. You can control both of those things.",
    personalizedAddition: (_answers: Record<string, string>) => {
      return "";
    }
  },
  {
    question: "I don't know what to talk about",
    answer: "That's exactly what the first session is for. You show up, start talking about what brought you there, and the therapist helps you figure out what to focus on. You don't need to have it all figured out - that's their job.",
    personalizedAddition: (answers: Record<string, string>) => {
      if (answers.q2 === 'Q2_A6') {
        return " You said you know something's off but can't name it - that's exactly the kind of thing a good therapist helps you identify and understand.";
      }
      return "";
    }
  }
];

export default function TherapyQuiz() {
  const [currentStep, setCurrentStep] = useState<'intro' | 'results' | number>('intro');
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [showAffirmation, setShowAffirmation] = useState(false);
  const [currentAffirmation, setCurrentAffirmation] = useState('');
  const [expandedFAQ, setExpandedFAQ] = useState<number | null>(null);
  const [expandedBetterHelp, setExpandedBetterHelp] = useState(false);

  const handleStartQuiz = () => {
    setCurrentStep(0);
  };

  const handleAnswer = (questionId: number, answerId: string, affirmation: string) => {
    setAnswers({ ...answers, [`q${questionId}`]: answerId });
    setCurrentAffirmation(affirmation);
    setShowAffirmation(true);
  };

  const handleAffirmationContinue = () => {
    setShowAffirmation(false);
    if (typeof currentStep === 'number' && currentStep < QUESTIONS.length - 1) {
      setCurrentStep(currentStep + 1);
    } else {
      setCurrentStep('results');
    }
  };


  // Helper function to render text with markdown formatting
  const renderMarkdownText = (text: string) => {
    if (!text) return null;

    // Split by double line breaks to get paragraphs
    const paragraphs = text.split(/\n\s*\n/).filter((p: string) => p.trim());

    return (
      <div className="space-y-4">
        {paragraphs.map((paragraph: string, idx: number) => {
          // Process inline markdown in each paragraph
          const parts = [];
          let remaining = paragraph.trim();
          let key = 0;

          while (remaining.length > 0) {
            // Check for bold **text** (allowing nested italics)
            // Changed regex to match everything until the CLOSING **
            const boldMatch = remaining.match(/^\*\*(.+?)\*\*/);
            if (boldMatch) {
              // Check if the captured text contains italic markers
              const innerText = boldMatch[1];
              const hasItalics = innerText.includes('*');

              if (hasItalics) {
                // Process nested italics inside bold
                const innerParts = [];
                let innerRemaining = innerText;
                let innerKey = 0;

                while (innerRemaining.length > 0) {
                  const italicMatch = innerRemaining.match(/^\*([^*]+)\*/);
                  if (italicMatch) {
                    innerParts.push(
                      <em key={innerKey++} className="italic">
                        {italicMatch[1]}
                      </em>
                    );
                    innerRemaining = innerRemaining.slice(italicMatch[0].length);
                  } else {
                    const nextAsterisk = innerRemaining.indexOf('*');
                    if (nextAsterisk === -1) {
                      innerParts.push(<span key={innerKey++}>{innerRemaining}</span>);
                      break;
                    } else {
                      innerParts.push(<span key={innerKey++}>{innerRemaining.slice(0, nextAsterisk)}</span>);
                      innerRemaining = innerRemaining.slice(nextAsterisk);
                    }
                  }
                }

                parts.push(
                  <strong key={key++} className="font-semibold">
                    {innerParts}
                  </strong>
                );
              } else {
                // No nested formatting
                parts.push(
                  <strong key={key++} className="font-semibold">
                    {innerText}
                  </strong>
                );
              }

              remaining = remaining.slice(boldMatch[0].length);
              continue;
            }

            // Check for italic *text* (only when not inside bold)
            const italicMatch = remaining.match(/^\*([^*]+)\*/);
            if (italicMatch) {
              parts.push(
                <em key={key++} className="italic">
                  {italicMatch[1]}
                </em>
              );
              remaining = remaining.slice(italicMatch[0].length);
              continue;
            }

            // Check for em dash —
            if (remaining.startsWith('—')) {
              parts.push(<span key={key++}> — </span>);
              remaining = remaining.slice(1);
              continue;
            }

            // Regular text - find next special character
            const nextSpecial = remaining.search(/[\*—]/);
            if (nextSpecial === -1) {
              parts.push(<span key={key++}>{remaining}</span>);
              break;
            } else {
              parts.push(<span key={key++}>{remaining.slice(0, nextSpecial)}</span>);
              remaining = remaining.slice(nextSpecial);
            }
          }

          return (
            <p
              key={idx}
              className="text-[17px] leading-tight text-black"
              style={{ fontFamily: 'Lexend, sans-serif' }}
            >
              {parts}
            </p>
          );
        })}
      </div>
    );
  };


  const getResultText = (key: keyof typeof RESULTS_LOGIC, answerCombo: string) => {
    const logic = RESULTS_LOGIC[key] as Record<string, string>;

    // Try exact match first
    if (logic[answerCombo]) {
      return logic[answerCombo];
    }

    // Try wildcard match (e.g., Q2_A3+Q5_ANY matches Q2_A3+Q5_A2)
    const parts = answerCombo.split('+');
    if (parts.length === 2) {
      // Try replacing second part with Q4_ANY or Q5_ANY
      const q4Wildcard = `${parts[0]}+Q4_ANY`;
      if (logic[q4Wildcard]) {
        return logic[q4Wildcard];
      }

      const q5Wildcard = `${parts[0]}+Q5_ANY`;
      if (logic[q5Wildcard]) {
        return logic[q5Wildcard];
      }
    }

    // Try partial matches (for fallback logic)
    for (let i = parts.length; i > 0; i--) {
      const partialKey = parts.slice(0, i).join('+');
      if (logic[partialKey]) {
        return logic[partialKey];
      }
    }

    return "";
  };

  const renderQuestion = () => {
    if (typeof currentStep !== 'number') return null;
    const question = QUESTIONS[currentStep];

    return (
      <div className="min-h-screen bg-white flex flex-col" style={{ padding: '25px' }}>
        {/* Progress Bar - At Top */}
        <div className="mb-6">
          <div className="flex items-center gap-3 mb-2">
            <div className="flex-1 bg-[#E5E5E5] rounded-[21px] h-2">
              <div
                className="h-2 rounded-[21px] transition-all duration-300"
                style={{
                  width: `${((currentStep + 1) / QUESTIONS.length) * 100}%`,
                  background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)'
                }}
              />
            </div>
            <span
              className="text-[14px] text-black/50 leading-tight whitespace-nowrap"
              style={{ fontFamily: 'Lexend, sans-serif' }}
            >
              {currentStep + 1} of {QUESTIONS.length}
            </span>
          </div>
        </div>

        {/* Content - Centered */}
        <div className="flex-1 flex items-center justify-center">
          <div className="max-w-2xl w-full">
            {/* Question - Light blue background box */}
            <div
              className="px-4 py-3 rounded-[21px] mb-6"
              style={{
                backgroundColor: '#E0F2FE',
                boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
              }}
            >
              <h2
                className="text-[22px] text-black leading-tight"
                style={{ fontFamily: 'Lexend, sans-serif' }}
              >
                {question.text}
              </h2>
            </div>

            {/* Options */}
            <div className="space-y-3">
              {question.options.map((option: { id: string; text: string; affirmation: string }) => (
                <button
                  key={option.id}
                  onClick={() => handleAnswer(question.id, option.id, option.affirmation)}
                  className="w-full text-left px-4 py-3 rounded-[21px] active:opacity-50 transition-all"
                  style={{
                    fontFamily: 'Lexend, sans-serif',
                    background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
                    boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                  }}
                >
                  <span className="text-[17px] text-white leading-tight">{option.text}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  };

  const renderAffirmation = () => {
    if (typeof currentStep !== 'number') return null;
    const currentQuestion = QUESTIONS[currentStep];
    const selectedAnswer = currentQuestion.options.find(
      (opt: { id: string; text: string; affirmation: string }) => opt.id === answers[`q${currentQuestion.id}`]
    );

    // Extract emoji from answer text
    const fullText = selectedAnswer?.text || '';
    // Comprehensive emoji regex that matches all emoji ranges including symbols
    const emojiMatch = fullText.match(/^([\u{1F300}-\u{1FAD6}]|[\u{1F900}-\u{1F9FF}]|[\u{2600}-\u{26FF}]|[\u{2700}-\u{27BF}]|[\u{1F600}-\u{1F64F}]|[\u{1F680}-\u{1F6FF}]|[\u{1F1E0}-\u{1F1FF}]|[\u{23E9}-\u{23FA}]|[\u{25AA}-\u{25FE}]|[\u{2B50}-\u{2B55}])\s*/u);
    const emoji = emojiMatch ? emojiMatch[1] : '✓';
    const answerTextWithoutEmoji = fullText.replace(/^([\u{1F300}-\u{1FAD6}]|[\u{1F900}-\u{1F9FF}]|[\u{2600}-\u{26FF}]|[\u{2700}-\u{27BF}]|[\u{1F600}-\u{1F64F}]|[\u{1F680}-\u{1F6FF}]|[\u{1F1E0}-\u{1F1FF}]|[\u{23E9}-\u{23FA}]|[\u{25AA}-\u{25FE}]|[\u{2B50}-\u{2B55}])\s*/u, '').trim();

    return (
      <div className="min-h-screen bg-gradient-to-br from-[#F0FDF4] to-[#ECFDF5] flex flex-col" style={{ padding: '25px', paddingBottom: '120px' }}>
        {/* Progress Bar */}
        <div className="mb-6">
          <div className="flex items-center gap-3 mb-2">
            <div className="flex-1 bg-white/40 rounded-[21px] h-2">
              <div
                className="h-2 rounded-[21px] transition-all duration-300"
                style={{
                  width: `${((currentStep + 1) / QUESTIONS.length) * 100}%`,
                  background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)'
                }}
              />
            </div>
            <span
              className="text-[14px] text-black/50 leading-tight whitespace-nowrap"
              style={{ fontFamily: 'Lexend, sans-serif' }}
            >
              {currentStep + 1} of {QUESTIONS.length}
            </span>
          </div>
        </div>

        <div className="flex-1 flex flex-col items-center" style={{ paddingTop: '40px' }}>
          <div className="max-w-xl w-full">
            {/* Emoji in Green Circle */}
            <div className="flex justify-center mb-6">
              <div
                className="w-32 h-32 rounded-full flex items-center justify-center"
                style={{
                  background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
                  boxShadow: '0 4px 20px rgba(91, 180, 169, 0.3)',
                  fontSize: '75px'
                }}
              >
                {emoji}
              </div>
            </div>

            {/* Answer Text - White Rounded Box */}
            <div
              className="px-4 py-3 rounded-[21px] bg-white/80 backdrop-blur-sm mb-6 text-center"
              style={{ boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)' }}
            >
              <p
                className="text-[17px] leading-tight text-black/80"
                style={{ fontFamily: 'Lexend, sans-serif' }}
              >
                {answerTextWithoutEmoji}
              </p>
            </div>

            {/* Affirmation text - Light blue rounded box */}
            <div
              className="px-4 py-3 rounded-[21px] space-y-4"
              style={{
                backgroundColor: '#E0F2FE',
                boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
              }}
            >
              {(() => {
                const paragraphs = currentAffirmation.split(/\n\s*\n/).filter(p => p.trim());

                return paragraphs.map((paragraph, idx) => {
                  const parts = [];
                  let remaining = paragraph.trim();
                  let key = 0;

                  while (remaining.length > 0) {
                    const boldMatch = remaining.match(/^\*\*([^*]+)\*\*/);
                    if (boldMatch) {
                      parts.push(
                        <strong key={key++} className="font-semibold">
                          {boldMatch[1]}
                        </strong>
                      );
                      remaining = remaining.slice(boldMatch[0].length);
                      continue;
                    }

                    const italicMatch = remaining.match(/^\*([^*]+)\*/);
                    if (italicMatch) {
                      parts.push(
                        <em key={key++} className="italic">
                          {italicMatch[1]}
                        </em>
                      );
                      remaining = remaining.slice(italicMatch[0].length);
                      continue;
                    }

                    if (remaining.startsWith('—')) {
                      parts.push(<span key={key++}> — </span>);
                      remaining = remaining.slice(1);
                      continue;
                    }

                    const nextSpecial = remaining.search(/[\*—]/);
                    if (nextSpecial === -1) {
                      parts.push(<span key={key++}>{remaining}</span>);
                      break;
                    } else {
                      parts.push(<span key={key++}>{remaining.slice(0, nextSpecial)}</span>);
                      remaining = remaining.slice(nextSpecial);
                    }
                  }

                  return (
                    <p
                      key={idx}
                      className="text-[17px] leading-tight text-black/90"
                      style={{ fontFamily: 'Lexend, sans-serif' }}
                    >
                      {parts}
                    </p>
                  );
                });
              })()}
            </div>
          </div>
        </div>

        {/* Fixed Continue Button */}
        <div
          className="fixed bottom-0 left-0 right-0 bg-white/80 backdrop-blur-md border-t border-white/60 pt-4 pb-8"
          style={{
            paddingLeft: '25px',
            paddingRight: '25px',
            boxShadow: '0 -4px 20px rgba(0, 0, 0, 0.06)'
          }}
        >
          <div className="max-w-xl mx-auto">
            <button
              onClick={handleAffirmationContinue}
              className="w-full px-4 py-3 text-[17px] text-white rounded-[21px] active:scale-95 transition-transform"
              style={{
                fontFamily: 'Lexend, sans-serif',
                background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
                boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
              }}
            >
              Continue
            </button>
          </div>
        </div>
      </div>
    );
  };

  const renderResults = () => {
    // Build lookup keys
    const q2q3Key = `${answers.q2}+${answers.q3}`;
    const q2q5Key = `${answers.q2}+${answers.q5}`;
    const q3q4Key = `${answers.q3}+${answers.q4}`;

    // Get all result sections
    const whatDealing = getResultText('whatYoureDealing', q2q3Key);
    const whatNeed = getResultText('whatYouNeed', q2q5Key) || getResultText('whatYouNeed', answers.q2);
    const therapyOpening = getResultText('therapyOpening', q3q4Key) || getResultText('therapyOpening', answers.q3);
    const therapyOutcome = getResultText('therapyOutcomeBridge', answers.q5);
    const therapyValidation = getResultText('therapyValidation', answers.q1);

    // Get problem label for FAQ
    const problemLabels: Record<string, string> = {
      'Q2_A1': 'feeling stuck',
      'Q2_A2': 'stress and anxiety',
      'Q2_A3': 'unprocessed experiences',
      'Q2_A4': 'relationship patterns',
      'Q2_A5': 'the gap between functioning and feeling good',
      'Q2_A6': 'this vague discomfort'
    };
    const problemLabel = problemLabels[answers.q2] || 'what you shared';

    return (
      <div className="min-h-screen bg-white pb-48" style={{ padding: '25px' }}>
        <div className="max-w-3xl mx-auto">
          <div className="space-y-6">
            {/* Header */}
            <div className="text-center pb-6 border-b-2 border-black/10">
              <h1
                className="text-[32px] font-medium text-black leading-tight mb-4"
                style={{ fontFamily: 'Lexend, sans-serif' }}
              >
                Your Results
              </h1>
              <p
                className="text-[14px] text-black/50 leading-tight"
                style={{ fontFamily: 'Lexend, sans-serif' }}
              >
                Based on your answers, here's what we found
              </p>
            </div>

            {/* Section 1: What You're Dealing With */}
            <div
              className="px-4 py-3 rounded-[21px] bg-white"
              style={{ boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)' }}
            >
              <div
                className="px-4 py-3 rounded-[21px] mb-4"
                style={{
                  background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)'
                }}
              >
                <h2
                  className="text-[22px] leading-tight text-white"
                  style={{ fontFamily: 'Lexend, sans-serif' }}
                >
                  What You're Dealing With
                </h2>
              </div>
              {renderMarkdownText(whatDealing)}
            </div>

            {/* Section 2: What You Need */}
            <div
              className="px-4 py-3 rounded-[21px] bg-white"
              style={{ boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)' }}
            >
              <div
                className="px-4 py-3 rounded-[21px] mb-4"
                style={{
                  background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)'
                }}
              >
                <h2
                  className="text-[22px] leading-tight text-white"
                  style={{ fontFamily: 'Lexend, sans-serif' }}
                >
                  What You Need
                </h2>
              </div>
              {renderMarkdownText(whatNeed)}
            </div>

            {/* Section 3: Does Therapy Make Sense */}
            <div
              className="px-4 py-3 rounded-[21px] bg-white"
              style={{ boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)' }}
            >
              <div
                className="px-4 py-3 rounded-[21px] mb-4"
                style={{
                  background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)'
                }}
              >
                <h2
                  className="text-[22px] leading-tight text-white"
                  style={{ fontFamily: 'Lexend, sans-serif' }}
                >
                  Does Therapy Make Sense For You?
                </h2>
              </div>

              <div className="space-y-4">
                {/* Subsection 1: Based on Your Answers */}
                <div
                  className="px-4 py-3 rounded-[21px]"
                  style={{
                    backgroundColor: '#F0F9FF',
                    boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                  }}
                >
                  <h3
                    className="text-[22px] mb-4 text-black leading-tight"
                    style={{ fontFamily: 'Lexend, sans-serif' }}
                  >
                    Based on Your Answers
                  </h3>
                  {renderMarkdownText(therapyOpening)}
                </div>

                {/* Subsection 2: What Therapy Can Help You Achieve */}
                <div
                  className="px-4 py-3 rounded-[21px]"
                  style={{
                    backgroundColor: '#F0FDF4',
                    boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                  }}
                >
                  <h3
                    className="text-[22px] mb-4 text-black leading-tight"
                    style={{ fontFamily: 'Lexend, sans-serif' }}
                  >
                    What Therapy Can Help You Achieve
                  </h3>
                  {renderMarkdownText(therapyOutcome)}
                </div>

                {/* Subsection 3: Why This Makes Sense For You */}
                <div
                  className="px-4 py-3 rounded-[21px]"
                  style={{
                    backgroundColor: '#FFFBEB',
                    boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                  }}
                >
                  <h3
                    className="text-[22px] mb-4 text-black leading-tight"
                    style={{ fontFamily: 'Lexend, sans-serif' }}
                  >
                    Why This Makes Sense For You
                  </h3>
                  {renderMarkdownText(therapyValidation)}
                </div>
              </div>
            </div>

            {/* BetterHelp CTA Section - Expandable */}
            <div
              className="px-4 py-3 rounded-[21px] mt-6"
              style={{
                backgroundColor: '#F8BF74',
                boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
              }}
            >
              {/* Header - Always Visible */}
              <button
                onClick={() => setExpandedBetterHelp(!expandedBetterHelp)}
                className="w-full text-left active:opacity-50"
              >
                <div className="text-center mb-4">
                  <h2
                    className="text-[22px] leading-tight text-black mb-2"
                    style={{ fontFamily: 'Lexend, sans-serif' }}
                  >
                    💚 Members Get 20% Off Therapy with BetterHelp
                  </h2>
                </div>

                <div className="flex items-center justify-center gap-2 text-black/50">
                  <span
                    className="text-[17px] underline"
                    style={{ fontFamily: 'Lexend, sans-serif' }}
                  >
                    Why BetterHelp?
                  </span>
                  <svg
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="none"
                    style={{
                      transform: expandedBetterHelp ? 'rotate(180deg)' : 'rotate(0deg)',
                      transition: 'transform 0.2s'
                    }}
                  >
                    <path
                      d="M6 9L12 15L18 9"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>
              </button>

              {/* Expandable Benefits */}
              {expandedBetterHelp && (
                <div className="mt-5 space-y-3">
                  {/* Why BetterHelp Header */}
                  <h3
                    className="text-[22px] text-black mb-4 leading-tight"
                    style={{ fontFamily: 'Lexend, sans-serif' }}
                  >
                    Why BetterHelp
                  </h3>

                  {/* Benefit 1 */}
                  <div
                    className="px-4 py-3 rounded-[21px]"
                    style={{
                      backgroundColor: '#FFF9F0',
                      border: '2px solid #F8BF74',
                      boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                    }}
                  >
                    <div className="flex items-start gap-3">
                      <span className="text-[24px]">💰</span>
                      <p
                        className="text-[17px] leading-tight text-black/90"
                        style={{ fontFamily: 'Lexend, sans-serif' }}
                      >
                        Up to 50% more affordable than traditional in-person therapy without insurance
                      </p>
                    </div>
                  </div>

                  {/* Benefit 2 */}
                  <div
                    className="px-4 py-3 rounded-[21px]"
                    style={{
                      backgroundColor: '#FFF9F0',
                      border: '2px solid #F8BF74',
                      boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                    }}
                  >
                    <div className="flex items-start gap-3">
                      <span className="text-[24px]">💬</span>
                      <p
                        className="text-[17px] leading-tight text-black/90"
                        style={{ fontFamily: 'Lexend, sans-serif' }}
                      >
                        Message your licensed therapist anytime - not just during your weekly session
                      </p>
                    </div>
                  </div>

                  {/* Benefit 3 */}
                  <div
                    className="px-4 py-3 rounded-[21px]"
                    style={{
                      backgroundColor: '#FFF9F0',
                      border: '2px solid #F8BF74',
                      boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                    }}
                  >
                    <div className="flex items-start gap-3">
                      <span className="text-[24px]">🤝</span>
                      <p
                        className="text-[17px] leading-tight text-black/90"
                        style={{ fontFamily: 'Lexend, sans-serif' }}
                      >
                        Join nearly 3 million people who've sought help through online professional therapy
                      </p>
                    </div>
                  </div>

                  {/* Benefit 4 */}
                  <div
                    className="px-4 py-3 rounded-[21px]"
                    style={{
                      backgroundColor: '#FFF9F0',
                      border: '2px solid #F8BF74',
                      boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                    }}
                  >
                    <div className="flex items-start gap-3">
                      <span className="text-[24px]">✅</span>
                      <p
                        className="text-[17px] leading-tight text-black/90"
                        style={{ fontFamily: 'Lexend, sans-serif' }}
                      >
                        Switch therapists or cancel anytime - find your perfect fit with zero commitment
                      </p>
                    </div>
                  </div>

                  {/* CTA Button */}
                  <a
                    href="https://clear30.org/redir/?des=therapy_quiz"
                    className="block w-full text-center px-4 py-3 text-[17px] text-white rounded-[21px] active:scale-95 transition-transform mt-4"
                    style={{
                      fontFamily: 'Lexend, sans-serif',
                      background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
                      boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                    }}
                  >
                    Get Started with 20% Off →
                  </a>
                </div>
              )}
            </div>
          </div>


          {/* Section 4: FAQ */}
          <div
            className="px-4 py-3 rounded-[21px] bg-white"
            style={{ boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)' }}
          >
            <div
              className="px-4 py-3 rounded-[21px] mb-6"
              style={{
                background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)'
              }}
            >
              <h2
                className="text-[22px] leading-tight text-white"
                style={{ fontFamily: 'Lexend, sans-serif' }}
              >
                Common Questions About Starting Therapy
              </h2>
            </div>
            <div className="space-y-4">
              {FAQ_DATA.map((faq, index) => {
                const personalizedText = faq.personalizedAddition(answers);
                const answerText = faq.answer.replace('[PROBLEM]', problemLabel);
                const fullAnswer = answerText + personalizedText;
                const isExpanded = expandedFAQ === index;

                return (
                  <div
                    key={index}
                    className="border-2 rounded-[21px] overflow-hidden transition-all"
                    style={{ borderColor: '#5BB4A9' }}
                  >
                    {/* Question - Clickable Header */}
                    <button
                      onClick={() => setExpandedFAQ(isExpanded ? null : index)}
                      className="w-full text-left px-4 py-3 flex items-center justify-between active:opacity-50 transition-opacity"
                      style={{
                        background: isExpanded ? 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)' : 'white'
                      }}
                    >
                      <h3
                        className="text-[17px] leading-tight pr-4"
                        style={{
                          fontFamily: 'Lexend, sans-serif',
                          color: isExpanded ? 'white' : 'black'
                        }}
                      >
                        {faq.question}
                      </h3>
                      <svg
                        width="24"
                        height="24"
                        viewBox="0 0 24 24"
                        fill="none"
                        style={{
                          transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)',
                          transition: 'transform 0.2s'
                        }}
                      >
                        <path
                          d="M6 9L12 15L18 9"
                          stroke={isExpanded ? 'white' : '#5BB4A9'}
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                    </button>

                    {/* Answer - Expandable Content */}
                    {isExpanded && (
                      <div className="px-4 py-3 bg-white border-t-2" style={{ borderColor: '#5BB4A9' }}>
                        {renderMarkdownText(fullAnswer)}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Fixed Bottom CTAs */}
          <div
            className="fixed bottom-0 left-0 right-0 bg-white pt-3 pb-6"
            style={{
              paddingLeft: '25px',
              paddingRight: '25px',
              boxShadow: '0 -4px 20px rgba(0, 0, 0, 0.1)',
              zIndex: 50
            }}
          >
            <div className="max-w-3xl mx-auto space-y-3">
              {/* BetterHelp CTA - Green Gradient */}
              <a
                href="https://clear30.org/redir/?des=therapy_quiz"
                className="block w-full text-center px-4 py-3 text-[17px] text-white rounded-[21px] active:scale-95 transition-transform"
                style={{
                  fontFamily: 'Lexend, sans-serif',
                  background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
                  boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
                }}
              >
                Explore Therapy with BetterHelp
              </a>

              {/* Return to Clear30 Button */}
              <a
                href="clear30://open"
                className="block w-full text-center px-4 py-3 text-[17px] rounded-[21px] active:scale-95 transition-transform"
                style={{
                  fontFamily: 'Lexend, sans-serif',
                  background: 'white',
                  color: '#5BB4A9',
                  border: '2px solid #5BB4A9'
                }}
              >
                Return to Clear30
              </a>
            </div>
          </div>
        </div>
      </div>
    );
  };

  // Main render logic
  if (currentStep === 'intro') {
    return <QuizIntro onStartQuiz={handleStartQuiz} />;
  }

  if (currentStep === 'results') {
    return renderResults();
  }

  if (showAffirmation) {
    return renderAffirmation();
  }

  return renderQuestion();
}
