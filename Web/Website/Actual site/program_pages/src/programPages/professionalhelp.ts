import { ProgramPage } from "@/js/types";

const ProfessionalHelp: ProgramPage = {
  title: "Discovering a World of Support",
  quote:
    "Asking for help does not mean that we are weak or incompetent. It usually indicates an advanced level of honesty and intelligence - AWS",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `You don't have to do your Clear30 alone. There are so many resources out there to help people along their journey. At Clear30, we want to help you find them. Here are some that we have found to be extremely helpful.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/professionalhelp.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/professionalhelp.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `There is support available. Go down the rabbithole and find one you like, even if just for your Clear30.`,
        },
        {
          type: "text",
          header: "",
          content: `📝 Important Note: Some of the sites and recommendations are for people who want to entirely stop. The reason we included them and sent them to everyone is because they have great information on the “first month”. Ignore what you don't like and embrace what you do.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: `Cannabis Social Support Sites`,
      paragraphs: [
        {
          type: "text",
          header: `Reddit Communities`,
          content: `Connect with like-minded individuals online and engage in insightful discussions about cannabis moderation and detoxification.`,
        },
        {
          type: "list",
          list: [
            `<b>r/Petioles</b>: Engage in discussions about reduction, moderation, and responsible consumption of cannabis.`,
            `<b>r/leaves</b>: Connect with people who are trying to quit cannabis.`,
            `<b>r/marijuanaanonymous</b>: An online hub for members of Marijuana Anonymous.`,
          ],
        },
        {
          type: "text",
          header: `Digital Support`,
          content: `There are lots digital remote treatments and apps if you want to completely stop. Just type in addiction recovery apps and tons will pop-up.`,
        },
        {
          type: "text",
          header: ``,
          content: `✅ One group we recommend is recovery dharma. In groups, they don't ask about your goals - whether for a t-break or stopping or for cannabis or trouble controlling food intake. They heavily focus on craving and using wise mind and meditation to overcome desires that move us away from our intentions.`,
        },
        {
          type: "list",
          list: [
            `<a href="https://recoverydharma.org/" target="_blank">Recovery dharma</a>`,
            `<a href="https://www.youtube.com/channel/UCgUZVKJJ_gqPMfSO2wj" target="_blank">Youtube: Recovery dharma</a>`,
          ],
        },
        {
          type: "text",
          header: `Other T-break guides`,
          content: `Check out these great t-break resources below to help you on your journey. We will be sending more as Clear30 unfolds!`,
        },
        {
          type: "list",
          list: [
            `<a href="https://www.weedless.org/" target="_blank">Weedless.org</a>`,
            `<a href="https://www.uvm.edu/health/t-break-take-cannabis-tolerance-break" target="_blank">UVM: On tolerance breaks</a>`,
            `<a href="https://mcwell.nd.edu/your-well-being/physical-well-being/drugs/marijuana-or-cannabis-sativa/quitting-marijuana-a-30-day-self-help-guide/" target="_blank">Mcwell: Self help guide for 30 day T-break</a>`,
          ],
        },
        {
          type: "text",
          header: `Podcasts and Videos`,
          content: `Hands down the best overall video series on stopping or reducing cannabis use is <a href="https://www.youtube.com/@AddictionMindset" target="_blank">Addiction Mindset</a>`,
        },
        {
          type: "list",
          list: [
            `<a href="https://www.youtube.com/@AddictionMindset/playlists" target="_blank">Addiction mindset: reducing cannabis use</a>`,
            `<a href="https://www.youtube.com/watch?v=WdM8r6R3FfM&t=1437s" target="_blank">Youtube: Dr. K talks weed</a>`,
            `<a href="https://www.youtube.com/watch?v=gXvuJu1kt48&t=1075s/" target="_blank">Youtube: Effects of cannabis on your body</a>`,
            `<a href="https://open.spotify.com/show/4iW8efuX6D4iW6Z2wwf1kE/" target="_blank">Spotify: Quitting marijuana podcast</a>`,
          ],
        },
        {
          type: "text",
          header: `Helplines and Crisis Text Lines`,
          content: `Immediate help is available if you're in crisis. Don't hesitate to reach out to these national helplines.`,
        },
        {
          type: "list",
          list: [
            `National Mental Health Support Helpline: Call 988`,
            `Crisis Text Line: Text 741741`,
          ],
        },
        {
          type: "list",
          header: `Treatment Finders (for substance use treatment)`,
          list: [
            `<a href="https://findtreatment.gov/" target="_blank">SAMHSA Treatment Locator</a>`,
            `<a href="https://treatmentatlas.org/" target="_blank">Shatterproof Atlas (Provides information on evidence based treatment)</a>`,
            `<a href="https://www.safeproject.us/locator/" target="_blank">SAMHSA Treatment Locator Safe Locator (Provides information on treatment, recovery housing, and family support groups)</a>`,
          ],
        },
        {
          type: "list",
          header: `Digital Telehealth Cannabis Treatments (More coming soon)`,
          list: [
            `<a href="https://www.affecttherapeutics.com/" target="_blank">Affect Therapeutics (Cannabis Specific Support)</a>`,
            `<a href="https://www.charliehealth.com/" target="_blank">Charlie Health</a>`,
            `<a href="https://r20.com/lifebeyondcannabis/" target="_blank">Life Beyond Cannabis (6-Week Abstinence Program)</a>`,
          ],
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Discovering a World of Support",
      shareDescription: "Discovering a World of Support page",
    },
  ],
};

export default ProfessionalHelp;
