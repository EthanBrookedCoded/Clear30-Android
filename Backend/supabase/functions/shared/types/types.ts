export interface ChatResponse {
  status: number;
  message: string;
  data: ReadableStream<Uint8Array> | null;
}

export interface ThreadResponse {
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

export interface UserContext {
  name: string;
  assessmentResponses: string;
  programName: string;
  currentDay: number;
  currentDayContext: string;
  checkIns: string;
  lastSmoked: string;
  currentDate: string;


  // Individual assessment responses
  // completedDays: number;
  // loAge?: string;
  // trigger?: string;
  // helpHarm?: string;
  // thenWhat?: string;
  // commitment?: string;
  // daysUsing?: string;
  // breakReason?: string;
  // previousBreak?: string;
  // consumptionMethod?: string;
}

export interface ThreadMessageListParams {
  limit?: number;
  order?: 'asc' | 'desc';
  after?: string;
  before?: string;
}

export interface ThreadMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: number;
}

export interface User {
  id: string;
  aud: string;
  role: string;
  email: string;
  email_confirmed_at: string;
  phone: string;
  confirmation_sent_at: string;
  confirmed_at: string;
  recovery_sent_at: string;
  last_sign_in_at: string;
  app_metadata: AppMetadata;
  user_metadata: UserMetadata;
  identities: Identity[];
  created_at: string;
  updated_at: string;
  is_anonymous: boolean;
}

export interface AppMetadata {
  provider: string;
  providers: string[];
}

export interface UserMetadata {
  email: string;
  email_verified: boolean;
  phone_verified: boolean;
  sub: string;
}

export interface Identity {
  identity_id: string;
  id: string;
  user_id: string;
  identity_data: IdentityData;
  provider: string;
  last_sign_in_at: string;
  created_at: string;
  updated_at: string;
  email: string;
}

export interface IdentityData {
  email: string;
  email_verified: boolean;
  phone_verified: boolean;
  sub: string;
}

// Re-export program types from programUtils for convenience
export type { ProgramBreak, ProgramInfo } from '../utils/programUtils.ts'

/**
 * User record with timezone field (from public.users table)
 * Used by edge functions that need timezone-aware calculations
 */
export interface UserWithTimezone {
  id: string
  timezone: string | null
  program_breaks: ProgramBreak[] | null
  day_info: unknown[]
  created_at: string
  name?: string
}

/**
 * Day data structure stored in day_info array
 * day_info alternates between YYYY-MM-DD strings and DayData objects
 */
export interface DayData {
  sober: boolean
  loggedCheckIns?: { id: string; completion: boolean }[]
}

// Import type for re-export
import type { ProgramBreak } from '../utils/programUtils.ts'