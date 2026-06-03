import { ProgramPage } from "@/js/types";

const ShortLongTerm: ProgramPage = {
  title: "Shifting from Short-Term Gratification to Long-Term Success 🚀",
  quote:
    "I still haven't figured out how to make processed sugar good for me even though I try everyday",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `The journey from short-term gratification to long-term success is like climbing a mountain. It's a demanding task, requiring resilience, dedication, and an understanding of whether a short-term reward is helping, harming or neutral regarding your long-term goals.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/shortlongterm.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/shortlongterm.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Today, do 3 things that will improve your life one year from now.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: `Short-term vs. Long-term`,
      paragraphs: [
        {
          type: "text",
          header: `🧠 Our brains are hardwired for immediate pleasure`,
          content: `That is especially true for people who use substances, even though they are trying not to use. 💯 Hundreds of studies support that it is harder for regular users of substances to resist temptation in general than people who use occasionally or not at all. Essentially, your brain tricks you to remember the good things about use and minimize the bad in the moment (more on that tomorrow). You essentially have a built-in lying machine to feed your craving monster. So what does that mean?`,
        },
        {
          type: "text",
          header: ``,
          content: `👉 You have to work extra hard to overcome temptation when it comes to things that reward you in the short-term but may have longer-term negative consequences. Here are some questions to ask yourself and tips to get you in a long-term mindset. 👈`,
        },
        {
          type: "list",
          list: [
            `💡 <b>Understand the Nature of Desire</b>: Is your desire to use to change the moment despite you wanting to break or not use in that moment? `,
            `🎯 <b>Understand the Outcome</b>: It is okay to be a short-term chooser sometimes - in fact it is recommended - but not when you are on Clear30 or when  your choices affect your long-term wellbeing. How is your choice today going to affect how you feel tomorrow?`,
            `🏆 <b>Understand Successful People</b>: The most successful and happy people do not do things that only benefit the short-term. They work hard. They also play hard, but not when it affects their self-esteem or long-term goals. Find your balance.`,
            `🔄 <b>Understand the Cycle of Temptation</b>: Recognize that temptation starts with a trigger that tricks your mind into thinking it is okay, and ends with disregarding long-term goals.`,
            `🔮 <b>Keep the Big Picture in Mind</b>: Regularly reminding ourselves of our long-term goals can help us resist short-term temptations.`,
          ],
        },
        {
          type: "text",
          header: ``,
          content: `"You have to do something that sucks every day... That's how you overcome. That's how you get better." David Goggins`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Long-Term Success",
      shareDescription: "Long-Term Success page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Struggling with delaying gratification`,
          link: "https://www.reddit.com/r/productivity/comments/nvd4u0/tips_for_those_who_suck_at_delaying_gratification/",
        },
        {
          type: "external",
          description: `Reddit: Delaying gratification is the key to a happy life`,
          link: "https://www.reddit.com/r/getdisciplined/comments/gs12al/advice_master_delayed_gratification_is_the_secret/",
        },
        {
          type: "external",
          description: `Reddit: How to focus on long term rewards`,
          link: "https://www.reddit.com/r/PsychologicalTricks/comments/ej0ldy/pt_how_do_i_focus_on_long_term_rewards_rather/",
        },
        {
          type: "external",
          description: `Reddit: The power of long term thinking and how to get it`,
          link: "https://www.reddit.com/r/getdisciplined/comments/d2qkbf/advice_your_lack_of_consistency_comes_from_your/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Michael J. Sofis, Alan J. Budney, Catherine Stanger, Ashley A. Knapp, Jacob T. Borodovsky, Greater delay discounting and cannabis coping motives are associated with more frequent cannabis use in a large sample of adult cannabis users, Drug and Alcohol Dependence, Volume 207, 2020, 107820, ISSN 0376-8716.`,
          link: "https://www.sciencedirect.com/science/article/abs/pii/S0376871619305976/",
        },
        {
          type: "scientific",
          description: `Justin C. Strickland, Joshua A. Lile, William W. Stoops, Unique prediction of cannabis use severity and behaviors by delay discounting and behavioral economic demand, Behavioural Processes, Volume 140, 2017, Pages 33-40, ISSN 0376-6357`,
          link: "https://www.sciencedirect.com/science/article/abs/pii/S0376635716303898",
        },
        {
          type: "scientific",
          description: `MacKillop, J., Amlung, M.T., Few, L.R. et al. Delayed reward discounting and addictive behavior: a meta-analysis. Psychopharmacology 216, 305-321 (2011).`,
          link: "https://link.springer.com/article/10.1007/s00213-011-2229-0",
        },
        {
          type: "scientific",
          description: `Andrea Bari, Trevor W. Robbins, Inhibition and impulsivity: Behavioral and neural basis of response control, Progress in Neurobiology, Volume 108, 2013, Pages 44-79, ISSN 0301-0082`,
          link: "https://www.sciencedirect.com/science/article/abs/pii/S0301008213000543",
        },
      ],
    },
  ],
};

export default ShortLongTerm;
