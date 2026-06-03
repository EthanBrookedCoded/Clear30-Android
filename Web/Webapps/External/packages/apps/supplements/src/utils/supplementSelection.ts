/**
 * SUPPLEMENT SELECTION ALGORITHM
 * 
 * This algorithm creates personalized supplement recommendations by matching user-selected 
 * symptoms with available supplements, ensuring optimal coverage with minimal redundancy.
 * 
 * ALGORITHM OVERVIEW:
 * 1. One-to-One Matching: Each symptom gets exactly one supplement
 * 2. Smart Scoring: Supplements are scored based on how many user symptoms they address
 * 3. Optimal Selection: Highest-scoring supplements are chosen for each symptom
 * 4. Deduplication: No supplement appears more than once across recommendations
 * 5. General Supplements: Additional general wellness supplements added at the bottom
 * 
 * SCORING SYSTEM:
 * - Score: Number of user-selected symptoms that match the supplement's tags
 * - Match Percentage: (Score / Total Selected Symptoms) * 100
 * - Higher scores indicate supplements that address multiple user symptoms
 * - This prioritizes "multi-purpose" supplements for better overall coverage
 * 
 * EXAMPLE:
 * User selects: ["Anxiety", "Sleep Issues"]
 * Supplement A: ["Anxiety", "Sleep Issues", "Stress"] → Score: 2 (100% match)
 * Supplement B: ["Anxiety", "Depression"] → Score: 1 (50% match)
 * Supplement C: ["Sleep Issues", "Insomnia"] → Score: 1 (50% match)
 * 
 * Result: Supplement A for Anxiety (covers both), Supplement C for Sleep Issues
 * This gives optimal coverage (2 supplements) instead of 3 separate ones.
 * 
 * PERFORMANCE: O(n²) where n = number of supplements, but practical for typical counts
 * MEMORY: O(n) for tracking used supplements and building recommendations
 */

import type { Supplement, Symptom } from '../types';

// Hardcoded list of supplement IDs that should be recommended to all users
const GENERAL_SUPPLEMENT_IDS = [
  1 // NAC
];

// Scored supplement interface for recommendations
export interface ScoredSupplement extends Supplement {
  score: number;
  matchPercentage: number;
  matchingTags: string[];
  primarySymptom: Symptom;
}

// General supplement interface for general recommendations
export interface GeneralSupplement extends Supplement {
  isGeneral: true;
}

/**
 * Generates supplement recommendations based on selected symptoms
 * @param allSupplements - All available supplements
 * @param allSymptoms - All available symptoms
 * @param selectedSymptoms - Array of selected symptom IDs
 * @returns Array of scored supplements with recommendations
 */
export const generateSupplementRecommendations = (
  allSupplements: Supplement[],
  allSymptoms: Symptom[],
  selectedSymptoms: number[]
): ScoredSupplement[] => {
  // Get the selected symptoms with their data
  const selectedSymptomData = allSymptoms.filter(symptom =>
    selectedSymptoms.includes(symptom.id)
  );

  // Track used supplements to avoid duplicates
  const usedSupplementIds = new Set<number>();
  const recommendations: ScoredSupplement[] = [];

  selectedSymptomData.forEach(symptom => {
    // Find supplements that match this symptom and haven't been used yet
    const availableSupplements = allSupplements.filter(supplement =>
      supplement.tag_names.includes(symptom.name) &&
      !usedSupplementIds.has(supplement.id)
    );

    if (availableSupplements.length > 0) {
      // Score supplements for this symptom and pick the best one
      const scoredForSymptom = availableSupplements.map(supplement => {
        const matchingTags = supplement.tag_names.filter(tag =>
          selectedSymptoms.some(symptomId =>
            allSymptoms.find(s => s.id === symptomId)?.name === tag
          )
        );

        return {
          ...supplement,
          score: matchingTags.length,
          matchPercentage: (matchingTags.length / selectedSymptoms.length) * 100,
          matchingTags,
          primarySymptom: symptom // Track which symptom this supplement is primarily for
        };
      });

      // Pick the supplement with the highest score for this symptom
      const bestSupplement = scoredForSymptom.sort((a, b) => b.score - a.score)[0];

      // Mark this supplement as used and add to recommendations
      usedSupplementIds.add(bestSupplement.id);
      recommendations.push(bestSupplement);
    }
  });

  return recommendations;
};

/**
 * Generates general supplement recommendations
 * @param allSupplements - All available supplements
 * @param usedSupplementIds - Set of supplement IDs already recommended
 * @returns Array of general supplements
 */
export const generateGeneralSupplementRecommendations = (
  allSupplements: Supplement[],
  usedSupplementIds: Set<number>
): GeneralSupplement[] => {
  return allSupplements
    .filter(supplement => 
      GENERAL_SUPPLEMENT_IDS.includes(supplement.id) && 
      !usedSupplementIds.has(supplement.id)
    )
    .map(supplement => ({
      ...supplement,
      isGeneral: true
    }));
};
