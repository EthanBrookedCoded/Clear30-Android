import React from 'react';
import { Icon } from 'react-feather';
import { Card } from './Card';
import { CLEAR30_CONSTANTS } from '../../lib/constants';
import { TextSmall, TextTiny } from './TextSizes';

export interface TextIconButtonProps {
  // Required props
  text: string;
  action: () => void;

  // Optional props matching SwiftUI
  icon?: Icon; // Instead of imageName for React
  iconSize?: number; // Matches imageSize
  subtext?: string;
  gradient?: string; // Full gradient string instead of enum
  lineLimit?: number;
  translucent?: boolean;
  foregroundOpacity?: number;
  foregroundColor?: string;
  outlineGradient?: string; // Full gradient string instead of enum
  outlineGradientOpacity?: number;
  spacer?: boolean;

  // Additional React-specific props
  className?: string;
  disabled?: boolean;
}

export const TextIconButton: React.FC<TextIconButtonProps> = ({
  // Required props
  text,
  action,

  // Optional props
  icon: IconComponent,
  iconSize = 15,
  subtext,
  gradient,
  lineLimit,
  translucent = false,
  foregroundOpacity = 1,
  foregroundColor = CLEAR30_CONSTANTS.colors.text,
  outlineGradient,
  outlineGradientOpacity = 0.5,
  spacer = true,

  // React-specific props
  className = '',
  disabled = false
}) => {
  const handleClick = () => {
    if (disabled) return;
    action();
  };

  // Calculate card props based on translucent state
  const cardProps = {
    color: translucent ? `rgba(255, 255, 255, 0.25)` : CLEAR30_CONSTANTS.colors.button,
    gradient: translucent ? undefined : gradient,
    outlineGradient,
    outlineOpacity: outlineGradientOpacity,
    foregroundColor: translucent ?
      `rgba(255, 255, 255, ${foregroundOpacity})` :
      foregroundColor
  };

  return (
    <button
      onClick={handleClick}
      disabled={disabled}
      className={`${spacer ? 'w-full' : ''} ${className} ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
    >
      <Card {...cardProps}>
        {/* Main container - VStack */}
        <div className="flex flex-col justify-center items-center">
          {/* Content wrapper */}
          <div className="flex items-center gap-2">
            {/* Text */}
            {text && (
              <TextSmall
                style={{
                  opacity: foregroundOpacity,
                  ...(lineLimit && {
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    display: '-webkit-box',
                    WebkitLineClamp: lineLimit,
                    WebkitBoxOrient: 'vertical',
                  })
                }}
              >
                {text}
              </TextSmall>
            )}

            {/* Icon */}
            {IconComponent && (
              <IconComponent
                size={iconSize}
                style={{
                  opacity: foregroundOpacity,
                  height: iconSize,
                  flexShrink: 0
                }}
              />
            )}
          </div>

          {/* Subtext */}
          {subtext && (
            <div
              className="w-full text-center"
              style={{
                paddingTop: 0
              }}
            >
              <TextTiny style={{ opacity: 0.5 * foregroundOpacity }}>
                {subtext}
              </TextTiny>
            </div>
          )}
        </div>
      </Card>
    </button>
  );
};
