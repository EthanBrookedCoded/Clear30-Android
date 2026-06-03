import React from 'react';
import { CardProps } from '../../lib/types';
import { classNames } from '../../lib/utils';
import { CLEAR30_CONSTANTS } from '../../lib/constants';

/**
 * Card Component - Recreated CardStyle from SwiftUI
 * 
 * This component recreates the SwiftUI CardStyle with simplified props.
 * Excluded features: glow, outlineTrim, shadow offsets, transitions per user request.
 * Shadow has no offset (x=0, y=0) with 6px blur.
 * 
 * @example
 * // Default card
 * <Card>Content</Card>
 * 
 * // Card with gradient background
 * <Card gradient="linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)">Content</Card>
 * 
 * // Card with outline
 * <Card outlineGradient="linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)" outlineWidth={3}>Content</Card>
 * 
 * // Card with custom colors
 * <Card color="#FFFFFF" foregroundColor="#000000" shadowColor="rgba(0,0,0,0.2)">Content</Card>
 * 
 * // Card without padding
 * <Card padding={false}>Content</Card>
 */
export const Card: React.FC<CardProps> = ({
  children,
  className = '',
  style,
  color = CLEAR30_CONSTANTS.colors.button,
  shadowColor = CLEAR30_CONSTANTS.colors.shadow,
  cornerRadius = CLEAR30_CONSTANTS.cornerRadius,
  gradient,
  outlineGradient,
  outlineWidth = CLEAR30_CONSTANTS.outlineWidth,
  outlineOpacity = 1.0,
  foregroundColor = CLEAR30_CONSTANTS.colors.text,
  padding = true
}) => {
  // Build the style object
  const cardStyle: React.CSSProperties = {
    // Background - gradient takes priority over color
    background: gradient || color,

    // Text color - white for gradients, custom foregroundColor otherwise
    color: gradient ? '#FFFFFF' : foregroundColor,

    // Border radius
    borderRadius: `${cornerRadius}px`,

    // Shadow (no offset - x=0, y=0)
    boxShadow: `0 0 6px ${shadowColor}`,

    // Padding
    padding: padding ? `${CLEAR30_CONSTANTS.cardPadding}px` : '0',
  };

  // For gradient outlines, we need a pseudo-element approach
  const hasGradientOutline = !!outlineGradient;

  return (
    <div
      className={classNames(
        // Base classes
        hasGradientOutline ? 'relative' : '',
        className
      )}
      style={{ ...cardStyle, ...style }}
    >
      {/* Gradient outline pseudo-element */}
      {hasGradientOutline && (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            padding: `${outlineWidth}px`,
            background: outlineGradient,
            borderRadius: `${cornerRadius}px`,
            mask: 'linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)',
            maskComposite: 'xor',
            WebkitMask: 'linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)',
            WebkitMaskComposite: 'xor',
            opacity: outlineOpacity,
            pointerEvents: 'none',
          }}
        />
      )}

      {children}
    </div>
  );
};