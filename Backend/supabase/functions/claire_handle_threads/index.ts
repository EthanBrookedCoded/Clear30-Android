import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import OpenAI from "https://deno.land/x/openai@v4.28.0/mod.ts"
import { ThreadResponse, User } from "../shared/types/types.ts";
import { createResponse, threadRequestSchema, createThread, stopThread, deleteThread, getThreadMessages, updateApiMode } from "./helper.ts";
import { withAuth } from "../shared/middleware/auth.ts";

// OpenAI client
const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY");

if (!OPENAI_API_KEY) {
  throw new Error("OPENAI_API_KEY environment variable is not set");
}

const openai = new OpenAI({
  apiKey: OPENAI_API_KEY,
  defaultHeaders: {
    'OpenAI-Beta': 'assistants=v2'
  }
});

// Supabase client
const supabase = createClient(
  Deno.env.get('SUPABASE_URL'),
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
)

// Handler
const handler = async (req: Request, user?: User): Promise<Response> => {
  try {
    // Update API mode based on settings
    await updateApiMode(supabase);

    const requestData = await req.json();

    const result = threadRequestSchema.safeParse(requestData);

    if (!result.success) {
      const errorMessage = result.error.errors.map(err => {
        if (err.code === 'unrecognized_keys') {
          return `Invalid fields found: ${err.keys.join(', ')}`;
        }
        return err.message;
      }).join('; ');

      console.log('User validation error:', errorMessage);

      return createResponse({
        status: 400,
        message: errorMessage,
        data: null
      }, 400);
    }

    const { threadId, action, userContext, oldThreadId } = result.data;

    let response: ThreadResponse;

    switch (action) {
      case 'create':
        response = await createThread(userContext!, oldThreadId, openai, user as User, supabase);
        break;
      case 'delete':
        response = await deleteThread(threadId!, openai, user as User, supabase);
        break;
      case 'stop':
        response = await stopThread(threadId!, openai);
        break;
      case 'getMessages':
        response = await getThreadMessages(threadId!, {
          limit: requestData.limit,
          before: requestData.before,
          after: requestData.after,
          order: requestData.order,
        }, openai, user?.id, supabase);
        break;
      default:
        throw new Error('Invalid action specified');
    }

    return createResponse(response, response.status);
  } catch (error) {
    return createResponse({
      status: error.status || 500,
      message: error.message,
      data: null
    }, error.status || 500);
  }
};

// Wrap the handler with withAuth
serve(withAuth(handler, supabase));


/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  # Create a new thread with user context (valid request)
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/claire_handle_threads' \
    --header 'Authorization: Bearer YOUR_ANON_KEY' \
    --header 'Content-Type: application/json' \
    --data '{
      "action": "create",
      "userContext": {
        "id": "user_123",
        "name": "John",
        "programName": "Clear30",
        "completedDays": 15
      }
    }'

  # Invalid request examples:
  # Missing userContext for create action
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/claire_handle_threads' \
    --header 'Authorization: Bearer YOUR_ANON_KEY' \
    --header 'Content-Type: application/json' \
    --data '{"action": "create"}'

  # Missing threadId for delete action
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/claire_handle_threads' \
    --header 'Authorization: Bearer YOUR_ANON_KEY' \
    --header 'Content-Type: application/json' \
    --data '{"action": "delete"}'

  # Invalid completedDays (negative number)
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/claire_handle_threads' \
    --header 'Authorization: Bearer YOUR_ANON_KEY' \
    --header 'Content-Type: application/json' \
    --data '{
      "action": "create",
      "userContext": {
        "id": "user_123",
        "name": "John",
        "programName": "Clear30",
        "completedDays": -1
      }
    }'

*/
