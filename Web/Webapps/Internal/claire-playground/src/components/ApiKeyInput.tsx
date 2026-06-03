import React, { useState } from 'react';
import { OpenAIModel } from '../types';

interface ApiKeyInputProps {
  onSubmit: (apiKey: string, model: OpenAIModel) => void;
}

const ApiKeyInput: React.FC<ApiKeyInputProps> = ({ onSubmit }) => {
  const [apiKey, setApiKey] = useState('');
  const [selectedModel, setSelectedModel] = useState<OpenAIModel>('gpt-4o-mini');
  const [error, setError] = useState('');

  const models: { value: OpenAIModel; label: string }[] = [
    { value: 'gpt-4o-mini', label: 'GPT-4o Mini (Recommended)' },
    { value: 'gpt-4o', label: 'GPT-4o' },
    { value: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
    { value: 'gpt-4', label: 'GPT-4' },
    { value: 'gpt-3.5-turbo', label: 'GPT-3.5 Turbo' },
  ];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!apiKey.trim()) {
      setError('Please enter your OpenAI API key');
      return;
    }

    if (!apiKey.startsWith('sk-')) {
      setError('OpenAI API keys should start with "sk-"');
      return;
    }

    setError('');
    onSubmit(apiKey.trim(), selectedModel);
  };

  return (
    <div className="api-key-container">
      <div className="api-key-form">
        <h2>Welcome to Claire Playground</h2>
        <p>Enter your OpenAI API key to start chatting with Claire</p>
        
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="apiKey">OpenAI API Key:</label>
            <input
              type="password"
              id="apiKey"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="sk-..."
              className="api-key-input"
            />
          </div>

          <div className="form-group">
            <label htmlFor="model">Model:</label>
            <select
              id="model"
              value={selectedModel}
              onChange={(e) => setSelectedModel(e.target.value as OpenAIModel)}
              className="model-select"
            >
              {models.map((model) => (
                <option key={model.value} value={model.value}>
                  {model.label}
                </option>
              ))}
            </select>
          </div>

          {error && <div className="error-message">{error}</div>}

          <button type="submit" className="submit-button">
            Start Chatting
          </button>
        </form>

        <div className="info-box">
          <h3>Information:</h3>
          <ul>
            <li>Your API key is only stored in memory and will be lost when you refresh the page</li>
            <li>This playground uses OpenAI's completion API to simulate Claire's responses</li>
            <li>Dummy user context data is automatically provided for testing</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default ApiKeyInput; 