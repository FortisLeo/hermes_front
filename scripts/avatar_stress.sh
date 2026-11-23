#!/bin/sh
# Stress-test a VRM on the phone: import it N times and check the app survives (same process) and the model proves itself (status ok).
# usage: scripts/avatar_stress.sh "/sdcard/Download/Some Model.vrm" [rounds=3]
# Needs: adb + root on the phone, Deskbot running. Prints ok/FAIL per round; exit code 1 on any failure.
# Each round adds a copy to the avatar library and removes it afterwards. The bridge token is read on the phone and never printed.
MODEL="$1"; ROUNDS="${2:-3}"
[ -n "$MODEL" ] || { echo "usage: $0 <phone path to .vrm> [rounds]"; exit 2; }
PKG=com.rishitgoklani.hermes_front_mobile
HELPER=/data/local/tmp/deskbot-bridge.sh

adb shell "cat > $HELPER" <<'EOS'
#!/system/bin/sh
# usage: deskbot-bridge.sh <tool> <base64-json-args>
T=$(cat /data/data/com.termux/files/home/.hermes/deskbot-bridge-token | tr -d '[:space:]')
A=$(printf '%s' "$2" | base64 -d)
printf '{"method":"call","token":"%s","name":"%s","arguments":%s}\n' "$T" "$1" "$A" | nc -w 20 127.0.0.1 8643
EOS
call() { adb shell "su -c 'sh $HELPER $1 $(printf '%s' "$2" | base64 -w0)'"; }

adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 4
PID=$(adb shell pidof $PKG | tr -d '\r'); [ -n "$PID" ] || { echo "FAIL app not running"; exit 1; }
JSON=$(printf '{"path":"%s"}' "$MODEL")
fail=0
for r in $(seq 1 "$ROUNDS"); do
  BEFORE=$(call avatar_library '{"action":"list"}')
  RES=$(call avatar_import "$JSON")
  case "$RES" in *'"status":"completed"'*) ;; *) echo "round $r: FAIL import refused: $(echo "$RES" | cut -c1-160)"; fail=1; continue;; esac
  LIST=$(call avatar_library '{"action":"list"}')
  ID=$(printf '%s' "$LIST" | grep -o '[0-9a-f]\{8\}-[0-9a-f]\{4\}-[0-9a-f]\{4\}-[0-9a-f]\{4\}-[0-9a-f]\{12\}[^\\]*showing' | head -1 | cut -d' ' -f1)
  i=0; while [ $i -lt 30 ]; do
    L=$(call avatar_library '{"action":"list"}'); case "$L" in *"$ID | "*"| ok"*) break;; esac; sleep 3; i=$((i+1))
  done
  NOW=$(adb shell pidof $PKG | tr -d '\r')
  case "$L" in *"$ID | "*"| ok"*) OK=1;; *) OK=0;; esac
  if [ "$NOW" = "$PID" ] && [ "$OK" = 1 ]; then echo "round $r: ok pid=$NOW"; else echo "round $r: FAIL app restarted or model not proven (pid $PID -> $NOW)"; fail=1; PID="$NOW"; fi
  [ -n "$ID" ] && call avatar_library "$(printf '{"action":"remove","id":"%s"}' "$ID")" >/dev/null
done
exit $fail
