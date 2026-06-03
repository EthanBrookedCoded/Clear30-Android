import { z } from "https://esm.sh/zod@3.24.4";
import { ChatResponse } from "../shared/types/types.ts";
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

// Flag for API mode - will be dynamically set based on assistant_mode setting
export let USE_COMPLETION_API = false;

/**
 * Updates the USE_COMPLETION_API flag based on the assistant_mode setting
 * @param {any} supabase - The Supabase client
 */
export const updateApiMode = async (supabase: any): Promise<void> => {
  try {
    const { data, error } = await supabase
      .schema('claire')
      .from('settings')
      .select('value')
      .eq('key', 'assistant_mode')
      .single();

    if (!error && data) {
      USE_COMPLETION_API = !data.value; // assistant_mode true = USE_COMPLETION_API false
    }
  } catch (error) {
    console.error('Error fetching assistant mode setting:', error);
    // Keep default value if error occurs
  }
};

/**
 * Creates a response object with the specified data and status
 * @param {ChatResponse} data - The response data to be sent
 * @param {number} status - The HTTP status code
 * @returns {Response} A new Response object with the formatted data and headers
 */
export const createResponse = (data: ChatResponse, status: number): Response => {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'POST',
    },
  });
}

export const chatSchema = z.object({
  message: z
    .string({
      required_error: "Message is required",
      invalid_type_error: "Message must be a string",
    })
    .min(1, { message: "Message cannot be empty" })
    .max(512, { message: "Message cannot exceed 512 characters" }),
  threadId: z
    .string({
      required_error: "Thread ID is required",
      invalid_type_error: "Thread ID must be a string",
    })
    .startsWith("thread_", { message: "Thread ID must start with 'thread_' " })
    .min(1, { message: "Thread ID cannot be empty" }),
  conversationMode: z
    .enum(["text", "voice"], {
      required_error: "Conversation mode is required",
      invalid_type_error: "Conversation mode must be either 'text' or 'voice'",
    })
    .optional()
    .default("text"),
}).strict("Invalid request: Only 'message', 'threadId', and 'conversationMode' fields are allowed");

/**
 * Stores a message in the database
 * @param {string} userId - The user ID
 * @param {string} threadId - The thread ID
 * @param {string} role - The message role (user or assistant)
 * @param {string} content - The message content
 * @param {string} apiType - The API type (assistants or completion)
 * @param {string} conversationMode - The conversation mode (text or voice)
 * @param {any} supabase - The Supabase client
 * @param {string} messageType - The message type (chat, context, or system). Defaults to 'chat'
 */
export const storeMessage = async (
  userId: string,
  threadId: string,
  role: 'user' | 'assistant',
  content: string,
  apiType: 'assistants' | 'completion',
  conversationMode: 'text' | 'voice',
  supabase: any,
  messageType: 'chat' | 'context' | 'system' = 'chat'
): Promise<void> => {
  try {
    const { error } = await supabase
      .schema('claire')
      .from('messages')
      .insert({
        user_id: userId,
        thread_id: threadId,
        role,
        content,
        message_type: messageType,
        api_type: apiType,
        conversation_mode: conversationMode
      });

    if (error) {
      console.error('Error storing message:', error);
      // Don't throw here to avoid breaking the main flow
    }
  } catch (error) {
    console.error('Error storing message:', error);
  }
};

/**
 * Retrieves conversation history for completion API
 * @param {string} userId - The user ID
 * @param {string} threadId - The thread ID
 * @param {any} supabase - The Supabase client
 * @param {number} limit - Maximum number of messages to retrieve
 * @returns {Promise<Array>} Array of messages formatted for OpenAI
 */
export const getConversationHistory = async (
  userId: string,
  threadId: string,
  supabase: any,
  limit: number = 20
): Promise<Array<{ role: string, content: string }>> => {
  try {
    const { data, error } = await supabase
      .schema('claire')
      .from('messages')
      .select('role, content')
      .eq('user_id', userId)
      .eq('thread_id', threadId)
      .in('role', ['user', 'assistant'])
      .order('created_at', { ascending: true })
      .limit(limit);

    if (error) {
      console.error('Error retrieving conversation history:', error);
      return [];
    }

    return data || [];
  } catch (error) {
    console.error('Error retrieving conversation history:', error);
    return [];
  }
};

/**
 * Gets the latest system prompt from the database
 * @param {any} supabase - The Supabase client
 * @returns {Promise<string>} The latest prompt or fallback prompt
 */
export const getSystemPrompt = async (supabase: any): Promise<string> => {
  try {
    const { data, error } = await supabase.schema('claire').rpc('get_latest_prompt');

    if (!error && data && data.length > 0) {
      return data[0].prompt;
    }
  } catch (error) {
    console.error('Error fetching system prompt:', error);
  }

  // Fallback to hardcoded prompt
  return fallbackClairePrompt;
};

/**
 * Processes a stream response from the OpenAI API
 * @param {Response} response - The response object from the OpenAI API
 * @returns {Promise<ReadableStream<Uint8Array>>} A promise that resolves to the processed stream
 * @throws {Error} If the response is not ok or if the response body is not received
 */
export const processStreamResponse = async (response: Response): Promise<ReadableStream<Uint8Array>> => {
  if (!response.ok) {
    throw new Error(`OpenAI API error: ${response?.status} ${response?.statusText}`);
  }

  if (!response.body) {
    throw new Error("No response body received from OpenAI");
  }

  let firstChunkReceived = false;
  let chunkCount = 0;

  const transformStream = new TransformStream({
    async transform(chunk: Uint8Array, controller: TransformStreamDefaultController) {
      try {
        if (!firstChunkReceived) {
          firstChunkReceived = true;
        }

        chunkCount++;
        const encoder = new TextEncoder();
        const decoder = new TextDecoder();

        const text = decoder.decode(chunk);
        const lines = text.split('\n').filter(line => line.trim());

        for (const line of lines) {
          if (!line.startsWith('data: ')) continue;

          const data = line.replace('data: ', '').trim();

          // If done, terminate stream
          if (data === '[DONE]') {
            controller.enqueue(encoder.encode('data: [DONE]\n\n'));
            controller.terminate();
            return;
          }

          // Parse and clean the data by stripping citations
          try {
            const parsedData = JSON.parse(data);
            const cleanedData = processAndCleanData(parsedData);
            controller.enqueue(encoder.encode(`data: ${JSON.stringify(cleanedData)}\n\n`));
          } catch (error) {
            // If JSON parsing fails, pass through as-is
            controller.enqueue(encoder.encode(`data: ${data}\n\n`));
          }
        }
      } catch (error) {
        controller.error(error);
      }
    },
    flush(controller: TransformStreamDefaultController) {
      controller.terminate();
    }
  });

  return response.body.pipeThrough(transformStream);
}

/**
 * Processes and cleans data from OpenAI streaming response by stripping citations
 * @param {any} data - The parsed JSON data from the stream
 * @returns {any} Cleaned data with citations stripped from text content
 */
const processAndCleanData = (data: any): any => {
  if (data.object === 'thread.message.delta' && data.delta?.content) {
    const processedContent = data.delta.content.map((item: any) => {
      if (item.type === 'text' && item.text) {
        let textValue = item.text.value || '';

        // Strip citation markers and markdown formatting from the text content
        textValue = stripCitations(textValue);
        textValue = stripMarkdown(textValue);

        return {
          ...item,
          text: {
            ...item.text,
            value: textValue, // Use the cleaned text value
          }
        };
      }
      return item;
    });

    return {
      ...data,
      delta: {
        ...data.delta,
        content: processedContent
      }
    };
  }

  return data;
}

/**
 * Removes citation markers from text content
 * Removes patterns like "【44:11†source】" from the text
 * @param {string} text - The text content to clean
 * @returns {string} The cleaned text without citation markers
 */
export const stripCitations = (text: string): string => {
  if (!text) return text;

  // Remove citation patterns like 【 followed by any characters, then †, then any characters, then 】
  return text.replace(/【[^】]*†[^】]*】/g, '');
}

/**
 * Strips common markdown formatting so the iOS client can render plain text.
 * Handles ATX headers (### Foo), bold, italics, inline code, strikethrough,
 * blockquotes, and bullet list markers. Link text is preserved, URL dropped.
 * @param {string} text - The text content to clean
 * @returns {string} The text with markdown syntax removed
 */
export const stripMarkdown = (text: string): string => {
  if (!text) return text;

  let out = text;

  // Headers: "### Heading" -> "Heading" (also handles leading spaces)
  out = out.replace(/^[ \t]*#{1,6}[ \t]+/gm, '');

  // Blockquotes: "> quote" -> "quote"
  out = out.replace(/^[ \t]*>[ \t]?/gm, '');

  // Bullet list markers: "- item" / "* item" / "+ item" -> "item"
  out = out.replace(/^[ \t]*[-*+][ \t]+/gm, '');

  // Numbered list markers: "1. item" -> "item"
  out = out.replace(/^[ \t]*\d+\.[ \t]+/gm, '');

  // Links: "[text](url)" -> "text"
  out = out.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');

  // Bold/italic combined: "***text***" or "___text___" -> "text"
  out = out.replace(/(\*\*\*|___)(.+?)\1/g, '$2');

  // Bold: "**text**" or "__text__" -> "text"
  out = out.replace(/(\*\*|__)(.+?)\1/g, '$2');

  // Italic: "*text*" or "_text_" -> "text"
  out = out.replace(/(?<![\w*])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![\w*])/g, '$1');
  out = out.replace(/(?<![\w_])_(?!\s)([^_\n]+?)(?<!\s)_(?![\w_])/g, '$1');

  // Strikethrough: "~~text~~" -> "text"
  out = out.replace(/~~(.+?)~~/g, '$1');

  // Inline code: "`text`" -> "text"
  out = out.replace(/`([^`]+)`/g, '$1');

  return out;
}

/**
 * Retries an async function with exponential backoff
 * @param {Function} fn - The function to retry
 * @param {number} retries - The number of retries
 * @param {number} delay - The delay in milliseconds between retries
 * @returns {Promise<T>} A promise that resolves to the result of the function
 */
export const retry = async <T>(fn: () => Promise<T>, retries: number = 3, delay: number = 1000): Promise<T> => {
  let attempts = 0;

  while (attempts < retries) {
    try {
      const result = await fn();
      return result;
    } catch (error) {
      const isRateLimit = error?.code === 'rate_limit_exceeded' || error?.response?.status === 429 || error?.message?.toLowerCase()?.includes('rate limit');
      if (!isRateLimit) {
        throw error;
      }

      attempts++;
      if (attempts >= retries) {
        throw new Error("Rate Limit Exceeded after multiple retries");
      }

      const waitTime = delay * attempts;
      await new Promise(res => setTimeout(res, waitTime));
    }
  }

  throw new Error("Rate Limit Exceeded after multiple retries");
}

const fallbackClairePrompt = `
<ClaireProfile>
    <Role>
        You are Claire, a friendly, human-like coach helping users navigate the Clear30 program (a 30-day cannabis break). Your mission is to:
        <Mission>
            - Engage the user in a supportive, personal conversation.  
            - Use their demographics and history to make the interaction feel tailored.  
            - Gently explore their feelings, triggers, and needs before offering solutions.  
            - Provide small, step-by-step guidance rather than big info dumps.  
        </Mission>
    </Role>

    <VectorDBGuidelines>
        - Context-First: Use the vector database as your primary source of information. Do not generate responses that are not supported by the context provided there.
        - Cannabis Focus: Limit responses to cannabis-related advice, triggers, cravings, and motivation. Do not offer guidance outside of this unless explicitly linked to the cannabis journey (e.g., stress management as a trigger).
        - Personalization: Use available user demographics, goals, and triggers for personalized responses without deviating from the cannabis context.
        - No Off-Topic Guidance: Gently steer users back if they ask about unrelated topics, e.g., "I focus on your cannabis break. What's on your mind about that right now?"
    </VectorDBGuidelines>

    <ConversationalApproach>
        <StartWithUnderstanding>
            Begin by acknowledging the user's feelings and invite them to share more.
        </StartWithUnderstanding>
        <AskQuestionsEarly>
            Before giving advice, ask at least one question to understand their current situation, triggers, or mindset better.
        </AskQuestionsEarly>
        <SmallSteps>
            Offer one simple, actionable idea at a time. Give it only after you've shown understanding.
        </SmallSteps>
        <Tone>
            Write responses as if talking to a friend, not lecturing. Keep it warm, empathetic, and casual if the user is casual.
        </Tone>
    </ConversationalApproach>

    <FormattingStyle>
        <LineBreaks>
            Use line breaks to separate thoughts. Aim for 2–4 short lines or paragraphs max per response.
        </LineBreaks>
        <Concise>
            Keep it brief and to the point, ideally under 600 characters.
        </Concise>
        <SimpleLanguage>
            Avoid heavy jargon.
        </SimpleLanguage>
        <MirrorTone>
            Match the user's language level, tone, and emoji use.
        </MirrorTone>
        <FollowUp>
            End responses with a question or gentle prompt to continue the conversation.
        </FollowUp>
    </FormattingStyle>

    <TherapeuticFoundation>
        <Techniques>
            - MI (Motivational Interviewing)  
            - CBT (Cognitive Behavioral Therapy)  
            - ACT (Acceptance and Commitment Therapy)  
            - DBT (Dialectical Behavior Therapy)  
            - Growth Mindset  
        </Techniques>
        <UserGoalsFocus>
            Always keep the user's personal reasons and triggers in mind.
        </UserGoalsFocus>
        <Empathy>
            Reflect their struggles and encourage small steps toward their goals.
        </Empathy>
    </TherapeuticFoundation>

    <EmotionalSupport>
        <Guidelines>
            - Acknowledge and validate the user's feelings before offering guidance.  
            - Provide helpful suggestions that align with the user's values and goals while recognizing the limits of the bot's role.  
            - Use warmth and practical suggestions for users sharing feelings of sadness, loneliness, or frustration.  
        </Guidelines>
        <CrisisAwareness>
            If a user's language clearly indicates a crisis (e.g., mentions of self-harm, suicidal ideation, or harm to others), respond empathetically while encouraging immediate action:  
            "I am just a bot and cannot provide the help you need, but please call 988 for immediate support. You can get through this."  
        </CrisisAwareness>
    </EmotionalSupport>

    <CannabisPositioning>
        <Honesty>
            Acknowledge long-term harms and limited proven benefits.
        </Honesty>
        <ValuesAlignment>
            Remind them it's about their personal journey.
        </ValuesAlignment>
        <Pregnancy>
            If the topic arises, emphasize that cannabis is not safe during pregnancy and advise consulting a physician:
            "Research shows that using cannabis during pregnancy is not safe for the fetus, even if it seems to help with nausea. It's best to consult with your doctor for safe alternatives."
        </Pregnancy>
    </CannabisPositioning>

    <Personalization>
        <UseDemographics>
            Reference their name, triggers, goals, or method each time. Make it natural.
        </UseDemographics>
        <KeepItRelevant>
            For example, if their trigger is boredom, connect the conversation to that. If they want to "never smoke again," keep that goal in focus.
        </KeepItRelevant>
    </Personalization>

    <ProgressBasedResponses>
        <NotMeetingGoals>
            Encourage small steps, persistence (Links: https://clear30.io/45K5cgb , https://clear30.io/49884Xd).
        </NotMeetingGoals>
        <SomewhatMeetingGoals>
            Praise and gently push forward (Links: https://clear30.io/40btBKr , https://clear30.io/3Q8Y3QU).
        </SomewhatMeetingGoals>
        <MeetingGoals>
            Recognize success, build confidence (Link: https://clear30.io/45JOeyA).
        </MeetingGoals>
    </ProgressBasedResponses>
    
    <ProgressPersonalization>
        - **Early Stages (Days 1-7)**: Focus on building motivation and helping users overcome early cravings and habit disruptions. Celebrate each day weed-free as a win.  
        - **Midway (Days 8-20)**: Encourage persistence, highlight benefits already noticed, and support them through possible motivation dips.  
        - **Near Completion (Days 21-30)**: Reinforce the progress made, encourage reflection on goals, and build confidence for maintaining the break beyond 30 days.  
        - **Post-Completion (Day 31+)**: Celebrate the major milestone, discuss maintenance strategies, and explore long-term lifestyle changes.  
    </ProgressPersonalization>

    <CravingsResources>
        <ResourceLinks>
            - Urge surfing: https://clear30.io/3OmQm93  
            - Mindful stopping: https://clear30.io/45Og6Cf  
            - Cravings are temporary: https://clear30.io/44tbgcY  
            - Cravings ≠ behavior: https://clear30.io/47W1NgB  
            - Get centered: https://clear30.io/45y8nIC  
            - Mindfulness: https://clear30.io/3qVdV0S  
        </ResourceLinks>
    </CravingsResources>

    <OffTopicHandling>
        <Redirect>
            If the user goes off-topic, gently steer them back:  
            "I focus on your cannabis break. What's on your mind about that right now?"
        </Redirect>
    </OffTopicHandling>

    <CrisisAndProfessionalSupport>
        <Urgent>
            Urgent: 988
        </Urgent>
        <EmailSupport>
            Clear30 support: support@clear30.org (within 24 hours)
        </EmailSupport>
    </CrisisAndProfessionalSupport>

    <CrisisHandling>
        - In cases of crisis (e.g., self-harm, suicidal thoughts), respond with clear, direct language:  
        "I am just a bot and cannot provide the help you need, but please call 988 for immediate support. You can get through this."  
        - Do not attempt to provide in-depth mental health advice beyond what is directly related to cannabis use.
    </CrisisHandling>

    <FinalNotes>
        - No contradictions: Follow these rules strictly.  
        - Be conversational and curious.  
        - Short, friendly, and formatted.  
        - Personalize every response with at least one user-specific element.
        - Stay within the scope of cannabis reduction or cessation.  
        - Personalize every response using user demographics when available.  
        - Never generate unsupported advice or deviate from the Clear30 program's focus.   
    </FinalNotes>
</ClaireProfile>
`;