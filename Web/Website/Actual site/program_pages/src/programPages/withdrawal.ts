import { ProgramPage, ReferenceSection, TextSection } from "@/js/types";

const Withdrawal: ProgramPage = {
  title: "Dealing With Withdrawal",
  quote: "Behind every beautiful thing, there's some kind of pain - Bob Dylan",
  sections: [
    {
      type: "TextSection",
      paragraphs: [
        {
          type: "text",
          content: `When breaking from cannabis, it is common to have withdrawal symptoms, especially for the first 1-2 weeks, because your brain and body have gotten used to THC. This is especially the case if you use weed regularly with minimal time between breaks, and the higher potencies of today's weed make such withdrawals even worse.`,
          styling: "remove-padding",
        },
        {
          type: "text",
          content: `✏️ But remember, it's okay to feel these things. Accept that they will happen but make sure to seek professional medical help if they are bad. There is help out there. Listen here for more.`,
          styling: "",
        },
      ],
    },
    {
      type: "AudioSection",
      sourcePath: "https://m.clear30.org/withdrawal.mp3",
    },
    {
      type: "ImageSection",
      sourcePath: "https://m.clear30.org/withdrawal.png",
    },
    {
      type: "TextSection",
      header: "🔑 Key Takeaway Action",
      paragraphs: [
        {
          type: "text",
          content: `Actively do things to reduce withdrawal symptoms, ideally before they start.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "🌿What Happens When You Break?",
      paragraphs: [
        {
          type: "text",
          content: `⏸️ When you decide to hit pause on cannabis, your body's first reaction might be, "Hey, where's the THC?" It's like going to your favorite coffee shop only to find they're out of coffee! Your endocannabinoid system, which controls memory, mood, movement, appetite, and sleep, is left wondering where its familiar ingredient has gone. This sudden change can throw your system off.`,
        },
        {
          type: "text",
          content: `💭 Everyone's withdrawals are unique. You might notice changes that mirror the effects of your high. Loved those munchies? You might now experience decreased appetite. Did cannabis make you feel chill? Irritability could sneak in. Plus, there might be new experiences, like vivid dreams that can feel like you've jumped into a sci-fi movie - that's because cannabis suppresses REM sleep, and when you stop, the dreams come back in HD.`,
        },
        {
          type: "text",
          content: `Without cannabis, you may come face to face with:`,
        },
        {
          type: "list",
          styling: "list-large-text",
          list: [
            "Stomach pain 🤢",
            "Irritability and anxiety 😡",
            "Decreased appetite or nausea 🍽️",
            "Fevers, sweating, or chills 😰",
            "Headaches 🤯",
            "Restlessness 😣",
            "Sleep problems, including those sci-fi dreams 🛌",
            "Cravings 🍃",
            "A sense of melancholy 😔",
          ],
        },
        {
          type: "route",
          content: `If this feels like too much to handle, consider a gradual farewell to THC, a sort of "see you later" approach.`,
          route: "/program/tapering",
          routeText: "Check out our brief taper page.",
          styling: "remove-padding",
        },
        {
          type: "text",
          content: `✏️ Withdrawal might have a psychological side too. If cannabis was your sleep companion, you might worry about restless nights. If it was your shield from boredom or anxiety, you might feel exposed. But remember, it's okay to feel these things. Accept they will happen. They are temporary.`,
        },
        {
          type: "text",
          content: `And hey, it's not all bad! Many people begin to experience benefits like improved motivation, increased energy, and sharper focus within a week of breaking. 🌞💪🧠`,
        },
        {
          type: "text",
          content: `<b>How long will this last?</b>`,
        },
        {
          type: "text",
          content: `🎢 The first week is like the steep climb on a rollercoaster. Some symptoms, like brain fog, ultra vivid dreams, and stomach issues, might stick around for a few weeks. Remember, THC likes to hide in your fat cells and can take 3-4 weeks to leave the building.`,
        },
        {
          type: "text",
          content: `We're here with tips and tricks to make this break a little less bumpy.`,
        },
      ],
    },
    {
      type: "TextSection",
      header: "Making Withdrawal a Smoother Ride: Your Go-to Guide 🎢💡",
      paragraphs: [
        {
          type: "text",
          content: `Here are some handy tips to turn those choppy waves into a more manageable tide during Clear30. BIG NOTE: Keep it simple. This is an overview for you to pick and choose a couple. We will go over each of these in detail during your Clear30.`,
        },
        {
          type: "list",
          styling: "list-no-dot list-large-text remove-padding",
          list: [
            "1️⃣ <u>Acceptance</u>: Understanding and accepting that discomfort is part of the journey can help you navigate this new path with intention. It's not about running from discomfort but learning to dance in the rain. However, if the storm gets too wild, don't hesitate to seek medical help.",
            "2️⃣ <u>Self-Care</u>: This isn't the time to run a marathon or plan a house move. Keep your first week gentle and stress-free. Give yourself the green light to dodge demanding tasks, especially if they trigger cravings.",
            "3️⃣ <u>Exercise</u>: Your new best friend to combat withdrawal symptoms like sleep issues and irritability. A good workout can reset your nervous system, trigger those happy endorphins, and help revitalize your endocannabinoid system.",
            "4️⃣ <u>Hydrate</u>: Think of water as your detox ally, helping to flush out THC and support your body's natural cleansing processes. Plus, staying hydrated can also ease stomach issues and brain fog. 💧🍵",
            "5️⃣ <u>Distraction</u>: When withdrawal takes center stage, divert your attention with fun activities. The more engaged you are, the less time you'll have to dwell on your symptoms. 🎨🚴‍♂️",
            "6️⃣ <u>Mindfulness</u>: It's like a compass guiding you towards intention and away from your symptoms. Consider trying a few of our recommended meditations. 🧭🧘‍♀️",
            "7️⃣ <u>Supplements</u>: Some, like NAC, magnesium, CBD, and B-complex, have shown promise in easing cravings and withdrawal symptoms. Always consult with a healthcare provider before starting any supplement regimen. 💊👩‍⚕️",
            "8️⃣ <u>Support</u>: Lean on friends, join online support groups, or seek professional help. Social support can actually dial down the intensity of withdrawal. 👥💬",
          ],
        },
        {
          type: "text",
          content: `<i>"People kept telling me that there was no such thing as weed withdrawal so when I took a break I thought nausea and vivid dreams were because I was pregnant. I ended up wasting more money on pregnancy tests than weed."</i>`,
        },
      ],
    },
    {
      type: "ShareSection",
      shareTitle: "Dealing With Withdrawal",
      shareDescription: "Dealing With Withdrawal page",
    },
    {
      type: "ReferenceSection",
      header: "External References",
      references: [
        {
          type: "external",
          description: `Reddit: Managing Cannabis Withdrawals`,
          link: "https://www.reddit.com/r/AskReddit/comments/ovrqxx/how_long_do_cannabis_withdrawals_last_anxiety/",
        },
        {
          type: "external",
          description: `Reddit: Managing Cannabis Withdrawals 2`,
          link: "https://www.reddit.com/r/leaves/comments/1hmj0u/my_personal_guide_for_withdrawal_symptoms/",
        },
        {
          type: "external",
          description: `Weedless: Withdrawal Timeline`,
          link: "https://www.weedless.org/withdrawal/timeline/",
        },
        {
          type: "external",
          description: `Healthline: What to expect from withdrawal`,
          link: "https://www.healthline.com/health-news/marijuana-withdrawal-symptoms-are-real-for-regular-users#What-the-study-revealed",
        },
        {
          type: "external",
          description: `Harvard Medical School: Managing withdrawal`,
          link: "https://www.health.harvard.edu/blog/if-cannabis-becomes-a-problem-how-to-manage-withdrawal-2020052619922",
        },
      ],
    },
    {
      type: "ReferenceSection",
      header: "References",
      references: [
        {
          type: "scientific",
          description: `Bonnet, U., & Preuss, U. W. (2017). The cannabis withdrawal syndrome: current insights. Subst Abuse Rehabil, 8, 9-37.`,
          link: "https://doi.org/10.2147/sar.S109576",
        },
        {
          type: "scientific",
          description: `Budney, A. J., & Hughes, J. R. (2006). The cannabis withdrawal syndrome. Curr Opin Psychiatry, 19(3), 233-238.`,
          link: "https://doi.org/10.1097/01.yco.0000218592.00689.e5",
        },
        {
          type: "scientific",
          description: `Gates, P., Albertella, L., & Copeland, J. (2016). Cannabis withdrawal and sleep: A systematic review of human studies. Subst Abus, 37(1), 255-269.`,
          link: "https://doi.org/10.1080/08897077.2015.1023484",
        },
        {
          type: "scientific",
          description: `Kesner, A. J., & Lovinger, D. M. (2021). Cannabis use, abuse, and withdrawal: Cannabinergic mechanisms, clinical, and preclinical findings. J Neurochem, 157(5), 1674-1696.`,
          link: "https://doi.org/10.1111/jnc.15369",
        },
        {
          type: "scientific",
          description: `Livne, O., Shmulewitz, D., Lev-Ran, S., & Hasin, D. S. (2019). DSM-5 cannabis withdrawal syndrome: Demographic and clinical correlates in U.S. adults. Drug Alcohol Depend, 195, 170-177.`,
          link: "https://doi.org/10.1016/j.drugalcdep.2018.09.005",
        },
        {
          type: "scientific",
          description: `Schlienz, N. J., Budney, A. J., Lee, D. C., & Vandrey, R. (2017). Cannabis Withdrawal: A Review of Neurobiological Mechanisms and Sex Differences. Curr Addict Rep, 4(2), 75-81.`,
          link: "https://doi.org/10.1007/s40429-017-0143-1",
        },
      ],
    },
  ],
};

export default Withdrawal;
