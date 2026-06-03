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

- **Leading:** Use tight leading (`leading-tight`) for multi-line text to maintain compact, readable spacing

## Color Palette

| Color | Hex Code | Usage |
|-------|----------|-------|
| Blue | #5BB4A9 | Primary brand color, borders |
| Green | #80C97A | Secondary brand color |
| White | #FFFFFF | Background, card backgrounds |
| Black | #000000 | Primary text color |
| Gray | #E5E5E5 | Neutral elements |

### Color Guidelines

- **Gradients:** `linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)` used for CTAs, selected states, and accent elements
- **Text Opacity:** Text set to 50% opacity (`text-black/50`) for secondary information and descriptions
- **Border Opacity:** Borders use 10% opacity (`border-black/10`) for subtle separation

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
| **Main Container** | `pt-safe` (device safe area) | `pb-safe` (device safe area) | None | Full-screen container with safe area respect |
| **Header Section** | `pt-safe pb-3` | N/A | `px-6` (24px) | Sticky header with safe area + 12px bottom |
| **Header Inner** | `mt-16` (64px) | N/A | None | Additional top margin for header content |
| **Content Area** | Varies by page | `pb-[200px]` or `pb-[280px]` | `25px` (inline styles) | Dynamic bottom padding based on footer height |
| **Footer Section** | `pt-4` (16px) | `pb-8 pb-safe` | `25px` (inline styles) | Fixed footer with safe area + 32px |

## Component Styling

### Cards

- **Shadow:** `boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'` for cards and primary elements
- **Border:** 2px solid borders using brand colors (#5BB4A9)
- **Padding:** px-4 py-3 for internal card content
- **Purpose:** Primary method for organizing content with consistent visual hierarchy

### Buttons

- **Primary CTA:** Full-width with gradient background, 21px border radius, px-4 py-3 padding
- **Shadow:** `boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'` for depth
- **Active State:** `active:scale-95` for touch feedback
- **Product Selectors:** Gradient border treatment when selected using padding-box/border-box technique

### Interactive Elements

- **Touch Feedback:** `active:opacity-50` for buttons and interactive elements
- **Transitions:** `transition-transform` for smooth scaling effects
- **Minimum Touch Target:** Consistent sizing for accessibility
