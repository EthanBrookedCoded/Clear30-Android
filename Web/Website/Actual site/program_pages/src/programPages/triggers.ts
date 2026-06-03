import { ProgramPage } from "@/js/types";

const Triggers: ProgramPage = {
  title: "Managing and Avoiding Triggers to Use",
  quote:
    "If you stay in the barbershop long enough, you are going to get a haircut",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `Triggers are thoughts, behaviors, places, or even  people that can cause you to experience cravings. Our brains seek consistency. So when we walk by a dispensary or hangout with a weed friend, your brain automatically says, “okay, time to get high.”`,
          styling: "remove-padding",
        },
        {
          type: "text",
          content: `While triggers may feel hard to manage, know that there are ways to shape your world so you are faced with fewer triggers - which results in less use. Listen here for more.`,
          styling: "",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/triggers.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/triggers.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `When in your control, try to stay away from or lessen the biggest things - or mega triggers -  that tempt you the most during your Clear30. Now that you have been doing Clear30 for over a week, you may have identified others.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "💭 Personal Triggers: Recognize",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `🔍 Research shows those who encounter triggers are more likely to use when they didn't plan to, compared to those who stay clear of triggering situations or influences.`,
        },
        {
          type: "text",
          header: "",
          content: `Triggers can come in various forms. It could be a group of friends who enjoy lighting up, a stressful situation, a regular haunt, or even strong emotions like stress and anxiety if cannabis was your go-to coping mechanism. During the assessment, we asked you about a few. Now we are diving a little deeper. What are some other triggers you may not have thought about?`,
        },
        {
          type: "list",
          list: [
            `<b>Environmental</b>: Like specific locations or items associated with cannabis or cannabis-related media.`,
            `<b>Behavioral</b>: Like unhealthy sleeping or eating habits.`,
            `<b>Social</b>: Like being around friends who use or conflict with others.`,
            `<b>Emotional</b>: Like anger or boredom.`,
            `<b>Cognitive</b>: Like negative beliefs about yourself.`,
          ],
        },
      ],
    },
    {
      type: "TextSection",
      header: "Personal Triggers: Overcome",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `You have gotten some of these tips earlier for your key triggers. Now that you have been doing Clear30 for over a week,, you may have identified others. Here are some tips:`,
        },
        {
          type: "list",
          list: [
            `🔎 <b>Identify Your Triggers</b>: Use each encounter with a trigger as a learning moment rather than a setback. Reflect on the situations that spark cravings and name your triggers, taking away their power to push you off course. Take note of them.`,
            `🎭 <b>Plan Your Strategy</b>: Once you know all your triggers, devise a plan to avoid or cope with them. Change your routines, avoid cannabis-prone settings, and let your cannabis-using friends know about your goals to help you.`,
            `📝🗒️ <b>Avoid - Prepare -  Avoid</b>: Pretty much the easiest way to avoid triggers is to plan ahead. Anything you can do over your Clear30 to create your environment for health will make it way easier.`,
            `🌳🚀 <b>New Activities and Connections</b>: Invest time in exploring new activities that interest you. 🎯 Both new routines and new or different people. 🚀 This not only helps you avoid triggers but also provides fresh experiences and habits not linked with using.`,
          ],
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Triggers",
      shareDescription: "Triggers page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: How to stop smoking weed everyday`,
          link: "https://www.reddit.com/r/IWantToLearn/comments/7lh5ib/iwtl_how_to_stop_smoking_weed_everyday/",
        },
        {
          type: "external",
          description: `Reddit: Anecdote, resisting the reward of weed`,
          link: "https://www.reddit.com/r/trees/comments/83o5qw/i_havent_smoked_in_5_months_but_i_am_really/",
        },
        {
          type: "external",
          description: `Gateway Foundation: Common Relapse Triggers and How to Avoid Them`,
          link: "https://www.gatewayfoundation.org/addiction-blog/triggers-in-addiction-recovery/",
        },
        {
          type: "external",
          description: `Kingsway: Coping Skills to Deal With Addiction Triggers`,
          link: "https://kingswayrecovery.com/dealing-with-addiction-triggers/",
        },
        {
          type: "external",
          description: `Experience Recovery: Identifying addiction triggers`,
          link: "https://www.experiencerecovery.com/blog/identifying-addiction-triggers/",
        },
        {
          type: "external",
          description: `DrugRehab: Relapse Trigger Sources`,
          link: "https://www.drugrehab.com/recovery/triggers/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Ansell, E. B., Laws, H. B., Roche, M. J., & Sinha, R. (2015). Effects of marijuana use on impulsivity and hostility in daily life. Drug and alcohol dependence, 148, 136-142.`,
          link: "https://doi.org/10.1016/j.drugalcdep.2014.12.029",
        },
        {
          type: "scientific",
          description: `Cannabis triggers impulsive behavior among all users. (2013). Nursing Standard (through 2013), 28(12), 20.`,
          link: "https://doi.org/10.7748/ns2013.11.28.12.20.s22",
        },
        {
          type: "scientific",
          description: `O'Connor, R. J., & DiClemente, C. C. (2012). Relapse triggers in the marijuana-abstinent adolescent. The American journal of drug and alcohol abuse, 38(5), 442-451.`,
          link: "",
        },
        {
          type: "scientific",
          description: `Smith, J. D. (2022, February 5). Understanding Relapse Triggers in Addiction Recovery. Red Oak Recovery.`,
          link: "https://www.redoakrecovery.com/addiction-blog/relapse-triggers/",
        },
      ],
    },
  ],
};

export default Triggers;
