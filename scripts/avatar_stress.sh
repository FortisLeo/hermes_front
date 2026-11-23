#!/bin/sh
# Stress-test a VRM on the phone: import it N times and check the app survives (same process, no rollback).
# usage: scripts/avatar_stress.sh "/sdcard/Download/Some Model.vrm" [rounds=3]
# Needs: adb + root on the phone, Deskbot running. Prints ok/FAIL per round; exit code 1 on any failure.
# The bridge token is read on the phone and never printed.
MODEL="$1"; ROUNDS="${2:-3}"
[ -n "$MODEL" ] || { echo "usage: $0 <phone path to .vrm> [rounds]"; exit 2; }
PKG=com.rishitgoklani.hermes_front_mobile
PREFS=/data/data/$PKG/shared_prefs/deskbot.xml
HELPER=/data/local/tmp/deskbot-bridge.sh

adb shell "cat > $HELPER" <<'EOS'
#!/system/bin/sh
# usage: deskbot-bridge.sh <tool> <base64-json-args>
T=$(cat /data/data/com.termux/files/home/.hermes/deskbot-bridge-token | tr -d '[:space:]')
A=$(printf '%s' "$2" | base64 -d)
printf '{"method":"call","token":"%s","name":"%s","arguments":%s}\n' "$T" "$1" "$A" | nc -w 20 127.0.0.1 8643
EOS
call() { adb shell "su -c 'sh $HELPER $1 $(printf '%s' "$2" | base64 -w0)'"; }
pending() { adb shell "su -c 'grep -c avatar_model_pending $PREFS'" | tr -d '\r'; }

adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 4
PID=$(adb shell pidof $PKG | tr -d '\r'); [ -n "$PID" ] || { echo "FAIL app not running"; exit 1; }
JSON=$(printf '{"path":"%s"}' "$MODEL")
fail=0
for r in $(seq 1 "$ROUNDS"); do
  call avatar_mode '{"mode":"avatar"}' >/dev/null
  RES=$(call avatar_import "$JSON")
  case "$RES" in *'"status":"completed"'*) ;; *) echo "round $r: FAIL import refused: $(echo "$RES" | cut -c1-160)"; fail=1; continue;; esac
  i=0; while [ "$(pending)" != "0" ] && [ $i -lt 30 ]; do sleep 3; i=$((i+1)); done
  NOW=$(adb shell pidof $PKG | tr -d '\r')
  NOTE=$(adb shell "su -c 'grep avatar_note $PREFS'" | sed 's/<[^>]*>//g' | cut -c1-120)
  if [ "$NOW" = "$PID" ] && [ "$(pending)" = "0" ]; then echo "round $r: ok pid=$NOW $NOTE"; else echo "round $r: FAIL app restarted or model not proven (pid $PID -> $NOW) $NOTE"; fail=1; PID="$NOW"; fi
done
exit $fail
