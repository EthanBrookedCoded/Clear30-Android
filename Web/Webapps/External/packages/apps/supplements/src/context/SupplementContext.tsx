import React, { createContext, useContext, useState, useEffect, useMemo, type ReactNode } from 'react';
import { PageType } from '../types';
import type { Supplement, Symptom, AppState } from '../types';

// Types for the context
interface SupplementContextType {

  // Data state
  allTags: Symptom[];
  allSupplements: Supplement[];
  selectedSymptoms: number[];
  filteredSupplements: Supplement[];
  selectedSupplement: Supplement | null;
  currentPage: PageType;
  isDataLoaded: boolean;

  // Setters
  setAllTags: (tags: Symptom[]) => void;
  setAllSupplements: (supplements: Supplement[]) => void;
  setSelectedSymptoms: (symptoms: number[]) => void;
  setSelectedSupplement: (supplement: Supplement | null) => void;
  setCurrentPage: (page: PageType) => void;

  // Methods
  filterSupplements: () => void;
  resetApp: () => void;
  clearSelectedSymptoms: () => void;
  hasSelectedSymptoms: boolean;
}

// Create the context
const SupplementContext = createContext<SupplementContextType | undefined>(undefined);

// Provider component
interface SupplementProviderProps {
  children: ReactNode;
}

export const SupplementProvider: React.FC<SupplementProviderProps> = ({ children }) => {

  // Initializer, everything is stored in an App State object (from localStorage if available)
  const [appState, setAppState] = useState<AppState>(() => {
    try {
      const saved = localStorage.getItem('supplementAppState');

      if (saved) {
        const parsedState = JSON.parse(saved) as AppState;
        return {
          ...parsedState,
          isDataLoaded: false,
        };
      }
      return {
        allTags: [],
        allSupplements: [],
        selectedSymptoms: [],
        filteredSupplements: [],
        selectedSupplement: null,
        currentPage: PageType.Intro,
        isDataLoaded: false,
      };
    } catch (err) {
      console.error('Error loading app state from localStorage:', err);
      return {
        allTags: [],
        allSupplements: [],
        selectedSymptoms: [],
        filteredSupplements: [],
        selectedSupplement: null,
        currentPage: PageType.Intro,
        isDataLoaded: false,
      };
    }
  });

  // Computed values
  const isDataLoaded = useMemo(() => {
    return appState.allTags.length > 0 && appState.allSupplements.length > 0;
  }, [appState.allTags.length, appState.allSupplements.length]);

  const hasSelectedSymptoms = useMemo(() => {
    return appState.selectedSymptoms.length > 0;
  }, [appState.selectedSymptoms.length]);

  // Save to localStorage whenever relevant state changes
  useEffect(() => {
    // Store entire app state (excluding computed values)
    const stateToSave = {
      ...appState,
      isDataLoaded: undefined
    };
    localStorage.setItem('supplementAppState', JSON.stringify(stateToSave));
  }, [appState.selectedSymptoms, appState.selectedSupplement, appState.currentPage, appState.allTags, appState.allSupplements, appState.filteredSupplements]);

  // Setters (sets app state with new info)
  const setAllTags = (tags: Symptom[]) => {
    setAppState(prev => ({ ...prev, allTags: tags }));
  };

  const setAllSupplements = (supplements: Supplement[]) => {
    setAppState(prev => ({ ...prev, allSupplements: supplements }));
  };

  const setSelectedSymptoms = (symptoms: number[]) => {
    setAppState(prev => ({ ...prev, selectedSymptoms: symptoms }));
  };

  const setSelectedSupplement = (supplement: Supplement | null) => {
    setAppState(prev => ({ ...prev, selectedSupplement: supplement }));
  };

  const setCurrentPage = (page: PageType) => {
    setAppState(prev => ({ ...prev, currentPage: page }));
  };

  // Reset app state to initial values
  const resetApp = () => {
    const resetState: AppState = {
      allTags: [],
      allSupplements: [],
      selectedSymptoms: [],
      filteredSupplements: [],
      selectedSupplement: null,
      currentPage: PageType.Intro,
      isDataLoaded: false,
    };
    setAppState(resetState);
    localStorage.removeItem('supplementAppState');
  };

  // Clear selected symptoms
  const clearSelectedSymptoms = () => {
    setAppState(prev => ({ ...prev, selectedSymptoms: [] }));
  };

  // Filter supplements whenever selected symptoms or data changes
  useEffect(() => {
    if (appState.allSupplements.length > 0 && appState.allTags.length > 0) {
      filterSupplements();
    }
  }, [appState.selectedSymptoms, appState.allSupplements, appState.allTags]);

  // Filter supplements based on selected symptoms (and save to app state)
  // Called when selected symptoms or all tags/supplements change
  const filterSupplements = () => {
    if (appState.selectedSymptoms.length === 0) {
      setAppState(prev => ({ ...prev, filteredSupplements: [] }));
      return;
    }

    // Get selected symptom names
    const selectedSymptomNames = appState.allTags
      .filter(tag => appState.selectedSymptoms.includes(tag.id))
      .map(tag => tag.name);

    // Filter supplements that have at least one matching tag
    const filtered = appState.allSupplements.filter(supplement =>
      supplement.tag_names.some(tagName =>
        selectedSymptomNames.includes(tagName)
      )
    );

    setAppState(prev => ({ ...prev, filteredSupplements: filtered }));
  };

  // Return the context value
  const value: SupplementContextType = {
    // State
    allTags: appState.allTags,
    allSupplements: appState.allSupplements,
    selectedSymptoms: appState.selectedSymptoms,
    filteredSupplements: appState.filteredSupplements,
    selectedSupplement: appState.selectedSupplement,
    currentPage: appState.currentPage,
    isDataLoaded,

    // Methods
    setAllTags,
    setAllSupplements,
    setSelectedSymptoms,
    setSelectedSupplement,
    setCurrentPage,
    filterSupplements,
    resetApp,
    clearSelectedSymptoms,
    hasSelectedSymptoms,
  };

  return (
    <SupplementContext.Provider value={value}>
      {children}
    </SupplementContext.Provider>
  );
};

// Custom hook to use the context
export const useSupplementContext = (): SupplementContextType => {
  const context = useContext(SupplementContext);
  if (context === undefined) {
    throw new Error('useSupplementContext must be used within a SupplementProvider');
  }
  return context;
};
