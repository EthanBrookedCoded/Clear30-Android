import React from 'react';

export type VStackAlignment = 'leading' | 'center' | 'trailing';

export interface VStackProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  alignment?: VStackAlignment;
  spacing?: number;
  className?: string;
}

export const VStack: React.FC<VStackProps> = ({
  children,
  alignment = 'leading',
  spacing,
  className = '',
  style,
  ...props
}) => {
  // Map SwiftUI alignment terms to Tailwind classes
  const alignmentClasses = {
    leading: 'items-start',
    center: 'items-center',
    trailing: 'items-end',
  };

  // Ensure spacing works even when custom styles are provided
  const combinedStyle = {
    ...(spacing !== undefined ? { gap: `${spacing}px` } : {}),
    ...style, // Custom styles come after to allow overrides
  };

  return (
    <div
      className={`flex flex-col ${alignmentClasses[alignment]} ${className}`}
      style={combinedStyle}
      {...props}
    >
      {children}
    </div>
  );
};