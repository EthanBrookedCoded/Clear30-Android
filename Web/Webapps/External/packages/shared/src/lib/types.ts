// Common types and interfaces for the shared package

export interface BaseComponentProps {
  className?: string;
  children?: React.ReactNode;
}

export interface ButtonProps extends BaseComponentProps {
  variant?: 'primary' | 'secondary' | 'outline';
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  onClick?: () => void;
  type?: 'button' | 'submit' | 'reset';
}

// Recreated CardStyle based on SwiftUI CardStyle (simplified)
export interface CardStyleProps {
  color?: string; // Background color
  shadowColor?: string; // Shadow color
  cornerRadius?: number; // Corner radius in pixels  
  gradient?: string; // CSS gradient string
  outlineGradient?: string; // Outline gradient string
  outlineWidth?: number; // Outline width in pixels
  outlineOpacity?: number; // Outline opacity (0-1)
  foregroundColor?: string; // Text color
  padding?: boolean; // Enable padding
}

export interface CardProps extends BaseComponentProps, CardStyleProps {
  // Inherits all CardStyle props plus base component props
  style?: React.CSSProperties; // Allow custom inline styles
}

export interface LayoutProps extends BaseComponentProps {
  pageName: string;
}
