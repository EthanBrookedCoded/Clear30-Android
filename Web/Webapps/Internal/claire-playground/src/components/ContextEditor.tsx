import React, { useState, useEffect } from 'react';
import { UserContext } from '../types';

interface ContextEditorProps {
  systemPrompt: string;
  userContext: UserContext;
  onSystemPromptChange: (prompt: string) => void;
  onUserContextChange: (context: UserContext) => void;
  onClose: () => void;
}

const ContextEditor: React.FC<ContextEditorProps> = ({
  systemPrompt,
  userContext,
  onSystemPromptChange,
  onUserContextChange,
  onClose
}) => {
  const [localSystemPrompt, setLocalSystemPrompt] = useState(systemPrompt);
  const [localUserContext, setLocalUserContext] = useState<UserContext>(userContext);

  useEffect(() => {
    setLocalSystemPrompt(systemPrompt);
    setLocalUserContext(userContext);
  }, [systemPrompt, userContext]);

  const handleSave = () => {
    onSystemPromptChange(localSystemPrompt);
    onUserContextChange(localUserContext);
    onClose();
  };

  const handleUserContextChange = (field: keyof UserContext, value: string | number) => {
    setLocalUserContext(prev => ({
      ...prev,
      [field]: value
    }));
  };

  return (
    <div className="context-editor-overlay">
      <div className="context-editor">
        <div className="context-editor-header">
          <h2>Edit Context & Prompt</h2>
          <button onClick={onClose} className="close-button">×</button>
        </div>

        <div className="context-editor-content">
          <div className="section">
            <h3>System Prompt</h3>
            <textarea
              value={localSystemPrompt}
              onChange={(e) => setLocalSystemPrompt(e.target.value)}
              className="system-prompt-textarea"
              rows={15}
              placeholder="Enter the system prompt for Claire..."
            />
          </div>

          <div className="section">
            <h3>User Context</h3>
            <div className="user-context-form">
              <div className="form-group">
                <label>Name:</label>
                <input
                  type="text"
                  value={localUserContext.name}
                  onChange={(e) => handleUserContextChange('name', e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>Program Name:</label>
                <input
                  type="text"
                  value={localUserContext.programName}
                  onChange={(e) => handleUserContextChange('programName', e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>Current Day:</label>
                <input
                  type="number"
                  value={localUserContext.currentDay}
                  onChange={(e) => handleUserContextChange('currentDay', parseInt(e.target.value) || 0)}
                />
              </div>

              <div className="form-group">
                <label>Current Day Context:</label>
                <textarea
                  value={localUserContext.currentDayContext}
                  onChange={(e) => handleUserContextChange('currentDayContext', e.target.value)}
                  rows={4}
                />
              </div>

              <div className="form-group">
                <label>Assessment Responses:</label>
                <textarea
                  value={localUserContext.assessmentResponses || ''}
                  onChange={(e) => handleUserContextChange('assessmentResponses', e.target.value)}
                  rows={8}
                />
              </div>

              <div className="form-group">
                <label>Check-ins:</label>
                <textarea
                  value={localUserContext.checkIns || ''}
                  onChange={(e) => handleUserContextChange('checkIns', e.target.value)}
                  rows={6}
                />
              </div>

              <div className="form-group">
                <label>Last Smoked:</label>
                <input
                  type="text"
                  value={localUserContext.lastSmoked || ''}
                  onChange={(e) => handleUserContextChange('lastSmoked', e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>Current Date:</label>
                <input
                  type="text"
                  value={localUserContext.currentDate || ''}
                  onChange={(e) => handleUserContextChange('currentDate', e.target.value)}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="context-editor-footer">
          <button onClick={onClose} className="cancel-button">Cancel</button>
          <button onClick={handleSave} className="save-button">Save Changes</button>
        </div>
      </div>
    </div>
  );
};

export default ContextEditor; 