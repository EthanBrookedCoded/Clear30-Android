# Handle Threads Edge Function

This Edge Function manages OpenAI Assistant threads, providing comprehensive thread management capabilities for the Clear30 application.

## Functionality

The function provides four main operations:
1. Create a new thread with user context and optional previous thread history
2. Delete an existing thread
3. Stop a running thread
4. Retrieve thread messages with pagination

## Setup

1. Environment Variables:
   - Required environment variables in the Supabase Edge Function:
     ```
     OPENAI_API_KEY=your-api-key-here
     SUPABASE_URL=your-supabase-url
     SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
     OPENAI_ASSISTANT_ID=your-assistant-id
     OPENAI_API_BASE_URL=your-api-base-url
     ```

2. Dependencies:
   - OpenAI API v4.28.0 (from Deno)
   - Deno standard library v0.168.0
   - Zod v3.24.4 for request validation
   - Supabase Edge Runtime v2
   - Supabase Client

### Core Components

1. **Main Handler** (`functions/claire_handle_threads/index.ts`)
   - Entry point for all thread operations
   - Request validation and routing
   - OpenAI client initialization
   - Database operations

2. **Authentication Middleware** (`shared/middleware/auth.ts`)
   - JWT token validation
   - User authentication
   - Request authorization

3. **Helper Functions** (`functions/claire_handle_threads/helper.ts`)
   - Thread creation and management
   - Message retrieval and pagination
   - Context summarization
   - Response formatting

4. **Type Definitions** (`shared/types/types.ts`)
   - Request/Response interfaces
   - User context types
   - Thread message types

## Authentication

The function uses a middleware-based authentication system:

1. **Token Validation**
   - Extracts JWT token from Authorization header
   - Validates token using Supabase Auth
   - Retrieves user information

2. **Error Handling**
   - 401: No token provided
   - 401: Invalid token
   - 401: User not found
   - 500: Authentication system error

## Types and Interfaces

### User Context
```typescript
interface UserContext {
  name: string;
  programName: string;
  completedDays: number;
}
```

### Thread Message
```typescript
interface ThreadMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: number;
}
```

### Thread Response
```typescript
interface ThreadResponse {
  status: number;
  message: string;
  data: {
    threadId: string;
    messages?: ThreadMessage[];
    hasMore?: boolean;
    firstId?: string;
    lastId?: string;
    order?: 'asc' | 'desc';
  } | null;
}
```

## Request Parameters

### Common Parameters
- `action`: Required. One of: 'create', 'delete', 'stop', 'getMessages'
- `threadId`: Required for delete, stop, and getMessages actions

### Create Action Parameters
  - `userContext`: Required object containing:
  - `name`: String (required)
  - `programName`: String (required)
  - `completedDays`: Number (required, non-negative)
  - `loAge`: String(Optional)
  - `trigger`: String(Optional)
  - `helpHarm`: String(Optional)
  - `thenWhat`: String(Optional)
  - `commitment`: String(Optional)
  - `daysUsing`: String(Optional)
  - `breakReason`: String(Optional)
  - `previousBreak`: String(Optional)
  - `consumptionMethod`: String(Optional)
- `oldThreadId`: Optional string - ID of previous thread to include in context

### GetMessages Action Parameters
- `limit`: Optional number (default: 20)
- `before`: Optional string for pagination
- `after`: Optional string for pagination
- `order`: Optional 'asc' or 'desc' (default: 'desc')

## API Endpoints

### Create Thread
- **Method**: POST
- **Body**:
  ```json
  {
    "action": "create",
    "userContext": {
      "name": "John",
      "programName": "Clear30",
      "completedDays": 15,
      "loAge": "31-40",
      "trigger": "My Friend",
      "helpHarm": "Equally Helping and Harming (but in  different ways)",
        "thenWhat": "I want to stop cannabis/weed use entirely",
      "commitment": "Extremely",
      "daysUsing": "4-5 days a week",
      "breakReason": "Gain Mental Clarity",
      "previousBreak": "Maintained",
      "consumptionMethod": "Pen"
    },
    "oldThreadId": "optional_previous_thread_id"
  }
  ```
- **Response**: 
  ```json
  {
    "status": 200,
    "message": "Thread created successfully",
    "data": {
      "threadId": "thread_abc123..."
    }
  }
  ```

### Delete Thread
- **Method**: POST
- **Body**:
  ```json
  {
    "action": "delete",
    "threadId": "thread_abc123..."
  }
  ```
- **Response**:
  ```json
  {
    "status": 200,
    "message": "Thread deleted successfully",
    "data": {
      "threadId": "thread_abc123..."
    }
  }
  ```

### Stop Thread
- **Method**: POST
- **Body**:
  ```json
  {
    "action": "stop",
    "threadId": "thread_abc123..."
  }
  ```
- **Response**:
  ```json
  {
    "status": 200,
    "message": "Thread stopped successfully",
    "data": {
      "threadId": "thread_abc123..."
    }
  }
  ```

### Get Thread Messages
- **Method**: POST
- **Body**:
  ```json
  {
    "action": "getMessages",
    "threadId": "thread_abc123...",
    "limit": 20,
    "order": "desc",
    "before": "msg_abc...",
    "after": "msg_xyz..."
  }
  ```
- **Response**:
  ```json
  {
    "status": 200,
    "message": "Messages retrieved successfully",
    "data": {
      "threadId": "thread_abc123...",
      "messages": [
        {
          "id": "msg_123",
          "role": "user",
          "content": "message content",
          "createdAt": 1234567890
        }
      ],
      "hasMore": true,
      "firstId": "msg_first_id",
      "lastId": "msg_last_id",
      "order": "desc"
    }
  }
  ```

## Special Features

### Thread Context and History
When creating a new thread with an `oldThreadId`:
1. The system retrieves up to 50 messages from the old thread
2. Creates a concise summary using GPT-4
3. Combines the user context with the conversation summary
4. Initializes the new thread with this combined context

### Database Integration
- Thread_ids are stored in the `claire_sessions` table with:
  - User ID
  - Thread ID
  - Session name (format: `chat-{timestamp}`)

## Error Handling

The function includes comprehensive validation and error handling:

1. Request Validation (Zod Schema):
   - Missing required fields
   - Invalid action types
   - Invalid user context data
   - Missing threadId for required actions
   - Type validation for all fields

2. OpenAI API Errors:
   - API connection issues
   - Invalid thread operations
   - Rate limiting

3. Database Errors:
   - Connection issues
   - Insert/Delete failures
   - Authentication failures

Response format for errors:
```json
{
  "status": 400 | 401 | 403 | 500,
  "message": "Detailed error message",
  "data": null
}
```

## Security

- Environment variables for sensitive data
- CORS enabled with configurable origins
- Request validation using Zod schema
- JWT authentication middleware
- Supabase service role key for database operations

## Local Development

1. Start Supabase:
   ```bash
   supabase start
   ```

2. Set up environment variables in `supabase/functions/.env`:
   ```
   OPENAI_API_KEY=your-api-key
   SUPABASE_URL=your-local-supabase-url
   SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
   ```

3. Deploy the function:
   ```bash
   supabase functions deploy handle_threads
   ```

4. Test with curl:
   ```bash
   curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/handle_threads' \
     --header 'Authorization: Bearer YOUR_JWT_TOKEN' \
     --header 'Content-Type: application/json' \
     --data '{
       "action": "create",
       "userContext": {
         "id": "user_123",
         "email": "user@example.com",
         "role": "user",
         "name": "John",
         "programName": "Clear30",
         "completedDays": 15
       }
     }'
   ```