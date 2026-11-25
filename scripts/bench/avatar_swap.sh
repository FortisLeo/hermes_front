#!/bin/bash
# Avatar swap benchmark over the bridge: walks the whole ring with "next" (each step lands on the model staged behind the previous
# one), then jumps to avatars that are not staged (two steps ahead), which forces a load from scratch.
# usage: scripts/bench/avatar_swap.sh [seconds-per-step=9]
# Needs: adb + root, the app open, the bridge token synced (scripts/sync_token.sh), at least two avatars installed.
# Prints want->shown per step (ok/FAIL) and the load/stage/finish phase lines. Touching the phone during the run mixes in swaps.
source "$(dirname "$0")/lib.sh"
WAIT=${1:-9}; push_helper
mapfile -t IDS < <(avatar_ids); N=${#IDS[@]}
[ "$N" -ge 2 ] || { echo "FAIL need at least 2 avatars (found $N)"; exit 1; }
fail=0
step() { # <json args> <label> [expect-avatar=1]
  adb logcat -c; call avatar_library "$1" >/dev/null; sleep "$WAIT"
  local L; L=$(perf_lines); local S; S=$(echo "$L" | grep '^shown' | head -1)
  if [ -n "$S" ]; then echo "$2: ok $(echo "$S" | grep -o 'staged=[a-z]* .*want->shown=[0-9-]*' | sed 's/slotMs=[0-9]* //')"
  elif [ "${3:-1}" = 0 ]; then echo "$2: ok (eyes)"
  else echo "$2: FAIL nothing shown in ${WAIT}s"; fail=1; fi
  echo "$L" | grep -E '^(load|finish|stage) ' | sed 's/^/    /' | cut -c1-220
}
step "{\"action\":\"select\",\"id\":\"${IDS[0]}\"}" "select ${IDS[0]:0:8}"
for k in $(seq 1 $((N - 1))); do step '{"action":"next"}' "next -> ${IDS[$k]:0:8}"; done
step '{"action":"next"}' "next -> eyes" 0
step '{"action":"next"}' "next -> ${IDS[0]:0:8} (from eyes: nothing staged)"
cur=0; for _ in $(seq 1 "$N"); do cur=$(( (cur + 2) % N )); step "{\"action\":\"select\",\"id\":\"${IDS[$cur]}\"}" "jump -> ${IDS[$cur]:0:8}"; done
exit $fail
