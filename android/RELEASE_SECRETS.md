# Release secrets — where they live & how to recover them

**No secret values are in this file.** Actual keys/passwords live in
`android/local.properties` and `android/app/upload-keystore.jks` (both
git-ignored) on the build machine. This is only a map so nothing is ever "lost."

## Upload keystore (signs release AABs) — the one that matters most
- **Primary source of truth:** EAS. Retrieve with
  `cd ~/Workspace/Android/Clear30-android && eas credentials -p android`
  (→ production → Keystore → Download), or on **expo.dev → project Clear30 →
  Credentials → Android → `org.clear30.Clear30v1`**.
- **Local backup:** `~/Downloads/@thatcherclough__Clear30-keystore-backup.zip`
  (contains the `.jks` + a `…credentials.md` with the store/key passwords + alias).
  → **Store this ZIP in a password manager / company vault.**
- **In this repo (git-ignored):** `app/upload-keystore.jks` +
  `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` in
  `local.properties`.
- **Fingerprints (public — safe to record):**
  - Upload key SHA-256: `52:E1:7E:65:66:E7:1C:FC:62:39:A0:E5:56:DA:12:C8:D8:7A:4F:7A:FB:3B:1C:64:51:AD:21:38:D1:F7:96:FD`
- **If ever lost:** Play App Signing IS enabled, so request an **upload-key reset**
  in Play Console → App integrity. Google keeps the app-signing key, so existing
  installs keep updating.

## App signing key (Google-managed via Play App Signing)
- You never hold this; Google re-signs uploads with it.
- **SHA-256 (public):** `8E:CD:C2:15:D5:C3:66:49:F1:79:E4:9A:7C:7C:79:67:01:E3:5B:91:E7:D2:7F:7B:9F:2E:49:A6:00:1B:D9:CA`
  — this is the value used in `deeplinks/assetlinks.json` and for Firebase /
  Google APIs that need the production cert.
- View at: Play Console → your app → **Test and release → App integrity → App signing**.

## API keys (all re-copyable from dashboards; kept in `local.properties`)
| Key | `local.properties` field | Where to re-copy |
|---|---|---|
| RevenueCat Android SDK key (public, `goog_…`) | `REVENUECAT_API_KEY` | RevenueCat → project **Clear30** → app **Clear30 (Play Store)** → API keys |
| Supabase prod anon key | `SUPABASE_ANON_KEY` | Supabase dashboard → project → Settings → API |
| Helium API key (when set up) | `HELIUM_API_KEY` | app.tryhelium.com → Profile |

## Google Play service-account JSON (RevenueCat ↔ Play validation)
- Uploaded into RevenueCat's Play Store app; the JSON was downloaded as
  `~/Downloads/clear30-revenue-cat-*.json`. **Store in the password manager too.**
- Regenerate if lost: Play Console → Setup → API access → service account → new JSON key.

## Rule of thumb
Fingerprints and the `goog_` RevenueCat key are effectively public (the key ships
inside the APK). The **keystore `.jks` + its passwords** and the **Play
service-account JSON** are the genuinely sensitive files — keep those in a
password manager, never in git.
