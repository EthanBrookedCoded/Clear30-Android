// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"

// Environment variables
const META_ACCESS_TOKEN = Deno.env.get("META_ACCESS_TOKEN")
const META_AD_ACCOUNT_ID = Deno.env.get("META_AD_ACCOUNT_ID") // e.g. "act_123456789"
const TIKTOK_ACCESS_TOKEN = Deno.env.get("TIKTOK_ACCESS_TOKEN")
const TIKTOK_ADVERTISER_ID = Deno.env.get("TIKTOK_ADVERTISER_ID")
const AMPLITUDE_API_KEY = Deno.env.get("AMPLITUDE_API_KEY")
const AD_SPEND_USER_ID = "ad_spend_tracker";

// Fetch total ad spend from Meta Marketing API for a given date (YYYY-MM-DD)
async function fetchMetaAdSpend(date: string): Promise<number> {
  const url = new URL(`https://graph.facebook.com/v21.0/${META_AD_ACCOUNT_ID}/insights`)
  url.searchParams.append("fields", "spend")
  url.searchParams.append("level", "account")
  url.searchParams.append("time_range", JSON.stringify({ since: date, until: date }))
  url.searchParams.append("access_token", META_ACCESS_TOKEN!)

  const response = await fetch(url.toString());

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Meta API error: ${response.status} ${errorText}`);
  }

  const json = await response.json();

  // If no data returned (no spend that day), return 0
  if (!json.data || json.data.length === 0) {
    return 0;
  }

  return parseFloat(json.data[0].spend);
}

// Fetch total ad spend from TikTok Marketing API for a given date (YYYY-MM-DD)
async function fetchTikTokAdSpend(date: string): Promise<number> {
  const url = new URL("https://business-api.tiktok.com/open_api/v1.3/report/integrated/get/");
  url.searchParams.append("advertiser_id", TIKTOK_ADVERTISER_ID!);
  url.searchParams.append("report_type", "BASIC");
  url.searchParams.append("data_level", "AUCTION_ADVERTISER");
  url.searchParams.append("dimensions", JSON.stringify(["stat_time_day"]));
  url.searchParams.append("metrics", JSON.stringify(["spend"]));
  url.searchParams.append("start_date", date);
  url.searchParams.append("end_date", date);

  const response = await fetch(url.toString(), {
    headers: {
      "Access-Token": TIKTOK_ACCESS_TOKEN!,
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`TikTok API error: ${response.status} ${errorText}`);
  }

  const json = await response.json();

  if (json.code !== 0) {
    throw new Error(`TikTok API error: ${json.code} ${json.message}`);
  }

  // If no data returned (no spend that day), return 0
  if (!json.data?.list || json.data.list.length === 0) {
    return 0;
  }

  // Sum spend across all entries (should typically be one for a single day)
  return json.data.list.reduce((sum: number, entry: any) => sum + parseFloat(entry.metrics.spend), 0);
}

// Build an Amplitude event for ad spend
function buildAmplitudeEvent(options: {
  eventType: string;
  dataDate: string;
  timestampDate: string;
  spend: number;
  channel: string;
}) {
  const { eventType, dataDate, timestampDate, spend, channel } = options;

  // Use UTC end-of-day for the timestamp
  const timestamp = Date.UTC(
    parseInt(timestampDate.split('-')[0]),
    parseInt(timestampDate.split('-')[1]) - 1,
    parseInt(timestampDate.split('-')[2]),
    23, 59, 59, 999
  );

  return {
    user_id: AD_SPEND_USER_ID,
    event_type: eventType,
    time: timestamp,
    event_properties: {
      channel: channel,
      spend: spend,
      date: dataDate,
      timestamp: timestamp
    }
  };
}

// Send events to Amplitude
async function sendToAmplitude(events: any[]) {
  const payload = { api_key: AMPLITUDE_API_KEY, events };
  const response = await fetch('https://api2.amplitude.com/2/httpapi', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': '*/*' },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Amplitude API error: ${response.status} ${errorText}`);
  }

  return response.status;
}

// Fetch spend for both platforms for a given date, then send to Amplitude
async function processDateSpend(dateString: string, timestampDateString: string, eventType: string) {
  const [metaResult, tiktokResult] = await Promise.allSettled([
    fetchMetaAdSpend(dateString),
    fetchTikTokAdSpend(dateString),
  ]);

  const metaSpend = metaResult.status === "fulfilled" ? metaResult.value : null;
  const tiktokSpend = tiktokResult.status === "fulfilled" ? tiktokResult.value : null;

  // Calculate total from successful fetches
  const totalSpend = (metaSpend ?? 0) + (tiktokSpend ?? 0);

  const events = [];

  // Total event (combined spend)
  events.push(buildAmplitudeEvent({
    eventType,
    dataDate: dateString,
    timestampDate: timestampDateString,
    spend: totalSpend,
    channel: "total"
  }));

  // Facebook event
  if (metaSpend !== null) {
    events.push(buildAmplitudeEvent({
      eventType,
      dataDate: dateString,
      timestampDate: timestampDateString,
      spend: metaSpend,
      channel: "Facebook"
    }));
  }

  // TikTok event
  if (tiktokSpend !== null) {
    events.push(buildAmplitudeEvent({
      eventType,
      dataDate: dateString,
      timestampDate: timestampDateString,
      spend: tiktokSpend,
      channel: "TikTok for Business"
    }));
  }

  if (events.length > 0) {
    await sendToAmplitude(events);
  }

  return {
    success: true,
    totalSpend,
    metaSpend,
    tiktokSpend,
    metaError: metaResult.status === "rejected" ? metaResult.reason.message : null,
    tiktokError: tiktokResult.status === "rejected" ? tiktokResult.reason.message : null,
  };
}

// Main function handler
Deno.serve(async (req) => {
  // Parse request body for optional date parameter
  const { date } = await req.json().catch(() => ({}));

  const results = {
    yesterday: { success: false, error: null as string | null, totalSpend: null as number | null, metaSpend: null as number | null, tiktokSpend: null as number | null, metaError: null as string | null, tiktokError: null as string | null },
    threeDaysAgo: { success: false, error: null as string | null, totalSpend: null as number | null, metaSpend: null as number | null, tiktokSpend: null as number | null, metaError: null as string | null, tiktokError: null as string | null }
  };

  // Calculate yesterday's date in UTC
  const now = new Date();
  const yesterdayDate = date ? new Date(date + "T00:00:00Z") : new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1));
  const yesterdayDateString = yesterdayDate.toISOString().split('T')[0];

  // Calculate three days ago (relative to yesterday) in UTC
  const threeDaysAgoDate = new Date(Date.UTC(yesterdayDate.getUTCFullYear(), yesterdayDate.getUTCMonth(), yesterdayDate.getUTCDate() - 3));
  const threeDaysAgoDateString = threeDaysAgoDate.toISOString().split('T')[0];

  // --- Yesterday's data ---
  try {
    const result = await processDateSpend(yesterdayDateString, yesterdayDateString, "ad_spend_update");
    results.yesterday = { success: true, error: null, ...result };
  } catch (error) {
    results.yesterday = { ...results.yesterday, error: error.message };
  }

  // --- Three days ago data ---
  try {
    const result = await processDateSpend(threeDaysAgoDateString, yesterdayDateString, "ad_spend_update_three_days_ago");
    results.threeDaysAgo = { success: true, error: null, ...result };
  } catch (error) {
    results.threeDaysAgo = { ...results.threeDaysAgo, error: error.message };
  }

  const overallSuccess = results.yesterday.success || results.threeDaysAgo.success;

  return new Response(
    JSON.stringify({
      success: overallSuccess,
      message: overallSuccess
        ? "Ad spend data processing completed"
        : "All ad spend operations failed",
      results
    }),
    {
      headers: { "Content-Type": "application/json" },
      status: overallSuccess ? 200 : 500
    },
  );
});
