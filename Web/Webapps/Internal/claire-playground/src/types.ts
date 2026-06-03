export type OpenAIModel = 
  | 'gpt-4' 
  | 'gpt-4-turbo' 
  | 'gpt-4o' 
  | 'gpt-4o-mini'
  | 'gpt-3.5-turbo';

export interface Message {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
}

export interface UserContext {
  name: string;
  programName: string;
  currentDay: number;
  currentDayContext: string;
  assessmentResponses?: string;
  checkIns?: string;
  lastSmoked?: string;
  currentDate?: string;
}

export interface ChatState {
  messages: Message[];
  isLoading: boolean;
  error: string | null;
} 