import React, { useEffect, useState } from 'react';
import { Card } from '@clear30/shared/src/components/ui/Card';
import { TextIconButton } from '@clear30/shared/src/components/ui/TextIconButton';
import { TextSizes } from '@clear30/shared/src/components/ui/TextSizes';
import { VStack } from '@clear30/shared/src/components/layout/VStack';
import { HStack } from '@clear30/shared/src/components/layout/HStack';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import { ChevronRight, Info } from 'react-feather';
import { useSupplementContext } from '../context/SupplementContext';
import { ScrollView } from '@clear30/shared/src/components/ui/ScrollView';
import { ScrollViewContainer } from '@clear30/shared/src/components/ui/ScrollViewContainer';
import { DynamicIcon } from '@clear30/shared/src/components/ui/DynamicIcon';
import { BottomSheet } from '@clear30/shared/src/components/ui/BottomSheet';
import { Markdown } from '@clear30/shared/src/components/ui/Markdown';
import { PageType } from '../types';
import { gradientFromHex } from '../utils/utils';
import {
  generateSupplementRecommendations,
  generateGeneralSupplementRecommendations,
  type ScoredSupplement,
  type GeneralSupplement
} from '../utils/supplementSelection';

export const RecommendedPage: React.FC = () => {
  const {
    selectedSymptoms,
    filteredSupplements,
    allSupplements,
    allTags,
    setCurrentPage,
    setSelectedSupplement
  } = useSupplementContext();
  const [recommendations, setRecommendations] = useState<ScoredSupplement[]>([]);
  const [generalSupplements, setGeneralSupplements] = useState<GeneralSupplement[]>([]);
  const [isGeneralInfoOpen, setIsGeneralInfoOpen] = useState(false);

  // Process recommendations from context data
  useEffect(() => {
    const processRecommendations = () => {
      // If no symptoms selected, redirect to symptoms page
      if (selectedSymptoms.length === 0) {
        setCurrentPage(PageType.Symptoms);
        return;
      }

      // Go to intro if no data
      if (filteredSupplements.length === 0 || allTags.length === 0) {
        setCurrentPage(PageType.Intro);
        return;
      }

      // Generate symptom-based recommendations
      const newRecommendations = generateSupplementRecommendations(
        filteredSupplements,
        allTags,
        selectedSymptoms
      );
      setRecommendations(newRecommendations);

      // Track used supplement IDs to avoid duplicates in general section
      const usedSupplementIds = new Set(newRecommendations.map(r => r.id));

      // Generate general supplement recommendations using ALL supplements
      const newGeneralSupplements = generateGeneralSupplementRecommendations(
        allSupplements,
        usedSupplementIds
      );
      setGeneralSupplements(newGeneralSupplements);
    };

    processRecommendations();
  }, []);

  const handleGeneralInfo = () => {
    setIsGeneralInfoOpen(true);
  };

  const handleCloseGeneralInfo = () => {
    setIsGeneralInfoOpen(false);
  };

  const generalInfoContent = () => {
    const markdownContent = `
# **Final Summary Tables**

### **Morning (Focus · Energy · Mood · Craving Control)**

| Supplement | Dose | Primary Use | Key Withdrawal Symptoms |
| ----- | ----- | ----- | ----- |
| **NAC** | 600–1200 mg | Craving control (glutamate balance) | Cravings, irritability, focus |
| **Omega-3 (EPA+DHA)** | 1–2 g | Mood/brain support | Low mood, irritability |
| **L-Theanine** | 100–200 mg (± coffee) | Calm focus | Anxiety, racing thoughts, calm |
| **Vitamin-B Complex** | 1 serving | Energy metabolism | Fatigue, brain fog |
| **Citicoline (optional)** | 250–500 mg | Attention/impulse control | Brain fog, focus, distractibility |
|  **Ginger** | 250–500 mg  |  Nausea, digestion |  Nausea, digestion, stomach |
| **L-Tyrosine (optional)** | 500 mg | Alertness under stress | Low motivation, fatigue |

**Before your usual smoke time (30–60 min prior):** add **NAC 600 mg** \+ **L-Theanine 200 mg**. 

---

### **Evening & Night (Sleep · Calm · Craving Suppression)**

| Supplement | Dose | Timing | Primary Use | Key Withdrawal Symptoms |
| ----- | ----- | ----- | ----- | ----- |
| **Magnesium glycinate** | 200–300 mg | Evening | Relaxation, sleep quality | Tension, insomnia |
| **Passionflower**  | 250–500 mg *(or 300–600 mg)* | Evening | Calm / sleep depth | Anxiety, restlessness |
| **Melatonin** | 1–3 mg | Night (30–60 min pre-bed) | Sleep onset / REM rebound | Insomnia, vivid dreams |
| **NAC (optional)** | 600 mg | Early evening if cravings | Craving control | Evening cravings |

---

## **Safety quick-checks**

* **Do not stack multiple sedatives** (CBD \+ passionflower \+ valerian \+ alcohol/benzos).

* **Kidney disease:** talk to your clinician before **magnesium**.

* **Warfarin/anticoagulants:** extra caution with **melatonin** and high-dose **omega-3**; monitor INR.

* **Fluvoxamine:** avoid with **melatonin** (huge level increase).

* **Nitroglycerin:** avoid with **NAC** unless supervised.

    `;

    return (
      <ScrollViewContainer>
        <ScrollView style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
          <Markdown>{markdownContent}</Markdown>
        </ScrollView>
      </ScrollViewContainer>
    );
  };

  return (
    <ScrollViewContainer>

      {/* Header */}
      <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="leading" className="w-full">
        <TextSizes.Heading3>Your Supplements</TextSizes.Heading3>
        <TextSizes.Small style={{ opacity: 0.5 }}>Based on your selected symptoms, here are our recommended supplements.</TextSizes.Small>
      </VStack>

      {/* Scrollable Recommendations List */}
      <ScrollView style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }} showScrollIndicator={true}>
        <VStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="leading" className="w-full">
          {recommendations.length > 0 ? (
            recommendations.map(supplement => (
              <Card
                key={supplement.id}
                gradient={gradientFromHex(supplement.primarySymptom.color)}
                className="w-full"
              >
                <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="leading">

                  {/* Header with title and subtitle */}
                  <HStack alignment="top" className="w-full justify-between">
                    <VStack className="w-full" spacing={CLEAR30_CONSTANTS.spacing.card / 2}>
                      <TextSizes.Heading3>{supplement.title}</TextSizes.Heading3>
                      <TextSizes.Small>
                        {supplement.primarySymptom.headline}
                      </TextSizes.Small>
                    </VStack>

                    <HStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="center">
                      <Card
                        color="rgba(255, 255, 255, 0.25)"
                        padding={false}
                        shadowColor="transparent"
                      >
                        <HStack spacing={CLEAR30_CONSTANTS.spacing.card / 4} alignment="center" style={{
                          paddingLeft: `${CLEAR30_CONSTANTS.spacing.card / 2}px`,
                          paddingRight: `${CLEAR30_CONSTANTS.spacing.card / 2}px`,
                          paddingTop: `${CLEAR30_CONSTANTS.spacing.card / 3}px`,
                          paddingBottom: `${CLEAR30_CONSTANTS.spacing.card / 3}px`,
                          opacity: 0.75
                        }}>
                          <TextSizes.Tiny style={{ color: 'white', opacity: 1.0, whiteSpace: 'nowrap' }}>
                            {supplement.primarySymptom.name}
                          </TextSizes.Tiny>
                          <DynamicIcon
                            iconName={supplement.primarySymptom.icon}
                            size={16}
                            className="text-white"
                          />
                        </HStack>
                      </Card>
                    </HStack>
                  </HStack>

                  {/* Main description text */}
                  <TextSizes.Small style={{ opacity: 0.5, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card / 2}px` }}>
                    {supplement.heading}
                  </TextSizes.Small>

                  {/* Action button */}
                  <TextIconButton
                    text="Learn More"
                    icon={ChevronRight}
                    action={() => {
                      setSelectedSupplement(supplement);
                      setCurrentPage(PageType.Detail);
                    }}
                  />
                </VStack>
              </Card>
            ))
          ) : (
            <VStack alignment="center" spacing={CLEAR30_CONSTANTS.spacing.card / 2}>
              <TextSizes.Small>
                No supplements found for your symptoms.
              </TextSizes.Small>
              <TextSizes.Tiny style={{ opacity: 0.5 }}>
                Try selecting different symptoms or browse all supplements.
              </TextSizes.Tiny>
            </VStack>
          )}

          {/* General Supplements Section */}
          {generalSupplements.length > 0 && (
            <>
              {/* Section Header */}
              <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="leading" style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
                <TextSizes.Small style={{ opacity: 0.5 }}>General Wellness</TextSizes.Small>
              </VStack>

              {/* General Supplement Cards */}
              {generalSupplements.map(supplement => (
                <Card
                  key={supplement.id}
                  className="w-full"
                >
                  <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="leading">

                    {/* Header with title and subtitle */}
                    <HStack alignment="top" className="w-full justify-between">
                      <VStack className="w-full" spacing={CLEAR30_CONSTANTS.spacing.card / 2}>
                        <TextSizes.Heading3>{supplement.title}</TextSizes.Heading3>
                      </VStack>

                      <HStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="center">
                        <Card
                          color="rgba(0, 0, 0, 0.1)"
                          padding={false}
                          shadowColor="transparent"
                        >
                          <HStack spacing={CLEAR30_CONSTANTS.spacing.card / 4} alignment="center" style={{
                            paddingLeft: `${CLEAR30_CONSTANTS.spacing.card / 2}px`,
                            paddingRight: `${CLEAR30_CONSTANTS.spacing.card / 2}px`,
                            paddingTop: `${CLEAR30_CONSTANTS.spacing.card / 3}px`,
                            paddingBottom: `${CLEAR30_CONSTANTS.spacing.card / 3}px`,
                            opacity: 0.75
                          }}>
                            <TextSizes.Tiny style={{ color: 'black', opacity: 1.0, whiteSpace: 'nowrap' }}>
                              General
                            </TextSizes.Tiny>
                            <DynamicIcon
                              iconName="UserCheck"
                              size={16}
                              className="text-black"
                            />
                          </HStack>
                        </Card>
                      </HStack>
                    </HStack>

                    {/* Main description text */}
                    <TextSizes.Small style={{ opacity: 0.5, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
                      {supplement.heading}
                    </TextSizes.Small>

                    {/* Action button */}
                    <TextIconButton
                      text="Learn More"
                      icon={ChevronRight}
                      action={() => {
                        setSelectedSupplement(supplement);
                        setCurrentPage(PageType.Detail);
                      }}
                    />
                  </VStack>
                </Card>
              ))}
            </>
          )}

          {/* General Information Button */}
          <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="center" className="w-full" style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
            <TextIconButton
              text="General Information"
              icon={Info}
              action={handleGeneralInfo}
            />
          </VStack>
        </VStack>
      </ScrollView>

      {/* General Information BottomSheet */}
      <BottomSheet
        isOpen={isGeneralInfoOpen}
        onClose={handleCloseGeneralInfo}
        title="General Information"
        maxHeight="100dvh"
        className="max-h-screen"
      >
        {generalInfoContent()}
      </BottomSheet>
    </ScrollViewContainer>
  );
};
