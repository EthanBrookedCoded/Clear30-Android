import { ProgramPage } from "@/js/types";

const SlowChange: ProgramPage = {
  title: "Process of Change is Slow",
  quote: "The journey is the reward - SJ",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          header: "",
          content: `When we pause a habit such as cannabis, we often expect significant, immediate shifts in our lives. While these big changes can occur, it's frequently the smaller, subtler transformations that make a profound impact over time.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/slowchange.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/slowchange.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Write down (or respond to the message), any little changes for the better you have noticed while doing Clear30. `,
        },
      ],
    },
    {
      type: "TextSection",
      header: `Appreciate the Journey`,
      paragraphs: [
        {
          type: "text",
          header: ``,
          content: `🙂 Small changes may include becoming more proactive, clear minded, gaining a deeper understanding of your feelings and motivations, or learning to adapt to discomfort without a substance. It is also possible that there can be negative small changes that you are beginning to notice.`,
        },
        {
          type: "text",
          header: ``,
          content: `📈 These small changes, both positive and negative, lay the foundation for growth and change. Remember that progress doesn't always look like dramatic leaps forward; sometimes, it's the gradual, subtle shifts that make the most significant impact in the long run.`,
        },
        {
          type: "text",
          header: ``,
          content: `👉 Appreciate Your Journey: Remember that the Clear 30 journey isn't just about the end goal; it's about appreciating the process of change itself and the little things along the way! ✅`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Process of Change",
      shareDescription: "Process of Change page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Slow progress is better than no progress`,
          link: "https://www.reddit.com/r/selfimprovement/comments/mtwksm/slow_progress_is_better_than_no_progress/",
        },
        {
          type: "external",
          description: `Reddit: Accepting slow progress`,
          link: "https://www.reddit.com/r/getdisciplined/comments/c46waw/advice_3_ways_to_be_more_accepting_of_slow/",
        },
        {
          type: "external",
          description: `Reddit: Self Improvement when you feel like you're getting worse`,
          link: "https://www.reddit.com/r/selfimprovement/comments/rgztkm/why_is_it_that_since_ive_started_trying_to/",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Understanding Hard to Maintain Behaviour Change: A Dual Process Approach`,
          link: "https://books.google.com/books?hl=en&lr=&id=gjOpAgAAQBAJ&oi=fnd&pg=PR9&dq=behavior+change+is+slow+persistence+exercise&ots=nhBeHIjZH7&sig=XJ8xndrAOLTBxAqXB0SQGsM0g8A#v=onepage&q&f=false",
        },
        {
          type: "scientific",
          description: `Slow wins: patience, perseverance and behavior change`,
          link: "https://www.tandfonline.com/doi/full/10.4155/cmt.11.59",
        },
      ],
    },
  ],
};

export default SlowChange;
