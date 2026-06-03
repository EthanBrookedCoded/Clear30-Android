import React, { useState, useRef, useEffect } from 'react';
import OpenAI from 'openai';
import { OpenAIModel, Message, UserContext } from '../types';
import { createUserContextPrompt } from '../utils/dummyData';

interface ChatInterfaceProps {
  apiKey: string;
  model: OpenAIModel;
  messages: Message[];
  systemPrompt: string;
  userContext: UserContext;
  onNewMessage: (role: 'user' | 'assistant', content: string) => void;
  onReset: () => void;
}

const ChatInterface: React.FC<ChatInterfaceProps> = ({
  apiKey,
  model,
  messages,
  systemPrompt,
  userContext,
  onNewMessage,
  onReset
}) => {
  const [inputMessage, setInputMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const openaiRef = useRef<OpenAI | null>(null);

  // Initialize OpenAI client
  useEffect(() => {
    openaiRef.current = new OpenAI({
      apiKey: apiKey,
      dangerouslyAllowBrowser: true
    });
  }, [apiKey]);

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const buildMessagesForAPI = (userMessage: string): Array<{role: 'system' | 'user' | 'assistant', content: string}> => {
    const userContextPrompt = createUserContextPrompt(userContext);
    const fullSystemMessage = `${systemPrompt}\n\n${userContextPrompt}`;
    
    // Get conversation history (only user and assistant messages)
    const conversationHistory = messages
      .filter(msg => msg.role === 'user' || msg.role === 'assistant')
      .map(msg => ({
        role: msg.role as 'user' | 'assistant',
        content: msg.content
      }));

    return [
      { role: 'system', content: fullSystemMessage },
      ...conversationHistory,
      { role: 'user', content: userMessage }
    ];
  };

  const handleSendMessage = async () => {
    if (!inputMessage.trim() || isLoading || !openaiRef.current) return;

    const userMessage = inputMessage.trim();
    setInputMessage('');
    setError(null);
    setIsLoading(true);

    // Add user message
    onNewMessage('user', userMessage);

    try {
      const messagesToSend = buildMessagesForAPI(userMessage);

      const completion = await openaiRef.current.chat.completions.create({
        model: model,
        messages: messagesToSend,
        temperature: 0.7,
        max_tokens: 1000,
      });

      const assistantResponse = completion.choices[0]?.message?.content || 'No response received';
      onNewMessage('assistant', assistantResponse);

    } catch (err) {
      console.error('OpenAI API Error:', err);
      let errorMessage = 'Failed to get response from OpenAI';
      
      if (err instanceof Error) {
        if (err.message.includes('401')) {
          errorMessage = 'Invalid API key. Please check your OpenAI API key.';
        } else if (err.message.includes('429')) {
          errorMessage = 'Rate limit exceeded. Please wait a moment and try again.';
        } else if (err.message.includes('insufficient_quota')) {
          errorMessage = 'Insufficient quota. Please check your OpenAI account usage.';
        } else {
          errorMessage = err.message;
        }
      }
      
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const formatTimestamp = (timestamp: number) => {
    return new Date(timestamp).toLocaleTimeString([], { 
      hour: '2-digit', 
      minute: '2-digit' 
    });
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  return (
    <div className="chat-interface">
      <div className="chat-header">
        <div className="chat-info">
          <h2>Claire - {model.toUpperCase()}</h2>
          <p>Chatting as: <strong>{userContext.name}</strong> (Day {userContext.currentDay})</p>
        </div>
        <button onClick={onReset} className="reset-button">
          Reset Session
        </button>
      </div>

      <div className="messages-container">
        {messages.length === 0 && (
          <div className="welcome-message">
            <p>👋 Hi! I'm Claire, your AI coach for the Clear30 program.</p>
            <p>I'm here to help you on your cannabis break journey. What's on your mind today?</p>
          </div>
        )}

        {messages.map((message, index) => (
          <div key={index} className={`message ${message.role}`}>
            <div className="message-header">
              <span className="message-role">
                {message.role === 'user' ? '👤 You' : '🤖 Claire'}
              </span>
              <span className="message-time">
                {formatTimestamp(message.timestamp)}
              </span>
            </div>
            <div className="message-content">
              {message.content}
              <button 
                className="copy-button"
                onClick={() => copyToClipboard(message.content)}
                title="Copy message"
              >
                📋
              </button>
            </div>
          </div>
        ))}

        {isLoading && (
          <div className="message assistant loading">
            <div className="message-header">
              <span className="message-role">🤖 Claire</span>
              <span className="message-time">typing...</span>
            </div>
            <div className="message-content">
              <div className="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {error && (
        <div className="error-banner">
          <strong>Error:</strong> {error}
          <button onClick={() => setError(null)} className="close-error">×</button>
        </div>
      )}

      <div className="input-container">
        <textarea
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder="Type your message here... (Press Enter to send, Shift+Enter for new line)"
          className="message-input"
          disabled={isLoading}
          rows={3}
        />
        <button
          onClick={handleSendMessage}
          disabled={!inputMessage.trim() || isLoading}
          className="send-button"
        >
          {isLoading ? '⏳' : '➤'}
        </button>
      </div>

      <div className="context-info">
        <details>
          <summary>Current User Context (Click to expand)</summary>
          <div className="context-details">
            <p><strong>Name:</strong> {userContext.name}</p>
            <p><strong>Program:</strong> {userContext.programName}</p>
            <p><strong>Current Day:</strong> {userContext.currentDay}</p>
            <p><strong>Context:</strong> {userContext.currentDayContext}</p>
            <p><strong>Assessment:</strong> {userContext.assessmentResponses}</p>
            <p><strong>Check-ins:</strong> {userContext.checkIns}</p>
            <p><strong>Last Smoked:</strong> {userContext.lastSmoked}</p>
          </div>
        </details>
      </div>
    </div>
  );
};

export default ChatInterface; 