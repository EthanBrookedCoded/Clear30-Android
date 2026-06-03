import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
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
