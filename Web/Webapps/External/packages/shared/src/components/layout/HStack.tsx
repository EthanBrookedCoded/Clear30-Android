import React from 'react';

export type HStackAlignment = 'top' | 'center' | 'bottom';

export interface HStackProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  alignment?: HStackAlignment;
  spacing?: number;
  className?: string;
}

export const HStack: React.FC<HStackProps> = ({
  children,
  alignment = 'center',
  spacing,
  className = '',
  style,
  ...props
}) => {
  // Map SwiftUI alignment terms to Tailwind classes
  const alignmentClasses = {
    top: 'items-start',
    center: 'items-center',
    bottom: 'items-end',
  };

  // Ensure spacing works even when custom styles are provided
  const combinedStyle = {
    ...(spacing !== undefined ? { gap: `${spacing}px` } : {}),
    ...style, // Custom styles come after to allow overrides
  };

  return (
    <div
      className={`flex flex-row ${alignmentClasses[alignment]} ${className}`}
      style={combinedStyle}
      {...props}
    >
      {children}
    </div>
  );
};