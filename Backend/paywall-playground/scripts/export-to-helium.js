#!/usr/bin/env node

/**
 * Export script to convert local development code to Helium format
 * 
 * This script extracts the BaseApp component and necessary code from src/App.tsx
 * and formats it as a single file ready for Helium, preserving the fenced code markers.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

// Read the App.tsx file
const appPath = path.join(rootDir, 'src', 'App.tsx');
const appContent = fs.readFileSync(appPath, 'utf-8');

// Extract the BaseApp component and everything between the fenced code markers
// We need to extract everything from the BaseApp function definition to the export statement

// Find the start marker (after "END FENCED CODE --- DO NOT EDIT ABOVE THIS LINE ---")
const startMarker = '// END FENCED CODE --- DO NOT EDIT ABOVE THIS LINE ---';
const endMarker = '// BEGIN FENCED CODE --- DO NOT EDIT BELOW THIS LINE ---';

const startIndex = appContent.indexOf(startMarker);
const endIndex = appContent.indexOf(endMarker);

if (startIndex === -1 || endIndex === -1) {
  console.error('Error: Could not find fenced code markers in App.tsx');
  process.exit(1);
}

// Extract the main component code (between the markers)
const mainCode = appContent.substring(startIndex + startMarker.length, endIndex).trim();

// Extract the code after the end marker (the export statement)
const exportCode = appContent.substring(endIndex).trim();

// Build the final Helium-compatible file
const heliumContent = `import { useLocalization } from "./localization";
import { withPaywallState, WithPaywallProps } from "./withPaywallState";
import paywallConfig from "./paywallConfig";
import { useState, useEffect, Fragment, useRef } from 'react';

/**
 * BRAND STYLE GUIDELINES - ALWAYS FOLLOW THESE GUIDELINES
 * 

# Brand Style Guidelines

## Typography

**Font Family:** Lexend

| Element | Size | Weight | Usage in Paywall |
|---------|------|--------|------------------|
| Heading 1 | 32px | Medium | Rarely used |
| Heading 2 | 25px | Regular | Single emojis display |
| Heading 3 | 22px | Regular | Default for all headings |
| Default Text | 19px | Regular | Rarely used, sometimes for section headings |
| Small Text | 17px | Regular | Primary body text, button text, most content |
| Tiny Text | 14px | Regular | Subtext, secondary information | 

### Text Styling

- **Leading:** Use tight leading (\`leading-tight\`) for multi-line text to maintain compact, readable spacing

## Color Palette

| Color | Hex Code | Usage |
|-------|----------|-------|
| Blue | #5BB4A9 | Primary brand color, borders |
| Green | #80C97A | Secondary brand color |
| White | #FFFFFF | Background, card backgrounds |
| Black | #000000 | Primary text color |
| Gray | #E5E5E5 | Neutral elements |

### Color Guidelines

- **Gradients:** \`linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)\` used for CTAs, selected states, and accent elements
- **Text Opacity:** Text set to 50% opacity (\`text-black/50\`) for secondary information and descriptions
- **Border Opacity:** Borders use 10% opacity (\`border-black/10\`) for subtle separation

## Layout & Spacing

| Property | Value | Application |
|----------|-------|-------------|
| Corner Radius | 21px | All rounded elements (cards, buttons, selectors) |
| Horizontal Padding | 25px | Left and right margins on every screen |
| Card Internal Padding | px-4 py-3 | Internal padding for cards and content |
| Component Spacing | 24px (space-y-6) | Vertical spacing between day cards |
| Section Spacing | 16px (mb-4, mb-6) | Spacing between major sections |

### Spacing Rules

- **Screen Margins:** Apply 25px horizontal padding via inline styles to left and right of content areas
- **Component Spacing:** Use 24px for major component separation, 16px for related elements
- **Button Padding:** px-4 py-3 for primary CTAs
- **Card Spacing:** px-4 py-3 internal padding with 24px between cards

### Overall View Padding/Spacing

| Area | Top Padding | Bottom Padding | Horizontal Padding | Notes |
|------|-------------|----------------|-------------------|-------|
| **Main Container** | \`pt-safe\` (device safe area) | \`pb-safe\` (device safe area) | None | Full-screen container with safe area respect |
| **Header Section** | \`pt-safe pb-3\` | N/A | \`px-6\` (24px) | Sticky header with safe area + 12px bottom |
| **Header Inner** | \`mt-16\` (64px) | N/A | None | Additional top margin for header content |
| **Content Area** | Varies by page | \`pb-[200px]\` or \`pb-[280px]\` | \`25px\` (inline styles) | Dynamic bottom padding based on footer height |
| **Footer Section** | \`pt-4\` (16px) | \`pb-8 pb-safe\` | \`25px\` (inline styles) | Fixed footer with safe area + 32px |

## Component Styling

### Cards

- **Shadow:** \`boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'\` for cards and primary elements
- **Border:** 2px solid borders using brand colors (#5BB4A9)
- **Padding:** px-4 py-3 for internal card content
- **Purpose:** Primary method for organizing content with consistent visual hierarchy

### Buttons

- **Primary CTA:** Full-width with gradient background, 21px border radius, px-4 py-3 padding
- **Shadow:** \`boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'\` for depth
- **Active State:** \`active:scale-95\` for touch feedback
- **Product Selectors:** Gradient border treatment when selected using padding-box/border-box technique

### Interactive Elements

- **Touch Feedback:** \`active:opacity-50\` for buttons and interactive elements
- **Transitions:** \`transition-transform\` for smooth scaling effects
- **Minimum Touch Target:** Consistent sizing for accessibility
 * 
 * IMPORTANT: Always follow these guidelines when making any changes to the paywall design.
 * These specifications ensure brand consistency and optimal user experience.
 */

// Base App component that will receive paywall props
function BaseApp({
  isSubscribing,
  selectedProductId,
  isDismissing,
  isRestoring,
  error,
  availableProducts,
  handleSubscribe,
  handleSelectProduct,
  dismiss,
  restorePurchases,
  navigate,
  clearError
}: WithPaywallProps) {
  const {
    translate
  } = useLocalization();
  // END FENCED CODE --- DO NOT EDIT ABOVE THIS LINE ---

${mainCode}

// Add styles to hide scrollbar
const style = document.createElement('style');
style.textContent = \`
  .scrollbar-hide {
    -ms-overflow-style: none;
    scrollbar-width: none;
  }
  .scrollbar-hide::-webkit-scrollbar {
    display: none;
  }
\`;
document.head.appendChild(style);

// Load Lexend font from Google Fonts
const fontLink = document.createElement('link');
fontLink.href = 'https://fonts.googleapis.com/css2?family=Lexend:wght@100;200;300;400;500;600;700;800;900&display=swap';
fontLink.rel = 'stylesheet';
fontLink.crossOrigin = 'anonymous';
document.head.appendChild(fontLink);

// Add preconnect links for better performance
const preconnect1 = document.createElement('link');
preconnect1.href = 'https://fonts.googleapis.com';
preconnect1.rel = 'preconnect';
document.head.appendChild(preconnect1);
const preconnect2 = document.createElement('link');
preconnect2.href = 'https://fonts.gstatic.com';
preconnect2.rel = 'preconnect';
preconnect2.crossOrigin = 'anonymous';
document.head.appendChild(preconnect2);

${exportCode}
`;

// Write to helium.ts in the root directory
const outputPath = path.join(rootDir, 'helium.ts');
fs.writeFileSync(outputPath, heliumContent, 'utf-8');

console.log('✅ Successfully exported to helium.ts');
console.log('📝 File is ready to be copied to Helium');

