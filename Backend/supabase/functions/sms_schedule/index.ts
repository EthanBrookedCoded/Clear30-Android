// 
// This function is run once a day
// It schedules sms messages from the backend
// It uses the lirbary.sms_schedule table
// library.sms_schedule: id, message, paid, type
//

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

Deno.serve(async (req) => {
  // 1. Get SMS types for the day
  const smsTypes = getSMSTypes();

  // 3. If empty, return
  if (smsTypes.length === 0) {
    return new Response(
      JSON.stringify({ message: 'No messages to process' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )
  }

  // 4. Get client
  const supabaseClient = createClient(supabaseURL, supabaseServiceRoleKey)

  // 5. Handle SMS types
  await handleSMSTypes(supabaseClient, smsTypes);

  return new Response(
    JSON.stringify({ message: 'Messages scheduled.' }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
})

// GENERAL
enum SmsType {
  EndOfWeekSummary = 'end_of_week_summary',
  InactivityMessage = 'inactivity_message'
}

enum SmsIds {
  EndOfWeekSummary = 'eow-summary-template',
  InactivityMessage = 'inactivity-message-template'
}

enum Replacements {
  name = '_CLIENTNAME_',
  emoji = '_EMOJI_',
  sober = '_SOBER_',
  checkedIn = '_CHECKED_IN_',
  custom = '_CUSTOM_',
  message = '_MESSAGE_',
  dayOfWeek = '_DAY_OF_WEEK_'
}

function getSMSTypes(): SmsType[] {
  const smsTypes: SmsType[] = [];

  const today = new Date();

  if (today.getDay() === 0) { // Sunday
    smsTypes.push(SmsType.EndOfWeekSummary);
  }

  if (today.getDate() === 1) { // First day of the month
    smsTypes.push(SmsType.InactivityMessage);
  }

  return smsTypes;
}

async function handleSMSTypes(supabaseClient: ReturnType<typeof createClient>, smsTypes: SmsType[]) {
  for (const smsType of smsTypes) {
    switch (smsType) {
      case SmsType.EndOfWeekSummary:
        await sendEndOfWeekSummaries(supabaseClient);
        break;
      case SmsType.InactivityMessage:
        await sendInactivityMessages(supabaseClient);
        break;
      default:
        console.warn(`Unhandled SMS type: ${smsType}`);
    }
  }
}

// END OF WEEK SUMMARY
type User = {
  id: string;
  name: string;
  phone_number: string;
  emoji: string;
  phone_number: string;
  day_info: (string | DayData)[];
}

type CheckIn = {
  id: string;
  completion: boolean;
  amount?: number;
}

type DayData = {
  sober: boolean;
  loggedCheckIns: CheckIn[];
}

const motivationalMessages = [
  "Another Monday another dollar!! Lets make the most of this week _CLIENTAME_",
  "How lucky are we to have another week together _CLIENTNAME_",
  "😾Garfield hated Mondays. He couldn’t handle them. You can _CLIENTNAME_!",
  "Wow! You're here! Winners use Clear30 on Mondays.",
  "Happy Monday! You got it this week _CLIENTNAME_",
  "Starting the week off with a checkin? Kudos to you _CLIENTNAME_",
  "There's nothing like powering through a monday! You got it _CLIENTNAME_!",
  "Let's tackle the week together _CLIENTNAME_",
  "I hope you had a great weekend _CLIENTNAME_ :) Lets lock in..."
]

async function sendEndOfWeekSummaries(supabaseClient: ReturnType<typeof createClient>) {

  // 1. Get summaries
  const { data: summaries, error: summariesError } = await supabaseClient
    .schema('library')
    .from('sms_eow_summary')
    .select('*')

  if (summariesError) {
    console.error('Error fetching summaries:', summariesError);
    return;
  }

  // 2. Get users with the appropriate sms settings
  const { data: users, error: usersError } = await supabaseClient
    .from('users')
    .select('id, name, emoji, day_info, phone_number')
    .not('phone_number', 'is', null)
    .filter('sms_settings->all', 'eq', true)
    .filter('sms_settings->options->Milestone Texts', 'eq', true);

  if (usersError) {
    console.error('Error fetching users:', usersError);
    return;
  }

  // 3. Generate and send summaries for each user
  for (const user of users) {
    // Generate summary
    let summary = await getSummary(supabaseClient, user, summaries);
    if (summary === null || summary === undefined) { continue }

    // Add summary to sms table
    const { data: sms, error: smsError } = await supabaseClient
      .schema('comms')
      .from('sms_messages')
      .insert([
        {
          user_id: user.id,
          phone_number: user.phone_number,
          text: summary,
          outbound: true,
          scheduled_for: new Date(),
        }
      ]);

    if (smsError) {
      console.error('Error adding EOW sms:', smsError);
    }
  }
}

async function getSummary(supabaseClient: ReturnType<typeof createClient>, user: User, templates: [any]): String | null {
  // Parsing function
  function filterDayInfo(arr: (string | DayData)[]): Record<string, DayData> {
    const oneWeekAgo = new Date();
    oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
    const oneWeekAgoStr = oneWeekAgo.toISOString().slice(0, 10);

    const result: Record<string, DayData> = {};
    for (let i = 0; i < arr.length; i += 2) {
      const dateStr = arr[i] as string;

      if (dateStr >= oneWeekAgoStr) {
        result[dateStr] = arr[i + 1] as DayData;
      }
    }

    return result;
  }

  // Get min date
  let minDateString = new Date().toISOString().slice(0, 10);
  for (let i = 0; i < user.day_info.length; i += 2) {
    const dateStr = user.day_info[i] as string;

    if (dateStr < minDateString) {
      minDateString = dateStr;
    }
  }

  // If min date is within 5 days, return null
  const fiveDaysAgo = new Date();
  fiveDaysAgo.setDate(fiveDaysAgo.getDate() - 5);
  const fiveDaysAgoStr = fiveDaysAgo.toISOString().slice(0, 10);
  if (minDateString > fiveDaysAgoStr) {
    return null;
  }

  // Filter day info from the last week
  let dayInfo = filterDayInfo(user.day_info);

  // Calculate check in stats
  let daysSober = 0;
  let daysCheckedIn = 0;
  let customCheckIns: Record<string, int> = {};
  for (const day in dayInfo) {
    // Get day info
    let currentDayInfo = dayInfo[day];

    // Sober
    let checkInValue = currentDayInfo.sober;
    if (checkInValue !== undefined) {
      daysCheckedIn++;

      if (checkInValue) {
        daysSober++;
      }
    }

    // Custom Check Ins
    for (const index in currentDayInfo.loggedCheckIns) {
      let loggedCheckIn = currentDayInfo.loggedCheckIns[index];

      if (loggedCheckIn.id == "sober") { continue; }

      if (loggedCheckIn.completion) {
        customCheckIns[loggedCheckIn.id] = (customCheckIns[loggedCheckIn.id] ?? 0) + 1;
      }
    }
  }

  // Get most recent assessment
  const mostRecentAssessmentResponse = await getRecentAssessmentResponse(supabaseClient, user);
  if (mostRecentAssessmentResponse === null) {
    return getEndOfWeekDefalt(supabaseClient, user, templates, daysSober, daysCheckedIn, customCheckIns);
  }

  // Sort by check in days then sober days
  const sortedTemplates = templates.sort((a, b) => {
    if (a.check_in_days === b.check_in_days) {
      return b.sober_days - a.sober_days;
    }
    return b.check_in_days - a.check_in_days;
  });

  const matchingTemplate = sortedTemplates.find((template) => {

    // Check check in days
    if (template.check_in_days !== null && template.check_in_days <= daysCheckedIn) {

      // Check sober days
      if (template.sober_days !== null && template.sober_days <= daysSober) {

        // Check assessment responses
        let assessmentResponse = mostRecentAssessmentResponse.responses[template.question_id];

        // Response can be array or string
        if (Array.isArray(assessmentResponse)) {
          if (assessmentResponse.includes(template.question_response)) {
            return true;
          }
        } else {
          if (assessmentResponse === template.question_response) {
            return true;
          }
        }
      }
    }
    return false;
  });

  // Default if none selected
  if (matchingTemplate === null || matchingTemplate === undefined) {
    return getEndOfWeekDefalt(supabaseClient, user, templates, daysSober, daysCheckedIn, customCheckIns);
  }

  // Replace placeholds

  // Usser Info
  let message = matchingTemplate.message;
  message = message.replace(Replacements.name, user.name);
  message = message.replace(Replacements.emoji, user.emoji);

  // Sober and Check Ins
  message = message.replace(Replacements.sober, daysSober);
  message = message.replace(Replacements.checkedIn, daysCheckedIn);

  // Day of week (fuck you asher)
  if (message.includes(Replacements.dayOfWeek)) {
    // Get first sober day from dayInfo
    let firstSoberDay = null;
    for (const day in dayInfo) {
      if (dayInfo[day].sober) {
        firstSoberDay = day;
        break;
      }
    }

    // Get day of week for day
    let date = new Date(firstSoberDay);
    let options = { weekday: 'long' };
    firstSoberDay = new Intl.DateTimeFormat('en-US', options).format(date);

    // Replace in message
    message = message.replace(Replacements.dayOfWeek, firstSoberDay);
  }

  return message;
}

async function getRecentAssessmentResponse(supabaseClient: ReturnType<typeof createClient>, user: User): any | null {
  const { data: assessments, error: assessmentError } = await supabaseClient
    .schema('programs')
    .from('program_assessment_responses')
    .select('responses')
    .eq('user_id', user.id)
    .order('timestamp', { ascending: false })
    .limit(1);

  if (assessmentError) {
    console.error('Error fetching assessment:', assessmentError);
    return null;
  }

  // Return the first assessment if it exists, otherwise null
  return assessments && assessments.length > 0 ? assessments[0] : null;
}

function getEndOfWeekDefalt(
  supabaseClient: ReturnType<typeof createClient>,
  user: User,
  templates: [any],
  daysSober: int,
  daysCheckedIn: int,
  customCheckIns: Record<string, int>
): String | null {
  // 1. Get the first template where program, question_id, question_response,, and days_checked_in are null
  let template = null;

  for (const currentTemplate of templates) {
    if (currentTemplate.sober_days === null && currentTemplate.check_in_days === null) {
      template = currentTemplate;
      break;
    }
  }

  // 2. If no template is found, return null
  if (template === null) {
    return null;
  }

  // 3. Generate message
  let message = template.message;

  // User info
  message = message.replace(Replacements.name, user.name);
  message = message.replace(Replacements.emoji, user.emoji);

  // Sober and Check Ins
  message = message.replace(Replacements.sober, daysSober > 0 ? `🤩 You didn't smoke for ${daysSober} day${daysSober > 1 ? 's' : ''}!` : '');
  message = message.replace(Replacements.checkedIn, daysCheckedIn > 0 ? `✅ You checked in ${daysCheckedIn} time${daysCheckedIn > 1 ? 's' : ''}!` : '');

  // Custom Check Ins
  let customCheckInMessage = '';
  for (const checkIn in customCheckIns) {
    let times = customCheckIns[checkIn];
    customCheckInMessage += `💯 You made time to ${checkIn} ${times} time${times > 1 ? 's' : ''}!\n`
  }
  if (customCheckInMessage.length > 0) { customCheckInMessage = customCheckInMessage.slice(0, -1); }
  message = message.replace(Replacements.custom, customCheckInMessage);

  // Motivational Message if needed
  let threshold = 3;
  let randomMessage = motivationalMessages[Math.floor(Math.random() * motivationalMessages.length)];
  message = message.replace(Replacements.message, daysSober < threshold ? randomMessage : '');

  // Replace all empty lines with a two new lines
  message = message.replace(/\n\s*\n/g, '\n\n');

  return message;
}

// INACTIVITY SMS
async function sendInactivityMessages(supabaseClient: ReturnType<typeof createClient>) {

  // 2. Get inactivity messages
  const { data: inactivityMessages, error: inactivityMessagesError } = await supabaseClient
    .schema('library')
    .from('sms_inactive')
    .select('day, message')

  if (inactivityMessagesError) {
    console.error('Error fetching inactivity messages:', inactivityMessagesError);
    return;
  }

  // 2. Check for messages with day as same date as current day (not including year)
  const todayDay = new Date().toISOString().slice(5, 10);
  const matchingMessage = inactivityMessages.find((message) => {
    if (message.day === null || message.day === undefined) {
      return false;
    }
    return message.day.slice(5, 10) === todayDay;
  });

  // 3. If no matching message found, return
  if (matchingMessage === null || matchingMessage === undefined) {
    console.log('No matching inactivity message found');
    return;
  }

  // 4. Get users with the appropriate sms settings
  const { data: users, error: usersError } = await supabaseClient
    .from('users')
    .select('id, name, emoji, day_info, phone_number')
    .not('phone_number', 'is', null)
    .filter('sms_settings->all', 'eq', true)
    .filter('sms_settings->options->Milestone Texts', 'eq', true);

  if (usersError) {
    console.error('Error fetching users:', usersError);
    return;
  }

  // 5. Generate all dates in the previous month of the form YYYY-MM-DD
  const today = new Date();
  const firstDay = new Date(today.getFullYear(), today.getMonth() - 1, 1);
  const firstDayString = firstDay.toISOString().slice(0, 10);
  const lastDay = new Date(today.getFullYear(), today.getMonth(), 0);
  const lastDayString = lastDay.toISOString().slice(0, 10);
  const dates = [];
  for (let date = firstDay; date <= lastDay; date.setDate(date.getDate() + 1)) {
    dates.push(date.toISOString().slice(0, 10));
  }

  // 6. Send inactivity messgae for relevent users 
  for (const index in users) {
    let user = users[index] as User;

    // Get min date & num of checkins in day info
    let minDate = today.toISOString().slice(0, 10);
    let numCheckIns = 0;
    for (const index in user.day_info) {
      let currentDayInfo = user.day_info[index];

      // Not a date
      if (!/^\d{4}-\d{2}-\d{2}$/.test(currentDayInfo)) {
        continue;
      }

      // New min date
      if (currentDayInfo < minDate) {
        minDate = currentDayInfo;
      }

      // Check in has been in last month
      if (dates.includes(currentDayInfo)) {
        numCheckIns++;
      }
    }

    // Get num of posible checkins in previous month
    // max(minUserDate, firstOfPreviousMonth) -> lastOfPreviousMonth
    const startDate = minDate > firstDayString ? minDate : firstDayString;
    let startDateIndex = dates.indexOf(startDate);
    let numPossibleCheckIns = startDateIndex === -1 ? 0 : dates.length - startDateIndex;

    // Continue to next person if possible checkins is not >= 15 and ratio < 0.5
    if (!(numPossibleCheckIns >= 15 && (numCheckIns / numPossibleCheckIns) < 0.5)) {
      continue;
    }

    // Generate message
    let message = getInactivityMessage(user, matchingMessage.message);

    // Send message
    const { data: sms, error: smsError } = await supabaseClient
      .schema('comms')
      .from('sms_messages')
      .insert([
        {
          user_id: user.id,
          phone_number: user.phone_number,
          text: message,
          outbound: true,
          scheduled_for: new Date(),
        }
      ]);

    if (smsError) {
      console.error('Error adding inactivity sms:', smsError);
    }
  }
}

function getInactivityMessage(user: User, template: string): String {
  // User info
  let message = template.replace(Replacements.name, user.name);
  message = message.replace(Replacements.emoji, user.emoji);
  return message;
}

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_schedule' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"name":"Functions"}'

*/
