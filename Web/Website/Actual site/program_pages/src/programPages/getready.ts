import { ProgramPage } from "@/js/types";

const GetReady: ProgramPage = {
  title: "Preparing for Clear30",
  quote: "",
  sections: [
    {
      type: "TextSection",
      header: "",
      paragraphs: [
        {
          type: "text",
          content: `As you think about when you want to start your Clear30, it is helpful to set the stage for a successful break. Below are some suggestions that can help you optimize your experience.`,
          styling: "remove-padding",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/preparation.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/preparation.png",
    },
    {
      type: "TextSection",
      header: `Tips for a Successful Clear30`,
      paragraphs: [
        {
          type: "text",
          header: `📅 Pick a date`,
          content: `Picking a date to start Clear30 helps you mentally prepare yourself for Clear30. It gives you time to mentally prepare while you are still using. Whatever your date, don’t get caught up in delaying, delaying, delaying; there is never a perfect time, and a break takes work no matter when you do it. Here are a few things that can help once you pick a date to start–you can even choose to begin today! On the other hand, don’t worry if you signed up and realize you’d prefer to start in a few days. Getting Clear30 break messages can be really helpful during the preparation phase, too. 
          `,
        },
        {
          type: "text",
          header: `❓ Consider a taper`,
          content: `If you use cannabis more than 5 days a week, tapering off rather than going “cold turkey” can be an effective way to reduce withdrawal symptoms. Tapering can be a part of your 30 days, or you can do it before you start counting.`,
        },
        {
          type: "text",
          header: `🚫 Get rid of your supply`,
          content: `Get rid of your papers, bongs, and anything related to substance use before you do Clear30. Other than giving your body a break, you will be giving your mind a break, too; having substance use paraphernalia may make this period more challenging. Ask a friend to store more expensive items; consider throwing out those that are not. Free your mind from temptation. Free your mind to do other, more healthful things.`,
        },
        {
          type: "text",
          header: `🔍 Identify your “mega trigger”`,
          content: `You will do lots of identifying triggers during Clear30, but if there is one specific trigger — perhaps going to bed, waking up, watching TV, or hanging out with a specific person or people — it is good to have an idea of how you will plan to cope with cravings during those times.✨`,
        },
        {
          type: "text",
          header: `🎯 Alternate activities`,
          content: `Start to think about other things you can do to create a fun and interesting Clear30. What are things you have always wanted to do but haven’t had the time for or just were stuck on? What is something new you can do?`,
        },
        {
          type: "text",
          header: `💪🏽 Intention setting`,
          content: `Setting your goal and intentions for the day is a win. Doing this in the mornings will help you center your day around your goals; doing this in the evenings can help keep your mind on the future and the benefits of this break. Use Clear30 daily meditations to help you get (and stay) intentional.`,
        },
        {
          type: "text",
          header: `🤗 Get support`,
          content: `There are three kinds of support that you can mix and match:`,
        },
        {
          type: "list",
          list: [
            `<b>👥 Group Clear30</b>. The first is doing Clear30 with friends. You would be surprised that some of your friends who smoke will do Clear30 with you if you ask. It will not only be helpful for you but also for them. It creates a very cool bonding experience if you are lucky enough to have these kinds of friendships.`,
            `<b>🙋 Solo Clear30 with non-user support</b>. Research has revealed the importance of supportive people—whether they use substances or not—to bounce things off of and to do things with. While not everyone has these relationships, if you have people who are willing to help you, let them know you are trying to make a change and would love their support in chatting or doing things that don’t involve weed over the next 30 days.`,
            `<b>💻 Solo Clear30 with online support</b>. Covid-19 made it clear that online support is really powerful and effective. Regardless of whether you have in-person support, use online support because it is there 24-7 and can be super targeted.`,
          ],
        },
        {
          type: "text",
          header: `🚀 Commit!`,
          content: `Regardless of what tools you use or recommendations you follow, <i>truly commit</i> to Clear30 when you start. For the next 30 days, your identity is someone who doesn't use weed. It kind of doesn't matter how you do it - just own and live Clear30. It is really fun to create a new identity for a month. Act as if and the rest becomes so much easier.`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Preparing for Clear30",
      shareDescription: "Preparing for Clear30 page",
    },
  ],
};

export default GetReady;
