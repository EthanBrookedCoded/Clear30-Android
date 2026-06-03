# Clear30 Shared Library

The Clear30 Shared Library (`@clear30/shared`) is the centralized design system and component library for all Clear30 web applications. It provides consistent UI components, animations, database access, logging, and utilities that ensure a unified experience across all sub-apps.

## Table of Contents

- [Installation & Setup](#installation--setup)
- [Import Guide](#import-guide)
- [UI Components](#ui-components)
- [UI Best Practices](#ui-best-practices)
- [Navigation & Context Patterns](#navigation--context-patterns)
- [Supabase Database Integration](#supabase-database-integration)
- [Animation System](#animation-system)
- [Logging & Events](#logging--events)
- [Constants & Design System](#constants--design-system)
- [Development Rules](#development-rules)

## Installation & Setup

### 1. Add as Dependency

In your app's `package.json`:

```json
{
  "dependencies": {
    "@clear30/shared": "workspace:*"
  }
}
```

### 2. Import Styles

In your app's main entry point (e.g., `main.tsx`):

```typescript
import '@clear30/shared/src/styles/globals.css';
```

### 3. Tailwind Configuration

Extend the shared Tailwind config in your app's `tailwind.config.js`:

```javascript
const sharedConfig = require('@clear30/shared/tailwind.config.js');

module.exports = {
  ...sharedConfig,
  content: [
    ...sharedConfig.content,
    './src/**/*.{js,ts,jsx,tsx}',
    './index.html'
  ],
};
```

### 4. PostCSS Configuration

Your `postcss.config.js` should include:

```javascript
module.exports = {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

## Import Guide

Always import from the shared library using explicit paths:

```typescript
// ✅ Correct - Import from @clear30/shared
import { Card, VStack, HStack } from '@clear30/shared';
import { AppLayout } from '@clear30/shared';
import { useSupabase, useLogging } from '@clear30/shared';
import { CLEAR30_CONSTANTS } from '@clear30/shared';

// ✅ Also correct - Specific imports
import { Card } from '@clear30/shared/src/components/ui/Card';
import { AppLayout } from '@clear30/shared/src/components/layout/AppLayout';
import { useSupabase } from '@clear30/shared/src/hooks/useSupabase';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';

// ❌ Never import directly from node_modules or relative paths
import { Card } from '../../../shared/src/components/ui/Card';
```

### Available Exports

The library exports all components, hooks, and utilities from a single entry point:

```typescript
// Components
import { 
  Card, TextIconButton, TinyTextButton, BottomSheet,
  ScrollView, ScrollViewContainer, DynamicIcon, TextSizes,
  AppLayout, HStack, VStack 
} from '@clear30/shared';

// Hooks
import { useSupabase, useLogging, useQueryParams } from '@clear30/shared';

// Utilities & Constants
import { 
  CLEAR30_CONSTANTS, classNames,
  PAGE_TRANSITIONS, UI_ANIMATIONS, LOADING_ANIMATIONS, 
  BOTTOM_SHEET_ANIMATIONS, ANIMATE_PRESENCE_PROPS,
  LOGGABLE_EVENTS, supabaseService, loggingService
} from '@clear30/shared';
```

## UI Components

### Core Layout Components

#### AppLayout
The foundational layout component that all pages should use. Provides consistent spacing, viewport configuration, and mobile optimizations.

```typescript
import { AppLayout } from '@clear30/shared';

export const MyPage: React.FC = () => {
  const handleBack = () => {
    // Navigation logic
  };

  return (
    <AppLayout 
      pageName="My Page" 
      onBack={handleBack} // Optional back button
    >
      {/* Your page content */}
    </AppLayout>
  );
};
```

**Features:**
- Automatic page view logging
- Mobile-optimized viewport settings
- Consistent spacing using `CLEAR30_CONSTANTS.spacing.horizontal`
- Optional back button with animation
- Helmet integration for meta tags

#### VStack & HStack
SwiftUI-inspired layout components for vertical and horizontal stacking.

```typescript
import { VStack, HStack } from '@clear30/shared';
import { CLEAR30_CONSTANTS } from '@clear30/shared';

// Vertical stack
<VStack 
  spacing={CLEAR30_CONSTANTS.spacing.card} 
  alignment="center" // 'leading' | 'center' | 'trailing'
>
  <Component1 />
  <Component2 />
</VStack>

// Horizontal stack
<HStack 
  spacing={CLEAR30_CONSTANTS.spacing.card} 
  alignment="center" // 'top' | 'center' | 'bottom'
>
  <Component1 />
  <Component2 />
</HStack>
```

### Card Component
The primary container component that implements the Clear30 design system.

```typescript
import { Card } from '@clear30/shared';
import { CLEAR30_CONSTANTS } from '@clear30/shared';

// Basic card
<Card>
  <TextSizes.Small>Content</TextSizes.Small>
</Card>

// Card with gradient
<Card gradient={CLEAR30_CONSTANTS.gradients.clear30}>
  <TextSizes.Small>Gradient content</TextSizes.Small>
</Card>

// Card with outline
<Card 
  outlineGradient={CLEAR30_CONSTANTS.gradients.clear30}
  outlineWidth={3}
>
  <TextSizes.Small>Outlined content</TextSizes.Small>
</Card>

// Card without padding
<Card padding={false}>
  <CustomComponent />
</Card>
```

**Props:**
- `color`: Background color (default: white)
- `gradient`: CSS gradient string (overrides color)
- `shadowColor`: Shadow color (default: `CLEAR30_CONSTANTS.colors.shadow`)
- `cornerRadius`: Border radius in pixels (default: 21)
- `outlineGradient`: Gradient for border outline
- `outlineWidth`: Outline width in pixels (default: 3)
- `outlineOpacity`: Outline opacity (default: 1.0)
- `foregroundColor`: Text color (default: black)
- `padding`: Enable/disable padding (default: true)

### Interactive Components

#### TextIconButton
Primary button component combining text and icons.

```typescript
import { TextIconButton } from '@clear30/shared';
import { CLEAR30_CONSTANTS } from '@clear30/shared';
import { ArrowRight } from 'react-feather';

<TextIconButton
  text="Continue"
  icon={ArrowRight}
  action={() => console.log('Clicked')}
  gradient={CLEAR30_CONSTANTS.gradients.clear30}
  disabled={false}
/>
```

#### BottomSheet
Modal component that slides up from the bottom.

```typescript
import { BottomSheet } from '@clear30/shared';

const [isOpen, setIsOpen] = useState(false);

<BottomSheet
  isOpen={isOpen}
  onClose={() => setIsOpen(false)}
  title="Sheet Title"
  showCloseButton={true}
>
  <VStack spacing={CLEAR30_CONSTANTS.spacing.card}>
    <TextSizes.Small>Sheet content</TextSizes.Small>
  </VStack>
</BottomSheet>
```

### Scrolling Components

#### ScrollView & ScrollViewContainer
Components for handling scrollable content within the layout system.

```typescript
import { ScrollView, ScrollViewContainer } from '@clear30/shared';

<ScrollViewContainer>
  {/* Fixed header content */}
  <VStack spacing={CLEAR30_CONSTANTS.spacing.card}>
    <TextSizes.Heading3>Header</TextSizes.Heading3>
  </VStack>

  {/* Scrollable content */}
  <ScrollView>
    <VStack spacing={CLEAR30_CONSTANTS.spacing.card}>
      {items.map(item => <ItemComponent key={item.id} item={item} />)}
    </VStack>
  </ScrollView>

  {/* Fixed footer content */}
  <TextIconButton text="Action" action={handleAction} />
</ScrollViewContainer>
```

### Typography Components

#### TextSizes
Predefined text components that implement the Clear30 typography system.

```typescript
import { TextSizes } from '@clear30/shared';

<TextSizes.Heading1>Main Title</TextSizes.Heading1>
<TextSizes.Heading2>Section Title</TextSizes.Heading2>
<TextSizes.Heading3>Subsection Title</TextSizes.Heading3>
<TextSizes.Default>Body text</TextSizes.Default>
<TextSizes.Small>Smaller text</TextSizes.Small>
<TextSizes.Tiny>Fine print</TextSizes.Tiny>
<TextSizes.Mini>Micro text</TextSizes.Mini>
```

### Icon Component

#### DynamicIcon
Component for rendering Feather icons dynamically by name.

```typescript
import { DynamicIcon } from '@clear30/shared';

<DynamicIcon 
  iconName="Heart" // Feather icon name
  size={20}
  className="text-blue-500"
  fallbackIcon={Star} // Optional fallback
/>
```

## UI Best Practices

### Spacing Guidelines

**Always use Card spacing for consistent layouts:**

```typescript
import { CLEAR30_CONSTANTS } from '@clear30/shared';

// ✅ Correct - Use predefined spacing
<VStack spacing={CLEAR30_CONSTANTS.spacing.card}>
  <Component1 />
  <Component2 />
</VStack>

// ✅ Correct - Use spacing variants
<VStack spacing={CLEAR30_CONSTANTS.spacing.card / 2}> // Half spacing
<VStack spacing={CLEAR30_CONSTANTS.spacing.card * 2}> // Double spacing

// ❌ Avoid - Magic numbers
<VStack spacing={14}>
<VStack spacing={28}>
```

**Available spacing constants:**
- `CLEAR30_CONSTANTS.spacing.horizontal` (25px) - App-level horizontal padding
- `CLEAR30_CONSTANTS.spacing.card` (14px) - Standard component spacing
- `CLEAR30_CONSTANTS.spacing.headingTop` (10px) - Top margin for headings
- `CLEAR30_CONSTANTS.cardPadding` (16px) - Internal card padding

### Color Usage

```typescript
// ✅ Use constant colors
<Card color={CLEAR30_CONSTANTS.colors.button}>
<Card gradient={CLEAR30_CONSTANTS.gradients.clear30}>

// Available colors
CLEAR30_CONSTANTS.colors = {
  blue: '#5BB4A9',
  green: '#80C97A', 
  white: '#FFFFFF',
  black: '#000000',
  gray: 'rgba(0, 0, 0, 0.1)',
  shadow: 'rgba(0, 0, 0, 0.2)',
  text: '#000000',
  button: '#FFFFFF'
};

// Available gradients
CLEAR30_CONSTANTS.gradients = {
  clear30: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
  white: 'linear-gradient(135deg, #FFFFFF 0%, #FFFFFF 100%)',
  black: 'linear-gradient(135deg, #000000 0%, #000000 100%)'
};
```

### Component Composition

```typescript
// ✅ Good - Proper nesting and spacing
<Card>
  <VStack spacing={CLEAR30_CONSTANTS.spacing.card}>
    <TextSizes.Heading3>Title</TextSizes.Heading3>
    <HStack spacing={CLEAR30_CONSTANTS.spacing.card / 2}>
      <DynamicIcon iconName="Star" />
      <TextSizes.Small>Content</TextSizes.Small>
    </HStack>
  </VStack>
</Card>
```

## Navigation & Context Patterns

Clear30 apps use **context-based navigation** instead of URL routing. This approach stores navigation state in React context and localStorage for persistence.

### Context Setup Pattern

```typescript
// types.ts
export const PageType = {
  Intro: 'intro',
  Main: 'main',
  Detail: 'detail'
} as const;

export type PageType = typeof PageType[keyof typeof PageType];

export interface AppState {
  currentPage: PageType;
  // Other app state...
}

// AppContext.tsx
interface AppContextType {
  currentPage: PageType;
  setCurrentPage: (page: PageType) => void;
  // Other methods...
}

export const AppProvider: React.FC = ({ children }) => {
  const [appState, setAppState] = useState<AppState>(() => {
    // Load from localStorage
    try {
      const saved = localStorage.getItem('appState');
      return saved ? JSON.parse(saved) : { currentPage: PageType.Intro };
    } catch {
      return { currentPage: PageType.Intro };
    }
  });

  // Save to localStorage on state changes
  useEffect(() => {
    localStorage.setItem('appState', JSON.stringify(appState));
  }, [appState.currentPage]);

  const setCurrentPage = (page: PageType) => {
    setAppState(prev => ({ ...prev, currentPage: page }));
  };

  return (
    <AppContext.Provider value={{ currentPage: appState.currentPage, setCurrentPage }}>
      {children}
    </AppContext.Provider>
  );
};
```

### App Structure Pattern

```typescript
// App.tsx
const AppContent: React.FC = () => {
  const { currentPage, setCurrentPage } = useAppContext();

  // Handle back navigation
  const handleBack = () => {
    switch (currentPage) {
      case PageType.Main:
        setCurrentPage(PageType.Intro);
        break;
      case PageType.Detail:
        setCurrentPage(PageType.Main);
        break;
      // No back for intro
    }
  };

  const renderCurrentPage = () => {
    switch (currentPage) {
      case PageType.Intro:
        return <IntroPage />;
      case PageType.Main:
        return <MainPage />;
      case PageType.Detail:
        return <DetailPage />;
      default:
        return <IntroPage />;
    }
  };

  return (
    <AppLayout
      pageName="App Name"
      onBack={currentPage !== PageType.Intro ? handleBack : undefined}
    >
      <AnimatePresence {...ANIMATE_PRESENCE_PROPS}>
        <motion.div key={currentPage} {...PAGE_TRANSITIONS.fade}>
          {renderCurrentPage()}
        </motion.div>
      </AnimatePresence>
    </AppLayout>
  );
};

function App() {
  return (
    <AppProvider>
      <AppContent />
    </AppProvider>
  );
}
```

### State Persistence

All navigation and app state should be stored in localStorage for persistence across sessions:

```typescript
// ✅ Correct - Persist all relevant state
useEffect(() => {
  const stateToSave = {
    currentPage: appState.currentPage,
    selectedItems: appState.selectedItems,
    userPreferences: appState.userPreferences
    // Exclude computed/temporary values
  };
  localStorage.setItem('appState', JSON.stringify(stateToSave));
}, [appState.currentPage, appState.selectedItems, appState.userPreferences]);
```

## Supabase Database Integration

The shared library provides a centralized Supabase service with consistent error handling and logging.

### Configuration

The `SupabaseService` automatically configures itself based on environment:

```typescript
// config.ts handles environment detection
const config = getConfig(); // Returns { supabaseUrl, supabaseAnonKey, isQA, isDevelopment }
```

### Using the Supabase Hook

```typescript
import { useSupabase } from '@clear30/shared';

export const MyComponent: React.FC = () => {
  const { getTableData, callFunction, insertData, loading, error } = useSupabase();

  // Fetch table data
  const fetchSupplements = async () => {
    const supplements = await getTableData<Supplement>('supplements', 'webapps');
    if (supplements) {
      // Handle success
    }
  };

  // Call Supabase function
  const callAnalytics = async () => {
    const result = await callFunction('analyze_user_data', { userId: 123 });
    if (result) {
      // Handle result
    }
  };

  // Insert data
  const saveUserPreference = async () => {
    const result = await insertData('user_preferences', {
      user_id: userId,
      preference: 'dark_mode',
      value: true
    });
  };

  return (
    <div>
      {loading && <TextSizes.Small>Loading...</TextSizes.Small>}
      {error && <TextSizes.Small>Error: {error.message}</TextSizes.Small>}
      <TextIconButton text="Fetch Data" action={fetchSupplements} />
    </div>
  );
};
```

### Direct Service Usage

For use outside React components:

```typescript
import { supabaseService } from '@clear30/shared';

// In utility functions, classes, etc.
const { data, error } = await supabaseService.getTableData('supplements', 'webapps');
const { data: result, error: functionError } = await supabaseService.callSupabaseFunction('my_function');
```

### Database Schema Usage

Always specify the correct schema when accessing webapp-specific tables:

```typescript
// ✅ Correct - Specify webapps schema for webapp tables
await getTableData<Supplement>('sup_supplements', 'webapps');
await getTableData<Tag>('sup_tags', 'webapps');

// ✅ Correct - Public schema (default) for shared tables
await getTableData<User>('users'); // Uses 'public' schema by default
```

## Animation System

The shared library uses **Framer Motion** with predefined animation configurations.

### Available Animations

```typescript
import { 
  PAGE_TRANSITIONS, 
  UI_ANIMATIONS, 
  LOADING_ANIMATIONS, 
  BOTTOM_SHEET_ANIMATIONS,
  ANIMATE_PRESENCE_PROPS 
} from '@clear30/shared';
```

### Page Transitions

```typescript
import { motion, AnimatePresence } from 'framer-motion';
import { PAGE_TRANSITIONS, ANIMATE_PRESENCE_PROPS } from '@clear30/shared';

<AnimatePresence {...ANIMATE_PRESENCE_PROPS}>
  <motion.div key={currentPage} {...PAGE_TRANSITIONS.fade}>
    {renderCurrentPage()}
  </motion.div>
</AnimatePresence>
```

### UI Element Animations

```typescript
// Back button animation
<motion.button {...UI_ANIMATIONS.backButton}>
  Back
</motion.button>

// Expandable content
<motion.div {...UI_ANIMATIONS.instructions}>
  Instructions content
</motion.div>
```

### Loading Animations

```typescript
// Text transition for loading states
<AnimatePresence mode="wait">
  <motion.div key={loadingText} {...LOADING_ANIMATIONS.textTransition}>
    <TextSizes.Small>{loadingText}</TextSizes.Small>
  </motion.div>
</AnimatePresence>
```

### Custom Animations

**Only add new animations to the centralized file:**

```typescript
// In packages/shared/src/lib/animations.ts
export const NEW_ANIMATIONS = {
  slideIn: {
    initial: { x: -100, opacity: 0 },
    animate: { x: 0, opacity: 1 },
    exit: { x: 100, opacity: 0 },
    transition: { duration: 0.3 }
  }
} as const;
```

## Logging & Events

The shared library provides centralized logging with automatic user tracking and environment-aware behavior.

### Using the Logging Hook

```typescript
import { useLogging } from '@clear30/shared';

export const MyComponent: React.FC = () => {
  const { logEvent, logPageView, logButtonClick, LOGGABLE_EVENTS } = useLogging();

  const handleButtonClick = async () => {
    await logButtonClick('my_button', { additional: 'data' });
    // Button action
  };

  const handleCustomEvent = async () => {
    await logEvent(LOGGABLE_EVENTS.FEATURE_ACCESS, { feature: 'premium_content' });
  };

  return (
    <TextIconButton text="Click Me" action={handleButtonClick} />
  );
};
```

### Available Event Types

```typescript
import { LOGGABLE_EVENTS } from '@clear30/shared';

// Available events:
LOGGABLE_EVENTS.APP_OPEN        // App lifecycle
LOGGABLE_EVENTS.APP_CLOSE
LOGGABLE_EVENTS.PAGE_VIEW       // Navigation
LOGGABLE_EVENTS.BUTTON_CLICK    // User interactions
LOGGABLE_EVENTS.FORM_SUBMIT
LOGGABLE_EVENTS.FEATURE_ACCESS  // Feature usage
LOGGABLE_EVENTS.ERROR_OCCURRED  // Errors
```

### Automatic Logging

The `AppLayout` component automatically logs page views:

```typescript
// Automatically logs when pageName prop changes
<AppLayout pageName="Supplements Detail"> 
  {/* Page content */}
</AppLayout>
```

### User Context

Logging automatically includes user context from URL parameters:
- `user_id`: From `?user_id=` query parameter
- `from_app`: From `?from_app=true` query parameter

### Environment Behavior

- **Development**: Logs only to console
- **Production**: Logs to Supabase `events` table

## Constants & Design System

### Core Constants

```typescript
import { CLEAR30_CONSTANTS } from '@clear30/shared';

// Design system values
CLEAR30_CONSTANTS.cornerRadius    // 21px
CLEAR30_CONSTANTS.cardPadding     // 16px  
CLEAR30_CONSTANTS.outlineWidth    // 3px

// Spacing system
CLEAR30_CONSTANTS.spacing.horizontal  // 25px
CLEAR30_CONSTANTS.spacing.card        // 14px
CLEAR30_CONSTANTS.spacing.headingTop  // 10px

// Typography
CLEAR30_CONSTANTS.fontSize.heading1   // 32px
CLEAR30_CONSTANTS.fontSize.heading2   // 25px
CLEAR30_CONSTANTS.fontSize.heading3   // 22px
CLEAR30_CONSTANTS.fontSize.default    // 19px
CLEAR30_CONSTANTS.fontSize.small      // 17px
CLEAR30_CONSTANTS.fontSize.tiny       // 14px
CLEAR30_CONSTANTS.fontSize.mini       // 10px
```

### Tailwind CSS Classes

The shared Tailwind config provides utility classes based on constants:

```typescript
// Spacing utilities
className="p-card"           // padding: 14px
className="gap-card"         // gap: 14px  
className="px-horizontal"    // padding-left/right: 25px

// Typography utilities
className="text-heading1"    // font-size: 32px, weight: 600
className="text-default"     // font-size: 19px
className="text-tiny"        // font-size: 14px

// Color utilities
className="bg-clear-blue"    // background: #5BB4A9
className="text-clear-text"  // color: #000000
className="shadow-clear"     // box-shadow with clear30 shadow

// Border utilities
className="rounded-clear"    // border-radius: 21px
className="border-3"         // border-width: 3px
```

## Development Rules

### 1. Always Use Constants

```typescript
// ✅ Correct
spacing={CLEAR30_CONSTANTS.spacing.card}
fontSize={CLEAR30_CONSTANTS.fontSize.small}
color={CLEAR30_CONSTANTS.colors.blue}

// ❌ Avoid magic numbers
spacing={14}
fontSize="17px"  
color="#5BB4A9"
```

### 2. Always Use Predefined Components

```typescript
// ✅ Correct - Use shared components
import { Card, VStack, TextSizes } from '@clear30/shared';

<Card>
  <VStack spacing={CLEAR30_CONSTANTS.spacing.card}>
    <TextSizes.Heading3>Title</TextSizes.Heading3>
  </VStack>
</Card>

// ❌ Avoid creating custom versions
<div className="bg-white rounded-lg p-4 shadow">
  <div className="flex flex-col gap-4">
    <h3 className="text-xl font-semibold">Title</h3>
  </div>
</div>
```

### 3. Only Use Predefined Animations

```typescript
// ✅ Correct - Use shared animations
import { PAGE_TRANSITIONS } from '@clear30/shared';
<motion.div {...PAGE_TRANSITIONS.fade}>

// ❌ Don't create custom animations inline
<motion.div 
  initial={{ opacity: 0 }} 
  animate={{ opacity: 1 }}
>
```

### 4. Add New Animations to Central File

When you need a new animation:

1. Add it to `packages/shared/src/lib/animations.ts`
2. Export it in the appropriate category
3. Document the use case
4. Update this README

### 5. Context Over URL Routing

```typescript
// ✅ Correct - Context-based navigation
const { setCurrentPage } = useAppContext();
setCurrentPage(PageType.Detail);

// ❌ Avoid URL-based routing for app navigation
navigate('/detail');
```

### 6. Persist State in localStorage

```typescript
// ✅ Correct - Persist navigation and user state
useEffect(() => {
  localStorage.setItem('appState', JSON.stringify(appState));
}, [appState]);

// Load state on initialization
const [appState] = useState(() => {
  const saved = localStorage.getItem('appState');
  return saved ? JSON.parse(saved) : defaultState;
});
```

### 7. Follow Import Patterns

```typescript
// ✅ Correct imports
import { Component } from '@clear30/shared';
import { CLEAR30_CONSTANTS } from '@clear30/shared';

// ❌ Avoid relative imports to shared
import { Component } from '../../../shared/src/components/Component';
```

---

## Contributing

When adding new components or utilities to the shared library:

1. Follow existing patterns and naming conventions
2. Add comprehensive TypeScript types
3. Include JSDoc documentation
4. Export from the main `index.ts` file
5. Update this README with usage examples
6. Ensure all new components use `CLEAR30_CONSTANTS` for styling
7. Add appropriate Tailwind classes to the config if needed

## Support

For questions about the shared library or help implementing these patterns, reach out to the Clear30 development team or create an issue in the repository.

## Favicon Management

The shared package provides a centralized favicon system that ensures consistency across all Clear30 web applications.

### How It Works

1. **Centralized Storage**: Favicon files are stored in `packages/shared/src/assets/images/`
2. **Automatic Distribution**: Favicons are automatically copied to all consuming apps' public directories
3. **Consistent Paths**: All apps use the same favicon paths for consistency

### Available Favicon Formats

- `favicon.svg` - Modern SVG format (recommended)
- `favicon.ico` - Traditional ICO format for older browsers
- `favicon.png` - PNG format for fallback support

### Updating Favicons

To update favicons across all apps:

```bash
# From the root of the monorepo
pnpm update-favicons

# Or run the script directly
node scripts/update-favicons.js
```
