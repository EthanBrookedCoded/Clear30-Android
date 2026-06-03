import React, { useState } from 'react';
import './App.css';
import ApiKeyInput from './components/ApiKeyInput';
import ChatInterface from './components/ChatInterface';
import ContextEditor from './components/ContextEditor';
import { OpenAIModel, UserContext } from './types';
import { claireSystemPrompt, dummyUserContext } from './utils/dummyData';

interface AppState {
  apiKey: string | null;
  selectedModel: OpenAIModel;
  messages: Array<{ role: 'user' | 'assistant' | 'system'; content: string; timestamp: number }>;
  systemPrompt: string;
  userContext: UserContext;
}

const App: React.FC = () => {
  const [appState, setAppState] = useState<AppState>({
    apiKey: null,
    selectedModel: 'gpt-4o-mini',
    messages: [],
    systemPrompt: claireSystemPrompt,
    userContext: dummyUserContext
  });

  const [showContextEditor, setShowContextEditor] = useState(false);

  const handleApiKeySubmit = (apiKey: string, model: OpenAIModel) => {
    setAppState(prev => ({
      ...prev,
      apiKey,
      selectedModel: model
    }));
  };

  const handleReset = () => {
    setAppState(prev => ({
      ...prev,
      apiKey: null,
      selectedModel: 'gpt-4o-mini',
      messages: []
    }));
  };

  const handleNewMessage = (role: 'user' | 'assistant', content: string) => {
    setAppState(prev => ({
      ...prev,
      messages: [...prev.messages, { role, content, timestamp: Date.now() }]
    }));
  };

  const handleSystemPromptChange = (prompt: string) => {
    setAppState(prev => ({
      ...prev,
      systemPrompt: prompt
    }));
  };

  const handleUserContextChange = (context: UserContext) => {
    setAppState(prev => ({
      ...prev,
      userContext: context
    }));
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>Claire Playground</h1>
        <p>AI Chatbot Playground for Clear30</p>
        {appState.apiKey && (
          <button 
            onClick={() => setShowContextEditor(true)} 
            className="edit-context-button"
          >
            Edit Context & Prompt
          </button>
        )}
      </header>
      
      {!appState.apiKey ? (
        <ApiKeyInput onSubmit={handleApiKeySubmit} />
      ) : (
        <ChatInterface
          apiKey={appState.apiKey}
          model={appState.selectedModel}
          messages={appState.messages}
          systemPrompt={appState.systemPrompt}
          userContext={appState.userContext}
          onNewMessage={handleNewMessage}
          onReset={handleReset}
        />
      )}

      {showContextEditor && (
        <ContextEditor
          systemPrompt={appState.systemPrompt}
          userContext={appState.userContext}
          onSystemPromptChange={handleSystemPromptChange}
          onUserContextChange={handleUserContextChange}
          onClose={() => setShowContextEditor(false)}
        />
      )}
    </div>
  );
};

export default App;
