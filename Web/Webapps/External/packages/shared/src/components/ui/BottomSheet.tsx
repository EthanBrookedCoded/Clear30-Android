import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X } from 'react-feather';
import { CLEAR30_CONSTANTS } from '../../lib/constants';
import { BOTTOM_SHEET_ANIMATIONS } from '../../lib/animations';
import { TextSizes } from './TextSizes';

export interface BottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  showCloseButton?: boolean;
  maxHeight?: string;
  className?: string;
}

export const BottomSheet: React.FC<BottomSheetProps> = ({
  isOpen,
  onClose,
  children,
  title,
  showCloseButton = true,
  maxHeight = '90vh',
  className = ''
}) => {
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Animated Backdrop */}
          <motion.div
            {...BOTTOM_SHEET_ANIMATIONS.backdrop}
            className="fixed inset-0 bg-black bg-opacity-50 z-40"
            onClick={onClose}
          />

          {/* Animated Bottom Sheet */}
          <motion.div
            {...BOTTOM_SHEET_ANIMATIONS.sheet}
            className={`fixed bottom-0 left-0 right-0 z-50 bg-white rounded-t-[21px] shadow-lg flex flex-col ${className}`}
            style={{
              maxHeight,
              height: maxHeight === '100dvh' ? '100dvh' : undefined,
              paddingBottom: `${CLEAR30_CONSTANTS.spacing.horizontal}px`,
              paddingLeft: `${CLEAR30_CONSTANTS.spacing.horizontal}px`,
              paddingRight: `${CLEAR30_CONSTANTS.spacing.horizontal}px`,
            }}
          >
            {/* Header with Title and Close Button */}
            {(title || showCloseButton) && (
              <div className="relative flex-shrink-0 flex justify-between" style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.horizontal}px` }}>
                {title && (
                  <TextSizes.Heading3>
                    {title}
                  </TextSizes.Heading3>
                )}

                {showCloseButton && (
                  <button
                    onClick={onClose}
                    className="absolute top-4 right-0 p-1.5 rounded-full transition-colors hover:bg-gray-200"
                    style={{ backgroundColor: CLEAR30_CONSTANTS.colors.gray }}
                    aria-label="Close"
                  >
                    <X size={20} className="text-black" />
                  </button>
                )}
              </div>
            )}

            {/* Content Area - Now supports scrolling */}
            <div 
              className="flex-1 min-h-0 overflow-hidden"
              style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px` }}
            >
              {children}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};
