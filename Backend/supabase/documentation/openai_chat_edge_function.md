# OpenAI Chat Edge Function Documentation

## Overview

The OpenAI Chat Edge Function is a Supabase Edge Function that handles real-time chat interactions with OpenAI's Assistant API. It provides a secure, authenticated streaming interface for chat interactions with rate limiting and error handling capabilities.

## Architecture

### Technology Stack
- **Runtime**: Deno
- **Framework**: Supabase Edge Functions
- **Authentication**: JWT-based (Supabase Auth)
- **AI Integration**: OpenAI API v4.20.1
- **Type Safety**: Zod v3.24.4
- **HTTP Server**: Deno Standard Library v0.168.0
- **Streaming**: Server-Sent Events (SSE)

### Core Components

1. **Main Handler** (`functions/claire_openai_chat/index.ts`)
   - Entry point for chat operations
   - Request validation
   - OpenAI client initialization
   - Stream handling
   - Error management

2. **Helper Functions** (`functions/claire_openai_chat/helper.ts`)
   - Stream processing
   - Response formatting
   - Rate limit handling
   - Schema validation

3. **Authentication Middleware** (`shared/middleware/auth.ts`)
   - JWT token validation
   - User authentication
   - Request authorization

## Environment Configuration

Required environment variables:
```bash
OPENAI_API_KEY=your-openai-api-key
OPENAI_ASSISTANT_ID=your-assistant-id
OPENAI_API_BASE_URL=https://api.openai.com/v1
SUPABASE_URL=your-supabase-url
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
```

## API Reference

### Base URL
```
https://<project-ref>.functions.supabase.co/claire_openai_chat
```

### Common Headers
```http
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### Chat Request

Sends a message to the OpenAI Assistant and receives a streaming response.

**Request:**
```http
POST /claire_openai_chat
{
  "message": "string",
  "threadId": "thread_..."
}
```

**Validation Rules:**
- `message`: Required string, 1-512 characters
- `threadId`: Required string, must start with "thread_"

**Response:**
Server-Sent Events (SSE) stream with the following format:
```text
data: {"id":"run_abc...","status":"queued"}
data: {"id":"run_abc...","status":"in_progress"}
data: {"id":"run_abc...","status":"completed","response":"Assistant's message"}
data: [DONE]
```

## Special Features

### Stream Processing
The function implements a custom stream processor that:
1. Decodes incoming chunks from OpenAI
2. Filters and transforms SSE data
3. Handles stream termination
4. Manages error states

### Rate Limiting
Implements exponential backoff retry mechanism:
```typescript
retry(async () => {
  // API call
}, retries = 3, delay = 5000)
```

- Initial delay: 5000ms
- Maximum retries: 3
- Exponential backoff: delay * (attempt + 1)

## Error Handling

### HTTP Status Codes
- 200: Successful operation
- 400: Invalid request/validation error
- 401: Authentication error
- 429: Rate limit exceeded
- 500: Server/OpenAI API error

### Error Response Format
```json
{
  "status": number,
  "message": "Detailed error description",
  "data": null
}
```

### Validation (Zod Schema)
```typescript
chatSchema = z.object({
  message: z.string()
    .min(1)
    .max(512),
  threadId: z.string()
    .startsWith("thread_")
    .min(1)
}).strict()
```

## Stream Processing Details

### Transform Stream Implementation
```typescript
new TransformStream({
  async transform(chunk, controller) {
    // 1. Decode chunk
    // 2. Split into lines
    // 3. Process SSE format
    // 4. Handle stream termination
    // 5. Error handling
  }
})
```

### Stream Events
1. **Regular Message**
   ```json
   {
     "id": "run_abc...",
     "status": "completed",
     "response": "message content"
   }
   ```

2. **Termination**
   ```
   [DONE]
   ```

## Security Considerations

1. **Authentication**
   - JWT-based authentication
   - Token validation on every request
   - Supabase Auth integration

2. **Data Protection**
   - Environment variables for sensitive data
   - CORS configuration
   - Request validation
   - Message size limits

3. **API Security**
   - Rate limiting with retry mechanism
   - Request validation
   - Error handling
   - Secure headers

## Best Practices

1. **Error Handling**
   - Comprehensive error messages
   - Proper status codes
   - Stream error management
   - Rate limit handling

2. **Performance**
   - Streaming responses
   - Efficient chunk processing
   - Memory management
   - Connection handling

3. **Code Organization**
   - Modular architecture
   - Type safety
   - Clear error boundaries
   - Consistent response format

## Limitations and Considerations

1. **Rate Limits**
   - OpenAI API rate limits
   - Retry mechanism limits
   - Connection timeouts

2. **Stream Handling**
   - Connection duration limits
   - Memory usage for long streams
   - Client disconnection handling

3. **Message Constraints**
   - Maximum message length (512 characters)
   - Thread ID format requirements
   - Response size limitations

## Local Development

1. Start Supabase:
   ```bash
   supabase start
   ```

2. Set up environment variables in `supabase/functions/.env`:
   ```bash
   OPENAI_API_KEY=your-api-key
   OPENAI_ASSISTANT_ID=your-assistant-id
   OPENAI_API_BASE_URL=https://api.openai.com/v1
   SUPABASE_URL=your-local-supabase-url
   SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
   ```

3. Deploy function:
   ```bash
   supabase functions deploy claire_openai_chat
   ```

4. Test with curl:
   ```bash
   curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/claire_openai_chat' \
     --header 'Authorization: Bearer YOUR_JWT_TOKEN' \
     --header 'Content-Type: application/json' \
     --data '{
       "message": "Hello, how are you?",
       "threadId": "thread_abc123"
     }'
   ```

## Error Response Examples

1. **Invalid Request**
   ```json
   {
     "status": 400,
     "message": "Invalid fields found: extraField. Only 'message' and 'threadId' are allowed.",
     "data": null
   }
   ```

2. **Rate Limit**
   ```json
   {
     "status": 429,
     "message": "Rate Limit Exceeded after multiple retries",
     "data": null
   }
   ```

3. **Authentication Error**
   ```json
   {
     "status": 401,
     "message": "Invalid JWT token",
     "data": null
   }
   ```

## Debugging Tips

1. **Stream Issues**
   - Check SSE format in response
   - Verify client-side event handling
   - Monitor connection timeouts

2. **Rate Limits**
   - Monitor retry attempts in logs
   - Check exponential backoff timing
   - Verify API quota usage

3. **Authentication**
   - Validate JWT token format
   - Check token expiration
   - Verify Supabase configuration 