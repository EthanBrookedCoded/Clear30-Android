import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  base: '/betterhelp-quiz/', // Base path for deployment
  plugins: [react()],
  server: {
    host: '0.0.0.0', // Listen on all network interfaces
    port: 5173, // Default Vite port
    strictPort: false, // Allow port to be changed if 5173 is taken
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
  },
})
