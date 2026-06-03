
// Page type constants for navigation
export const PageType = {
  Intro: 'intro',
  Symptoms: 'symptoms',
  Loading: 'loading',
  Recommended: 'recommended',
  Detail: 'detail'
} as const;

export type PageType = typeof PageType[keyof typeof PageType];

// Supplement interface based on database structure
export interface Supplement {
  id: number;
  title: string;
  heading: string;
  subheading: string;
  tag_names: string[];
  instructions: string[];
  caution: string;
  amazon_link: string;
  proof?: Array<{
    link: string;
    text: string;
  }>;
  created_at: string;
  updated_at: string;
}

// Symptom interface based on database structure
export interface Symptom {
  id: number;
  name: string;
  headline: string;
  color: string;
  icon: string;
}

// App state interface for context
export interface AppState {
  // From Supabase
  allTags: Symptom[];
  allSupplements: Supplement[];

  // User selected
  selectedSymptoms: number[];
  filteredSupplements: Supplement[];
  selectedSupplement: Supplement | null;

  // App state
  currentPage: PageType;
  isDataLoaded: boolean;
}
