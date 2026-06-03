import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TextSizes } from '@clear30/shared/src/components/ui/TextSizes';
import { VStack } from '@clear30/shared/src/components/layout/VStack';
import { HStack } from '@clear30/shared/src/components/layout/HStack';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import { LOADING_ANIMATIONS } from '@clear30/shared/src/lib/animations';
import { DynamicIcon } from '@clear30/shared/src/components/ui/DynamicIcon';
import { Card } from '@clear30/shared/src/components/ui/Card';
import { useSupplementContext } from '../context/SupplementContext';
import { PageType } from '../types';

export const LoadingPage: React.FC = () => {

    const { setCurrentPage } = useSupplementContext();
    const [loadingText, setLoadingText] = useState('Analyzing your symptoms...');
    const [currentStep, setCurrentStep] = useState(-1);

    // Loading steps with different messages
    const loadingSteps = [
        'Analyzing your symptoms...',
        'Removing irrelevant supplements...',
        'Finding best fit...',
        'Putting it all together...'
    ];

    // Simulate loading steps
    useEffect(() => {
        // Start with a small delay to show progress bar at 0
        const startDelay = setTimeout(() => {
            setCurrentStep(0);
        }, 500);

        // Update loading text based on current step
        const stepInterval = setInterval(() => {
            setCurrentStep(prev => {
                if (prev < loadingSteps.length - 1) {
                    return prev + 1;
                }
                return prev;
            });
        }, 1500);

        // Clear timeouts and intervals on unmount
        return () => {
            clearTimeout(startDelay);
            clearInterval(stepInterval);
        };
    }, [loadingSteps.length]);

    // Update loading text based on current step
    useEffect(() => {
        setLoadingText(loadingSteps[currentStep]);
    }, [currentStep]);

    // Navigate to recommendations after loading
    useEffect(() => {
        const timer = setTimeout(() => {
            setCurrentPage(PageType.Recommended);
        }, 6500);

        return () => clearTimeout(timer);
    }, [setCurrentPage]);

    return (
        <VStack spacing={CLEAR30_CONSTANTS.spacing.card} className="w-full min-h-screen flex flex-col">
            {/* Header */}
            <VStack className="w-full">
                <TextSizes.Heading3>Sourcing your supplements...</TextSizes.Heading3>
            </VStack>


            <VStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="center" className="w-full flex-1 justify-center">
                {/* Loading Animation */}
                <VStack alignment="center" className="w-full" style={{ paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
                    <Card>
                        <VStack alignment="center" style={{ paddingLeft: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingRight: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
                            {/* Animated Icon */}
                            <div className="animate-pulse" style={{ paddingBottom: `${CLEAR30_CONSTANTS.spacing.card / 2}px` }}>
                                <div style={{
                                    background: CLEAR30_CONSTANTS.gradients.clear30,
                                    WebkitBackgroundClip: 'text',
                                    WebkitTextFillColor: 'transparent',
                                    backgroundClip: 'text'
                                }}>
                                    <DynamicIcon
                                        iconName="User"
                                        size={48}
                                    />
                                </div>
                            </div>

                            {/* Loading Dots */}
                            <HStack spacing={CLEAR30_CONSTANTS.spacing.card / 4}>
                                <div className="w-2 h-2 rounded-full animate-bounce" style={{
                                    animationDelay: '0ms',
                                    background: CLEAR30_CONSTANTS.gradients.clear30
                                }}></div>
                                <div className="w-2 h-2 rounded-full animate-bounce" style={{
                                    animationDelay: '150ms',
                                    background: CLEAR30_CONSTANTS.gradients.clear30
                                }}></div>
                                <div className="w-2 h-2 rounded-full animate-bounce" style={{
                                    animationDelay: '300ms',
                                    background: CLEAR30_CONSTANTS.gradients.clear30
                                }}></div>
                            </HStack>
                        </VStack>
                    </Card>
                </VStack>

                {/* Progress Indicator */}
                <div className="w-48 bg-gray-200 rounded-full h-2">
                    <div
                        className="h-2 rounded-full transition-all duration-1000 ease-out"
                        style={{
                            width: `${Math.max(0, ((currentStep + 1) / loadingSteps.length) * 100)}%`,
                            background: CLEAR30_CONSTANTS.gradients.clear30
                        }}
                    ></div>
                </div>

                {/* Animated Loading Text */}
                <div className="h-6 flex items-center justify-center">
                    <AnimatePresence mode="wait">
                        <motion.div
                            key={loadingText}
                            {...LOADING_ANIMATIONS.textTransition}
                        >
                            <TextSizes.Small style={{ opacity: 0.5 }}>
                                {loadingText}
                            </TextSizes.Small>
                        </motion.div>
                    </AnimatePresence>
                </div>
            </VStack>
        </VStack>
    );
};
