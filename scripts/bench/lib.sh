# shellcheck shell=bash
# Shared helpers for scripts/bench/*.sh: package name, the on-phone bridge helper (the token is read on the phone and never
# printed), and DeskbotVrmPerf log capture. Source it; do not run it.
PKG=com.rishitgoklani.hermes_front_mobile
HELPER=/data/local/tmp/deskbot-bridge.sh
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)

# Same helper as scripts/avatar_stress.sh: <tool> <base64-json-args>, so arguments survive adb + su quoting.
push_helper() {
  adb shell "cat > $HELPER" <<'EOS'
#!/system/bin/sh
# usage: deskbot-bridge.sh <tool> <base64-json-args>
T=$(cat /data/data/com.termux/files/home/.hermes/deskbot-bridge-token | tr -d '[:space:]')
A=$(printf '%s' "$2" | base64 -d)
printf '{"method":"call","token":"%s","name":"%s","arguments":%s}\n' "$T" "$1" "$A" | nc -w 20 127.0.0.1 8643
EOS
}
call() { adb shell "su -c 'sh $HELPER $1 $(printf '%s' "$2" | base64 -w0)'"; }

# Installed avatar ids in the order the swipe ring walks them (avatar_library list order).
avatar_ids() { call avatar_library '{"action":"list"}' | grep -o '[0-9a-f]\{8\}-[0-9a-f]\{4\}-[0-9a-f]\{4\}-[0-9a-f]\{4\}-[0-9a-f]\{12\}'; }

# DeskbotVrmPerf lines currently in the log buffer, without the logcat prefix.
perf_lines() { adb logcat -d -s DeskbotVrmPerf | sed 's/^.*DeskbotVrmPerf: //'; }

# Grant permissions again after an install (CLAUDE.md: re-run after every update).
grant_all() { adb push "$ROOT/scripts/grant_all.sh" /data/local/tmp/ >/dev/null && adb shell su -c 'sh /data/local/tmp/grant_all.sh' >/dev/null; }
