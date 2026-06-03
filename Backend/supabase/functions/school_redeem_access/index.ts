// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
/**
 * school_redeem_access — stores a student email lead for a school
 *
 * POST with JSON body: { "email": "student@university.edu" }
 *
 * What it does:
 *   1. Extracts the domain from the email
 *   2. Looks up the domain in payment.domain_allowlist
 *   3. Upserts into schools.school_leads with school_id if available (duplicates silently ignored)
 *   4. Returns success — even if no school_id is linked, the user still has free access
 *
 * No JWT required — public endpoint for website redemption forms.
 */

import { createClient } from "jsr:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } }
);

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Content-Type": "application/json",
};

Deno.serve(async (req: Request) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ error: "Method not allowed" }),
      { status: 405, headers: corsHeaders }
    );
  }

  try {
    const { email } = await req.json();

    if (!email || typeof email !== "string") {
      return new Response(
        JSON.stringify({ error: "email is required" }),
        { status: 400, headers: corsHeaders }
      );
    }

    // Basic email format check
    const atIndex = email.indexOf("@");
    if (atIndex < 1 || atIndex === email.length - 1) {
      return new Response(
        JSON.stringify({ error: "Invalid email format" }),
        { status: 400, headers: corsHeaders }
      );
    }

    const domain = email.substring(atIndex + 1).toLowerCase();

    // Look up the domain in the allowlist
    const { data: domainRow, error: domainError } = await supabase
      .schema("payment")
      .from("domain_allowlist")
      .select("school_id")
      .eq("domain", domain)
      .single();

    if (domainError || !domainRow) {
      return new Response(
        JSON.stringify({ error: "This email domain is not eligible for school access" }),
        { status: 400, headers: corsHeaders }
      );
    }

    // Insert the lead — school_id may be null if the domain isn't linked to a school yet
    const leadRow: Record<string, string | null> = {
      email: email.toLowerCase(),
      school_id: domainRow.school_id ?? null,
    };

    const { error: leadError } = await supabase
      .schema("schools")
      .from("school_leads")
      .insert(leadRow);

    // Ignore unique constraint violations (duplicate submissions) — everything else is a real error
    if (leadError && leadError.code !== "23505") {
      console.error("Failed to insert school lead:", leadError.message);
      return new Response(
        JSON.stringify({ error: "Failed to save lead" }),
        { status: 500, headers: corsHeaders }
      );
    }

    return new Response(
      JSON.stringify({ success: true, school_id: domainRow.school_id ?? null }),
      { status: 200, headers: corsHeaders }
    );
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: corsHeaders }
    );
  }
});

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/school_redeem_access' \
    --header 'Content-Type: application/json' \
    --data '{"email":"student@university.edu"}'

  Remote:

  curl -i --location --request POST 'https://quluipmdicjsolnsopkg.supabase.co/functions/v1/school_redeem_access' \
    --header 'Content-Type: application/json' \
    --data '{"email":"student@university.edu"}'

*/
