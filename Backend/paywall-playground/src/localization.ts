/**
 * Mock localization hook for local development
 * In Helium, this would be provided by their system
 */
export function useLocalization() {
  const translate = (key: string, fallback: string): string => {
    // In local dev, just return the fallback
    // In Helium, this would use their translation system
    return fallback;
  };

  return {
    translate
  };
}

