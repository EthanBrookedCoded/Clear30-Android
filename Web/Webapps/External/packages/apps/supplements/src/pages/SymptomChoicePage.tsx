import React from 'react';
import { TextIconButton } from '@clear30/shared/src/components/ui/TextIconButton';
import { TextSizes } from '@clear30/shared/src/components/ui/TextSizes';
import { VStack } from '@clear30/shared/src/components/layout/VStack';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import { ArrowRight } from 'react-feather';
import { ScrollView } from '@clear30/shared/src/components/ui/ScrollView';
import { ScrollViewContainer } from '@clear30/shared/src/components/ui/ScrollViewContainer';
import { useSupplementContext } from '../context/SupplementContext';
import { SymptomCard } from '../components/SymptomCard';
import { PageType } from '../types';

export const SymptomChoicePage: React.FC = () => {
  const { allTags, selectedSymptoms, setSelectedSymptoms, setCurrentPage } = useSupplementContext();

  // For selecting symmptoms
  const toggleSymptom = (id: number) => {
    const newSymptoms = selectedSymptoms.includes(id)
      ? selectedSymptoms.filter((s: number) => s !== id)
      : [...selectedSymptoms, id];
    setSelectedSymptoms(newSymptoms);
  };

  return (
    <ScrollViewContainer>

      {/* Header */}
      <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} className="w-full" style={{ paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
        <TextSizes.Heading3>What are you struggling with?</TextSizes.Heading3>
        <TextSizes.Small style={{ opacity: 0.5 }} >Let's find your fit.</TextSizes.Small>
      </VStack>

      {/* Scrollable Symptom List */}
      <ScrollView style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
        <VStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="leading" className="w-full">
          {allTags.map(symptom => {
            const isSelected = selectedSymptoms.includes(symptom.id);
            return (
              <SymptomCard
                key={symptom.id}
                symptom={symptom}
                variant="selectable"
                isSelected={isSelected}
                onToggle={toggleSymptom}
              />
            );
          })}
        </VStack>
      </ScrollView>

      {/* Action Buttons */}
      <div className="w-full" style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
        <TextIconButton
          text="Get Suggestions"
          icon={ArrowRight}
          action={() => setCurrentPage(PageType.Loading)}
          gradient={CLEAR30_CONSTANTS.gradients.clear30}
          disabled={selectedSymptoms.length === 0}
        />
      </div>
    </ScrollViewContainer>
  );
};
