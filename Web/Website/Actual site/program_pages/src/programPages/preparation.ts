import { ProgramPage } from "@/js/types";

const Preparation: ProgramPage = {
  title: "Setting the Stage",
  quote: "Before anything else, preparation is the key to success - AGB",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Preparation sets the stage for a successful Clear30. You will be getting individual messages and full pages about many of these topics during your Clear30, but it is also helpful to think about a few of them immediately. Listen to the Clear30 Preparation Meditation to get started and read the tips below.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/preparation.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/preparation.png",
    },
    {
      type: "TextSection",
      header: `Tips for a Successful Clear30`,
      paragraphs: [
        {
          type: "text",
          header: `🚀 Start off strong!`,
          content: `Regardless of what tools you use or recommendations you follow, truly commit to Clear30. For 30 days, your identity is someone who doesn’t use weed. It kind of doesn’t matter how you do it - just own and live Clear30. It is really fun to create a new identity for a month. Act as if, and the rest becomes so much easier.`,
        },
        {
          type: "text",
          header: `🔧 Use the Clear30 tools`,
          content: `Spend at least 5 minutes (hopefully more) on listening to Clear30 meditations, reading pages and resources, journaling, and responding to assessments. The more you engage - especially in the first 2 weeks - the better your chances of succeeding.`,
        },
        {
          type: "text",
          content: `<a href="./assets/pdfs/calendar.pdf" rel="noreferrer" target="_blank">Here</a> is a Clear30 Goal Calendar you can print out and keep next to your bed. It sounds simple, but it works to fill it out every night!`,
        },
        {
          type: "text",
          header: `🗑️ Get rid of your supply`,
          content: `Get rid of your papers, bongs, and anything related to substance use before you do Clear30. Other than giving your body a break, you will be giving your mind a break, too; having substance use paraphernalia may make this period more challenging. Ask a friend to store more expensive items; consider throwing out those that are not. Free your mind from temptation. Free your mind to do other, more healthful things.`,
        },
        {
          type: "text",
          header: `🎯 Identify your “mega trigger”`,
          content: `You will do lots of identifying triggers during Clear30, but if there is one specific trigger—perhaps going to bed, waking up, watching TV, or hanging out with a specific person or people —it is good to have an idea of how you will plan to cope with cravings during those times.`,
        },
        {
          type: "text",
          header: `🎉 Alternate activities`,
          content: `Start to think about other things you can do to create a fun and interesting Clear30. What are things you have always wanted to do but haven’t had the time for or just were stuck on? What is something new you can do?`,
        },
        {
          type: "text",
          header: `🌟 Intention setting`,
          content: `Setting your goal and intentions for the day is a win. Doing this in the mornings will help you center your day around your goals; doing this in the evenings can help keep your mind on the future and the benefits of this break. Use Clear30 daily meditations to help you get (and stay) intentional.`,
        },
        {
          type: "text",
          header: `🤝 Get support`,
          content: `There are three kinds of support that you can mix and match:`,
        },
        {
          type: "list",
          list: [
            `<b>👥 Group Clear30</b>. The first is doing Clear30 with friends. You would be surprised that some of your friends who smoke will do Clear30 with you if you ask. It will not only be helpful for you but also for them. It creates a very cool bonding experience if you are lucky enough to have these kinds of friendships.`,
            `<b>🙋 Solo Clear30 with non-user support</b>. Research has revealed the importance of supportive people—whether they use substances or not—to bounce things off of and to do things with. While not everyone has these relationships, if you have people who are willing to help you, let them know you are trying to make a change and would love their support in chatting or doing things that don’t involve weed over the next 30 days.`,
            `<b>💻 Solo Clear30 with online support</b>. Covid-19 made it clear that online support is really powerful and effective. Regardless of whether you have in-person support, use online support because it is there 24-7 and can be super targeted.`,
          ],
        },
        {
          type: "text",
          header: `💡 Do what works for you!`,
          content: `We will be sending lots of information over the next 30 days. Keep it simple. There is no way to follow every recommendation. It is meant to give you a menu to choose from. If you start to get overwhelmed, take a step back and focus on what is working for you. The goal is to take a 30-day break, not to follow a rule.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Setting the Stage",
      shareDescription: "Setting the Stage page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Suggestions during a break`,
          link: "https://www.reddit.com/r/canadients/comments/5yylas/taking_a_break_is_hard_any_suggestions/",
        },
        {
          type: "external",
          description: `Weedless Guide to Preparation (Don't worry that it says quitting, it still applies for a break too)`,
          link: "https://www.weedless.org/guide/",
        },
        {
          type: "external",
          description: `UVM: T-Break Preparation (and Guide): Definitely Check it out`,
          link: "https://www.uvm.edu/health/day-0-preparation",
        },
        {
          type: "external",
          description: `Mcwell: Self help guide for 30 day T-break`,
          link: "https://mcwell.nd.edu/your-well-being/physical-well-being/drugs/marijuana-or-cannabis-sativa/quitting-marijuana-a-30-day-self-help-guide/",
        },
        {
          type: "external",
          description: `Addiction Mindset: T-Breaks`,
          link: "https://www.youtube.com/watch?v=Zg8mYYwbA1I",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Bailey R. R. (2017). Goal Setting and Action Planning for Health Behavior Change. American journal of lifestyle medicine, 13(6), 615–618.`,
          link: "https://doi.org/10.1177/1559827617729634",
        },
        {
          type: "scientific",
          description: `Epton, T., Currie, S., & Armitage, C. J. (2017). Unique effects of setting goals on behavior change: Systematic review and meta-analysis. Journal of consulting and clinical psychology, 85(12), 1182.`,
          link: "https://dspace.stir.ac.uk/bitstream/1893/25978/1/goal%20meta%20(2017.09.25).pdf",
        },
      ],
    },
  ],
};

export default Preparation;
