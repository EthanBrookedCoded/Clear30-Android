import { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { LayoutProps } from '../../lib/types';
import { useLogging } from '../../hooks/useLogging';
import { CLEAR30_CONSTANTS } from '../../lib/constants';
import { UI_ANIMATIONS } from '../../lib/animations';
import { ChevronLeft } from 'react-feather';
import { Helmet } from 'react-helmet';

// Extend the existing LayoutProps interface
interface ExtendedLayoutProps extends LayoutProps {
  appName?: string;
  onBack?: () => void;
  favicon?: string; // Optional custom favicon URL
  appTitle?: string; // Optional custom app title
}

export const AppLayout: React.FC<ExtendedLayoutProps> = ({
  children,
  className = '',
  appName,
  onBack,
  favicon,
  appTitle
}) => {
  const { logAppOpen, logAppClose } = useLogging();

  const finalFavicon = favicon || './favicon.svg';
  const finalTitle = appTitle || appName || 'Clear30 App';

  useEffect(() => {
    logAppOpen();
  }, []);

  // Log app close when user leaves the site
  useEffect(() => {
    const handleBeforeUnload = () => {
      logAppClose();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden' && appName) {
        logAppClose();
      } else if (document.visibilityState === 'visible' && appName) {
        logAppOpen();
      }
    };

    // Listen for page unload/close
    window.addEventListener('beforeunload', handleBeforeUnload);

    // Listen for tab/window visibility changes (mobile apps, tab switching)
    document.addEventListener('visibilitychange', handleVisibilityChange);

    // Cleanup
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);

  return (
    <>
      <Helmet>
        <title>{finalTitle}</title>

        {/* Favicon - Multiple formats for better browser compatibility */}
        <link rel="icon" type="image/svg+xml" href={finalFavicon} />
        <link rel="apple-touch-icon" href={finalFavicon} />

        <meta
          name="viewport"
          content="width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no, viewport-fit=cover"
        />
        {/* Additional meta tags for iOS */}
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
        <style>
          {`
            html, body {
              touch-action: none;
              overflow: hidden;
              -webkit-touch-callout: none;
              -webkit-user-select: none;
              -webkit-text-size-adjust: none;
            }
          `}
        </style>
      </Helmet>
      <div className={`h-[100dvh] w-screen max-w-md mx-auto overflow-hidden bg-clear-white ${className}`} >
        <AnimatePresence>
          {onBack && (
            <motion.div
              className="absolute top-4 left-4 z-10"
              {...UI_ANIMATIONS.backButton}
            >
              <button
                onClick={onBack}
                className="flex items-center justify-center w-8 h-8 bg-clear-gray backdrop-blur-sm rounded-full shadow-sm transition-colors"
              >
                <ChevronLeft size={18} className="text-gray-700" />
              </button>
            </motion.div>
          )}
        </AnimatePresence>
        <main
          className="h-full w-full overflow-hidden"
          style={{
            padding: `${CLEAR30_CONSTANTS.spacing.horizontal}px ${CLEAR30_CONSTANTS.spacing.horizontal}px`,
            paddingTop: onBack ? `${CLEAR30_CONSTANTS.spacing.horizontal + 48}px` : `${CLEAR30_CONSTANTS.spacing.horizontal}px`
          }}
        >
          {children}
        </main>
      </div>
    </>
  );
};
