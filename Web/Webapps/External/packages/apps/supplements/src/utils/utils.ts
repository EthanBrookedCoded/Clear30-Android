// Utility functions for the supplements app

/**
 * Creates a gradient from a hex color
 * @param hex - Hex color code (e.g., "#FF0000")
 * @returns CSS linear gradient string
 */
export const gradientFromHex = (hex: string) => {
  return `linear-gradient(135deg, ${hex} 0%, ${hex}90 100%)`;
};
