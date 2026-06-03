import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TextSizes } from '@clear30/shared/src/components/ui/TextSizes';
import { VStack } from '@clear30/shared/src/components/layout/VStack';
import { HStack } from '@clear30/shared/src/components/layout/HStack';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import { UI_ANIMATIONS } from '@clear30/shared/src/lib/animations';
import { DynamicIcon } from '@clear30/shared/src/components/ui/DynamicIcon';
import { Card } from '@clear30/shared/src/components/ui/Card';
import { TextIconButton } from '@clear30/shared/src/components/ui/TextIconButton';
import { ScrollViewContainer } from '@clear30/shared/src/components/ui/ScrollViewContainer';
import { ScrollView } from '@clear30/shared/src/components/ui/ScrollView';
import { useLogging } from '@clear30/shared/src/hooks/useLogging';

import { ShoppingCart } from 'react-feather';
import { SymptomCard } from '../components/SymptomCard';
import { useSupplementContext } from '../context/SupplementContext';
import { PageType } from '../types';

export const SupplementDetailPage: React.FC = () => {

    const { selectedSupplement, allTags, setCurrentPage } = useSupplementContext();
    const { logButtonClick } = useLogging();
    const [instructionsExpanded, setInstructionsExpanded] = useState(false);

    // Get matching symptoms for this supplement
    const matchingSymptoms = selectedSupplement ? allTags.filter(symptom =>
        selectedSupplement.tag_names.includes(symptom.name)
    ) : [];

    // If no supplement selected, redirect to recommended page
    useEffect(() => {
        if (!selectedSupplement) {
            setCurrentPage(PageType.Recommended);
        }
    }, []);

    if (!selectedSupplement) {
        return (
            <ScrollViewContainer>
                <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="center" className="w-full">
                    <TextSizes.Small>No supplement selected</TextSizes.Small>
                    <TextSizes.Tiny style={{ opacity: 0.5 }}>Please select a supplement to view details</TextSizes.Tiny>
                </VStack>
            </ScrollViewContainer>
        );
    }

    return (
        <ScrollViewContainer>

            {/* Header */}
            <VStack spacing={CLEAR30_CONSTANTS.spacing.card} className="w-full">
                <TextSizes.Heading3>{selectedSupplement.title}</TextSizes.Heading3>
            </VStack>

            {/* Scrollable Content */}
            <ScrollView style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>

                {/* Symptom */}
                <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} style={{ paddingBottom: `${2 * CLEAR30_CONSTANTS.spacing.card}px` }} className="w-full">

                    <TextSizes.Small style={{ opacity: 0.5 }}>Will help you with</TextSizes.Small>

                    {matchingSymptoms.map(symptom => (
                        <SymptomCard
                            key={symptom.id}
                            symptom={symptom}
                            variant="selectable"
                            isSelected={true}
                        />
                    ))}
                </VStack>

                {/* Evidence & Research */}
                <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} style={{ paddingBottom: `${2 * CLEAR30_CONSTANTS.spacing.card}px` }} className="w-full">

                    <TextSizes.Small style={{ opacity: 0.5 }}>Evidence & Research</TextSizes.Small>

                    <Card outlineGradient={CLEAR30_CONSTANTS.gradients.clear30} outlineOpacity={0.5} className="w-full">
                        <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} alignment="leading">

                            <VStack style={{ padding: `${CLEAR30_CONSTANTS.spacing.card}px`, position: 'relative' }}>
                                <TextSizes.Heading1 style={{ opacity: 0.5, position: 'absolute', top: 0, left: 0 }}>"</TextSizes.Heading1>
                                <TextSizes.Small>{selectedSupplement.heading}</TextSizes.Small>
                                <TextSizes.Tiny style={{ opacity: 0.5 }}>{selectedSupplement.subheading}</TextSizes.Tiny>
                            </VStack>
                        </VStack>
                    </Card>

                    {/* View Product Button */}
                    <TextIconButton
                        text="View Product"
                        icon={ShoppingCart}
                        action={() => {
                            logButtonClick('View Product', {
                                supplementId: selectedSupplement.id,
                                supplementTitle: selectedSupplement.title,
                                supplementAmazonLink: selectedSupplement.amazon_link,
                            });
                            window.open(selectedSupplement.amazon_link, '_blank');
                        }}
                        gradient={CLEAR30_CONSTANTS.gradients.clear30}
                    />
                </VStack>

                {/* Additional Information */}
                <VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2} style={{ paddingBottom: `${2 * CLEAR30_CONSTANTS.spacing.card}px` }} className="w-full">

                    <TextSizes.Small style={{ opacity: 0.5 }}>Additional Information</TextSizes.Small>

                    {/* Proof Card */}
                    <Card className="w-full">
                        <VStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="leading">
                            {/* Proof points */}
                            {selectedSupplement.proof && selectedSupplement.proof.map((proof, index) => (
                                <HStack key={index} spacing={CLEAR30_CONSTANTS.spacing.card} alignment="top">
                                    <DynamicIcon iconName={index === 0 ? `BarChart` : `Check`} size={40} className="opacity-50" />
                                    <TextSizes.Small>
                                        {proof.text}
                                        {proof.link && (
                                            <TextSizes.Tiny style={{ opacity: 0.5 }}>
                                                <a
                                                    href={proof.link}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="underline"
                                                >
                                                    Citation
                                                </a>
                                            </TextSizes.Tiny>
                                        )}
                                    </TextSizes.Small>
                                </HStack>
                            ))}

                            {/* Contradiction/Caution with hazard symbol */}
                            {selectedSupplement.caution && (
                                <HStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="top">
                                    <DynamicIcon iconName="AlertTriangle" size={40} className="opacity-50" />
                                    <TextSizes.Small>{selectedSupplement.caution}</TextSizes.Small>
                                </HStack>
                            )}
                        </VStack>
                    </Card>

                    {/* Instructions Section */}
                    <Card className="w-full">
                        <button
                            onClick={() => setInstructionsExpanded(!instructionsExpanded)}
                            className="w-full"
                        >
                            <HStack className="w-full justify-between items-center">
                                <TextSizes.Small>Instructions</TextSizes.Small>
                                {instructionsExpanded ? (
                                    <DynamicIcon iconName="ChevronUp" size={20} />
                                ) : (
                                    <DynamicIcon iconName="ChevronDown" size={20} />
                                )}
                            </HStack>
                        </button>

                        <AnimatePresence>
                            {instructionsExpanded && (
                                <motion.div
                                    {...UI_ANIMATIONS.instructions}
                                    style={{ overflow: "hidden" }}
                                >
                                    <VStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="leading" style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
                                        {selectedSupplement.instructions.map((instruction, index) => (
                                            <TextSizes.Small key={index}>{instruction}</TextSizes.Small>
                                        ))}
                                    </VStack>
                                </motion.div>
                            )}
                        </AnimatePresence>
                    </Card>
                </VStack>
            </ScrollView>
        </ScrollViewContainer>
    );
};
