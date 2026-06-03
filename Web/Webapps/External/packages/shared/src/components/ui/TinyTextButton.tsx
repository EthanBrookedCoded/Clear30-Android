import React from 'react';
import { Icon } from 'react-feather';
import { Card } from './Card';
import { TextTiny } from './TextSizes';
import { CLEAR30_CONSTANTS } from '../../lib/constants';

export interface TinyTextButtonProps {
  // Required props
  text: string;
  action: () => void;

  // Optional props matching SwiftUI
  background?: boolean;
  icon?: Icon;
  iconSize?: number;
  foregroundColor?: string;
  backgroundColor?: string;
  gradient?: string;
  shadowColor?: string;
  stretch?: boolean;
  opacity?: number;
}

export const TinyTextButton: React.FC<TinyTextButtonProps> = ({
  // Required props
  text,
  action,

  // Optional props with defaults matching SwiftUI
  background = true,
  icon: IconComponent,
  iconSize = 10,
  foregroundColor = CLEAR30_CONSTANTS.colors.text,
  backgroundColor = CLEAR30_CONSTANTS.colors.gray,
  gradient,
  shadowColor = CLEAR30_CONSTANTS.colors.clear,
  stretch = false,
  opacity = 0.7,
}) => {
  const handleClick = () => {
    // TODO: Add haptic feedback equivalent if needed
    action();
  };

  const content = (
    <div
      className={`flex items-center gap-1 justify-center ${stretch ? 'w-full' : ''}`}
    >
      {text && (
        <TextTiny style={{ opacity: gradient ? 1 : opacity }}>
          {text}
        </TextTiny>
      )}

      {IconComponent && (
        <IconComponent
          size={iconSize}
          style={{
            opacity: gradient ? 1 : opacity,
            height: iconSize,
            flexShrink: 0
          }}
        />
      )}
    </div>
  );

  return (
    <button
      onClick={handleClick}
      className={`cursor-pointer ${stretch ? 'w-full' : ''}`}
      style={{ opacity: gradient ? 1 : opacity }}
    >
      {background ? (
        <Card
          color={backgroundColor}
          gradient={gradient}
          foregroundColor={foregroundColor}
          cornerRadius={12}
          padding={false}
          shadowColor={shadowColor}
          style={{
            padding: '5px 10px',
          }}
        >
          {content}
        </Card>
      ) : content}
    </button>
  );
};
