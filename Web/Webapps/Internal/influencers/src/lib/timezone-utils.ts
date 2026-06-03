import { toZonedTime, format as formatTz } from 'date-fns-tz';

// Eastern Time Zone
const EASTERN_TIMEZONE = 'America/New_York';

/**
 * Get the day key (YYYY-MM-DD) that a timestamp belongs to.
 * Uses EST so that 7pm EST Jan 21 (= midnight UTC Jan 22) → "2025-01-21".
 * Use this for grouping data by "day" consistently across the app.
 */
export function getDayKey(timestamp: Date | string): string {
  const date = typeof timestamp === 'string' ? new Date(timestamp) : timestamp;
  const estDate = toZonedTime(date, EASTERN_TIMEZONE);
  return formatTz(estDate, 'yyyy-MM-dd', { timeZone: EASTERN_TIMEZONE });
}
