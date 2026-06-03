# Clear30 Admin Panel

A modern React-based admin panel for the Clear30 application, built with Vite and featuring a comprehensive UI component library.

## 🚀 Quick Start

### Prerequisites
- Node.js (v18 or higher)
- pnpm package manager

### Installation

```bash
# Navigate to the admin panel directory
cd Backend/admin-panel

# Install dependencies
pnpm install
```

### Development

```bash
# Start development server
pnpm dev
```

The development server will start on `http://localhost:5173` (default Vite port).

### Build for Production

```bash
# Build the project
pnpm build

# Preview the production build (optional)
pnpm preview
```

## 📋 Available Scripts

| Command | Description |
|---------|-------------|
| `pnpm dev` | Start development server with hot reload |
| `pnpm build` | Build the project for production |
| `pnpm preview` | Preview the production build locally |
| `pnpm lint` | Run ESLint for code quality checks |

## 🛠 Technology Stack

### Core
- **React** (v19.1.0) - UI framework
- **Vite** (v6.3.5) - Build tool and development server
- **React Router DOM** (v7.6.1) - Client-side routing
- **TypeScript** - Type safety (configured via jsconfig.json)

### UI Components
- **Radix UI** - Accessible, unstyled UI components
- **Tailwind CSS** (v4.1.7) - Utility-first CSS framework
- **Lucide React** - Icon library
- **Framer Motion** - Animation library

### Forms & Validation
- **React Hook Form** (v7.56.3) - Form handling
- **Zod** (v3.24.4) - Schema validation
- **Hookform Resolvers** - Form validation integration

### Data & State
- **Supabase** (v2.50.0) - Backend integration
- **Recharts** (v2.15.3) - Data visualization
- **Date-fns** (v4.1.0) - Date manipulation

### Additional Features
- **Sonner** - Toast notifications
- **CMDK** - Command menu component
- **Embla Carousel** - Carousel component
- **React Day Picker** - Date picker

## 📁 Project Structure

```
admin-panel/
├── src/
│   ├── components/        # Reusable UI components
│   ├── lib/              # Utility functions and configuration
│   ├── hooks/            # Custom React hooks
│   └── ...
├── public/               # Static assets
├── dist/                 # Production build output
├── package.json          # Project dependencies and scripts
├── vite.config.js        # Vite configuration
├── tailwind.config.js    # Tailwind CSS configuration
├── components.json       # Shadcn/ui component configuration
└── eslint.config.js      # ESLint configuration
```

## ⚙️ Configuration

### Auto Logout
The admin panel includes auto-logout functionality configured in `src/lib/config.js`:

- **Timeout**: 25 minutes of inactivity
- **Warning**: Shows 2 minutes before logout
- **Activity Events**: Mouse, keyboard, touch, and scroll events reset the timer

### Package Manager
This project uses **pnpm** (v10.4.1) as specified in the `packageManager` field. Make sure to use pnpm for consistency with lockfile.

## 🔧 Development

### Code Quality
- ESLint is configured with React-specific rules
- Run `pnpm lint` to check for code quality issues

### Hot Reload
Vite provides fast hot module replacement during development for an optimal developer experience.

### Modern React Features
This project uses React 19.1.0 with modern patterns and hooks.

## 📦 Build Output

The production build is optimized and outputs to the `dist/` directory. The build includes:
- Minified JavaScript and CSS
- Asset optimization
- Tree-shaking for smaller bundle sizes

## 🤝 Contributing

1. Make sure to use `pnpm` as the package manager
2. Follow the existing code style and run `pnpm lint` before committing
3. Test your changes in development mode before building for production

## 📄 License

This project is part of the Clear30 application suite. 