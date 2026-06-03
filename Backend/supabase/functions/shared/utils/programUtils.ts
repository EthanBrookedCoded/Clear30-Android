/**
 * Program and break calculation utilities
 * Provides timezone-aware functions for determining user's current program state
 */

import {
  getTodayInTimezone,
  getDaysDifference,
  compareDates,
  DEFAULT_TIMEZONE,
  getValidTimezone
} from './dateUtils.ts'

export type ProgramBreak = {
  name: string
  type: string  // 'clear30', 'clear30-start-soon', 'life'
  start_date: string
  end_date_override?: string  // First day OUT of the break (not last day in)
  assessment_response_id?: number
}

export type ProgramInfo = {
  type: 'clear30' | 'life'
  day: number
}

/**
 * Gets all Clear30 breaks (not start-soon) sorted by start date
 * @param programBreaks - Array of program breaks from user record
 * @returns Sorted array of Clear30 breaks
 */
export function getClear30Breaks(programBreaks: ProgramBreak[] | null): ProgramBreak[] {
  return (programBreaks || [])
    .filter(pb => pb.type === 'clear30' && pb.start_date)
    .sort((a, b) => compareDates(a.start_date.substring(0, 10), b.start_date.substring(0, 10)))
}

/**
 * Checks if a user is currently in an active Clear30 break
 * @param programBreaks - Array of program breaks
 * @param timezone - User's timezone
 * @returns The active break if found, null otherwise
 */
export function getActiveClear30Break(
  programBreaks: ProgramBreak[] | null,
  timezone: string
): ProgramBreak | null {
  const tz = getValidTimezone(timezone)
  const todayStr = getTodayInTimezone(tz)
  const clear30Breaks = getClear30Breaks(programBreaks)

  for (const pb of clear30Breaks) {
    const startDateStr = pb.start_date.substring(0, 10)

    // Must have started (skip if start date is in the future)
    if (compareDates(todayStr, startDateStr) < 0) continue

    // Check if ended via end_date_override
    if (pb.end_date_override) {
      const endDateStr = pb.end_date_override.substring(0, 10)
      // end_date_override is the first day OUT of the break
      // If today >= end_date_override, user is OUT of this break
      if (compareDates(todayStr, endDateStr) >= 0) continue
    }

    // Check if ended by days (day 30 is last day, day 31+ means out of break)
    const daysSinceStart = getDaysDifference(startDateStr, todayStr)
    if (daysSinceStart > 30) continue

    // This break is active
    return pb
  }

  return null
}

/**
 * Checks if user is currently in an active Clear30 break
 * @param programBreaks - Array of program breaks
 * @param timezone - User's timezone
 * @returns true if in active Clear30
 */
export function isInActiveClear30(
  programBreaks: ProgramBreak[] | null,
  timezone: string
): boolean {
  return getActiveClear30Break(programBreaks, timezone) !== null
}

/**
 * Calculates the current day in a Clear30 break
 * @param breakStartDate - Start date of the break (YYYY-MM-DD or ISO)
 * @param timezone - User's timezone
 * @returns Day number (0-30)
 */
export function calculateBreakDay(breakStartDate: string, timezone: string): number {
  const tz = getValidTimezone(timezone)
  const todayStr = getTodayInTimezone(tz)
  const startDateStr = breakStartDate.substring(0, 10)
  return getDaysDifference(startDateStr, todayStr)
}

/**
 * Determines the user's current program (Clear30 or Life) and their day in that program.
 *
 * Logic:
 * - If user is in an active Clear30 break → return Clear30 + day number
 * - If user is NOT in an active Clear30 → they're in Life program
 *   - Life days = total days spent NOT in a Clear30 break (accumulates across gaps)
 *   - If no Clear30 breaks exist, Life days are calculated from account created_at
 *
 * Note: end_date_override represents the FIRST day OUT of the break
 *
 * @param programBreaks - Array of program breaks from user record
 * @param createdAt - User's account creation date (ISO timestamp)
 * @param timezone - User's timezone
 * @returns ProgramInfo with type and day, or null if cannot be determined
 */
export function getCurrentProgramInfo(
  programBreaks: ProgramBreak[] | null,
  createdAt: string,
  timezone: string
): ProgramInfo | null {
  const tz = getValidTimezone(timezone)
  const todayStr = getTodayInTimezone(tz)
  const clear30Breaks = getClear30Breaks(programBreaks)

  // If no Clear30 breaks, user started directly with Life program
  // Calculate Life days from account creation date
  if (clear30Breaks.length === 0) {
    if (!createdAt) return null

    const createdAtStr = createdAt.substring(0, 10)
    const lifeDays = getDaysDifference(createdAtStr, todayStr)

    return { type: 'life', day: Math.max(0, lifeDays) }
  }

  // Check if user is currently in an active Clear30 break
  const activeClear30 = getActiveClear30Break(programBreaks, tz)

  if (activeClear30) {
    const currentDay = calculateBreakDay(activeClear30.start_date, tz)
    return { type: 'clear30', day: currentDay }
  }

  // Not in active Clear30 → calculate Life days
  // Life days = sum of all days NOT in a Clear30 break, including days before first break
  let lifeDays = 0

  // First, count days from account creation until first Clear30 break
  if (createdAt) {
    const createdAtStr = createdAt.substring(0, 10)
    const firstBreakStartStr = clear30Breaks[0].start_date.substring(0, 10)

    // Only count if account was created before the first break
    if (compareDates(createdAtStr, firstBreakStartStr) < 0) {
      const initialLifeDays = getDaysDifference(createdAtStr, firstBreakStartStr)
      lifeDays += initialLifeDays
    }
  }

  // Then count days in gaps between/after Clear30 breaks
  for (let i = 0; i < clear30Breaks.length; i++) {
    const currentBreak = clear30Breaks[i]
    const breakStartStr = currentBreak.start_date.substring(0, 10)

    // If this break hasn't started yet, skip it
    if (compareDates(todayStr, breakStartStr) < 0) continue

    // Determine when this break ended
    let breakEndStr: string
    if (currentBreak.end_date_override) {
      breakEndStr = currentBreak.end_date_override.substring(0, 10)
    } else {
      // No end_date_override - break ends after 31 days (day 30 is last day)
      const breakStart = new Date(breakStartStr + 'T00:00:00Z')
      const breakEnd = new Date(breakStart.getTime() + 31 * 24 * 60 * 60 * 1000)
      breakEndStr = breakEnd.toISOString().substring(0, 10)
    }

    // If break hasn't ended yet, skip
    if (compareDates(todayStr, breakEndStr) < 0) continue

    // Calculate gap after this break until next break or today
    const nextBreak = clear30Breaks[i + 1]
    let gapEndStr: string

    if (nextBreak) {
      const nextStartStr = nextBreak.start_date.substring(0, 10)
      // Gap ends at the earlier of: next break start or today
      gapEndStr = compareDates(nextStartStr, todayStr) <= 0 ? nextStartStr : todayStr
    } else {
      // No next break, gap extends to today
      gapEndStr = todayStr
    }

    // Add days in this gap (breakEnd to gapEnd)
    // breakEnd is the first day OUT, so it counts as Life day 0
    if (compareDates(gapEndStr, breakEndStr) > 0) {
      const gapDays = getDaysDifference(breakEndStr, gapEndStr)
      lifeDays += gapDays
    } else if (gapEndStr === breakEndStr && gapEndStr === todayStr) {
      // Today is exactly the first day out - that's day 0
      // lifeDays stays as is, we'll return it as the current day
    }
  }

  // If today is the first day out of the most recent break, lifeDays might be 0
  // which is correct (Life day 0)
  return { type: 'life', day: lifeDays }
}

/**
 * Gets the number of days until a Clear30 break starts
 * Used by sms_start_soon_schedule
 * @param breakStartDate - Start date of the break (YYYY-MM-DD or ISO)
 * @param timezone - User's timezone
 * @returns Number of days until start (negative if already started)
 */
export function getDaysUntilBreakStart(breakStartDate: string, timezone: string): number {
  const tz = getValidTimezone(timezone)
  const todayStr = getTodayInTimezone(tz)
  const startDateStr = breakStartDate.substring(0, 10)
  return getDaysDifference(todayStr, startDateStr)
}

/**
 * Finds the most recent "start-soon" break (upcoming Clear30)
 * @param programBreaks - Array of program breaks
 * @returns The start-soon break if found, null otherwise
 */
export function getStartSoonBreak(programBreaks: ProgramBreak[] | null): ProgramBreak | null {
  const startSoonBreaks = (programBreaks || [])
    .filter(pb => pb.type === 'clear30-start-soon' && pb.start_date)
    .sort((a, b) => compareDates(b.start_date.substring(0, 10), a.start_date.substring(0, 10)))

  return startSoonBreaks[0] || null
}
