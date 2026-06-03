import React from 'react';
import { TextSizes } from '@clear30/shared/src/components/ui/TextSizes';
import { VStack } from '@clear30/shared/src/components/layout/VStack';
import { HStack } from '@clear30/shared/src/components/layout/HStack';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import { DynamicIcon } from '@clear30/shared/src/components/ui/DynamicIcon';
import { Card } from '@clear30/shared/src/components/ui/Card';
import type { Symptom } from '../types';
import { gradientFromHex } from '../utils/utils';

// SymptomCard props
interface SymptomCardProps {
    symptom: Symptom;
    variant?: 'selectable' | 'display';
    isSelected?: boolean;
    onToggle?: (id: number) => void;
}

export const SymptomCard: React.FC<SymptomCardProps> = ({
    symptom,
    variant = 'display',
    isSelected = false,
    onToggle
}) => {

    const handleClick = () => {
        if (variant === 'selectable' && onToggle) {
            onToggle(symptom.id);
        }
    };

    return (
        <button
            onClick={handleClick}
            disabled={variant === 'selectable' && !onToggle}
            className="w-full"
        >
            <Card
                gradient={isSelected ? gradientFromHex(symptom.color) : undefined}
                outlineGradient={isSelected ? CLEAR30_CONSTANTS.gradients.white : gradientFromHex(symptom.color)}
                outlineOpacity={0.5}
            >
                <HStack>
                    {/* Text */}
                    <VStack className="w-full">
                        <TextSizes.Tiny>{symptom.name}</TextSizes.Tiny>
                        <TextSizes.Tiny style={{ opacity: 0.5 }}>{symptom.headline}</TextSizes.Tiny>
                    </VStack>

                    {/* Icon */}
                    <div
                        style={{
                            opacity: 0.5,
                            color: isSelected ? '#FFFFFF' : symptom.color,
                        }}
                    >
                        <DynamicIcon
                            iconName={symptom.icon}
                            size={20}
                        />
                    </div>
                </HStack>
            </Card>
        </button>
    );
};
