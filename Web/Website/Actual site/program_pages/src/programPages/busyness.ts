import { ProgramPage } from "@/js/types";

const Busyness: ProgramPage = {
  title: "Staying Busy",
  quote:
    "Cravings don't like busy, engaged people because busy, engaged people don't think about cravings",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `"The best way to get rid of old habits is to swap them for new ones."`,
          styling: "remove-padding",
        },
        {
          type: "text",
          content: `This quote is more relevant than ever when breaking from a habit like weed. It might seem challenging – after all, smoking may have been a long-standing pastime. But hunting down fresh, intriguing activities that mirror your values and priorities is hard. The outcome? You'll shatter the chains of old habits and discover a more satisfying life. Listen here for more.`,
          styling: "",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/busyness.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/busyness.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Schedule next day activities each night so you have some structure for each day.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "What's the plan?",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `🏋️‍♂️ Work out or meditate, binge a new series, listen to an engaging podcast, or fine-tune a hobby. Now's the perfect opportunity to embark on something new. Staying busy is avoiding unstructured time. We don't need to say anything else. Just do it.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Staying Busy",
      shareDescription: "Staying Busy page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Healthshots: The dangers of idle living`,
          link: "https://www.healthshots.com/mind/mental-health/do-you-find-yourself-sitting-idle-often-heres-how-it-can-wreck-your-mental-health/",
        },
        {
          type: "external",
          description: `Reddit: Things to do instead of smoking weed`,
          link: "https://www.reddit.com/r/leaves/comments/jdy7b5/things_to_do_instead_of_smoking_weed/",
        },
        {
          type: "external",
          description: `Reddit: Things to do instead of smoking weed, part 2`,
          link: "https://www.reddit.com/r/leaves/comments/djhjw5/a_list_of_things_to_do_instead_of_smoking_weed/",
        },
      ],
    },
  ],
};

export default Busyness;
