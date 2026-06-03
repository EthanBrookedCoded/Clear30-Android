import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import OpenAI from "https://deno.land/x/openai@v4.20.1/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { chatSchema, createResponse, retry, USE_COMPLETION_API, storeMessage, getConversationHistory, stripCitations, stripMarkdown, updateApiMode, getSystemPrompt } from "./helper.ts";
import { withAuth } from "../shared/middleware/auth.ts";

// OpenAI client
const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY");
const OPENAI_ASSISTANT_ID = Deno.env.get("OPENAI_ASSISTANT_ID");
const OPENAI_API_BASE_URL = Deno.env.get("OPENAI_API_BASE_URL");

if (!OPENAI_API_KEY || !OPENAI_ASSISTANT_ID || !OPENAI_API_BASE_URL) {
  throw new Error("Missing required environment variables");
}

// Supabase client
const supabase = createClient(
  Deno.env.get('SUPABASE_URL') ?? '',
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
);

const openai = new OpenAI({
  apiKey: OPENAI_API_KEY,
  defaultHeaders: {
    'OpenAI-Beta': 'assistants=v2'
  }
});

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

// Completion API chat handler
const handleCompletionChat = async (req: Request, user: any) => {
  try {
    const requestData = await req.json();
    const result = chatSchema.safeParse(requestData);

    if (!result.success) {
      const errorMessage = result.error.errors.map(err => {
        if (err.code === 'unrecognized_keys') {
          return `Invalid fields found: ${err.keys.join(', ')}. Only 'message', 'threadId', and 'conversationMode' are allowed.`;
        }
        return err.message;
      }).join('; ');

      return createResponse({
        status: 400,
        message: errorMessage,
        data: null
      }, 400);
    }

    const { message, threadId, conversationMode } = result.data;

    // Get conversation history for context
    const history = await getConversationHistory(user.id, threadId, supabase);

    // Get system prompt from database
    const systemPrompt = await getSystemPrompt(supabase);

    // Build messages array for completion API
    const messages = [
      { role: 'system', content: systemPrompt },
      ...history,
      { role: 'user', content: message }
    ];

    // Store user message
    await storeMessage(user.id, threadId, 'user', message, 'completion', conversationMode, supabase);

    // Call completion API
    const completion = await retry(async () => {
      return await openai.chat.completions.create({
        model: 'gpt-4o',
        messages: messages,
        temperature: 0.7,
        max_tokens: 1000,
      });
    });

    const assistantMessage = completion.choices[0]?.message?.content || '';
    const cleanedResponse = stripMarkdown(stripCitations(assistantMessage));

    // Store assistant response
    await storeMessage(user.id, threadId, 'assistant', cleanedResponse, 'completion', conversationMode, supabase);

    // Return simple JSON response with the complete message
    return new Response(JSON.stringify({
      status: 200,
      message: 'Success',
      data: {
        content: cleanedResponse,
        threadId: threadId
      }
    }), {
      headers: {
        'Content-Type': 'application/json',
        ...corsHeaders,
      },
    });

  } catch (error) {
    return createResponse({
      status: error?.status || 500,
      message: error?.message || 'Internal Server Error',
      data: null
    }, error?.status || 500);
  }
};

// Original assistants API chat handler
const handleAssistantsChat = async (req: Request, user: any) => {
  try {
    const requestData = await req.json();
    const result = chatSchema.safeParse(requestData);

    // Parse request
    if (!result.success) {
      const errorMessage = result.error.errors.map(err => {
        if (err.code === 'unrecognized_keys') {
          return `Invalid fields found: ${err.keys.join(', ')}. Only 'message', 'threadId', and 'conversationMode' are allowed.`;
        }
        return err.message;
      }).join('; ');

      return createResponse({
        status: 400,
        message: errorMessage,
        data: null
      }, 400);
    }

    // Get message and threadID
    const { message, threadId, conversationMode } = result.data;

    // Store user message
    await storeMessage(user.id, threadId, 'user', message, 'assistants', conversationMode, supabase);

    // Create message to send
    const threadMessageResponse = await retry(async () => {
      return await openai.beta.threads.messages.create(
        threadId,
        { role: 'user', content: message },
        { headers: { 'OpenAI-Beta': 'assistants=v2' } }
      );
    });

    // Get stream for message
    const stream = await retry(async () => {
      return await fetch(`${OPENAI_API_BASE_URL}/threads/${threadId}/runs`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${OPENAI_API_KEY}`,
          'Content-Type': 'application/json',
          'OpenAI-Beta': 'assistants=v2',
          'Accept': 'text/event-stream',
        },
        body: JSON.stringify({
          assistant_id: OPENAI_ASSISTANT_ID,
          stream: true,
        }),
      });
    });

    // Process stream and collect assistant response for logging
    const processedStream = await processStreamResponseWithLogging(stream, user.id, threadId, conversationMode, supabase);

    return new Response(processedStream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        ...corsHeaders,
      },
    });

  } catch (error) {
    return createResponse({
      status: error?.status || 500,
      message: error?.message || 'Internal Server Error',
      data: null
    }, error?.status || 500);
  }
};

// Enhanced stream processor that captures assistant responses
const processStreamResponseWithLogging = async (
  response: Response,
  userId: string,
  threadId: string,
  conversationMode: 'text' | 'voice',
  supabase: any
): Promise<ReadableStream<Uint8Array>> => {
  if (!response.ok) {
    throw new Error(`OpenAI API error: ${response?.status} ${response?.statusText}`);
  }

  if (!response.body) {
    throw new Error("No response body received from OpenAI");
  }

  let assistantResponse = '';

  const transformStream = new TransformStream({
    async transform(chunk: Uint8Array, controller: TransformStreamDefaultController) {
      try {
        const encoder = new TextEncoder();
        const decoder = new TextDecoder();

        const text = decoder.decode(chunk);
        const lines = text.split('\n').filter(line => line.trim());

        for (const line of lines) {
          if (!line.startsWith('data: ')) continue;

          const data = line.replace('data: ', '').trim();

          if (data === '[DONE]') {
            // Store the complete assistant response before terminating
            if (assistantResponse.trim()) {
              try {
                await storeMessage(userId, threadId, 'assistant', assistantResponse.trim(), 'assistants', conversationMode, supabase);
                console.log('Stored assistant response for thread:', threadId);
              } catch (error) {
                console.error('Error storing assistant response:', error);
              }
            }
            controller.enqueue(encoder.encode('data: [DONE]\n\n'));
            controller.terminate();
            return;
          }

          try {
            const parsedData = JSON.parse(data);

            // Extract content for logging
            if (parsedData.object === 'thread.message.delta' && parsedData.delta?.content) {
              parsedData.delta.content.forEach((item: any) => {
                if (item.type === 'text' && item.text?.value) {
                  const cleanContent = stripMarkdown(stripCitations(item.text.value));
                  assistantResponse += cleanContent;
                }
              });
            }

            const cleanedData = processAndCleanData(parsedData);
            controller.enqueue(encoder.encode(`data: ${JSON.stringify(cleanedData)}\n\n`));
          } catch (error) {
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
};

// Helper function for processing and cleaning data (moved from helper.ts for local use)
const processAndCleanData = (data: any): any => {
  if (data.object === 'thread.message.delta' && data.delta?.content) {
    const processedContent = data.delta.content.map((item: any) => {
      if (item.type === 'text' && item.text) {
        let textValue = item.text.value || '';
        textValue = stripCitations(textValue);
        textValue = stripMarkdown(textValue);
        return {
          ...item,
          text: {
            ...item.text,
            value: textValue,
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
};

// Main chat handler that routes based on the flag
const handleChat = async (req: Request, user: any) => {
  // Update API mode based on settings
  await updateApiMode(supabase);

  if (USE_COMPLETION_API) {
    return await handleCompletionChat(req, user);
  } else {
    return await handleAssistantsChat(req, user);
  }
};

// TRUE Streaming TTS handler using OpenAI's native streaming
const handleStreamingTTS = async (req: Request) => {
  try {
    const { text } = await req.json();

    if (!text) {
      return new Response(
        JSON.stringify({ error: 'Text is required for synthesis' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        }
      );
    }

    // Use OpenAI's native streaming with PCM format
    const ttsResponse = await fetch('https://api.openai.com/v1/audio/speech', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${OPENAI_API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'gpt-4o-mini-tts', // Use the newer streaming-optimized model
        voice: 'alloy',
        input: text,
        response_format: 'pcm', // PCM format for true streaming
      }),
    });

    if (!ttsResponse.ok) {
      throw new Error(`OpenAI TTS API error: ${ttsResponse.status} ${ttsResponse.statusText}`);
    }

    if (!ttsResponse.body) {
      throw new Error('No response body received from OpenAI TTS');
    }

    // Stream the PCM data directly from OpenAI to client
    return new Response(ttsResponse.body, {
      headers: {
        ...corsHeaders,
        'Content-Type': 'application/octet-stream',
        'Transfer-Encoding': 'chunked',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'X-Audio-Format': 'pcm-16bit',
        'X-Sample-Rate': '24000', // OpenAI PCM is 24kHz, 16-bit
        'X-Channels': '1',
      },
    });

  } catch (error) {
    return new Response(
      JSON.stringify({
        error: error.message,
        details: 'Check server logs for more information'
      }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      }
    );
  }
};

// Traditional MP3 TTS handler (for MP3 mode)
const handleMP3TTS = async (req: Request) => {
  try {
    const { text } = await req.json();

    if (!text) {
      return new Response(
        JSON.stringify({ error: 'Text is required for synthesis' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        }
      );
    }

    // Create request to OpenAI TTS API
    const ttsResponse = await fetch('https://api.openai.com/v1/audio/speech', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${OPENAI_API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'tts-1',
        voice: 'alloy',
        input: text,
        response_format: 'mp3',
      }),
    });

    if (!ttsResponse.ok) {
      throw new Error(`OpenAI TTS API error: ${ttsResponse.status} ${ttsResponse.statusText}`);
    }

    // Get the complete audio data
    const audioArrayBuffer = await ttsResponse.arrayBuffer();

    // Return the complete MP3 file
    return new Response(audioArrayBuffer, {
      headers: {
        ...corsHeaders,
        'Content-Type': 'audio/mpeg',
        'Content-Length': audioArrayBuffer.byteLength.toString(),
        'Cache-Control': 'public, max-age=3600', // Cache for 1 hour
      },
    });

  } catch (error) {
    return new Response(
      JSON.stringify({
        error: error.message,
        details: 'Check server logs for more information'
      }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      }
    );
  }
};

const handler = async (req: Request, user?: any) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const url = new URL(req.url);
    const endpoint = url.pathname.split('/').pop();

    if (endpoint === 'synthesize') {
      return await handleStreamingTTS(req);
    } else if (endpoint === 'synthesize-mp3') {
      return await handleMP3TTS(req);
    } else {
      return await handleChat(req, user);
    }
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      }
    );
  }
};

serve(withAuth(handler, supabase));

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  Chat:
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/claire_openai_chat' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"message":"Hello, how are you?","threadId":"thread_1234567890"}'

  Streaming TTS:
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/claire_openai_chat/synthesize' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"text":"Hello, this is a test message for text to speech synthesis."}'

  MP3 TTS:
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/claire_openai_chat/synthesize-mp3' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"text":"Hello, this is a test message for text to speech synthesis."}'

*/