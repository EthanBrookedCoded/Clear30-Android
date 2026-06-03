import { ProgramPage } from "@/js/types";

const FinalPage: ProgramPage = {
  title: "Congratulations on Completing Your Clear30!",
  quote: "You are fucking beautiful! Own it everyday.",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Yes, Yes, YES! You should be incredibly proud that you have completed your Clear30. You don't know us, the founders of Clear30, but be assured, we are proud of everyone who makes it to this point. You could have texted stop at any time. You could not be reading this message. You are and be proud. Please listen to this last meditation.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/finalpage.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/finalpage.png",
    },
    {
      type: "TextSection",
      header: `The Wise Path`,
      paragraphs: [
        {
          type: "text",
          header: ``,
          content: `🎉 Now that you've reached this milestone, it's time to reflect on the lessons you've learned.`,
        },
        {
          type: "text",
          header: ``,
          content: `😊 Did you discover new strategies to handle stress, sleep, or boredom?`,
        },
        {
          type: "text",
          header: ``,
          content: `🌿 Did you find activities to replace the time you spent on cannabis?`,
        },
        {
          type: "text",
          header: ``,
          content: `🤔 Did you find where cannabis worked and did not work in your life?`,
        },
        {
          type: "text",
          header: ``,
          content: `💭 Did you find a balanced approach to weed that is aligned with your intentions or come to the conclusion you are done with it all together?`,
        },
        {
          type: "text",
          header: ``,
          content: `🎉 Whatever lessons you learned are a stepping stone for your continued growth and well-being. Be proud of yourself. 👏`,
        },
        {
          type: "text",
          header: `Charting Your Future Path`,
          content: `🌟 The journey of personal growth is a continuous one. So, as you step into the future, take along the lessons you've learned, the resilience you've shown, and the self-appreciation you've cultivated. Here's to your continued journey towards self-improvement and personal growth! 🌟`,
        },
        {
          type: "text",
          header: ``,
          content: `We appreciate you. Stay tuned for more!`,
        },
        {
          type: "text",
          header: `Books on living intentionally`,
          content: ``,
        },
        {
          type: "list",
          list: [
            `Oxenreider, T. (2014). Notes from a Blue Bike: The Art of Living Intentionally in a Chaotic World. Thomas Nelson.`,
            `Clear, J. (2018). Atomic Habits: An Easy & Proven Way to Build Good Habits & Break Bad Ones. Avery.`,
            `Duhigg, C. (2012). The Power of Habit: Why We Do What We Do in Life and Business. Random House.`,
            `Fogg, B.J. (2019). Tiny Habits: The Small Changes That Change Everything. Houghton Mifflin Harcourt.`,
            `Heath, C., & Heath, D. (2010). Switch: How to Change Things When Change Is Hard. Broadway Books.`,
          ],
        },
        {
          type: "text",
          header: `Some books on quitting or moderating (not much on moderation - maybe we should write one!)`,
          content: ``,
        },
        {
          type: "list",
          list: [
            `Copeland, J. (2015). Quit Cannabis: An Expert Guide to Coping with Cravings and Withdrawal, Unscrambling Your Brain and Kicking the Habit for Good. CreateSpace Independent Publishing Platform.`,
            `Frank, N. (2020). Microdosing Cannabis: A Comprehensive Step-by-Step Guide to Enjoying Marijuana Responsibly. Rockridge Press.`,
            `Marijuana Anonymous: Quitting Weed: The Complete Guide. Retrieved July 26, 2023 from Lembke, A. (2021). Dopamine Nation: Finding Balance in the Age of Indulgence. Dutton.`,
          ],
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Congratulations on Completing Your Clear30!",
      shareDescription: "Congratulations on Completing Your Clear30! page",
    },
  ],
};

export default FinalPage;
