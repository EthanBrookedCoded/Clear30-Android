export interface Creator {
  id: string;
  instagram_handle?: string | null;
  tiktok_handle?: string | null;
  instagram_url?: string | null;
  tiktok_url?: string | null;
  full_name?: string | null;
  status: string;
  starred: boolean;
  email?: string | null;
  created_at: string;
  added_by?: { id: string; full_name: string } | null;
  instagram_outreach_account?: {
    id: string;
    handle: string;
    display_name: string;
  } | null;
  tiktok_outreach_account?: {
    id: string;
    handle: string;
    display_name: string;
  } | null;
}

export interface OutreachAccount {
  id: string;
  platform: string;
  handle: string;
  display_name: string;
}

export type CreatorStatus =
  | "sent"
  | "replied"
  | "maybe_follow_up"
  | "followup"
  | "declined"
  | "sent_more_info"
  | "call_scheduled"
  | "contract_sent"
  | "guidelines_sent"
  | "references_sent"
  | "active"
  | "delayed"
  | "loose_ends";
