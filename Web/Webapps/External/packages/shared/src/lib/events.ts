// Event types that can be logged - similar to Swift enum
export const LOGGABLE_EVENTS = {
  // App lifecycle events
  APP_OPEN: 'app_open',
  APP_CLOSE: 'app_close',

  // Page navigation events
  PAGE_VIEW: 'page_view',

  // User interaction events
  BUTTON_CLICK: 'button_click',
} as const;

// Type for event names
export type LoggableEvent = typeof LOGGABLE_EVENTS[keyof typeof LOGGABLE_EVENTS];
