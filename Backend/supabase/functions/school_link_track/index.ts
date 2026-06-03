/**
 * school_link_track — Clear30 distribution kit tracking redirect
 *
 * Usage:
 *   GET https://quluipmdicjsolnsopkg.supabase.co/functions/v1/school_link_track?school=umich&key=poster-app-overview
 *
 * What it does:
 *   1. Reads `school` and `key` from the query string
 *   2. Looks up the destination URL from schools.schools.tracking_links[key]
 *   3. Logs a row in schools.tracking_link_clicks (service role, bypasses RLS)
 *   4. Returns a 302 redirect to the destination URL
 *
 * No JWT required — public endpoint, anyone scanning a QR code hits this.
 *
 * Valid link_key values (must match tracking_links JSONB keys in DB):
 *   poster-app-overview | poster-how-to | poster-check-in
 *   canvas-banner | instagram-post | voucher-cards
 */

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// Service role client — bypasses RLS so we can insert tracking rows
// and read school configs without portal user auth.
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});

Deno.serve(async (req: Request) => {
  if (req.method !== "GET") {
    return new Response("Method not allowed", { status: 405 });
  }

  const url = new URL(req.url);
  const school = url.searchParams.get("school");
  const key = url.searchParams.get("key");

  if (!school || !key) {
    return new Response("Missing required params: school, key", { status: 400 });
  }

  // Look up the destination URL from the school's tracking_links config.
  // The DB is the source of truth for which keys are valid — no hardcoded allowlist.
  const { data: schoolRow, error: schoolError } = await supabase
    .schema("schools")
    .from("schools")
    .select("tracking_links")
    .eq("school_id", school)
    .single();

  if (schoolError || !schoolRow) {
    return new Response("School not found", { status: 404 });
  }

  const destinationUrl: string | undefined = schoolRow.tracking_links?.[key];

  if (!destinationUrl) {
    return new Response(
      `No tracking URL configured for school '${school}', key '${key}'`,
      { status: 404 }
    );
  }

  // Log the click — fire-and-forget so a logging failure never blocks the redirect.
  supabase
    .schema("schools")
    .from("tracking_link_clicks")
    .insert({
      school_id:  school,
      link_key:   key,
      user_agent: req.headers.get("user-agent") ?? null,
      referrer:   req.headers.get("referer") ?? null,
    })
    .then(({ error }) => {
      if (error) console.error("Failed to log tracking click:", error.message);
    });

  return new Response(null, {
    status: 302,
    headers: {
      Location: destinationUrl,
      "Cache-Control": "no-store, no-cache, must-revalidate",
    },
  });
});
