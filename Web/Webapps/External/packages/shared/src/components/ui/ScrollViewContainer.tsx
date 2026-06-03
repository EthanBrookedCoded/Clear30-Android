import React from 'react';

export interface ScrollViewContainerProps {
  children: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
}

export const ScrollViewContainer: React.FC<ScrollViewContainerProps> = ({
  children,
  className = '',
  style
}) => {
  return (
    <div className={`h-full w-full flex flex-col ${className}`} style={style}>
      {children}
    </div>
  );
};
