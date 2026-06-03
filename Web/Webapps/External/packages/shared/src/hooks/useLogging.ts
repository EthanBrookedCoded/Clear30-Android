import { useCallback } from 'react';
import { loggingService } from '../lib/logging';

export const useLogging = () => {
  const logAppOpen = useCallback(async (): Promise<void> => {
    await loggingService.logAppOpen();
  }, []);

  const logAppClose = useCallback(async (): Promise<void> => {
    await loggingService.logAppClose();
  }, []);

  const logPageView = useCallback(async (pageName: string, extraInfo?: Record<string, any>): Promise<void> => {
    await loggingService.logPageView(pageName, extraInfo);
  }, []);

  const logButtonClick = useCallback(async (
    buttonName: string,
    additionalData?: Record<string, any>
  ): Promise<void> => {
    await loggingService.logButtonClick(buttonName, additionalData);
  }, []);

  return {
    logAppOpen,
    logAppClose,
    logPageView,
    logButtonClick,
  };
};
