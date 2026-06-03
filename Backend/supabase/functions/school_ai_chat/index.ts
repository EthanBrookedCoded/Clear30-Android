// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
/**
 * ai-chat — Clear30 AI Insights backend
 *
 * Receives a user message + conversation history, queries the school's
 * analytics data from Supabase, builds a dynamic system prompt, and
 * calls the Anthropic Claude API server-side.
 *
 * Requires a valid Supabase JWT (portal user or platform admin).
 * The ANTHROPIC_API_KEY must be set as an Edge Function secret.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import OpenAI from "https://deno.land/x/openai@v4.20.1/mod.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY");

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  if (!OPENAI_API_KEY) {
    return jsonResponse({ error: "OPENAI_API_KEY not configured" }, 500);
  }

  try {
    const { message, history, school_id: requestedSchoolId } = await req.json();

    if (!message || typeof message !== "string") {
      return jsonResponse({ error: "Missing 'message' field" }, 400);
    }

    console.log("[ai-chat] Received message:", message.slice(0, 80));
    console.log("[ai-chat] Requested school_id from frontend:", requestedSchoolId || "none");

    const authHeader = req.headers.get("Authorization")!;
    const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false },
    });

    const {
      data: { user },
    } = await supabase.auth.getUser();
    if (!user) {
      return jsonResponse({ error: "Unauthorized" }, 401);
    }

    console.log("[ai-chat] Auth user id:", user.id);

    // Use the school_id from the frontend if provided
    let schoolId = requestedSchoolId || null;

    if (!schoolId) {
      // Fallback: look up portal user's school
      const { data: portalUser } = await supabase
        .schema("schools")
        .from("portal_users")
        .select("school_id")
        .eq("auth_id", user.id)
        .single();

      schoolId = portalUser?.school_id;
      console.log("[ai-chat] Portal user school_id:", schoolId || "not found");
    }

    if (!schoolId) {
      // Fallback: platform admin — pick first school
      const { data: adminCheck } = await supabase
        .schema("schools")
        .rpc("get_platform_admin_self");
      if (adminCheck) {
        const { data: schools } = await supabase
          .schema("schools")
          .rpc("get_all_schools_for_admin");
        if (schools && schools.length > 0) {
          schoolId = schools[0].school_id;
          console.log("[ai-chat] Platform admin fallback — using first school:", schoolId);
        }
      }
    }

    if (!schoolId) {
      console.log("[ai-chat] ERROR: No school found for user");
      return jsonResponse({ error: "No school found for user" }, 403);
    }

    console.log("[ai-chat] Resolved school_id:", schoolId);

    // Fetch school analytics data in parallel
    const [
      schoolResult,
      analyticsResult,
      enrollmentResult,
      weeklyResult,
      assessmentResult,
    ] = await Promise.allSettled([
      supabase
        .schema("schools")
        .from("schools")
        .select("long_name")
        .eq("school_id", schoolId)
        .single(),
      supabase
        .schema("schools")
        .rpc("get_school_analytics", { p_school_id: schoolId }),
      supabase
        .schema("schools")
        .rpc("get_school_enrollment_trend", {
          p_school_id: schoolId,
          p_interval: "month",
        }),
      supabase
        .schema("schools")
        .rpc("get_school_checkin_trend", {
          p_school_id: schoolId,
          p_interval: "week",
        }),
      supabase
        .schema("schools")
        .rpc("get_school_assessment_stats", { p_school_id: schoolId }),
    ]);

    const schoolName =
      schoolResult.status === "fulfilled"
        ? schoolResult.value.data?.long_name
        : schoolId;
    const analytics =
      analyticsResult.status === "fulfilled"
        ? analyticsResult.value.data
        : null;
    const enrollment =
      enrollmentResult.status === "fulfilled"
        ? enrollmentResult.value.data
        : null;
    const weeklyCheckins =
      weeklyResult.status === "fulfilled" ? weeklyResult.value.data : null;
    const assessments =
      assessmentResult.status === "fulfilled"
        ? assessmentResult.value.data
        : null;

    console.log("[ai-chat] School name:", schoolName);
    console.log("[ai-chat] Analytics loaded:", !!analytics, analytics ? `(${analytics.total_checkins} checkins, ${analytics.total_students} students)` : "");
    console.log("[ai-chat] Enrollment rows:", enrollment?.length ?? 0);
    console.log("[ai-chat] Weekly checkin rows:", weeklyCheckins?.length ?? 0);
    console.log("[ai-chat] Assessment rows:", assessments?.length ?? 0);

    // Aggregate raw assessment responses (same shape as portal: array of { Trigger, Break-Reason, Goal30, ... })
    const assessmentSummary = aggregateAssessmentData(assessments);

    const systemPrompt = buildSystemPrompt(
      schoolName,
      analytics,
      enrollment,
      weeklyCheckins,
      assessmentSummary
    );

    const messages: Array<{ role: "user" | "assistant"; content: string }> = [];
    if (history && Array.isArray(history)) {
      for (const msg of history) {
        messages.push({
          role: msg.role === "ai" ? "assistant" : "user",
          content: msg.text,
        });
      }
    }
    messages.push({ role: "user", content: message });

    const openai = new OpenAI({ apiKey: OPENAI_API_KEY });
    const completion = await openai.chat.completions.create({
      model: "gpt-4o-mini",
      max_tokens: 1024,
      temperature: 0.7,
      messages: [
        { role: "system", content: systemPrompt },
        ...messages,
      ],
    });

    const text = completion.choices[0]?.message?.content || "";

    console.log("[ai-chat] Response generated, length:", text.length);

    return jsonResponse({ text });
  } catch (err: unknown) {
    const errorMessage =
      err instanceof Error ? err.message : "Internal error";
    console.error("[ai-chat] ERROR:", err);
    return jsonResponse({ error: errorMessage }, 500);
  }
});

/** Raw assessment responses from get_school_assessment_stats: array of { Trigger, Break-Reason, Goal30, ... } */
type RawResponses = Array<Record<string, unknown>>;

function aggregateField(
  responses: RawResponses,
  field: string,
  opts: { isMulti?: boolean; normalizer?: (v: string) => string; limit?: number } = {}
): Array<{ name: string; value: number }> {
  const { isMulti = false, normalizer, limit = 10 } = opts;
  if (!responses?.length) return [];
  const counts: Record<string, number> = {};
  for (const resp of responses) {
    const raw = resp[field];
    if (raw === null || raw === undefined) continue;
    const vals = isMulti
      ? (Array.isArray(raw) ? (raw as unknown[]) : [raw])
      : [Array.isArray(raw) ? (raw as unknown[])[0] : raw];
    for (const v of vals) {
      const key = normalizer ? normalizer(String(v ?? "")) : String(v ?? "");
      if (!key) continue;
      counts[key] = (counts[key] || 0) + 1;
    }
  }
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  if (!total) return [];
  return Object.entries(counts)
    .map(([name, cnt]) => ({ name, value: Math.round((cnt * 100) / total) }))
    .sort((a, b) => b.value - a.value)
    .slice(0, limit);
}

const normalizeDays = (v: string): string =>
  ({
    "6-7 days a week": "6–7 days/week",
    "4-5 days a week": "4–5 days/week",
    "2-3 days a week": "2–3 days/week",
    "About once a week or less": "Once/week or less",
    "7": "6–7 days/week",
    "6": "6–7 days/week",
    "5": "4–5 days/week",
    "4": "4–5 days/week",
    "3": "2–3 days/week",
    "2": "2–3 days/week",
    "1": "Once/week or less",
  }[v] ?? v);

type AssessmentSummary = {
  triggers: Array<{ name: string; value: number }>;
  breakReasons: Array<{ name: string; value: number }>;
  goals: Array<{ name: string; value: number }>;
  intentions: Array<{ name: string; value: number }>;
  daysUsing: Array<{ name: string; value: number }>;
  consumption: Array<{ name: string; value: number }>;
};

function aggregateAssessmentData(raw: unknown): AssessmentSummary | null {
  const responses = Array.isArray(raw) ? (raw as RawResponses) : null;
  if (!responses?.length) return null;
  return {
    triggers: aggregateField(responses, "Trigger", { isMulti: true }),
    breakReasons: aggregateField(responses, "Break-Reason", { isMulti: true }),
    goals: aggregateField(responses, "Goal30"),
    intentions: aggregateField(responses, "Then-What"),
    daysUsing: aggregateField(responses, "Days-Using", {
      normalizer: normalizeDays,
    }),
    consumption: aggregateField(responses, "Consumption-Method", {
      isMulti: true,
    }),
  };
}

function buildSystemPrompt(
  schoolName: string,
  analytics: Record<string, number> | null,
  enrollment: Array<Record<string, unknown>> | null,
  weeklyCheckins: Array<Record<string, unknown>> | null,
  assessmentSummary: AssessmentSummary | null
): string {
  let prompt = `You are Clear30 AI, a helpful research assistant for university wellness administrators who use the Clear30 app — a 30-day cannabis tolerance break program for college students.\n\nYou help administrators understand their student data, spot trends, and prepare insights for stakeholders. Be warm, concise, and actionable. Use bold (**text**) for emphasis. When sharing numbers, put them in context. Answer questions about triggers, break reasons, goals, consumption methods, and engagement using ONLY the data below — if a topic has no data, say so.\n\nWhen the user asks about "active users", "total users", "how many students", or "overall" without specifying a time frame, use the all-time total (Total students all-time) as the main answer. You can also mention "Active in last 30 days" for context, but do not report only the 30-day number when they are asking for overall or total counts.\n\nHere is the current program data for ${schoolName}:\n\n`;

  if (analytics) {
    prompt += `PROGRAM METRICS:\n- Total students (all-time): ${analytics.total_enrolled ?? analytics.total_students ?? "N/A"} (students who have ever been enrolled or engaged)\n- Total Check-Ins: ${analytics.total_checkins ?? "N/A"}\n- Sober Days Logged: ${analytics.total_sober_days ?? "N/A"}\n- Average Sober Rate: ${analytics.avg_sober_rate ?? "N/A"}%\n- Active in last 30 days only: ${analytics.active_last_30d ?? "N/A"} (a subset of the all-time total)\n\n`;
  } else {
    prompt += `PROGRAM METRICS: Data not available\n\n`;
  }

  if (enrollment && Array.isArray(enrollment) && enrollment.length > 0) {
    prompt += `ENROLLMENT TREND:\n`;
    for (const row of enrollment) {
      prompt += `- ${row.period ?? row.month ?? "Period"}: ${row.count ?? row.enrolled ?? row.total ?? "N/A"} students\n`;
    }
    prompt += `\n`;
  }

  if (
    weeklyCheckins &&
    Array.isArray(weeklyCheckins) &&
    weeklyCheckins.length > 0
  ) {
    prompt += `WEEKLY CHECK-IN TREND:\n`;
    for (const row of weeklyCheckins) {
      prompt += `- ${row.period ?? row.week ?? "Week"}: ${row.checkins ?? row.count ?? "N/A"} check-ins, ${row.sober_days ?? "N/A"} sober days\n`;
    }
    prompt += `\n`;
  }

  if (assessmentSummary) {
    if (assessmentSummary.triggers.length > 0) {
      prompt += `MOST COMMON TRIGGERS (from student assessments; percentages of responses):\n`;
      for (const t of assessmentSummary.triggers) {
        prompt += `- ${t.name}: ${t.value}%\n`;
      }
      prompt += `\n`;
    }
    if (assessmentSummary.breakReasons.length > 0) {
      prompt += `BREAK REASONS (why students are taking a break):\n`;
      for (const r of assessmentSummary.breakReasons) {
        prompt += `- ${r.name}: ${r.value}%\n`;
      }
      prompt += `\n`;
    }
    if (assessmentSummary.goals.length > 0) {
      prompt += `GOALS (Goal30 — what students want from the program):\n`;
      for (const g of assessmentSummary.goals) {
        prompt += `- ${g.name}: ${g.value}%\n`;
      }
      prompt += `\n`;
    }
    if (assessmentSummary.intentions.length > 0) {
      prompt += `POST-BREAK INTENTIONS (Then-What — plans after the break):\n`;
      for (const i of assessmentSummary.intentions) {
        prompt += `- ${i.name}: ${i.value}%\n`;
      }
      prompt += `\n`;
    }
    if (assessmentSummary.daysUsing.length > 0) {
      prompt += `DAYS USING (frequency before the break):\n`;
      for (const d of assessmentSummary.daysUsing) {
        prompt += `- ${d.name}: ${d.value}%\n`;
      }
      prompt += `\n`;
    }
    if (assessmentSummary.consumption.length > 0) {
      prompt += `CONSUMPTION METHODS:\n`;
      for (const c of assessmentSummary.consumption) {
        prompt += `- ${c.name}: ${c.value}%\n`;
      }
      prompt += `\n`;
    }
  }

  prompt += `Keep responses focused and practical. If asked about something not in the data above, say so honestly (e.g. "I don't have trigger data for your program" or "That metric isn't available in this view."). Format responses with clear sections and bullet points when helpful. Keep responses under 250 words unless a comprehensive summary is requested.`;

  return prompt;
}


/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/school_ai_chat' \
    --header 'Authorization: Bearer eyJhbGciOiJFUzI1NiIsImtpZCI6ImI4MTI2OWYxLTIxZDgtNGYyZS1iNzE5LWMyMjQwYTg0MGQ5MCIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjIwODcxNzg1MDZ9.55jNkDK_N-4sR2Cl99OhpTUzDNvB2jMBjfEvNXop6jrablS8GG_N89KSI2JVLSzLZkkJGD_nhcNOKL6rIhBwKw' \
    --header 'Content-Type: application/json' \
    --data '{"name":"Functions"}'

*/
