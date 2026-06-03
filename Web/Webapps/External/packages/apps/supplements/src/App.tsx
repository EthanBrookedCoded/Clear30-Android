import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { AppLayout } from '@clear30/shared/src/components/layout/AppLayout';
import { useLogging } from '@clear30/shared/src/hooks/useLogging';
import { PAGE_TRANSITIONS, ANIMATE_PRESENCE_PROPS } from '@clear30/shared/src/lib/animations';

import { IntroPage } from './pages/IntroPage';
import { SymptomChoicePage } from './pages/SymptomChoicePage';
import { LoadingPage } from './pages/LoadingPage';
import { RecommendedPage } from './pages/RecommendedPage';
import { SupplementDetailPage } from './pages/SupplementDetailPage';
import { SupplementProvider, useSupplementContext } from './context/SupplementContext';
import { PageType } from './types';

const AppContent: React.FC = () => {
  const { currentPage, setCurrentPage, selectedSupplement, selectedSymptoms, allTags } = useSupplementContext();
  const { logPageView } = useLogging();

  // Log page view on page change
  React.useEffect(() => {
    // Hanle extra data
    let extraData = {}
    if (currentPage === PageType.Detail) {
      extraData = {
        supplement_id: selectedSupplement?.id,
        supplement_name: selectedSupplement?.title
      }
    } else if (currentPage === PageType.Loading) {
      extraData = {
        symptom_ids: selectedSymptoms,
        symptoms_names: selectedSymptoms.map(symptom => allTags.find(tag => tag.id === symptom)?.name)
      }
    }

    logPageView(currentPage, extraData);
  }, [currentPage]);

  // Handle back button
  const handleBack = () => {
    switch (currentPage) {
      case PageType.Symptoms:
        setCurrentPage(PageType.Intro);
        break;
      case PageType.Loading:
        setCurrentPage(PageType.Symptoms);
        break;
      case PageType.Recommended:
        setCurrentPage(PageType.Symptoms);
        break;
      case PageType.Detail:
        setCurrentPage(PageType.Recommended);
        break;
      default:
        // No back action for intro page
        break;
    }
  };

  // Render appropriate page based on context state
  const renderCurrentPage = () => {
    switch (currentPage) {
      case PageType.Intro:
        return <IntroPage />;
      case PageType.Symptoms:
        return <SymptomChoicePage />;
      case PageType.Loading:
        return <LoadingPage />;
      case PageType.Recommended:
        return <RecommendedPage />;
      case PageType.Detail:
        return <SupplementDetailPage />;
      default:
        return <IntroPage />;
    }
  };

  return (
    <AppLayout
      pageName="Supplements"
      appName="Supplements"
      appTitle="Clear30 Supplements"
      onBack={currentPage !== PageType.Intro ? handleBack : undefined}
    >
      <AnimatePresence {...ANIMATE_PRESENCE_PROPS}>
        <motion.div
          key={currentPage}
          {...PAGE_TRANSITIONS.fade}
          className="h-full w-full"
        >
          {renderCurrentPage()}
        </motion.div>
      </AnimatePresence>
    </AppLayout>
  );
};

function App() {
  return (
    <SupplementProvider>
      <AppContent />
    </SupplementProvider>
  );
}

export default App;