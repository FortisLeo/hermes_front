#!/bin/bash
# Benchmarks the release build the way it would really run: builds it, signs it with the key Gradle uses for debug builds (so it
# installs over the debug app without wiping its avatar library, token or settings), compiles it ahead of time, runs the cold-start
# and swap benchmarks, then puts the debug build back.
# usage: scripts/bench/avatar_release.sh [cold-runs=3]     (override the key with DEBUG_KEYSTORE=/path/to/debug.keystore)
# Needs: adb + root, Android SDK build-tools (apksigner) found through local.properties.
source "$(dirname "$0")/lib.sh"
RUNS=${1:-3}; cd "$ROOT" || exit 1
KS=${DEBUG_KEYSTORE:-$HOME/.config/.android/debug.keystore}; [ -f "$KS" ] || KS=$HOME/.android/debug.keystore
SDK=$(grep '^sdk.dir' local.properties | cut -d= -f2); BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
./gradlew assembleRelease assembleDebug -q || { echo "FAIL build"; exit 1; }; echo "ok build"
mkdir -p build/bench; OUT=build/bench/app-release-debugsigned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out "$OUT" app/build/outputs/apk/release/app-release-unsigned.apk 2>/dev/null \
  || { echo "FAIL sign with $KS"; exit 1; }; echo "ok signed with $KS"
R=$(adb install -r -g "$OUT" 2>&1 | tail -1)
case "$R" in *Success*) echo "ok release installed";; *) echo "FAIL install: $R (signature mismatch? point DEBUG_KEYSTORE at the key that signed the debug app)"; exit 1;; esac
restore() { adb install -r -g app/build/outputs/apk/debug/app-debug.apk >/dev/null 2>&1 && grant_all && sh scripts/sync_token.sh >/dev/null 2>&1 && echo "ok debug build restored" || echo "FAIL restoring the debug build"; }
trap restore EXIT
grant_all
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 15
adb shell cmd package compile -f -m speed $PKG >/dev/null 2>&1 && echo "ok compiled ahead of time"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 66   # past AvatarSafety's 60 s window
bash "$ROOT/scripts/bench/avatar_cold.sh" "$RUNS"; c=$?
bash "$ROOT/scripts/bench/avatar_swap.sh"; s=$?
exit $((c | s))
