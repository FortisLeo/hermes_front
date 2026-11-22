#!/bin/sh
# One-click setup of a new Deskbot phone from this repo. Run on the PC with the phone connected over adb (rooted, Termux + Hermes installed).
#   ./scripts/setup_device.sh            full setup (build, install, grants, skills, Termux packages, token, watchdog)
#   ./scripts/setup_device.sh --no-build use the existing app/build/outputs/apk/debug/app-debug.apk
#   ./scripts/setup_device.sh --no-termux-packages
# Idempotent: every step prints "ok" or "FAIL" and later steps still run. Never prints the API key or bridge token.
cd "$(dirname "$0")/.." || exit 1
PKG=com.rishitgoklani.hermes_front_mobile
TERMUX=/data/data/com.termux/files
BUILD=1; PKGS=1
for a in "$@"; do case "$a" in --no-build) BUILD=0;; --no-termux-packages) PKGS=0;; esac; done
step() { printf '%s\n' "== $1"; }
ok() { printf 'ok   %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1"; }
root() { adb shell "su --mount-master -c '$1'"; }

step "device"
adb get-state >/dev/null 2>&1 && ok "adb device connected" || { fail "no adb device"; exit 1; }
[ "$(adb shell su -c id 2>/dev/null | grep -c uid=0)" = 1 ] && ok "root available" || { fail "root (su) not available"; exit 1; }
root "test -d $TERMUX/home/.hermes" >/dev/null 2>&1 && ok "Termux + ~/.hermes present" || fail "Termux/Hermes not found (install Termux and Hermes first)"

step "app"
if [ "$BUILD" = 1 ]; then ./gradlew testDebugUnitTest assembleDebug -q && ok "tests + build" || { fail "build/tests"; exit 1; }; fi
APK=app/build/outputs/apk/debug/app-debug.apk
adb push "$APK" /data/local/tmp/deskbot.apk >/dev/null && root "pm install -r -g /data/local/tmp/deskbot.apk" | grep -q Success && ok "app installed" || fail "app install"
adb push scripts/grant_all.sh /data/local/tmp/grant_all.sh >/dev/null && root "sh /data/local/tmp/grant_all.sh" | grep -c '^ok' | sed 's/^/ok   grants applied: /'

step "hermes skills"
rm -rf .tmp/skills-stage && mkdir -p .tmp/skills-stage && cp -r hermes-skills/deskbot .tmp/skills-stage/ && adb push .tmp/skills-stage/deskbot /data/local/tmp/skills-deskbot >/dev/null
root "U=\$(stat -c %u $TERMUX/home); mkdir -p $TERMUX/home/.hermes/skills && rm -rf $TERMUX/home/.hermes/skills/deskbot && cp -r /data/local/tmp/skills-deskbot $TERMUX/home/.hermes/skills/deskbot && chown -R \$U:\$U $TERMUX/home/.hermes/skills/deskbot && echo placed" | grep -q placed && ok "skills installed in ~/.hermes/skills/deskbot" || fail "skills"

step "termux scripts"
for f in watchdog boot; do adb push scripts/termux/$f.sh /data/local/tmp/$f.sh >/dev/null; done
adb push scripts/termux/setup_packages.sh /data/local/tmp/setup_packages.sh >/dev/null
root "U=\$(stat -c %u $TERMUX/home); mkdir -p $TERMUX/home/bin $TERMUX/home/.termux/boot; cp /data/local/tmp/watchdog.sh $TERMUX/home/bin/deskbot-watchdog.sh; cp /data/local/tmp/boot.sh $TERMUX/home/.termux/boot/10-deskbot.sh; cp /data/local/tmp/boot.sh $TERMUX/home/bin/deskbot-up; cp /data/local/tmp/setup_packages.sh $TERMUX/home/bin/setup_packages.sh; chown -R \$U:\$U $TERMUX/home/bin $TERMUX/home/.termux/boot; chmod 755 $TERMUX/home/bin/* $TERMUX/home/.termux/boot/*; echo placed" | grep -q placed && ok "watchdog + boot + package scripts placed (boot script needs the Termux:Boot app to run at boot)" || fail "termux scripts"
if [ "$PKGS" = 1 ]; then
  step "termux packages (backs up ~/.hermes first)"
  adb push scripts/termux/run_in_termux.sh /data/local/tmp/run_in_termux.sh >/dev/null
  adb shell "su --mount-master -c 'sh /data/local/tmp/run_in_termux.sh setup_packages.sh pkgsetup.log'" | grep -q rc=0 && ok "package install started in tmux window 'pkgsetup' (log: ~/pkgsetup.log)" || fail "package install start"
fi

step "bridge token + app"
sh scripts/sync_token.sh 2>/dev/null | grep -q token-synced && ok "bridge token synced to the app" || fail "token sync (does ~/.hermes/deskbot-bridge-token exist?)"
adb shell am start -n $PKG/.MainActivity >/dev/null && ok "app started"

step "next"
echo "1. Restart the Hermes gateway so it discovers the deskbot_* tools:  tmux new-window -d -t hermes -n deskbot-gateway 'hermes gateway run'  (in Termux)"
echo "2. Optionally install the Termux:Boot app and open it once so the boot script runs."
echo "3. Check: python3 scripts/verify_recipes.py   and   python3 scripts/verify_hermes.py"
