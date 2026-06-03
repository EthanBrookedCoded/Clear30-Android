import { ProgramPage } from "@/js/types";

const FadingAffect: ProgramPage = {
  title: "Navigating the Fading Affect Bias",
  quote:
    "Our memories are the worst liars, they change with our current motivations",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `“I get to caught in my own head when I smoke. I am not smoking anymore!” If this sounds familiar, and you end up smoking a few days later, you may have fallen victim to fading affect bias.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/fadingaffect.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/fadingaffect.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Write or better yet, record yourself, when you have negative consequences from anything, including weed, and listen to it later. Allow you to be your guide.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: `What is FAB?`,
      paragraphs: [
        {
          type: "text",
          header: ``,
          content: `💭 The Fading Affect Bias (FAB) is a process where the emotions associated with unpleasant memories fade faster than those linked to positive events. This can cause people to trick themselves into forgetting why they want to make changes or avoid the negative consequences in the future.`,
        },
        {
          type: "text",
          header: ``,
          content: `🤔 FAB is one of those things that is important to our evolutionary survival. If we remembered all the negative things about childbirth, for example, people might not have kids. But like many of our evolutionary biases, such as the drive to sugar and fat, they can cause trouble.`,
        },
        {
          type: "text",
          header: ``,
          content: `🤔 What's interesting is that it's not that you think cannabis will make you feel better than it actually does. You just forget the bad parts of your experience.`,
        },
        {
          type: "text",
          header: `So how do you avoid the FAB trap?`,
          content: `🧠 Understand FAB: Knowledge is power. Understanding that FAB is real can help you recognize when it's affecting your perception and decision-making.`,
        },
        {
          type: "text",
          header: ``,
          content: `🔍 Write or better yet, record yourself when you have negative consequences from anything, including weed, and listen to it later. That is the real you.`,
        },
        {
          type: "text",
          header: ``,
          content: `Here is a presentation from our founder, Fred Muench, talking about this process and the importance of recording yourself to stay honest with the real you and maintain motivation. <a href="https://www.youtube.com/watch?v=QVUrMXJ3wZ8" target="_blank" title="maintain motivation video">Click here to watch the video</a>.`,
        },
        {
          type: "text",
          header: ``,
          content: `🔹 <i>FAB bias doesn't affect everyone</i>. It is usually on overdrive in people who have trouble controlling behaviors that have a short-term reward, but we want to change. If you feel like this is you, definitely be aware of this mind trick that can keep you stuck in your patterns. 🔹`,
        },
        {
          type: "text",
          header: ``,
          content: `Remember, the fading affect bias might not affect everyone, but many of us experience its effects. Understanding it can lead us to a path of self-awareness and intentional living. 🌟`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Navigating the Fading Affect Bias",
      shareDescription: "Navigating the Fading Affect Bias page",
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Consciousness and Cognition. “The fading affect bias across alcohol consumption frequency for alcohol-related and non-alcohol-related events`,
          link: "https://www.sciencedirect.com/science/article/abs/pii/S1053810013001311",
        },
        {
          type: "scientific",
          description: `Cognitive Psychology. “The Fading Affect Bias: But what the hell is it for?`,
          link: "https://www.niu.edu/jskowronski/publications/walkerskowronski2009.pdf",
        },
        {
          type: "scientific",
          description: `Applied Cognitive Psychology. “The fading affect bias begins within 12 hours and persists for 3 months`,
          link: "https://onlinelibrary.wiley.com/doi/abs/10.1002/acp.1738",
        },
        {
          type: "scientific",
          description: `Muench F, Morgenstern J. Reducing past harm appraisals during treatment predicts worse substance use outcome. Alcohol Clin Exp Res. 2007 Oct;31(10 Suppl):67s-70s.`,
          link: "https://www.researchgate.net/publication/232060085_Maintaining_motivation_using_audio_review",
        },
      ],
    },
  ],
};

export default FadingAffect;
