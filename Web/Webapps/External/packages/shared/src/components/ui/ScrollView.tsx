import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { UI_ANIMATIONS } from '../../lib/animations';
import { CLEAR30_CONSTANTS } from '../../lib/constants';

export interface ScrollViewProps {
  children: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
  showScrollIndicator?: boolean;
}

export const ScrollView: React.FC<ScrollViewProps> = ({
  children,
  className = '',
  style,
  showScrollIndicator = false
}) => {
  const [canScrollDown, setCanScrollDown] = useState(true);
  const [hasReachedBottom, setHasReachedBottom] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const checkScrollPosition = () => {
    if (!scrollRef.current) return;

    const { scrollTop, scrollHeight, clientHeight } = scrollRef.current;

    // Only check scroll position after content is fully loaded
    if (scrollHeight <= 0 || clientHeight <= 0) return;

    const isAtBottom = scrollTop + clientHeight >= scrollHeight - 1; // 1px tolerance

    // Track if user has reached bottom at least once (but only after initialization)
    if (isAtBottom && !hasReachedBottom) {
      setHasReachedBottom(true);
    }

    // Show if not at bottom
    setCanScrollDown(!isAtBottom);
  };

  useEffect(() => {
    const scrollElement = scrollRef.current;
    if (!scrollElement) return;

    // Use a small delay to ensure content is fully rendered
    const initializeTimer = setTimeout(() => {
      checkScrollPosition();
    }, 100);

    const handleScroll = () => checkScrollPosition();
    const handleResize = () => checkScrollPosition();

    scrollElement.addEventListener('scroll', handleScroll);
    window.addEventListener('resize', handleResize);

    return () => {
      clearTimeout(initializeTimer);
      scrollElement.removeEventListener('scroll', handleScroll);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <div
      ref={scrollRef}
      className={`overflow-y-auto px-[15px] -mx-[15px] relative ${className}`}
      style={style}
    >
      {children}

      {/* Scroll Indicator - Fixed at bottom of viewport */}
      <AnimatePresence>
        {showScrollIndicator && canScrollDown && !hasReachedBottom && (
          <motion.div
            className="fixed bottom-4 right-4 flex items-center justify-center w-8 h-8 rounded-full pointer-events-none z-50"
            style={{ backgroundColor: CLEAR30_CONSTANTS.colors.gray }}
            {...UI_ANIMATIONS.backButton}
          >
            <svg
              className="w-4 h-4 text-black"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M19 14l-7 7m0 0l-7-7m7 7V3"
              />
            </svg>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
