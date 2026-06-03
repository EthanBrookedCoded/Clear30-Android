import { ProgramPage } from "@/js/types";

const Distractions: ProgramPage = {
  title: "Using Distractions to Crush Cravings",
  quote:
    "Almost everything will work again if you unplug it for a few minutes...including you - AL",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `Using distractions can redirect your attention away from cannabis when you have a craving - a craving for anything. Distractions allow you to navigate through discomfort when facing it head-on is too much. Listen here for more.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/distractions.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/distractions.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Have a distraction readily available (eg bookmark, homescreen, contact).`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "💭 Distraction: A Powerful Tool for Cravings",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Ever wondered why distraction helps with cravings? Let's break it down:`,
        },
        {
          type: "text",
          header: "1️⃣ Limited Cognitive Resources",
          content: `Our brain, despite its might, has its limits. When you're distracted, it helps your brain stay areas– like attention and working memory - leave less room for craving-centric thoughts. This makes cravings less intense, more manageable. 🧠💡`,
        },
        {
          type: "text",
          header: "2️⃣ Habituation",
          content: `This is the fancy term for 'familiarity breeds contempt'. When we repeatedly expose ourselves to a craving, our response can dull over time. Distractions help us engage in this exposure without giving in, reducing the craving's intensity. So, we are learning that a craving does not equal using. That we have power! 🔄⏳`,
        },
        {
          type: "text",
          header: "3️⃣ Slowing down your thoughts",
          content: `Brain-imaging studies suggest that distractions can dial down the activity in brain regions associated with cravings, such as the ventral striatum and the orbitofrontal cortex. This means distractions can help control our brain's response to craving triggers by lessening their power. 🧠🔬`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "🧠 Activities that can get you distracted",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `We previously talked about 🏄‍♂️ urge surfing which is a very  powerful tool. However, the only goal is to not use during Clear30, so do anything you like or can that will distract you when you have a craving.`,
        },
        {
          type: "text",
          header: "",
          content: `🌟 Whether Wordle or your favorite Youtube channel, doing something else when you have a craving is powerful. Here are some fun sites that can help you find a quick distraction to get your mind off a negative thought!`,
        },
        {
          type: "list",
          list: [
            `<a href="https://www.mind.org.uk/need-urgent-help/how-can-i-distract-myself/games-and-puzzles/" target="_blank">🧩 Mind - Games and Puzzles</a>`,
            `<a href="https://safespace.vibrant.org/en/seeking-help/" target="_blank">🛠️ Coping tools and exercises</a>`,
            `<a href="https://www.jigsawplanet.com/?rc=play&pid=01fa0dabb06a" target="_blank">🧩 Puzzle Practice</a>`,
          ],
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Distractions",
      shareDescription: "Distractions page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Advice for distractions while taking a break from weed`,
          link: "https://www.reddit.com/r/trees/comments/6xufrb/any_advice_how_to_distract_yourself_when_making_a/",
        },
        {
          type: "external",
          description: `Reddit: The trick to a T-break is just distractions`,
          link: "https://www.reddit.com/r/trees/comments/yh9g70/the_trick_to_a_tbreak_is_just_distraction/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Ashe, M. L., Newman, M. G., & Wilson, S. J. (2015). Delay discounting and the use of mindful attention versus distraction in the treatment of drug addiction: a conceptual review. Journal of the experimental analysis of behavior, 103(1), 234-248.`,
          link: "",
        },
        {
          type: "scientific",
          description: `Kober, H., Mende-Siedlecki, P., Kross, E. F., Weber, J., Mischel, W., Hart, C. L., & Ochsner, K. N. (2010). Prefrontal-striatal pathway underlies cognitive regulation of craving. Proceedings of the National Academy of Sciences, 107(33), 14811-14816.`,
          link: "",
        },
        {
          type: "scientific",
          description: `Tiffany, S. T. (1990). A cognitive model of drug urges and drug-use behavior: Role of automatic and nonautomatic processes. Psychological Review, 97(2), 147-168.`,
          link: "",
        },
      ],
    },
  ],
};

export default Distractions;
