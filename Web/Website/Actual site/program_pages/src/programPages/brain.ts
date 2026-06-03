import { ProgramPage } from "@/js/types";

const Brain: ProgramPage = {
  title: "🧠 Understanding Cannabis and Its Effects on the Brain",
  quote:
    "Our brains are forgiving, up until a point. Then they throw us out without us even knowing",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `How weed affects the brain depends on various factors, especially your age, but there are some things that are the same across people.  The active component of cannabis, delta-9-tetrahydrocannabinol (THC), binds to cannabinoid receptors in the brain, interacting with the endocannabinoid system responsible for regulating appetite, mood, and pain sensation. That creates short and longterm changes that can both make you feel good and bad. Listen here for more.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/brain.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/brain.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Heavy weed use changes your brain chemistry. Be an informed user.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "🌿 Short-term Effects of Cannabis on the Brain",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Cannabis use results in a range of short-term effects that vary depending on the dosage, method of consumption, and individual factors. Some of these effects include:`,
        },
        {
          type: "list",
          styling: "list-no-dot list-large-text remove-padding",
          list: [
            "<b>Euphoria 🎉</b>: THC stimulates the release of dopamine, leading to feelings of happiness, relaxation, and euphoria.",
            "<b>Divergent Thinking 🌀</b>: Weed can help you think differently about things and ideas.",
            "<b>Impaired Memory and Concentration 🌫️</b>: The interaction of THC with the hippocampus (the brain region responsible for memory) can lead to impaired short-term memory and difficulty concentrating.",
            "<b>Distorted Perception 🌀</b>: Altered perceptions of time, space, and sensory input may occur leading to perceiving things differently, especially social interactions.",
          ],
        },
      ],
    },
    {
      type: "TextSection",
      header: "🍁 Long-term Effects of Cannabis on the Brain",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Long-term and regular use of cannabis (like many substances) tends not to be your brains best friend.`,
        },
        {
          type: "list",
          styling: "list-no-dot list-large-text remove-padding",
          list: [
            "<b>Decreased Cognitive Function 🧩</b>: Long-term cannabis use can lead to diminished cognitive functions, affecting memory, attention, and decision-making capabilities.",
            "<b>Addiction 🔄</b>: Many regular users may develop a dependency on weed by changing the reward pathway in the brain to crave it. This is what Clear30 is really trying to help you avoid.",
            "<b>Mental Health Concerns 😰</b>: Regular cannabis use is linked to increased risks of anxiety, depression, and psychosis.",
            "<b>Brain Structure Changes 🧠</b>: Studies suggest that regular cannabis use can decrease grey matter volume in the hippocampus and prefrontal cortex, especially if you start young.",
          ],
        },
      ],
    },
    {
      type: "TextSection",
      header:
        "💪 Does the Brain Recover from Regular Cannabis Use over Time if You Stop?",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `The brain has the remarkable ability to recover from the effects of regular cannabis use if you stop or greatly reduce both the concentration of THC and the number of days you use.`,
        },
        {
          type: "text",
          header: "",
          content: `Research suggests that cognitive function can significantly improve several weeks after stopping cannabis use. Structural changes in the brain caused by long-term use can be reversed after stopping use, but it might take several months for the brain to return to its normal state.`,
        },
        {
          type: "text",
          header: "",
          content: `Bottom line is that you are doing your brain a favor by doing Clear30. Thank you!`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Understanding Cannabis and Its Effects on the Brain",
      shareDescription:
        "Understanding Cannabis and Its Effects on the Brain page",
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Dan I. Lubman, Ali Cheetham, Murat Yücel, Cannabis and adolescent brain development,Pharmacology & Therapeutics,Volume 148,2015,Pages 1-16,ISSN 0163-7258.`,
          link: "https://www.sciencedirect.com/science/article/abs/pii/S0163725814002095",
        },
        {
          type: "scientific",
          description: `Ana Fresán, Diana María Dionisio-García, Thelma Beatriz González-Castro, Miguel Ángel Ramos-Méndez, Rosa Giannina Castillo-Avila, Carlos Alfonso Tovilla-Zárate, Isela Esther Juárez-Rojop, María Lilia López-Narváez, Alma Delia Genis-Mendoza, Humberto Nicolini, Cannabis smoking increases the risk of suicide ideation and suicide attempt in young individuals of 11–21 years: A systematic review and meta-analysis,Journal of Psychiatric Research,Volume 153, 2022,Pages 90-98,ISSN 0022-3956.`,
          link: "https://www.sciencedirect.com/science/article/abs/pii/S0022395622003594",
        },
        {
          type: "scientific",
          description: `Alison C Burggren, Anaheed Shirazi, Nathaniel Ginder & Edythe D. London (2019) Cannabis effects on brain structure, function, and cognition: considerations for medical uses of cannabis and its derivatives, The American Journal of Drug and Alcohol Abuse, 45:6, 563-579.`,
          link: "https://www.tandfonline.com/doi/abs/10.1080/00952990.2019.1634086",
        },
        {
          type: "scientific",
          description: `Bara, A., Ferland, JM.N., Rompala, G. et al. Cannabis and synaptic reprogramming of the developing brain. Nat Rev Neurosci 22, 423–438 (2021).`,
          link: "https://www.nature.com/articles/s41583-021-00465-5",
        },
        {
          type: "scientific",
          description: `Bara, A., Ferland, JM.N., Rompala, G. et al. Cannabis and synaptic reprogramming of the developing brain. Nat Rev Neurosci 22, 423–438 (2021).`,
          link: "https://onlinelibrary.wiley.com/doi/abs/10.1002/bdr2.1572",
        },
        {
          type: "scientific",
          description: `Dhein, S. (2020, July 6). Different effects of cannabis abuse on adolescent and Adult Brain. Karger Publishers.`,
          link: "https://karger.com/pha/article/105/11-12/609/267673",
        },
        {
          type: "scientific",
          description: `Hurd, Y. L., Manzoni, O. J., Pletnikov, M. V., Lee, F. S., Bhattacharyya, S., & Melis, M. (2019, October 16). Cannabis and the developing brain: Insights into its long-lasting effects. Journal of Neuroscience.`,
          link: "https://www.jneurosci.org/content/39/42/8250",
        },
      ],
    },
  ],
};

export default Brain;
