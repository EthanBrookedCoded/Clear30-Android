import { ProgramPage } from "@/js/types";

const Acceptance: ProgramPage = {
  title: "What is Acceptance?",
  quote: "When you accept yourself, the whole world accepts you - TL",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `Acceptance is not surrender or complacency. It's a powerful state of mind that involves:`,
          styling: "remove-padding",
        },
        {
          type: "list",
          list: [
            `Acknowledging we are in the present moment and we can’t change our past`,
            `Freeing ourselves from our limitations, past regrets or unrealistic future expectations.`,
            `Embracing our journey towards self-improvement by accepting ourselves unconditionally.`,
          ],
        },
        {
          type: "text",
          content: `Acceptance is a form of self-love that acknowledges our flaws and beauty equally. Once we accept, we open the door to moving forward. 😌 Listen here for more.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/acceptance.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/acceptance.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Accept everything you are at this moment without judgment. Then decide how you will grow without any shame or blame - just progress.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "Acceptance of you",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `✅ Accepting who you are in the moment, rather than fighting it with frustration, sets us  free. We might use weed to make us “better” - as a way to fix the things we do not like about ourselves. Acceptance is about being okay with our perceived limitations.`,
        },
        {
          type: "text",
          header: "",
          content: `🙂 "Today, can you accept exactly who you are? All your limitations or things you do not like about yourself... Before we can work on our limitations, we have to accept where we are. Allow yourself to let go of any regrets and frustration that is occupying your mind and keeps you stuck." 😊`,
        },
        {
          type: "text",
          header: "",
          content: `👉This simple exercise of accepting your limitations and not trying to either suppress them or distract yourself from them has been shown to be a stepping stone to better mental health. Really, the research is powerful. So as you do your Clear30, accept you for everything you are. The good and the less good. Only then, can you work on what you want to change.`,
        },
        {
          type: "text",
          header: "",
          content: `You are fucking beautiful, all of you.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "What is Acceptance?",
      shareDescription: "What is Acceptance? page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Psychology Today: Being your best self`,
          link: "https://www.psychologytoday.com/us/blog/being-your-best-self/202203/the-healing-power-radical-acceptance",
        },
        {
          type: "external",
          description: `Forbes: What is radical acceptance?`,
          link: "https://www.forbes.com/health/mind/what-is-radical-acceptance/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Osaji J, Ojimba C, Ahmed S. The Use of Acceptance and Commitment Therapy in Substance Use Disorders: A Review of Literature. J Clin Med Res. 2020 Oct;12(10):629-633.`,
          link: "https://doi.org/10.14740/jocmr4311",
        },
        {
          type: "scientific",
          description: `Davoudi, M., Taheri, A., & Foroughi, A. (2021). Effectiveness of Acceptance and Commitment Therapy on Depression, Anxiety and Cessation in Marijuana Use Disorder: A Randomized Clinical Trial. International Journal of Behavioral Sciences, 15(3), 194-200.`,
          link: "",
        },
        {
          type: "scientific",
          description: `Hayes, S. C., Strosahl, K., & Wilson, K. G. (1999). Acceptance and Commitment Therapy: An experiential approach to behavior change. Guilford Press.`,
          link: "",
        },
      ],
    },
  ],
};

export default Acceptance;
