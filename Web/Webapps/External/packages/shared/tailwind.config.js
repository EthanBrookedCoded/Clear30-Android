/**
 * CENTRAL Tailwind Configuration for Clear30 Web Apps
 * 
 * This is the single source of truth for all Tailwind CSS configuration
 * across the entire monorepo. All apps should reference this config.
 */
const { CLEAR30_CONSTANTS } = require('./src/lib/constants');

module.exports = {
  content: [
    './src/**/*.{js,ts,jsx,tsx}',
    '../../apps/*/src/**/*.{js,ts,jsx,tsx}',
    '../../apps/*/index.html'
  ],
  theme: {
    extend: {
      colors: {
        'clear-blue': CLEAR30_CONSTANTS.colors.blue,
        'clear-green': CLEAR30_CONSTANTS.colors.green,
        'clear-white': CLEAR30_CONSTANTS.colors.white,
        'clear-black': CLEAR30_CONSTANTS.colors.black,
        'clear-gray': CLEAR30_CONSTANTS.colors.gray,
        'clear-shadow': CLEAR30_CONSTANTS.colors.shadow,
        'clear-text': CLEAR30_CONSTANTS.colors.text,
        'clear-button': CLEAR30_CONSTANTS.colors.button,
      },
      backgroundImage: {
        'clear30-gradient': CLEAR30_CONSTANTS.gradients.clear30,
      },
      fontFamily: {
        'lexend': ['Lexend', 'sans-serif'],
      },
      fontSize: {
        'heading1': [CLEAR30_CONSTANTS.fontSize.heading1, { lineHeight: '1.2', fontWeight: '600' }],
        'heading2': [CLEAR30_CONSTANTS.fontSize.heading2, { lineHeight: '1.2', fontWeight: '600' }],
        'heading3': [CLEAR30_CONSTANTS.fontSize.heading3, { lineHeight: '1.2', fontWeight: '500' }],
        'default': [CLEAR30_CONSTANTS.fontSize.default, { lineHeight: '1.4' }],
        'small': [CLEAR30_CONSTANTS.fontSize.small, { lineHeight: '1.4' }],
        'tiny': [CLEAR30_CONSTANTS.fontSize.tiny, { lineHeight: '1.4' }],
        'mini': [CLEAR30_CONSTANTS.fontSize.mini, { lineHeight: '1.4' }],
      },
      spacing: {
        'horizontal': `${CLEAR30_CONSTANTS.spacing.horizontal}px`,
        'heading-top': `${CLEAR30_CONSTANTS.spacing.headingTop}px`,
        'card': `${CLEAR30_CONSTANTS.spacing.card}px`,
        'card-padding': `${CLEAR30_CONSTANTS.cardPadding}px`,
      },
      borderRadius: {
        'clear': `${CLEAR30_CONSTANTS.cornerRadius}px`,
      },
      boxShadow: {
        'clear': `0 0 6px ${CLEAR30_CONSTANTS.colors.shadow}`,
      },
      borderWidth: {
        '3': `${CLEAR30_CONSTANTS.outlineWidth}px`,
      }
    },
  },
  plugins: [],
}