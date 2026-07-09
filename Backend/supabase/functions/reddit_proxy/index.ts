// reddit_proxy — server-side fetch of a Reddit thread's JSON.
//
// Reddit serves an HTML block page (HTTP 403) to mobile clients and datacenter
// IPs when they request `<permalink>.json` directly (TLS fingerprint / IP-range
// heuristics), so both the iOS and Android apps route Reddit fetches through this
// edge function, which fetches server-side with a browser User-Agent and returns
// the raw Reddit JSON unchanged. Mirrors the iOS `RedditScraper` proxy dependency.
//
// Request:  POST { "url": "https://www.reddit.com/r/<sub>/comments/<id>/<slug>/" }
// Response: the raw Reddit JSON array (2 listings), or { "error": "..." }.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
}

// A realistic mobile browser UA — a descriptive/bot UA gets 403'd by Reddit.
const BROWSER_UA =
  "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/** `https://reddit.com/r/x/comments/id/slug/?foo` → `…/slug.json?raw_json=1&limit=100`, or null if not a thread URL. */
function jsonEndpoint(raw: string): string | null {
  if (!raw.includes("reddit.com") || !raw.includes("comments")) return null
  const clean = raw.split("?")[0].replace(/\/+$/, "")
  const base = clean.endsWith(".json") ? clean : `${clean}.json`
  return `${base}?raw_json=1&limit=100`
}

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders })
  const json = (b: unknown, status = 200) =>
    new Response(typeof b === "string" ? b : JSON.stringify(b), {
      status,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    })

  try {
    const { url } = await req.json().catch(() => ({ url: null }))
    const endpoint = typeof url === "string" ? jsonEndpoint(url) : null
    if (!endpoint) return json({ error: "invalid reddit url" }, 400)

    const res = await fetch(endpoint, {
      headers: { "User-Agent": BROWSER_UA, "Accept": "application/json" },
    })
    if (!res.ok) return json({ error: `reddit ${res.status}` }, 502)

    const body = await res.text()
    // Guard against a 200-with-HTML block page.
    if (!body.trimStart().startsWith("[")) return json({ error: "reddit blocked" }, 502)
    return json(body)
  } catch (e) {
    return json({ error: String(e) }, 500)
  }
})
