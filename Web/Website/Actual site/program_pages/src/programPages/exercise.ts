import { ProgramPage } from "@/js/types";

const Exercise: ProgramPage = {
  title: "Exercise and Cannabis Breaks",
  quote:
    "If people knew how good even a little exercise was for mental health, I'd be out of business - Therapist",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `There is probably no better way to reduce cravings and improve sleep and mood during your Clear30 than exercising. Check out today's meditation for some motivation and guidance.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/exercise.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/exercise.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Exercise for at least 30 minutes a day. It doesn't matter what you do as long as you move and sweat. It is the closest thing to a cure-all for withdrawal management and optimal physical and mental health.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: `🧠 The Science Behind Exercise and Cannabis Withdrawal`,
      paragraphs: [
        {
          type: "text",
          header: ``,
          content: `<b>The Wonderdrug</b>: Ever wondered why exercise is known as a wonderdrug? 🤔 Part of it has to do with the endocannabinoid system and dopamine. Like weed, exercise activates these systems providing a natural release of neurotransmitters.`,
        },
        {
          type: "text",
          header: ``,
          content: `✅ A study conducted at the University of Colorado Boulder found that exercise can reduce cravings, lessen anxiety, and improve concentration. Other studies highlight that it can significantly benefit sleep, including deep sleep like you get when you use cannabis. 🛌🌙`,
        },
        {
          type: "text",
          header: ``,
          content: `<b>Work it</b>: So what's the catch? Exercise takes work. It takes effort. There is no magic pill that makes you want to exercise.  Some people have used weed to reduce the boredom of exercise. If this is you, you actually have a leg up on those who don't exercise because you already have a routine! You just need to push through some of the psychological barriers from not having weed as your companion. For those without a routine, we have a couple of suggestions for your Clear30. 💡😊`,
        },
        {
          type: "text",
          header: ``,
          content: `<b>Your Plan</b>: There are countless programs available to help you exercise 🏃‍♀️. The bottom line is, don't be a perfectionist! Make sure to move your body daily in a way that feels good and works up a sweat 💦.`,
        },
        {
          type: "text",
          header: ``,
          content: `🏋️‍♂️ <b>Discover an exercise you won't resist</b>: Consider watching your favorite show while on a treadmill, enrolling in a local gym, taking a brisk run while listening to your preferred podcast, becoming a gym rat, or dancing along with a YouTube tutorial. It doesn't matter. JUST MOVE and do what you like.`,
        },
        {
          type: "text",
          header: ``,
          content: `👫 <b>Other Humans</b>: The only other thing that we recommend, if possible, is to try to exercise with a friend or a group of people. It keeps you accountable, and it keeps you motivated when you don't want to put in the effort.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Exercise and Cannabis Breaks",
      shareDescription: "Exercise and Cannabis Breaks page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Exercise for quitting`,
          link: "https://www.reddit.com/r/leaves/comments/4mc6m1/if_youre_trying_to_quit_exercise_really_helps/",
        },
        {
          type: "external",
          description: `Reddit: Replacing weed with exercise`,
          link: "https://www.reddit.com/r/IWantToLearn/comments/hjukcj/iwtl_how_to_replace_smoking_weed_with_exercise/",
        },
        {
          type: "external",
          description: `Reddit: Go work out`,
          link: "https://www.reddit.com/r/leaves/comments/pvhdm6/go_work_out/",
        },
        {
          type: "external",
          description: `Vanderbilt University: Exercise can curb marijuana use and cravings`,
          link: "https://news.vanderbilt.edu/2011/03/04/exercise-can-curb-marijuana-use-and-cravings/",
        },
        {
          type: "external",
          description: `The New York Times: Can Marijuana Make You a Better Athlete?`,
          link: "https://www.nytimes.com/2021/07/11/well/move/marijuana-olympics-ban.html",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Brellenthin, A. G., & Koltyn, K. F. (2016). Exercise as an adjunctive treatment for cannabis use disorder. The American journal of drug and alcohol abuse, 42(5), 481–489.`,
          link: "https://doi.org/10.1080/00952990.2016.1185434",
        },
        {
          type: "scientific",
          description: `Buchowski, M. S., Meade, N. N., Charboneau, E., Park, S., Dietrich, M. S., Cowan, R. L., & Martin, P. R. (2011). Aerobic exercise training reduces cannabis craving and use in non-treatment seeking cannabis-dependent adults. PloS one, 6(3), e17465.`,
          link: "https://doi.org/10.1371/journal.pone.0017465",
        },
        {
          type: "scientific",
          description: `Charron, J., Carey, V., Marcotte L'heureux, V., Roy, P., Comtois, A. S., & Ferland, P. M. (2021). Acute effects of cannabis consumption on exercise performance: a systematic and umbrella review. J Sports Med Phys Fitness, 61(4), 551-561.`,
          link: "https://doi.org/10.23736/s0022-4707.20.11003-x",
        },
        {
          type: "scientific",
          description: `Wilson, S. D., Collins, R. L., Prince, M. A., & Vincent, P. C. (2018). Effects of exercise on experimentally manipulated craving for cannabis: A preliminary study. Exp Clin Psychopharmacol, 26(5), 456-466.`,
          link: "https://doi.org/10.1037/pha0000200",
        },
        {
          type: "scientific",
          description: `York-Williams, S. L., Gust, C. J., Mueller, R., Bidwell, L. C., Hutchison, K. E., Gillman, A. S., & Bryan, A. D. (2019). The New Runner's High? Examining Relationships Between Cannabis Use and Exercise Behavior in States With Legalized Cannabis. Front Public Health, 7, 99.`,
          link: "https://doi.org/10.3389/fpubh.2019.00099",
        },
      ],
    },
  ],
};

export default Exercise;
