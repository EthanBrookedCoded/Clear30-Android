import { ProgramPage } from "@/js/types";

const Tapering: ProgramPage = {
  title: "Tapering",
  quote:
    "It does not matter how slowly you go as long as you do not stop - Confucius",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Tapering can be an effective way to reduce withdrawal symptoms, especially when you use weed almost every day. During a taper, you should act like you are fully doing your Clear30. <b>Own it!</b> We will send you messages just like those not doing a taper.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/tapering.png",
    },
    {
      type: "TextSection",
      header: ``,
      paragraphs: [
        {
          type: "text",
          header: ``,
          content: `<b>IMPORTANT NOTE 1</b>: Clear30 does not endorse the use of cannabis in states or countries where it is illegal or for people under 21 in all states and countries. Please comply with all state and country regulations and laws.`,
        },
        {
          type: "text",
          header: ``,
          content: `<b>IMPORTANT NOTE 2</b>: If you decide to taper, consult with a professional. Clear30 is not medical advice but information and education. Below is an EXAMPLE of a tapering protocol. Below are several other resources for tapering available on the web.`,
        },
        {
          type: "text",
          header: `Tapering Protocols for Cannabis Use`,
          content: `There isn't a one-size-fits-all approach for tapering cannabis use, as there's no gold standard. Here is an example of a tapering plan:`,
        },
        {
          type: "list",
          styling: "list-no-dot list-large-text",
          list: [
            "🗓️ Day 1: 80% of usual use",
            "🗓️ Day 2: 80% of use",
            "🗓️ Day 3: 50% of use",
            "🗓️ Day 4: 50% of use",
            "🗓️ Day 5: 30% of use",
            "🗓️ Day 6: 20% of use",
            "🗓️ Day 7: 20% of use",
            "🗓️ Day 8: 10% of use",
            "🗓️ Day 9: Decide whether you can stop safely / seek additional help",
            "Note: We will send you many of the same messages we would send someone doing Clear30 with no taper but you will also receive additional messages about your taper.",
          ],
        },
        {
          type: "text",
          header: `👂 Your Body`,
          content: `Listening to your body is crucial during this process, as the taper may feel too intense or not intense enough. Remember, withdrawal symptoms will still occur, but they'll be less intense, except for vivid dreaming, which might persist regardless.`,
        },
        {
          type: "text",
          header: ``,
          content: `👩‍⚕️ Consult a medical professional if you're having difficulty with the taper. 🩺`,
        },
        {
          type: "text",
          header: `🌿 Cannabidiol (CBD) Tapering`,
          content: `Some people opt to use CBD to help with tapering. Research suggests that it can effectively reduce withdrawal symptoms in some people. If you decide to use CBD, try ingesting it differently from how you use THC (e.g., in tea ☕). Tomorrow and the next day, we'll send you more information on managing withdrawal.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Tapering",
      shareDescription: "Tapering page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Taper Thread 1`,
          link: "https://www.reddit.com/r/Petioles/comments/ckzdze/taper_your_usage_instead_of_cold_turkey/",
        },
        {
          type: "external",
          description: `Reddit: Taper Thread 2`,
          link: "https://www.reddit.com/r/Petioles/comments/147y37v/am_i_doing_this_right_tapering_and_i_feel_awfil/",
        },
        {
          type: "external",
          description: `Reddit: Taper versus Cold Turkey 1`,
          link: "https://www.reddit.com/r/leaves/comments/vasge5/anyone_any_success_tapering_off_weed_rather_than/",
        },
        {
          type: "external",
          description: `Reddit: Taper versus Cold Turkey 2`,
          link: "https://www.reddit.com/r/leaves/comments/pyo1cf/experience_with_weaning_off_vs_cold_turkey/",
        },
        {
          type: "external",
          description: `Reddit: Suggestions on Taper`,
          link: "https://www.reddit.com/r/Petioles/comments/s93cje/whats_the_best_way_to_slowly_taper_off/",
        },
        {
          type: "external",
          description: `Quora: Ways to Taper`,
          link: "https://www.quora.com/What-is-a-good-way-to-taper-down-heavy-cannabis-use-while-minimizing-withdrawal-symptoms",
        },
        {
          type: "external",
          description: `Weedless: Taper Guide`,
          link: "https://www.weedless.org/insights/how-i-tapered-to-quit-a-heavy-concentrate-habit/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Copersino, M. L., Boyd, S. J., Tashkin, D. P., Huestis, M. A., Heishman, S. J., & Dermand, J. C. (2006). Cannabis withdrawal among non-treatment-seeking adult cannabis users. American Journal of Addiction, 15(1), 8-14.`,
          link: "",
        },
        {
          type: "scientific",
          description: `Freeman, T. P., Hindocha, C., Green, S. F., & Bloomfield, M. A. P. (2020). Cannabidiol for the treatment of cannabis use disorder: a phase 2a, double-blind, placebo-controlled, randomised, adaptive Bayesian trial. The Lancet Psychiatry, 7(10), 865-874. [2]`,
          link: "",
        },
      ],
    },
  ],
};

export default Tapering;
