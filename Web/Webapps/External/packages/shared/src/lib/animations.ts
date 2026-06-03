
// ============================================================================
// PAGE TRANSITION ANIMATIONS
// ============================================================================
export const PAGE_TRANSITIONS = {
  // Neutral fade/scale transition (no directional movement)
  fade: {
    initial: { opacity: 0, scale: 0.98 },
    animate: { opacity: 1, scale: 1 },
    exit: { opacity: 0, scale: 0.98 },
    transition: {
      type: "tween",
      ease: "easeInOut",
      duration: 0.3
    }
  },
} as const;

// ============================================================================
// BOTTOM SHEET ANIMATIONS
// ============================================================================
export const BOTTOM_SHEET_ANIMATIONS = {
  backdrop: {
    initial: { opacity: 0 },
    animate: { opacity: 1 },
    exit: { opacity: 0 },
    transition: { duration: 0.15 }
  },

  sheet: {
    initial: { y: '100%', opacity: 0 },
    animate: { y: 0, opacity: 1 },
    exit: { y: '100%', opacity: 0 },
    transition: { 
      type: "spring",
      damping: 25,
      stiffness: 200,
      duration: 0.3
    }
  }
} as const;

// ============================================================================
// LOADING ANIMATIONS
// ============================================================================
export const LOADING_ANIMATIONS = {
  // Text transition animations
  textTransition: {
    initial: { opacity: 0, y: 10 },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: -10 },
    transition: {
      duration: 0.3,
      ease: "easeInOut"
    }
  },
} as const;

// ============================================================================
// UI ELEMENT ANIMATIONS
// ============================================================================
export const UI_ANIMATIONS = {
  // Back button show/hide
  backButton: {
    initial: { opacity: 0, scale: 0.8 },
    animate: { opacity: 1, scale: 1 },
    exit: { opacity: 0, scale: 0.8 },
    transition: { duration: 0.2, ease: "easeOut" }
  },
  
  // Instructions expand/collapse
  instructions: {
    initial: { opacity: 0, height: 0 },
    animate: { opacity: 1, height: "auto" },
    exit: { opacity: 0, height: 0 },
    transition: { duration: 0.3, ease: "easeInOut" }
  },
} as const;

// ============================================================================
// ANIMATE PRESENCE PROPS
// ============================================================================
export const ANIMATE_PRESENCE_PROPS = {
  mode: "wait" as const,
  initial: false
} as const;
