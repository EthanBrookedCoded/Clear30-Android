import React from 'react';

interface TextProps extends React.HTMLAttributes<HTMLSpanElement> {
  children: React.ReactNode;
  textAlign?: 'left' | 'right' | 'center' | 'justify';
}

const baseStyle = {
  display: 'block', // This ensures each text element is on its own line
};

export const Heading1: React.FC<TextProps> = ({ children, className = '', style, textAlign = 'left', ...props }) => (
  <span
    className={`text-heading1 ${className}`}
    style={{ ...baseStyle, textAlign, lineHeight: '1.1', ...style }}
    {...props}
  >
    {children}
  </span>
);

export const Heading2: React.FC<TextProps> = ({ children, className = '', style, textAlign = 'left', ...props }) => (
  <span
    className={`text-heading2 ${className}`}
    style={{ ...baseStyle, textAlign, lineHeight: '1.1', ...style }}
    {...props}
  >
    {children}
  </span>
);

export const Heading3: React.FC<TextProps> = ({ children, className = '', style, textAlign = 'left', ...props }) => (
  <span
    className={`text-heading3 ${className}`}
    style={{ ...baseStyle, textAlign, lineHeight: '1.1', ...style }}
    {...props}
  >
    {children}
  </span>
);

export const TextDefault: React.FC<TextProps> = ({ children, className = '', style, textAlign = 'left', ...props }) => (
  <span
    className={`text-default ${className}`}
    style={{ ...baseStyle, textAlign, lineHeight: '1.2', ...style }}
    {...props}
  >
    {children}
  </span>
);

export const TextSmall: React.FC<TextProps> = ({ children, className = '', style, textAlign = 'left', ...props }) => (
  <span
    className={`text-small ${className}`}
    style={{ ...baseStyle, lineHeight: '1.2', textAlign, ...style }}
    {...props}
  >
    {children}
  </span>
);

export const TextTiny: React.FC<TextProps> = ({ children, className = '', style, textAlign = 'left', ...props }) => (
  <span
    className={`text-tiny ${className}`}
    style={{ ...baseStyle, textAlign, lineHeight: '1.15', ...style }}
    {...props}
  >
    {children}
  </span>
);

export const TextMini: React.FC<TextProps> = ({ children, className = '', style, textAlign = 'left', ...props }) => (
  <span
    className={`text-mini ${className}`}
    style={{ ...baseStyle, textAlign, lineHeight: '1.1', ...style }}
    {...props}
  >
    {children}
  </span>
);

// Export a map of all text components for convenience
export const TextSizes = {
  Heading1,
  Heading2,
  Heading3,
  Default: TextDefault,
  Small: TextSmall,
  Tiny: TextTiny,
  Mini: TextMini,
} as const;