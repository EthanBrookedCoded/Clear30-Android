// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
// supabase/functions/school_invite_user/index.ts
// supabase/functions/school_invite_user/index.ts
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { SMTPClient } from "https://deno.land/x/denomailer/mod.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const SMTP_CONFIG = {
  hostname: "smtp.gmail.com",
  port: 465,
  username: "support@clear30.org",
  password: Deno.env.get("SMTP_PASSWORD")!,
  fromEmail: "support@clear30.org"
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    // Authenticated client (caller's JWT)
    const authHeader = req.headers.get("Authorization")!;
    const userClient = createClient(supabaseUrl, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: authHeader } },
    });

    // Admin client (service role — bypasses RLS)
    const adminClient = createClient(supabaseUrl, serviceRoleKey);

    // Verify caller identity
    const { data: { user: caller }, error: authError } = await userClient.auth.getUser();
    if (authError || !caller) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Check caller is platform admin
    const { data: platformAdmin } = await adminClient
      .schema("schools").from("platform_admins")
      .select("id").eq("auth_id", caller.id).maybeSingle();

    const body = await req.json();
    const { school_id, email, name, role, is_admin, redirect_to } = body;

    if (!school_id || !email || !name) {
      return new Response(JSON.stringify({ error: "Missing required fields" }), {
        status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Resolve caller's portal_users.id (bigint) for invited_by
    let callerPortalUserId: number | null = null;

    if (!platformAdmin) {
      const { data: callerPortalUser } = await adminClient
        .schema("schools").from("portal_users")
        .select("id")
        .eq("auth_id", caller.id)
        .eq("school_id", school_id)
        .maybeSingle();

      if (!callerPortalUser) {
        return new Response(JSON.stringify({ error: "You don't have permission to invite users" }), {
          status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
      callerPortalUserId = callerPortalUser.id;
    } else {
      const { data: adminPortalUser } = await adminClient
        .schema("schools").from("portal_users")
        .select("id")
        .eq("auth_id", caller.id)
        .maybeSingle();
      callerPortalUserId = adminPortalUser?.id ?? null;
    }

    // Check if this email already exists for this school
    const { data: existing } = await adminClient
      .schema("schools").from("portal_users")
      .select("id, invite_accepted_at")
      .eq("email", email.trim().toLowerCase())
      .eq("school_id", school_id)
      .maybeSingle();

    if (existing?.invite_accepted_at) {
      return new Response(JSON.stringify({ error: "This user is already a member of this school" }), {
        status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Upsert portal_users row
    let portalUser;
    if (existing) {
      const { data, error } = await adminClient
        .schema("schools").from("portal_users")
        .update({
          name: name.trim(),
          first_name: name.trim().split(" ")[0],
          role,
          invited_at: new Date().toISOString(),
          invited_by: callerPortalUserId,
        })
        .eq("id", existing.id)
        .select()
        .single();
      if (error) throw error;
      portalUser = data;
    } else {
      const { data, error } = await adminClient
        .schema("schools").from("portal_users")
        .insert({
          school_id,
          email: email.trim().toLowerCase(),
          name: name.trim(),
          first_name: name.trim().split(" ")[0],
          role,
          invited_at: new Date().toISOString(),
          invited_by: callerPortalUserId,
        })
        .select()
        .single();
      if (error) throw error;
      portalUser = data;
    }

    // Get school name for the email
    const { data: school } = await adminClient
      .schema("schools").from("schools")
      .select("short_name, long_name").eq("school_id", school_id).single();

    const schoolName = school?.long_name || school?.short_name || "your school";
    const portalUrl = redirect_to || "https://clear30.org/school-portal";
    const firstName = name.trim().split(" ")[0];

    // Send custom invite email via SMTP
    try {
      const client = new SMTPClient({
        connection: {
          hostname: SMTP_CONFIG.hostname,
          port: SMTP_CONFIG.port,
          tls: true,
          auth: {
            username: SMTP_CONFIG.username,
            password: SMTP_CONFIG.password,
            // password: Deno.env.get("SMTP_PASSWORD")!,
          },
        },
      });

      await client.send({
        from: SMTP_CONFIG.fromEmail,
        to: email.trim().toLowerCase(),
        subject: `You're invited to the ${school?.short_name || "Clear30"} admin portal`,
        html: buildInviteEmailHtml({ firstName, schoolName, portalUrl }),
      });

      await client.close();
    } catch (emailErr) {
      console.error("SMTP email error (non-fatal):", emailErr);
    }

    return new Response(JSON.stringify({ portal_user: portalUser }), {
      status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("Invite error:", err);
    return new Response(JSON.stringify({ error: err.message || "Internal server error" }), {
      status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});

function buildInviteEmailHtml({ firstName, schoolName, portalUrl }: { firstName: string; schoolName: string; portalUrl: string }) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <meta name="format-detection" content="telephone=no,address=no,email=no,date=no,url=no">
  <title>You're invited to the Clear30 Partner Portal</title>
  <style type="text/css">
    body, table, td, a { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
    table, td { mso-table-lspace: 0pt; mso-table-rspace: 0pt; }
    body { height: 100% !important; margin: 0 !important; padding: 0 !important; width: 100% !important; }
    @media only screen and (max-width: 600px) {
      .email-container { width: 100% !important; max-width: 100% !important; }
      .email-padding { padding-left: 20px !important; padding-right: 20px !important; }
    }
  </style>
</head>
<body style="margin: 0; padding: 0; background-color: #f0faf5;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background-color: #f0faf5;">
    <tr>
      <td align="center" style="padding: 40px 0;">
        <table role="presentation" class="email-container" cellpadding="0" cellspacing="0" border="0" width="560" style="max-width: 560px; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 2px 12px rgba(67, 182, 146, 0.08);">

          <!-- Accent bar -->
          <tr>
            <td style="height: 4px; font-size: 0; line-height: 0; background: linear-gradient(135deg, #43b692 0%, #3bbfa0 30%, #56c9a5 60%, #7dddb5 100%);">&nbsp;</td>
          </tr>

          <!-- Content -->
          <tr>
            <td style="padding: 32px 40px 32px 40px;" class="email-padding">
              <h1 style="margin: 0 0 20px 0; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 24px; line-height: 32px; font-weight: bold; color: #1a2e23; text-align: center;">
                You're invited to the<br>${schoolName} Partner Portal
              </h1>

              <p style="margin: 0 0 16px 0; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 15px; line-height: 24px; color: #374151;">
                Hi ${firstName},
              </p>
              <p style="margin: 0 0 16px 0; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 15px; line-height: 24px; color: #374151;">
                You've been added as a team member on the <strong>${schoolName}</strong> Clear30 Partner Portal. From there, you can view student engagement data, access distribution resources, and manage your school's Clear30 program.
              </p>
              <p style="margin: 0 0 28px 0; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 15px; line-height: 24px; color: #374151;">
                Sign in with your email to get started:
              </p>

              <!-- CTA Button -->
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">
                <tr>
                  <td align="center">
                    <a href="${portalUrl}" target="_blank" style="display: inline-block; background: linear-gradient(135deg, #43b692 0%, #3bbfa0 50%, #56c9a5 100%); color: #ffffff; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 16px; font-weight: bold; line-height: 50px; text-align: center; text-decoration: none; min-width: 220px; padding: 0 40px; border-radius: 50px; letter-spacing: 0.3px;">
                      Go to Partner Portal &rarr;
                    </a>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <!-- Divider -->
          <tr>
            <td style="padding: 0 40px;" class="email-padding">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">
                <tr><td style="border-top: 1px solid #e8f5ef; font-size: 0; line-height: 0; height: 1px;">&nbsp;</td></tr>
              </table>
            </td>
          </tr>

          <!-- Footer -->
          <tr>
            <td align="center" style="padding: 24px 40px 12px 40px;" class="email-padding">
              <p style="margin: 0; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 13px; line-height: 20px; color: #6b7280;">
                Questions? Reach us at <a href="mailto:support@clear30.org" style="color: #43b692; text-decoration: underline;">support@clear30.org</a>
              </p>
            </td>
          </tr>
          <tr>
            <td align="center" style="padding: 0 40px 28px 40px;" class="email-padding">
              <p style="margin: 0; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 12px; line-height: 18px; color: #9ca3af;">
                Clear30, Inc.
              </p>
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;
}

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/school_invite_user' \
    --header 'Authorization: Bearer eyJhbGciOiJFUzI1NiIsImtpZCI6ImI4MTI2OWYxLTIxZDgtNGYyZS1iNzE5LWMyMjQwYTg0MGQ5MCIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjIwODcxNzg1OTh9.ntw1o5i-6eRy-Cd3MSXde_noNNEF2GjIWsfg4RORA2Brc3YopCfinRtF_419F8Tf1ImQ5P8qqd2RZA1Cn1Gikw' \
    --header 'Content-Type: application/json' \
    --data '{"name":"Functions"}'

*/
