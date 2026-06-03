import { ProgramPage } from "@/js/types";

const Social: ProgramPage = {
  title: "Finding Positive Social Support",
  quote:
    "Each new person represents a world in us, a world possibly not born until we reach out -  AN",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `When taking a break, having a good social support system can make a significant difference. People around you can encourage you, motivate you, and help you navigate the rough patches of this journey. Aside from supportive friends and family, there are numerous resources like online groups to assist you. Listen here for more.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/social.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/social.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Find someone, anyone, ideally multiple people, that you can connect with when you need support.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: `There is no secret sauce here.`,
      paragraphs: [
        {
          type: "text",
          header: ``,
          content: `✨ The bottom line is that connections with others will help you achieve your goals. Get over the hump of asking, and you will be shocked at the outcome.`,
        },
        {
          type: "list",
          list: [
            `👥 Identify at least one person you can lean on. Share your Clear30 plan with them and let them know how they can help you. There are always people available who do not use - more than you think, too.`,
            `🤝🧑‍🤝‍🧑 Join a group with people who share similar goals - whether what we have suggested in your feedback (and below) or one you find yourself. Engaging with people who understand your journey can make a big difference.`,
          ],
        },
        {
          type: "text",
          header: ``,
          content: `*** Note: we are working on having peer support during Clear30. If you are interested in volunteering, please email us at <a href="mailto:support@clear30.org?subject=Peer Support">support@clear30.org</a>`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Social Support",
      shareDescription: "Social Support page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Discussions about reduction, moderation, and responsible consumption of cannabis - 1.`,
          link: "https://www.reddit.com/r/Petioles/",
        },
        {
          type: "external",
          description: `Reddit: Discussions about reduction, moderation, and responsible consumption of cannabis - 2.`,
          link: "https://www.reddit.com/r/leaves/",
        },
        {
          type: "external",
          description: `Reddit: An online hub for members of Marijuana Anonymous.`,
          link: "https://www.reddit.com/r/MarijuanaAnonymous/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Gliksberg, O., Livne, O., Lev-Ran, S., Rehm, J., Hasson-Ohayon, I., & Feingold, D. (2022). The association between cannabis use and perceived social support: The mediating role of decreased social network. International Journal of Mental Health and Addiction, 20(5), 2799-2812.`,
          link: "https://doi.org/10.1007/s11469-021-00549-4",
        },
        {
          type: "scientific",
          description: `Huang, K. Y., & Long, Y. (2019). Fighting together: Discovering the antecedents of social support and helpful discussion threads in online support forums for cannabis quitters.`,
          link: "",
        },
      ],
    },
  ],
};

export default Social;
