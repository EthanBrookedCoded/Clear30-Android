import { ProgramPage } from "@/js/types";

const Cravings: ProgramPage = {
  title: "Overcoming Cravings",
  quote: "Cravings are fleeting, regret is enduring",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `Essentially, cravings are your brain's way of asking for the THC it's used to when you are triggered by certain things, from specific times of day when you use, to people, to boredom, to needing to cope with something. Check out today's meditation!`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/cravings.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/cravings.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `There are things you can do to successfully manage cravings so you can live intentionally.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "Understanding Cravings",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `🚬 Cravings are a normal part of the process when you take a break from cannabis. Essentially, cravings are your brain's way of asking for the THC it's used to when you are triggered by certain things, from specific times of day when you use, to people, to boredom, to needing to cope with something.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "Strategies for Cravings",
      paragraphs: [
        {
          type: "text",
          header: "Coping with Cravings 🏋️",
          content: `Effectively managing cravings often involves a variety of strategies. Here are some proven methods:`,
        },
        {
          type: "list",
          list: [
            `✅ <b>Acceptance</b>: Simply accepting you will have cravings and allowing them to happen gives you power over them. They will happen. Over and over again. They will lessen over time. Most importantly, a craving does not mean you have to use.`,
            `🌊 <b>Urge Surfing / Meditations</b>: A great way to accept a craving and let it pass is by 'urge surfing.' Close your eyes and watch the urge float down a river or get carried on a wave and disappear. Check out today's meditation!`,
            `🏋🏿 <b>Exercise</b>: Good for your physical health and also serves as a mental distraction.`,
            `🔄 <b>Altering Your Environment</b>: Changing your surroundings can help you avoid triggers.`,
            `👀 <b>Distractions</b>: Engage in activities that shift your focus away from cravings.`,
            `📚 <b>Being Prepared</b>: Knowing what triggers you and avoiding it if you can (like certain people) is probably the best way to reduce cravings over time.`,
          ],
        },
        {
          type: "text",
          content: `Each time you overcome a craving, you gain power. Stay tuned for more on reducing triggers that can cause cravings! You've got this! 💪🌿`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Overcoming Cravings",
      shareDescription: "Overcoming Cravings page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Fighting the urge to smoke`,
          link: "https://www.reddit.com/r/eldertrees/comments/i6jfqp/a_potpositive_guide_on_how_to_fight_the_urge_to",
        },
        {
          type: "external",
          description: `Reddit: Weed cravings timeline`,
          link: "https://www.reddit.com/r/recovery/comments/z4arvf/when_do_weed_cravings_stop_or_at_least_let_up/",
        },
        {
          type: "external",
          description: `Youtube: guided meditation to curb cravings`,
          link: "https://www.youtube.com/watch?v=1B5YeONdRTk&t=151s",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Bowen, S., Chawla, N., & Marlatt, G. A. (2010). Mindfulness-based relapse prevention for 
                        substance use disorders: A clinician's guide. New York, NY: Guilford Press`,
        },
        {
          type: "scientific",
          description: `Kober, H., & Mell, M. M. (2015). Neural mechanisms underlying craving and the regulation 
                        of craving. <i>The Wiley handbook on the cognitive neuroscience of addiction</i>, 195-218.`,
        },
      ],
    },
  ],
};

export default Cravings;
