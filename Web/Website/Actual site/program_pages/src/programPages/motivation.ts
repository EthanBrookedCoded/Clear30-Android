import { ProgramPage } from "@/js/types";

const Motivation: ProgramPage = {
  title: "Weed and Motivation",
  quote:
    "I am most motivated to do clean my room when I should be doing something else",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Understanding how cannabis affects motivation isn't simple. Cannabis might have no effect or increase motivation in some people, especially in the short term, while heavy use over time could decrease it. Listen here for more.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/motivation.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/motivation.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Take a step back and see how weed is affecting your motivation and drive - both in the short-term and long-term.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: `Motivation and the Brain 💫`,
      paragraphs: [
        {
          type: "text",
          header: `Short Term Dopamine Increase`,
          content: `Cannabis can raise dopamine levels, leading to feelings of increased motivation in some people. This dopamine spike can also make some content with just hanging out, leading to lower drive. 😌`,
        },
        {
          type: "text",
          header: `😕 Long Term Dopamine Decrease`,
          content: `Regular and heavy cannabis use might lower dopamine levels over time. This drop could lead to feelings of indifference, lowering overall motivation in some, both when you are using and not using.`,
        },
        {
          type: "text",
          header: `🌿 Cannabis and the Prefrontal Cortex`,
          content: `Research also shows a link between heavy and long-term cannabis use and changes in the prefrontal cortex, a part of the brain involved in decision-making and motivation. These changes could affect your ability to make decisions, adding another layer to how cannabis affects motivation, especially around complex decisions and long-term goal directed behavior.`,
        },
        {
          type: "text",
          header: `Studies are Conflicting, so Awareness is Key 🎭`,
          content: `Despite what the brain studies and studies of long-term users show, some studies (not brain imaging) suggest that cannabis users who have been using for less than 5-10 years don't feel less motivated than non-users. Like almost anything we do, it highlights the need for personal exploration and awareness in understanding how cannabis might affect your motivation. <u>This is why you are doing Clear30!</u>`,
        },
        {
          type: "text",
          header: `Different Factors: The Role They Play 🎲`,
          content: `How cannabis affects motivation can change a lot depending on factors like:`,
        },
        {
          type: "list",
          list: [
            `How often you use📅`,
            `How long you have been using`,
            `Other substance use (more is worse)💊`,
            `Existing physical and mental health conditions💭😓`,
          ],
        },
        {
          type: "text",
          header: ``,
          content: `In short, the relationship between cannabis and motivation is complex and can depend on many factors. That is why it is up to you and your loved ones to figure out.`,
        },
        {
          type: "text",
          header: ``,
          content: `🚨 If you are feeling stuck or having trouble making important decisions in your life, definitely consider the role of weed. If you are not, phew! Either way, keep an eye on it because it can be subtle.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Weed and Motivation",
      shareDescription: "Weed and Motivation page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Low motivation during break`,
          link: "https://www.reddit.com/r/leaves/comments/1ggdoy/22_days_big_void_lack_of_motivation_apathy/",
        },
        {
          type: "external",
          description: `Reddit: Stopping for 3 months`,
          link: "https://www.reddit.com/r/leaves/comments/19dy9f/stopped_smoking_weed_for_3_months_nowlost/",
        },
        {
          type: "external",
          description: `Reddit: How marijuana saps motivation`,
          link: "https://www.reddit.com/r/OhioMarijuana/comments/pxizfo/marijuana_can_unquestionably_destroy_motivation/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Wong, Sam. Long-term cannabis use may blunt the brain's motivation system`,
          link: "https://www.imperial.ac.uk/news/124806/long-term-cannabis-blunt-brains-motivation-system/",
        },
        {
          type: "scientific",
          description: `Decreased dopamine brain reactivity in marijuana abusers is associated with negative emotionality and addiction severity`,
          link: "https://www.pnas.org/doi/10.1073/pnas.1411228111",
        },
        {
          type: "scientific",
          description: `Hetelekides E, Joseph VW, Pearson MR, Bravo AJ, Prince MA, Conner BT; Cross-Cultural Addictions Study Team; Protective Strategies Study Team; Marijuana Outcomes Study Team. Early Birds and Night Owls: Distinguishing Profiles of Cannabis Use Habits by Use Times with Latent Class Analysis. Cannabis. 2023 Feb 7;6(1):79-98.`,
          link: "https://pubmed.ncbi.nlm.nih.gov/37287731/",
        },
        {
          type: "scientific",
          description: `Martine Skumlien, MRes and others, Anhedonia, Apathy, Pleasure, and Effort-Based Decision-Making in Adult and Adolescent Cannabis Users and Controls, International Journal of Neuropsychopharmacology, Volume 26, Issue 1, January 2023, Pages 9-19.`,
          link: "https://academic.oup.com/ijnp/article/26/1/9/6674260/",
        },
      ],
    },
  ],
};

export default Motivation;
