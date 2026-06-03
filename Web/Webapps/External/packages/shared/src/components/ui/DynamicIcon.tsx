import React from 'react';
import * as FeatherIcons from 'react-feather';

export interface DynamicIconProps {
  iconName?: string;
  size?: number | string;
  className?: string;
  fallbackIcon?: React.ComponentType<{ size?: number | string; className?: string }>;
  onIconResolved?: (iconName: string, resolvedIcon: React.ComponentType<any>) => void;
}

export const DynamicIcon: React.FC<DynamicIconProps> = ({
  iconName,
  size = 20,
  className = '',
  fallbackIcon = FeatherIcons.Star,
  onIconResolved
}) => {
  // Get the icon component with robust fallback
  let IconComponent = fallbackIcon;

  if (iconName) {
    // Try exact match first
    if ((FeatherIcons as any)[iconName]) {
      IconComponent = (FeatherIcons as any)[iconName];
    }
    // Try case-insensitive match
    else {
      const iconKey = Object.keys(FeatherIcons).find(
        key => key.toLowerCase() === iconName.toLowerCase()
      );
      if (iconKey) {
        IconComponent = (FeatherIcons as any)[iconKey];
      }
    }

    // Callback for debugging/monitoring
    if (onIconResolved) {
      onIconResolved(iconName, IconComponent);
    }
  }

  return <IconComponent size={size} className={className} />;
};
