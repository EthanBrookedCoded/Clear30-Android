import React, { useState, useEffect } from 'react';
import { useSupplementContext } from '../context/SupplementContext';
import { useSupabase } from '@clear30/shared/src/hooks/useSupabase';
import type { Supplement, Symptom } from '../types';
import { PageType } from '../types';
import { TextIconButton } from '@clear30/shared/src/components/ui/TextIconButton';
import { TextSizes } from '@clear30/shared/src/components/ui/TextSizes';
import { VStack } from '@clear30/shared/src/components/layout/VStack';
import { HStack } from '@clear30/shared/src/components/layout/HStack';
import { Card } from '@clear30/shared/src/components/ui/Card';
import { BottomSheet } from '@clear30/shared/src/components/ui/BottomSheet';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import { ArrowRight, Check } from 'react-feather';
import Logo from '../assets/images/c30.svg';

export const IntroPage: React.FC = () => {

    // Context and variabels
    const { setCurrentPage, resetApp, setAllTags, setAllSupplements, isDataLoaded } = useSupplementContext();
    const { getTableData } = useSupabase();
    const [isWelcomeSheetOpen, setIsWelcomeSheetOpen] = useState(false);

    // Fetch data function
    const fetchData = async () => {
        try {
            const [supplementsResult, symptomsResult] = await Promise.all([
                getTableData<Supplement>('sup_supplements', 'webapps'),
                getTableData<Symptom>('sup_tags', 'webapps')
            ]);

            if (supplementsResult && symptomsResult) {
                setAllSupplements(supplementsResult);
                setAllTags(symptomsResult.sort((a, b) => a.id - b.id));
            } else {
                console.error('Failed to fetch data');
            }
        } catch (err) {
            console.error('Error fetching data:', err);
        }
    };

    // Reset app and fetch data on component mount
    useEffect(() => {
        // Reset app state
        resetApp();

        // Fetch data
        fetchData();
    }, []);

    const handleDiveIn = () => {
        setIsWelcomeSheetOpen(true);
    };

    const handleCloseWelcomeSheet = () => {
        setIsWelcomeSheetOpen(false);
    };

    const handleAgree = () => {
        setIsWelcomeSheetOpen(false);
        setCurrentPage(PageType.Symptoms);
    };

    const disclaimer = () => {
        return (
            <VStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="leading" className="w-full">
                <TextSizes.Tiny>
                    This page is educational and summarizes findings from scientific studies. It is not medical advice and not a prescription.
                </TextSizes.Tiny>

                <HStack alignment="top" spacing={CLEAR30_CONSTANTS.spacing.card / 2}>
                    <TextSizes.Heading2>🗣️</TextSizes.Heading2>
                    <TextSizes.Mini style={{ opacity: 0.5 }}>
                        Talk with your physician or qualified clinician before starting any supplement—especially if you take prescription meds (e.g., antidepressants, antipsychotics, benzodiazepines, seizure meds, blood thinners, heart meds), or if you have kidney, liver, thyroid, or psychiatric conditions, are pregnant or breastfeeding, or plan surgery.
                    </TextSizes.Mini>
                </HStack>

                <HStack alignment="top" spacing={CLEAR30_CONSTANTS.spacing.card / 2}>
                    <TextSizes.Heading2>☺️</TextSizes.Heading2>
                    <TextSizes.Mini style={{ opacity: 0.5 }}>
                        Supplements can interact with medications and may cause side effects. Start one at a time and monitor how you feel.
                    </TextSizes.Mini>
                </HStack>

                <HStack alignment="top" spacing={CLEAR30_CONSTANTS.spacing.card / 2}>
                    <TextSizes.Heading2>🙏</TextSizes.Heading2>
                    <TextSizes.Mini style={{ opacity: 0.5 }}>
                        If you develop severe symptoms (e.g., chest pain, suicidal thoughts, uncontrolled agitation, confusion), seek urgent care.
                    </TextSizes.Mini>
                </HStack>

                <TextIconButton
                    text="I agree"
                    icon={Check}
                    action={handleAgree}
                    gradient={CLEAR30_CONSTANTS.gradients.clear30}
                    className="w-full"
                />
            </VStack>
        );
    };

    return (
        <>
            <div className="h-full w-full flex flex-col justify-center items-center" >
                <VStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="center" className="w-full flex-1 justify-center">
                    {/* Header Section */}
                    <img src={Logo} alt="Clear30 Logo" style={{ width: '35%', opacity: 0.5 }} />

                    <TextSizes.Heading1 textAlign="center" style={{ paddingBottom: `${CLEAR30_CONSTANTS.spacing.card * 2}px` }}>Supplement Finder</TextSizes.Heading1>

                    {/* Content Cards */}
                    <VStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="center" className="w-full">
                        {/* First Card - Clapping Hands */}
                        <Card>
                            <HStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="center">
                                <div className="text-3xl">👏</div>
                                <TextSizes.Small className="text-black leading-relaxed">
                                    Find the best supplements to make your quitting weed journey easier.
                                </TextSizes.Small>
                            </HStack>
                        </Card>

                        {/* Second Card - Stethoscope */}
                        <Card>
                            <HStack spacing={CLEAR30_CONSTANTS.spacing.card} alignment="center">
                                <TextSizes.Small className="text-black leading-relaxed">
                                    Backed by evidence and research. Curated by clinicians.
                                </TextSizes.Small>
                                <div className="text-3xl">🩺</div>
                            </HStack>
                        </Card>
                    </VStack>
                </VStack>

                {/* Call-to-Action Button - Fixed at bottom */}
                <div className="w-full">
                    <TextIconButton
                        text={isDataLoaded ? "Dive In" : "Loading..."}
                        icon={ArrowRight}
                        action={isDataLoaded ? handleDiveIn : () => { }}
                        gradient={CLEAR30_CONSTANTS.gradients.clear30}
                        disabled={!isDataLoaded}
                    />
                </div>
            </div>

            {/* Welcome Page Bottom Sheet */}
            <BottomSheet isOpen={isWelcomeSheetOpen} onClose={handleCloseWelcomeSheet} title="Disclaimer">
                {disclaimer()}
            </BottomSheet>
        </>
    );
};