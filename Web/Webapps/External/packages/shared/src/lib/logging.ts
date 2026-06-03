import { supabaseService } from './supabase';
import { getConfig } from './config';
import { LOGGABLE_EVENTS } from './events';

export class LoggingService {
  private userId: string | null = null;
  private queryParams: Record<string, string> = {};
  private config = getConfig();
  private appName: string | null = null;

  constructor() {
    this.initializeUserData();
  }

  private initializeUserData() {
    const urlParams = new URLSearchParams(window.location.search);
    this.userId = urlParams.get('user_id');
    
    // Store all query params
    for (const [key, value] of urlParams.entries()) {
      this.queryParams[key] = value;
    }

    // Extract app name from URL path
    const pathname = window.location.pathname;
    const pathSegments = pathname.split('/').filter(segment => segment.length > 0);
    
    // Get the last non-empty segment as the app name
    if (pathSegments.length > 0) {
      this.appName = pathSegments[pathSegments.length - 1];
    }
  }

  private async logEvent(event: string, extraData?: Record<string, any>): Promise<void> {
    if (!this.userId) {
      console.warn('No user_id provided - skipping event logging');
      return;
    }

    // Include all query params (except user_id) plus any additional extra data
    const { user_id, ...otherQueryParams } = this.queryParams;
    const eventExtraData = {
      ...otherQueryParams,
      ...extraData
    };

    // Automatically include app name if available
    if (this.appName) {
      eventExtraData.app = this.appName;
    }

    const eventData = {
      user_id: this.userId,
      event: `webapp_${event}`,
      extra_data: Object.keys(eventExtraData).length > 0 ? eventExtraData : undefined
    };

    // During development, only log to console
    if (this.config.isDevelopment) {
      console.log('📊 Event Log (DEV):', eventData);
      return;
    }

    // In production, log to Supabase table
    try {
      const { error } = await supabaseService.insertData(
        'events',
        eventData,
        'public'
      );

      if (error) {
        console.error('Failed to log event:', error);
      } else {
        console.log('Event logged successfully:', eventData);
      }
    } catch (error) {
      console.error('Event logging error:', error);
    }
  }

  async logAppOpen(): Promise<void> {
    await this.logEvent(LOGGABLE_EVENTS.APP_OPEN);
  }

  async logAppClose(): Promise<void> {
    await this.logEvent(LOGGABLE_EVENTS.APP_CLOSE);
  }

  async logPageView(pageName: string, extraInfo?: Record<string, any>): Promise<void> {
    await this.logEvent(LOGGABLE_EVENTS.PAGE_VIEW, { page: pageName, ...extraInfo });
  }

  async logButtonClick(buttonName: string, additionalData?: Record<string, any>): Promise<void> {
    await this.logEvent(LOGGABLE_EVENTS.BUTTON_CLICK, { button: buttonName, ...additionalData });
  }
}

export const loggingService = new LoggingService();
