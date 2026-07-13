#!/usr/bin/env bash
# Build + run Clear30 on the emulator, end to end. Idempotent: safe to re-run;
# skips whatever is already up (backend, emulator). See README.md → "Running
# from the CLI" for the manual steps this automates.
#
# Usage:
#   ./run.sh              build, install, launch
#   ./run.sh --no-build   reinstall + relaunch the last-built APK
set -euo pipefail
cd "$(dirname "$0")"

SDK="$HOME/Library/Android/sdk"
ADB="$SDK/platform-tools/adb"
AVD="Medium_Phone_API_36.0"
BACKEND_DIR="$HOME/Workspace/iOS/Clear30/Backend"
APK="app/build/outputs/apk/debug/app-debug.apk"
APP_ID="org.clear30.debug"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"

step() { printf '\n\033[1;36m▸ %s\033[0m\n' "$*"; }

# 1. Local Supabase (only when SUPABASE_LOCAL isn't set to false)
if ! grep -q '^SUPABASE_LOCAL=false' local.properties 2>/dev/null; then
    if curl -fsS -o /dev/null --max-time 2 http://127.0.0.1:54321/auth/v1/health 2>/dev/null; then
        step "Local Supabase already running"
    else
        step "Starting local Supabase ($BACKEND_DIR)"
        (cd "$BACKEND_DIR" && supabase start)
    fi
else
    step "SUPABASE_LOCAL=false — skipping local backend"
fi

# 2. Emulator
if "$ADB" devices | grep -q 'emulator-.*device$'; then
    step "Emulator already running"
else
    step "Booting emulator ($AVD)"
    "$SDK/emulator/emulator" -avd "$AVD" >/dev/null 2>&1 &
    "$ADB" wait-for-device
    until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 3
    done
fi

# 3. Build
if [ "${1:-}" != "--no-build" ]; then
    step "Building debug APK"
    ./gradlew assembleDebug
fi

# 4. Install + launch
step "Installing $APK"
"$ADB" install -r "$APK"

step "Launching $APP_ID"
"$ADB" shell am force-stop "$APP_ID"
"$ADB" logcat -c
"$ADB" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null

sleep 5
step "Supabase env (from app log)"
"$ADB" logcat -d | grep "Supabase →" | tail -1 || echo "  (no Supabase log line yet — check manually: adb logcat | grep 'Supabase →')"
