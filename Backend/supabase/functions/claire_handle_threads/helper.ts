import { OpenAI } from 'https://deno.land/x/openai@v4.28.0/mod.ts';
import { ThreadMessageListParams, ThreadResponse, UserContext, ThreadMessage, User } from "../shared/types/types.ts";
import { z } from "https://esm.sh/zod@3.24.4";
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

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

// Constants
const HEADERS = {
  'Content-Type': 'application/json',
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization'
};

/**
 * Stores a message in the database
 * @param {string} userId - The user ID
 * @param {string} threadId - The thread ID
 * @param {string} role - The message role (user or assistant)
 * @param {string} content - The message content
 * @param {string} apiType - The API type (assistants or completion)
 * @param {any} supabase - The Supabase client
 * @param {string} messageType - The message type (chat, context, or system). Defaults to 'chat'
 */
export const storeMessage = async (
  userId: string,
  threadId: string,
  role: 'user' | 'assistant',
  content: string,
  apiType: 'assistants' | 'completion',
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
        api_type: apiType
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
  limit: number = 50
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
 * Creates a user context prompt string
 * @param {UserContext} userContext - The user context object
 * @returns {Object} An object containing the prompt string
 */
const getUserContext = (userContext: UserContext): { prompt: string } => {
  // user context prompt string
  const prompt = `
    <UserContext>
      <Name>${userContext.name}</Name>
      <AssessmentResponses>${userContext.assessmentResponses}</AssessmentResponses>
      <ProgramName>${userContext.programName}</ProgramName>
      <CurrentDay>${userContext.currentDay}</CurrentDay>
      <CurrentDayContext>${userContext.currentDayContext}</CurrentDayContext>
      <CheckIns>${userContext.checkIns}</CheckIns>
      <LastSmoked>${userContext.lastSmoked}</LastSmoked>
      <CurrentDate>${userContext.currentDate}</CurrentDate>
    </UserContext>
  `;

  // Return only the prompt string
  return {
    prompt: prompt
  };
}

/**
 * Removes citation markers from text content
 * Removes patterns like "【44:11†source】" from the text
 * @param {string} text - The text content to clean
 * @returns {string} The cleaned text without citation markers
 */
const stripCitations = (text: string): string => {
  if (!text) return text;

  // Remove citation patterns like 【 followed by any characters, then †, then any characters, then 】
  return text.replace(/【[^】]*†[^】]*】/g, '');
}

/**
 * Creates a response object with the specified data and status
 * @param {ThreadResponse} data - The response data to be sent
 * @param {number} status - The HTTP status code
 * @returns {Response} A new Response object with the formatted data and headers
 */
export const createResponse = (data: ThreadResponse, status: number): Response => {
  return new Response(
    JSON.stringify(data),
    {
      status,
      headers: HEADERS
    }
  );
}

// User context schema
export const userContextSchema = z.object({
  name: z.string({
    required_error: "Name is required",
    invalid_type_error: "Name must be a string"
  }).min(1, "Name cannot be empty"),
  programName: z.string({
    required_error: "Program name is required",
    invalid_type_error: "Program name must be a string"
  }).min(1, "Program name cannot be empty"),
  currentDay: z.number({
    invalid_type_error: "Current day must be a number"
  }).min(0, "Current day cannot be negative").optional(),
  currentDayContext: z.string({
    required_error: "Current day context is required",
    invalid_type_error: "Current day context must be a string"
  }).min(1, "Current day context cannot be empty"),
  assessmentResponses: z.string().optional(),
  checkIns: z.string().optional(),
  lastSmoked: z.string().optional(),
  currentDate: z.string().optional()
}).strict();

// Thread request schema
export const threadRequestSchema = z.object({
  threadId: z.string().optional(),
  oldThreadId: z.string().optional(),
  action: z.enum(['create', 'delete', 'stop', 'getMessages'], {
    required_error: "Action is required",
    invalid_type_error: "Action must be one of: create, delete, stop, getMessages"
  }),
  userContext: userContextSchema.optional(),
  limit: z.number().optional(),
  before: z.string().optional(),
  after: z.string().optional(),
  order: z.enum(['asc', 'desc']).optional(),
  lastId: z.string().optional(),
  firstId: z.string().optional()
}).strict().superRefine((data, ctx) => {
  if (data.action === 'create' && !data.userContext) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "User context is required for create action",
      path: ["userContext"]
    });
  }

  if (['delete', 'stop', 'getMessages'].includes(data.action) && !data.threadId) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Thread ID is required for delete, stop, and getMessages actions",
      path: ["threadId"]
    });
  }
});


/**
 * Creates a summary of thread messages using OpenAI's completion API
 * @param {string} messages - The thread messages to summarize
 * @param {OpenAI} openai - The OpenAI client instance
 * @returns {Promise<string>} A promise that resolves to the summary
 */
const createThreadSummary = async (messages: string, openai: OpenAI): Promise<string> => {
  try {
    const prompt = `
      Summarize the following conversation in AT MOST 10 short, concise bullet points. Focus on the most important points only. Do not include any information on the user's program day. Each bullet point should be one line maximum:
      ${messages}
      Summary (max 10 points):
    `;

    const completion = await openai.chat.completions.create({
      model: "gpt-4o",
      messages: [
        {
          role: "system",
          content: "You are a concise summarizer. Create summaries in bullet points, maximum 10 points, one line per point. Be direct and brief."
        },
        {
          role: "user",
          content: prompt
        }
      ],
      temperature: 0.3
    });

    return completion.choices[0]?.message?.content || "No summary available";
  } catch (error) {
    console.error('Error creating thread summary:', error);
    throw new Error('Failed to create thread summary');
  }
}

/**
 * Creates a thread with the given context and optional previous thread messages
 * @param {OpenAI} openai - The OpenAI client instance
 * @param {string} contextContent - The content to initialize the thread with
 * @returns {Promise<string>} A promise that resolves to the thread ID
 */
const initializeThread = async (openai: OpenAI, contextContent: string): Promise<string> => {
  const thread = await openai.beta.threads.create({
    messages: [{
      role: 'user',
      content: contextContent
    }]
  }, {
    headers: { 'OpenAI-Beta': 'assistants=v2' }
  });

  return thread.id;
}

/**
 * Creates a new thread using the OpenAI API
 * @returns {Promise<ThreadResponse>} A promise that resolves to the thread response
 * @throws {Error} If thread creation fails
 */
export const createThread = async (userContext: UserContext, oldThreadId: string, openai: OpenAI, user: User, supabase: ReturnType<typeof createClient>): Promise<ThreadResponse> => {
  const context = getUserContext(userContext);
  let threadContent = context.prompt;

  // Create summary of previous thread if it exists
  if (oldThreadId) {
    const oldThreadMessages = await getThreadMessages(oldThreadId, { limit: 50 }, openai, user.id, supabase);
    const oldMessages = oldThreadMessages.data?.messages;

    if (oldMessages && oldMessages.length > 0) {
      // Strip citations from old messages content before creating summary
      const oldThreadMessagesContent = oldMessages.map(msg => stripCitations(msg.content)).join('\n');
      const summary = await createThreadSummary(oldThreadMessagesContent, openai);

      threadContent = `
        ${context.prompt}
        
        <Previous Conversation Summary>
        ${summary}
        </Previous Conversation Summary>
      `;
    }
  }

  // Create thread
  const threadId = await initializeThread(openai, threadContent);

  // Store the context as a user message in the database (for both completion and assistants modes)
  try {
    await storeMessage(
      user.id,
      threadId,
      'user',
      threadContent,
      USE_COMPLETION_API ? 'completion' : 'assistants',
      supabase,
      'context'
    );
  } catch (error) {
    console.error('Error storing initial context message:', error);
  }

  console.log('Created thread:', threadId);

  return {
    status: 200,
    message: 'Thread created successfully',
    data: {
      threadId
    }
  };
}

/**
 * Deletes an existing thread using the OpenAI API
 * @param {string} threadId - The ID of the thread to delete
 * @returns {Promise<ThreadResponse>} A promise that resolves to the thread response
 * @throws {Error} If threadId is empty or deletion fails
 */
export const deleteThread = async (threadId: string, openai: OpenAI, user: User, supabase: ReturnType<typeof createClient>): Promise<ThreadResponse> => {
  if (!threadId.trim()) {
    throw new Error('Thread ID is required for deletion');
  }

  console.log('Deleting thread:', threadId);

  await openai.beta.threads.del(threadId, {
    headers: { 'OpenAI-Beta': 'assistants=v2' }
  });

  return {
    status: 200,
    message: 'Thread deleted successfully',
    data: { threadId }
  };
}

/**
 * Stops an existing thread using the OpenAI API
 * @param {string} threadId - The ID of the thread to stop
 * @returns {Promise<ThreadResponse>} A promise that resolves to the thread response
 * @throws {Error} If threadId is empty or stopping fails
 */
export const stopThread = async (threadId: string, openai: OpenAI): Promise<ThreadResponse> => {
  if (!threadId.trim()) {
    throw new Error('Thread ID is required to stop');
  }

  console.log('Stopping thread:', threadId);

  const runs = await openai.beta.threads.runs.list(threadId, {
    headers: { 'OpenAI-Beta': 'assistants=v2' }
  });

  if (runs.data.length > 0) {
    const currentRun = runs.data[0];
    await openai.beta.threads.runs.cancel(threadId, currentRun.id, {
      headers: { 'OpenAI-Beta': 'assistants=v2' }
    });
  }

  return {
    status: 200,
    message: 'Thread stopped successfully',
    data: { threadId }
  };
}

/**
 * Retrieves messages from a specific thread using database or OpenAI API based on the flag
 * @param {string} threadId - The ID of the thread to retrieve messages from
 * @param {ThreadMessageListParams} params - The parameters for the message list request
 * @param {OpenAI} openai - The OpenAI client instance
 * @param {string} userId - The user ID (for completion API)
 * @param {any} supabase - The Supabase client (for completion API)
 * @returns {Promise<ThreadResponse>} A promise that resolves to the thread response containing the messages
 * @throws {Error} If threadId is empty or retrieval fails
 */
export const getThreadMessages = async (
  threadId: string,
  params: ThreadMessageListParams,
  openai: OpenAI,
  userId?: string,
  supabase?: any
): Promise<ThreadResponse> => {
  if (!threadId.trim()) {
    throw new Error('Thread ID is required to get messages');
  }

  console.log('Getting messages for thread:', threadId);

  if (USE_COMPLETION_API && userId && supabase) {
    // Use database for completion API
    try {
      const limit = params.limit || 20;
      const order = params.order || 'desc';

      const { data, error } = await supabase
        .schema('claire')
        .from('messages')
        .select('id, role, content, created_at')
        .eq('user_id', userId)
        .eq('thread_id', threadId)
        .in('role', ['user', 'assistant'])
        .order('created_at', { ascending: order === 'asc' })
        .limit(limit);

      if (error) {
        console.error('Error retrieving messages from database:', error);
        throw new Error('Failed to retrieve messages from database');
      }

      const messages = (data || []).map((msg: any) => ({
        id: msg.id,
        role: msg.role,
        content: stripCitations(msg.content),
        createdAt: new Date(msg.created_at).getTime() / 1000 // Convert to Unix timestamp
      }));

      return {
        status: 200,
        message: 'Messages retrieved successfully',
        data: {
          threadId,
          messages,
          hasMore: false, // Could be enhanced
          firstId: messages[0]?.id,
          lastId: messages[messages.length - 1]?.id
        }
      };
    } catch (error) {
      console.error('Error getting thread messages from database:', error);
      return {
        status: 500,
        message: error.message || 'Failed to get thread messages',
        data: null
      };
    }
  } else {
    // Use OpenAI API for assistants mode
    try {
      const options: ThreadMessageListParams = {
        limit: params.limit || 20,
        after: params.after || undefined,
        before: params.before || undefined,
        order: params.order || 'desc',
      };

      const response = await openai.beta.threads.messages.list(threadId, options, {
        headers: { 'OpenAI-Beta': 'assistants=v2' }
      });

      const messages = response.data.map(msg => ({
        id: msg.id,
        role: msg.role,
        content: stripCitations(msg.content[0]?.text?.value || ''),
        createdAt: msg.created_at
      }));

      return {
        status: 200,
        message: 'Messages retrieved successfully',
        data: {
          threadId,
          messages,
          hasMore: response.body.has_more,
          firstId: response.body.first_id,
          lastId: response.body.last_id
        }
      };
    } catch (error) {
      console.error('Error getting thread messages from OpenAI:', error);
      return {
        status: 500,
        message: error.message || 'Failed to get thread messages',
        data: null
      };
    }
  }
};
