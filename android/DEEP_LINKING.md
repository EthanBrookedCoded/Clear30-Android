# Deep linking on Android (Clear30)

How inbound links open the app and route (group joins, school unlock, referral,
tabs). Ported from iOS `URLManager.swift` / `.onOpenURL`.

## Two mechanisms

| | Custom scheme | Verified App Links |
|---|---|---|
| Example | `clear30://group?group_id=ABC` | `https://clear30.org/group?group_id=ABC` |
| Opens the app | Always (any app can claim a scheme) | Only after Android verifies `assetlinks.json` |
| Needs the website | No | **Yes** — host `assetlinks.json` |
| Best for | in-app / notification / QR links you control | links shared in the wild (SMS, email, web) that must open the app, not the browser |

Both are wired to the same handler, so a `group_id` works identically over either.

## The manifest fix (why it was broken)

`clear30://` links previously **did not resolve** because the manifest declared
the `clear30` scheme and the `https`/`clear30.org` host in **one** intent-filter.
Android combines schemes + hosts across data tags within a filter, so it then
required `clear30://` URLs to *also* have host `clear30.org` — `clear30://group`
never matched. Fixed by splitting into two filters
(`AndroidManifest.xml`): a scheme-only `clear30` filter, and a separate
`autoVerify` filter for `https`/`clear30.org`.

## What YOU need to host (App Links)

Put **`deeplinks/assetlinks.json`** (in this folder) at exactly:

```
https://clear30.org/.well-known/assetlinks.json
```

Served over HTTPS, `Content-Type: application/json`, **200** (no redirect).

It currently contains:
- `org.clear30.Clear30v1.debug` + this machine's **debug** SHA-256 — so verified
  `https://clear30.org/...` links work on debug builds **now**.
- `org.clear30.Clear30v1` (production) with a **placeholder** SHA-256 —
  replace `REPLACE_WITH_PLAY_APP_SIGNING_SHA256` with the fingerprint from
  **Play Console → your app → Setup → App integrity → App signing key
  certificate → SHA-256**. (When you first upload, Play generates the signing
  key; that's the cert that must be in `assetlinks.json`, not your upload key.)

You can list multiple apps/fingerprints — keep the debug entry for testing.

## URL formats the app understands

`clear30://<kind>[/<tail>][?query]` or `https://clear30.org/<kind>[/<tail>][?query]`.
Query params (`school`, `code`) are honored on **any** URL, checked first.

| Link | Action |
|---|---|
| `…/group?group_id=ABC` (or `…/group/ABC`) | Groups tab → **join group ABC** |
| `…/?school=<id>` | Unlock app + school content |
| `…/?code=<code>` | Referral: unlock (if free) + join group if the code carries one |
| `…/post/<id>` | Community → open post |
| `…/today` · `…/community` · `…/groups` · `…/profile` · `…/support` | Switch tab |
| `…/meditation?url=<u>` | Support → play meditation |
| `…/chat/claire` · `…/chat/fred` | Open Claire / Dr Fred |
| `…/breakdown` | Open the day-30 post-assessment |

**Group join flow:** `URLManager.parse` → `DeepLinkRoute.Group(code)` →
`AppRoot` sets the Groups tab + emits the sub-route → `GroupsTab` consumes it →
`GroupController.join(code)` → `add_member` RPC (schema `groups`).

## Website redirect (recommended)

Host a small landing page at `https://clear30.org/group` (and `/`) that:
1. Tries the app via the verified App Link (Android opens the app directly if
   installed + verified).
2. Falls back to the Play Store listing if the app isn't installed.

A plain `assetlinks.json` is enough for installed apps; the landing page is only
for the "not installed yet" case.

## Testing with adb

Custom scheme (works without the website):
```bash
adb shell am start -a android.intent.action.VIEW -d "clear30://group?group_id=REALCODE"
adb shell am start -a android.intent.action.VIEW -d "clear30://?code=REFERRALCODE"
adb shell am start -a android.intent.action.VIEW -d "clear30://community"
```

Verified App Links (after assetlinks.json is live):
```bash
# force Android to (re)check the site association
adb shell pm verify-app-links --re-verify org.clear30.Clear30v1.debug
adb shell pm get-app-links org.clear30.Clear30v1.debug   # look for "verified"
adb shell am start -a android.intent.action.VIEW -d "https://clear30.org/group?group_id=REALCODE"
```

## Status

- ✅ Manifest split — `clear30://` links now resolve (tested on device).
- ✅ Group join **verified end-to-end on device (2026-07-22)** —
  `clear30://group?group_id=<real>` added a real `groups.group_members` row via
  the prod backend. (Join runs on `Clear30Application.appScope` so clearing the
  pending sub-route can't cancel it mid-RPC.)
- ✅ `group_id`, `school`, `code` (referral), post, tabs, meditation, chat routes
  parsed + dispatched.
- ⏳ **You:** host `assetlinks.json` at `clear30.org/.well-known/` (for verified
  `https` links), and drop in the Play App Signing SHA-256 when you publish.
