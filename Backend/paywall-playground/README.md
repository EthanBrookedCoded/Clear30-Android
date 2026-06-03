# Helium Paywall Local Development Environment

This is a local development environment for creating and testing Helium paywall configurations with hot reloading. Edit your paywall code locally, see changes instantly, and export to Helium when ready.

## 🚀 Quick Start

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Start the development server:**
   ```bash
   npm run dev
   ```

   This will start a Vite dev server at `http://localhost:3000` with hot module replacement (HMR) enabled. Any changes you make to `src/App.tsx` will automatically reload in the browser.

3. **Export to Helium format:**
   ```bash
   npm run export
   ```

   This generates `helium.ts` in the root directory, ready to copy into Helium.

## 📁 Project Structure

```
paywall-playground/
├── src/
│   ├── App.tsx              # Main paywall component (EDIT THIS)
│   ├── localization.ts      # Mock localization hook
│   ├── withPaywallState.tsx # Mock paywall state HOC
│   ├── paywallConfig.ts     # Mock paywall configuration
│   ├── main.tsx             # React entry point
│   └── index.css            # Global styles
├── scripts/
│   └── export-to-helium.js  # Export script
├── helium.ts                # Generated file (for Helium)
├── package.json
├── vite.config.ts
└── tailwind.config.js
```

## ✏️ How to Use

### Making Changes

1. **Edit `src/App.tsx`** - This is your main paywall component. Make all your design and logic changes here.

2. **See changes instantly** - The dev server will automatically reload when you save. No need to manually refresh!

3. **Test interactions** - The mock paywall state provides realistic behavior:
   - Product selection
   - Subscribe button (shows alert)
   - Restore purchases (shows alert)
   - Dismiss functionality

### Exporting to Helium

When you're happy with your changes:

1. Run `npm run export`
2. Copy the contents of `helium.ts` 
3. Paste into Helium's paywall editor

The export script:
- Extracts the `BaseApp` component
- Preserves the fenced code markers (Helium's required format)
- Includes all necessary code (styles, fonts, etc.)
- Formats it exactly as Helium expects

## 🎨 Styling

This project uses **Tailwind CSS** for styling. All the Tailwind classes in your code will work in both the local dev environment and in Helium.

## 🔧 Mock Implementations

The following are mock implementations for local development:

- **`localization.ts`** - Returns fallback text (in Helium, this uses their translation system)
- **`withPaywallState.tsx`** - Provides mock paywall state and handlers
- **`paywallConfig.ts`** - Mock product configuration

These mocks allow you to develop and test locally without needing Helium's actual infrastructure.

## 📝 Important Notes

- **Fenced Code Markers**: The code between `// END FENCED CODE --- DO NOT EDIT ABOVE THIS LINE ---` and `// BEGIN FENCED CODE --- DO NOT EDIT BELOW THIS LINE ---` is what gets exported. Keep your main component code between these markers.

- **Imports**: The imports at the top (`useLocalization`, `withPaywallState`, `paywallConfig`) are preserved in the export. Helium will provide the real implementations.

- **Brand Guidelines**: Follow the brand style guidelines in the comments at the top of `App.tsx` when making design changes.

## 🐛 Troubleshooting

**Port already in use?**
- Change the port in `vite.config.ts` or kill the process using port 3000

**Styles not updating?**
- Make sure Tailwind is processing your classes. Check `tailwind.config.js` content paths.

**Export not working?**
- Make sure the fenced code markers are present in `src/App.tsx`
- Check that `src/App.tsx` is valid TypeScript/React

## 💡 Tips

- Use browser DevTools to inspect elements and test responsive behavior
- The mock products can be customized in `src/paywallConfig.ts` to match your actual products
- Test on different screen sizes using browser DevTools device emulation
- Keep the original `helium.ts` file as a reference for the exact format Helium expects

