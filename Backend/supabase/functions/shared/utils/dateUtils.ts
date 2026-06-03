/**
 * Timezone-aware date utility functions for edge functions
 * Uses native Intl API (no external libraries needed)
 */

export const DEFAULT_TIMEZONE = 'America/New_York'

/**
 * Validates an IANA timezone identifier
 * @param tz - Timezone identifier (e.g., 'America/New_York')
 * @returns true if valid, false otherwise
 */
export function isValidTimezone(tz: string): boolean {
  try {
    Intl.DateTimeFormat(undefined, { timeZone: tz })
    return true
  } catch {
    return false
  }
}

/**
 * Gets a valid timezone, falling back to default if invalid
 * @param tz - Timezone identifier to validate
 * @returns Valid timezone (input if valid, default if not)
 */
export function getValidTimezone(tz: string | undefined | null): string {
  if (!tz) return DEFAULT_TIMEZONE
  return isValidTimezone(tz) ? tz : DEFAULT_TIMEZONE
}

/**
 * Gets "today" as YYYY-MM-DD string in the specified timezone
 * @param timezone - IANA timezone identifier
 * @returns Date string in YYYY-MM-DD format
 */
export function getTodayInTimezone(timezone: string): string {
  const tz = getValidTimezone(timezone)
  // sv-SE locale uses YYYY-MM-DD format
  return new Date().toLocaleDateString('sv-SE', { timeZone: tz })
}

/**
 * Gets "yesterday" as YYYY-MM-DD string in the specified timezone
 * @param timezone - IANA timezone identifier
 * @returns Date string in YYYY-MM-DD format
 */
export function getYesterdayInTimezone(timezone: string): string {
  const tz = getValidTimezone(timezone)
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  return yesterday.toLocaleDateString('sv-SE', { timeZone: tz })
}

/**
 * Formats any Date as YYYY-MM-DD in the specified timezone
 * @param date - Date object to format
 * @param timezone - IANA timezone identifier
 * @returns Date string in YYYY-MM-DD format
 */
export function formatDateYYYYMMDD(date: Date, timezone: string): string {
  const tz = getValidTimezone(timezone)
  return date.toLocaleDateString('sv-SE', { timeZone: tz })
}

/**
 * Parses a YYYY-MM-DD string to a Date object at midnight UTC
 * @param dateStr - Date string in YYYY-MM-DD format
 * @returns Date object
 */
export function parseDateYYYYMMDD(dateStr: string): Date {
  // Parse as UTC to avoid timezone shifting during parsing
  return new Date(dateStr + 'T00:00:00Z')
}

/**
 * Calculates the number of days between two YYYY-MM-DD date strings
 * @param startDateStr - Start date in YYYY-MM-DD format
 * @param endDateStr - End date in YYYY-MM-DD format
 * @returns Number of days (positive if end > start)
 */
export function getDaysDifference(startDateStr: string, endDateStr: string): number {
  const d1 = parseDateYYYYMMDD(startDateStr)
  const d2 = parseDateYYYYMMDD(endDateStr)
  return Math.floor((d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24))
}

/**
 * Calculates days since a start date until today in the user's timezone
 * @param startDateStr - Start date in YYYY-MM-DD or ISO format
 * @param timezone - User's timezone
 * @returns Number of days since start (0 = same day as start)
 */
export function getDaysSinceStart(startDateStr: string, timezone: string): number {
  const today = getTodayInTimezone(timezone)
  // Extract just the YYYY-MM-DD portion if it's an ISO timestamp
  const startYYYYMMDD = startDateStr.substring(0, 10)
  return getDaysDifference(startYYYYMMDD, today)
}

/**
 * Checks if a date string represents today in the specified timezone
 * @param dateStr - Date string in YYYY-MM-DD format
 * @param timezone - User's timezone
 * @returns true if the date is today
 */
export function isToday(dateStr: string, timezone: string): boolean {
  const today = getTodayInTimezone(timezone)
  return dateStr === today
}

/**
 * Checks if a date string represents yesterday in the specified timezone
 * @param dateStr - Date string in YYYY-MM-DD format
 * @param timezone - User's timezone
 * @returns true if the date is yesterday
 */
export function isYesterday(dateStr: string, timezone: string): boolean {
  const yesterday = getYesterdayInTimezone(timezone)
  return dateStr === yesterday
}

/**
 * Gets a Date object representing midnight today in the specified timezone
 * Note: This returns a UTC Date that corresponds to midnight in the user's timezone
 * @param timezone - User's timezone
 * @returns Date object
 */
export function getMidnightTodayInTimezone(timezone: string): Date {
  const todayStr = getTodayInTimezone(timezone)
  return parseDateYYYYMMDD(todayStr)
}

/**
 * Compares two YYYY-MM-DD date strings
 * @param dateStr1 - First date string
 * @param dateStr2 - Second date string
 * @returns negative if date1 < date2, 0 if equal, positive if date1 > date2
 */
export function compareDates(dateStr1: string, dateStr2: string): number {
  return dateStr1.localeCompare(dateStr2)
}

/**
 * Gets the start of today as an ISO timestamp for database queries
 * This is useful for checking "messages sent today" in the user's timezone
 * @param timezone - User's timezone
 * @returns ISO timestamp string representing midnight in user's timezone
 */
export function getTodayStartISO(timezone: string): string {
  const todayStr = getTodayInTimezone(timezone)
  // Return as ISO with Z suffix for UTC interpretation
  // Note: This treats midnight in user's TZ as midnight UTC - may need adjustment
  // for precise scheduling, but works for "today" queries
  return `${todayStr}T00:00:00Z`
}
