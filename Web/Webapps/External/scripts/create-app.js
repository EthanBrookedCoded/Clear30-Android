#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const readline = require('readline');

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

// Promisify readline question
const question = (query) => new Promise((resolve) => rl.question(query, resolve));

async function createApp() {
  try {
    // Get app name
    const appName = await question('Enter the app name (kebab-case): ');
    if (!appName.match(/^[a-z0-9-]+$/)) {
      throw new Error('App name must be in kebab-case (e.g., my-new-app)');
    }

    const appPath = path.join(process.cwd(), 'packages', 'apps', appName);

    // Create apps directory if it doesn't exist
    console.log('\n📁 Creating app directory...');
    fs.mkdirSync(path.join(process.cwd(), 'packages', 'apps'), { recursive: true });

    // Initialize new Vite project with React + TypeScript
    console.log('🚀 Initializing Vite project...');
    process.chdir(path.join(process.cwd(), 'packages', 'apps'));
    execSync(`pnpm create vite ${appName} --template react-ts`, { stdio: 'inherit' });
    process.chdir(appPath);

    // Remove unnecessary boilerplate files
    console.log('🧹 Cleaning up boilerplate...');
    const filesToRemove = [
      'src/App.css',
      'src/assets',
      'src/vite-env.d.ts',
      'public/vite.svg',
      '.eslintrc.cjs',
      'README.md',
      'src/index.css',
      'src/App.tsx',
      '.gitignore',
    ];

    filesToRemove.forEach(file => {
      const fullPath = path.join(appPath, file);
      if (fs.existsSync(fullPath)) {
        if (fs.lstatSync(fullPath).isDirectory()) {
          fs.rmSync(fullPath, { recursive: true });
        } else {
          fs.unlinkSync(fullPath);
        }
      }
    });

    // Create package.json for the new app
    const packageJson = {
      name: `@clear30/${appName}`,
      private: true,
      version: '0.0.1',
      type: "module",
      scripts: {
        "dev": "vite",
        "build": "tsc && vite build",
        "lint": "eslint src --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
        "preview": "vite preview"
      },
      dependencies: {
        "@clear30/shared": "workspace:*",
        "react": "^18.2.0",
        "react-dom": "^18.2.0",
        "react-feather": "^2.0.10"
      },
      devDependencies: {
        "@types/react": "^18.2.0",
        "@types/react-dom": "^18.2.0",
        "@typescript-eslint/eslint-plugin": "^5.57.1",
        "@typescript-eslint/parser": "^5.57.1",
        "@vitejs/plugin-react": "^4.0.0",
        "autoprefixer": "^10.4.14",
        "eslint": "^8.38.0",
        "eslint-plugin-react-hooks": "^4.6.0",
        "eslint-plugin-react-refresh": "^0.3.4",
        "postcss": "^8.4.23",
        "tailwindcss": "^3.3.2",
        "typescript": "^5.0.2",
        "vite": "^4.3.2"
      }
    };

    // Write package.json
    fs.writeFileSync(
      path.join(appPath, 'package.json'),
      JSON.stringify(packageJson, null, 2)
    );

    // Create tailwind.config.js
    const tailwindConfig = `import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import sharedConfig from '@clear30/shared/tailwind.config.js';

/** @type {import('tailwindcss').Config} */
export default {
  // Extend the shared config
  ...sharedConfig,
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
    "../../shared/src/**/*.{js,ts,jsx,tsx}",
  ],
};
`;

    fs.writeFileSync(
      path.join(appPath, 'tailwind.config.js'),
      tailwindConfig
    );

    // Create postcss.config.js
    const postcssConfig = `export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
`;

    fs.writeFileSync(
      path.join(appPath, 'postcss.config.js'),
      postcssConfig
    );

    // Create simplified index.html
    const indexHtml = `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>${appName}</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>`;

    fs.writeFileSync(
      path.join(appPath, 'index.html'),
      indexHtml
    );

    // Create vite.config.ts with base URL
    const viteConfig = `import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/webapps/${appName}/',
  server: {
    port: 5173,
  },
})
`;

    fs.writeFileSync(
      path.join(appPath, 'vite.config.ts'),
      viteConfig
    );

    // Create src/App.tsx with AppLayout
    const appTsx = `import React, { useEffect } from 'react';
import { AppLayout } from '@clear30/shared/src/components/layout/AppLayout';
import { Card } from '@clear30/shared/src/components/ui/Card';
import { TextIconButton } from '@clear30/shared/src/components/ui/TextIconButton';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import { useLogging } from '@clear30/shared/src/hooks/useLogging';
import { useSupabase } from '@clear30/shared/src/hooks/useSupabase';
import { Home } from 'react-feather';

function App() {
  const { logPageView } = useLogging();
  const { loading } = useSupabase();

  // Log page view on mount
  useEffect(() => {
    logPageView('HomePage');
  }, [logPageView]);

  return (
    <AppLayout>
      <div style={{ padding: \`\${CLEAR30_CONSTANTS.spacing.headingTop}px \${CLEAR30_CONSTANTS.spacing.horizontal}px\` }}>
        <Card>
          <h1 className="text-heading1">Welcome to ${appName}</h1>
          <div style={{ height: CLEAR30_CONSTANTS.spacing.card }} />
          <TextIconButton
            text="Home"
            icon={Home}
            action={() => {}}
            gradient={CLEAR30_CONSTANTS.gradients.clear30}
          />
        </Card>
      </div>
    </AppLayout>
  );
}

export default App;
`;

    fs.writeFileSync(
      path.join(appPath, 'src', 'App.tsx'),
      appTsx
    );

    // Create simplified main.tsx
    const mainTsx = `import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import '@clear30/shared/src/styles/globals.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);`;

    fs.writeFileSync(
      path.join(appPath, 'src', 'main.tsx'),
      mainTsx
    );

    // Install dependencies
    console.log('📦 Installing dependencies...');
    process.chdir(path.join(process.cwd(), '..', '..', '..')); // Go back to root
    
    // Update root package.json dev script
    console.log('📝 Updating root package.json...');
    const rootPackageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));
    
    // Parse existing dev script to add new app
    const devScript = rootPackageJson.scripts.dev;
    const newDevScript = devScript.replace(
      'dev"',
      `dev" "pnpm --filter @clear30/${appName} dev"`
    );
    
    rootPackageJson.scripts.dev = newDevScript;
    
    // Write updated package.json
    fs.writeFileSync(
      'package.json',
      JSON.stringify(rootPackageJson, null, 2) + '\n'
    );
    
    // Install dependencies
    execSync('pnpm install', { stdio: 'inherit' });

    console.log(`
✅ App created successfully!

Your app is configured to be served at: /apps/${appName}/

To start development:

1. Start all apps including your new one:
   pnpm dev

   Your new app will be available at: http://localhost:5173/apps/${appName}/

2. Or start only your app:
   pnpm --filter @clear30/${appName} dev

For more information, check the README.md in the root directory.
`);

  } catch (error) {
    console.error('❌ Error:', error.message);
    process.exit(1);
  } finally {
    rl.close();
  }
}

createApp();
