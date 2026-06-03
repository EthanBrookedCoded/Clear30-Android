# Clear30 Web Apps

A monorepo-based web app infrastructure for Clear30, allowing multiple React-based web applications to be hosted under `clear30.org/apps/...` with shared utilities, styling, and logging capabilities.

## Project Structure

```
packages/
├── shared/           # Shared utilities, components, and styling
│   ├── src/
│   │   ├── lib/     # Supabase, logging, utils, types, constants
│   │   ├── components/
│   │   │   ├── layout/  # AppLayout, Header
│   │   │   └── ui/      # Card, TextIconButton, TinyTextButton, TextSizes
│   │   ├── hooks/   # useLogging, useSupabase, useQueryParams
│   │   └── styles/  # Tailwind CSS configuration
│   └── package.json
├── apps/            # Individual web applications
│   ├── test-app/    # Component showcase and testing
│   └── supabase-test/ # Supabase integration testing
└── scripts/         # Build and deployment scripts
```

## Getting Started

### Prerequisites

- Node.js 18+
- pnpm 8+

### Installation

```bash
# Install all dependencies
pnpm install

# Build shared package
pnpm --filter @clear30/shared build

# Start development
pnpm dev
```

## Development Workflow

### Adding New Apps

1. Create new directory in `packages/apps/`
2. Initialize with Vite + React + TypeScript
3. Import shared package: `@clear30/shared`
4. Configure Tailwind to extend shared config
5. Add build script to root package.json

### Environment Variables

Each app should have its own `.env` file:
```
VITE_SUPABASE_URL_PROD=your_prod_url
VITE_SUPABASE_ANON_KEY_PROD=your_prod_key
VITE_SUPABASE_URL_QA=your_local_url
VITE_SUPABASE_ANON_KEY_QA=your_local_key
```

**Note**: QA environment is controlled via code flag in `packages/shared/src/lib/config.ts`, not environment variables.

## Components

### UI Components
- ✅ **Card**: Primary container component with gradient and outline support
- ✅ **TextIconButton**: Main button component with icon and text
- ✅ **TinyTextButton**: Compact button for secondary actions
- ✅ **TextSizes**: Typography components matching SwiftUI sizes
  - Heading1, Heading2, Heading3
  - TextDefault, TextSmall, TextTiny, TextMini
- ✅ **Stack Layout**: SwiftUI-style layout components
  - VStack: Vertical stack with leading/center/trailing alignment
  - HStack: Horizontal stack with top/center/bottom alignment

### Layout Components
- ✅ **AppLayout**: Standard app layout with header
- ✅ **Header**: App header with navigation

## Current Status

- ✅ **Phase 1**: Monorepo structure and shared package setup
- ✅ **Phase 2**: Supabase abstraction layer with QA/prod switching
- ✅ **Phase 3**: Event logging system with user tracking
- ✅ **Phase 4**: Centralized Tailwind configuration with brand guidelines
- 🔄 **Phase 5**: Component recreation from Swift UI
  - ✅ Card component
  - ✅ TextIconButton
  - ✅ TinyTextButton
  - ✅ Typography system
  - ⏳ More components in progress
- ⏳ **Phase 6**: First app (supplement generator)
- ⏳ **Phase 7**: Build and deployment pipeline
- ⏳ **Phase 8**: Testing and optimization

## Tech Stack

- **Frontend**: React 18+ with TypeScript
- **Build Tool**: Vite
- **Styling**: Tailwind CSS with custom theme
- **Icons**: Feather Icons (react-feather)
- **Backend**: Supabase integration
- **Hosting**: AWS S3 (existing setup)
- **Deployment**: Static build deployment to S3

## Success Criteria

- ✅ All apps share consistent styling and branding
- ✅ Standardized event logging across all apps (console in dev, Supabase in prod)
- ✅ Easy Supabase integration for all apps
- ✅ Mobile-first responsive design
- ✅ Query parameter handling for user tracking and app detection
- ✅ QA/production environment switching via code flag
- ✅ Simple deployment to AWS S3
- ✅ Scalable architecture for future apps
- ✅ React components that mirror existing Swift UI components
- ✅ Consistent icon usage with Feather icons

## Documentation

For detailed component documentation and usage examples, see the [shared package documentation](packages/shared/x_readme_shared.md).