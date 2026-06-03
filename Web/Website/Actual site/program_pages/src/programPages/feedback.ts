import { ProgramPage } from "@/js/types";

const Feedback: ProgramPage = {
  title: "Feedback from Others",
  quote: "Feedback is the breakfast of champions - KB",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `Feedback is how we grow. The problem is it is really uncomfortable to ask people for feedback - both for them and for you. Today, let's explore how asking for feedback about our cannabis use can shine a light on our blind spots and deepen our understanding of ourselves. Listen here for more.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/feedback.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/feedback.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Be courageous. Ask at least one person (hopefully more) today about whether they think cannabis is helping, hurting, or making no difference in your life.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: `💡 The Importance of Feedback`,
      paragraphs: [
        {
          type: "text",
          header: ``,
          content: `Feedback from others can serve as a mirror, reflecting aspects of our behavior that we might not be aware of. When we seek feedback about our cannabis use, we open ourselves up to a broader perspective so we can reveal something new about us.`,
        },
        {
          type: "text",
          header: `💬 Ask and You Shall Receive`,
          content: `The key to receiving meaningful feedback is asking the right people the right questions. Here's a three-step guide to gather different perspectives:`,
        },
        {
          type: "list",
          list: [
            `<b>The Nodding Heads</b>: These are people you believe will think your cannabis use is not an issue.`,
            `<b>The Concerned Voices</b>: These are individuals who, you suspect, might believe you're using too much.`,
            `<b>The Wildcards</b>: These are the people who know you, but you have no idea what they think about your cannabis use. Their feedback can often offer the most surprising insights, precisely because they're less predictable.`,
          ],
        },
        {
          type: "text",
          header: `🔍 How to Ask?`,
          content: `How has two parts. First is the communication method. If you feel really uncomfortable, you can text someone or make a phone call. This adds some distance it make it more comfortable. In-person is great too so you can really see peoples reactions and spend time with the interaction. Either way, both can work - the key is to do it.`,
        },
        {
          type: "text",
          header: ``,
          content: `🚀 How to pose your question is just as crucial as who you ask. Regardless of the communication method, first, make sure to tell people you are looking for honest feedback about your use, and you are aware it may be difficult for them, but you are asking so you can grow. Then, a simple, straightforward question often does the trick. For example, 'Do you think weed makes me better, worse, or both, and why?' This question invites honest, open-ended responses without leading the person to a particular answer. 🌟`,
        },
        {
          type: "text",
          header: `⚖️ Weighing the Feedback`,
          content: `Once you have collected feedback, the next step is to process it:`,
        },
        {
          type: "list",
          list: [
            `<b>🔍 Identify Common Themes</b>: Do certain observations recur? 🔄 These could indicate key areas to focus on.`,
            `<b>Consider Different Perspectives</b>: 🧐 Remember, each person brings their own bias to their feedback. Take all opinions into account but trust your judgment on what truly applies to you.`,
            `<b>🚩 Use Feedback as a Guide, Not Gospel</b>: Ultimately, you decide what feedback to accept and act on. It's a tool to help you gain insight, not a definitive jjudgment of your behavior. 🛠️`,
            `<b>🌱 Grow and Learn</b>: Use the feedback to build a better you and grow both on your own and with the people who help you. You will be surprised at how many people open up about themselves during this process.`,
          ],
        },
        {
          type: "text",
          header: `🏆 Conclusion`,
          content: `Asking others for their perspective on your cannabis use may be a daunting task, but it is a brave step towards greater self-awareness and growth. Remember, feedback is not about criticism or praise - it's about gaining a clearer picture of yourself as you journey through Clear30.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Feedback from Others",
      shareDescription: "Feedback from Others page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: How to stop taking criticism in the workplace`,
          link: "https://www.reddit.com/r/LifeProTips/comments/12c93b3/lpt_request_how_to_stop_taking_criticismfeedback/",
        },
        {
          type: "external",
          description: `Reddit: Giving negative feedback`,
          link: "https://www.reddit.com/r/LifeProTips/comments/9e4oc9/lpt_when_giving_negative_feedback_take_a_small/",
        },
        {
          type: "external",
          description: `Reddit: Accepting negative feedback`,
          link: "https://www.reddit.com/r/TheGirlSurvivalGuide/comments/g3zw3q/how_to_get_better_at_accepting_negative_feedback/",
        },
      ],
    },
  ],
};

export default Feedback;
