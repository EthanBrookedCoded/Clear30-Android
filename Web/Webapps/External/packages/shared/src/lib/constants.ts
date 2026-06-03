// Shared constants for Clear30 design system
// These values are used by both Tailwind config and React components

export const CLEAR30_CONSTANTS = {
  // Corner radius matching GlobalData.shared.cornerRadius from SwiftUI
  cornerRadius: 21,

  // Padding matching SwiftUI CardStyle
  cardPadding: 16,

  // Default outline width matching SwiftUI
  outlineWidth: 3,

  // Colors
  colors: {
    blue: '#5BB4A9',
    green: '#80C97A',
    white: '#FFFFFF',
    black: '#000000',
    gray: 'rgba(0, 0, 0, 0.1)',
    shadow: 'rgba(0, 0, 0, 0.2)',
    clear: 'rgba(0, 0, 0, 0.0)',
    text: '#000000',
    button: '#FFFFFF',
  },

  // Gradients
  gradients: {
    clear30: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
    white: 'linear-gradient(135deg, #FFFFFF 0%, #FFFFFF 100%)',
    black: 'linear-gradient(135deg, #000000 0%, #000000 100%)',
  },

  // Typography
  fontSize: {
    heading1: '32px',
    heading2: '25px',
    heading3: '22px',
    default: '19px',
    small: '17px',
    tiny: '14px',
    mini: '10px',
  },

  // Spacing
  spacing: {
    horizontal: 25,
    headingTop: 10,
    card: 14,
  },
} as const;
