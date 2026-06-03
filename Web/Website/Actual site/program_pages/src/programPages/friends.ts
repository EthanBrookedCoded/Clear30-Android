import { ProgramPage } from "@/js/types";

const Friends: ProgramPage = {
  title: "Managing Your World When Your Friends Use",
  quote:
    "The most beautiful discovery true friends make is that they can grow separately without growing apart -  EF",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `Clear30 can be especially hard when you're surrounded by friends who continue to use, even if you use other social support. Most people struggle with pressure to use because they want to - not because anyone is forcing them.  That is why it is up to you to stay committed.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/friends.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/friends.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Set boundaries with yourself and be clear with your friends who use. They will support you.  Its only 30 days!`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "Navigating the Environment 🏞️",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `The first step towards success involves creating a social environment where you feel safe and in control. In practice, this means avoiding friend triggers. Here are five things you can do.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "Have the Conversation with Friends who Use",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Informing your friends about your decision is a vital step, and it might be easier than you think. Here's a way to go about it:`,
        },
        {
          type: "list",
          styling: "list-large-text",
          list: [
            "🤝 Be Honest: Clearly communicate why you've decided to take a break. Honesty will help them understand your decision better.",
            "👍 Be Positive: Share the benefits you anticipate from this break and how it aligns with your personal growth. ",
            "🗣️🚫 Don't badmouth weed or their choices. This is about you and only you.",
            "🙏 Request Support: Ask for their support during this period. They can help by respecting your decision, providing distraction when needed, and even joining you in positive activities.",
          ],
        },
        {
          type: "text",
          header: "👥 Identify When to Hang Out with Weed Friends",
          content: `Unless you want to avoid certain friends, one way to reduce triggers is to hang out with friends who use only when they are not using. If they are true friends, they will support you and work with you. You would be surprised at your positive influence on them, actually.`,
        },
        {
          type: "text",
          header: "🚫 Find Safe Environments",
          content: `Pick environments you can hang out that do not involve weed. That could be your place more often, or a neutral place like a cafe.`,
        },
        {
          type: "text",
          header: "🕵️‍♂️ Identify Non-Weed Friends",
          content: `There are always people available who do not use. More than you think too. Identify some non-weed friends to hang out with and make a plan to hang with them a couple of times. They don't have to be your new best friends. It is about opening up your world.`,
        },
        {
          type: "text",
          header: "📱Join online groups",
          content: `<b>Okay, we keep repeating this but there is a reason</b>. If you are really feeling triggered everywhere you go, go to online groups as discussed earlier.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Managing Your World When Your Friends Use",
      shareDescription: "Managing Your World When Your Friends Use page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Don't throw away your “weed friends”!`,
          link: "https://www.reddit.com/r/leaves/comments/xbh3pw/dont_throw_your_weed_friends_away/",
        },
        {
          type: "external",
          description: `Reddit: Dealing with being the only one in a friend group who doesn't smoke`,
          link: "https://www.reddit.com/r/socialskills/comments/2r7p4l/how_do_you_guys_deal_with_being_the_only_one_in_a/",
        },
        {
          type: "external",
          description: `Reddit: Quitting while all your friends smoke`,
          link: "https://www.reddit.com/r/leaves/comments/fg08fp/i_want_to_quit_weed_but_all_of_my_friends_smoke/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Dias, P. C., Lopes, S., & García del Castillo, J. A. (2022). Tell me who your friends are?! The mediating role of friends’ use in cannabis abuse. Trends in Psychiatry and Psychotherapy, 44.`,
          link: "",
        },
      ],
    },
  ],
};

export default Friends;
