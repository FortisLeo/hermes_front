#!/bin/bash
# Cold-start benchmark for the 3D avatar: force-stop, launch, and measure until the saved avatar is on screen. Repeats N times.
# usage: scripts/bench/avatar_cold.sh [runs=3]
# Needs: adb, the app installed with an avatar selected. Prints ok/FAIL per run plus the raw DeskbotVrmPerf milestones.
#  - The process is back within ~0.3 s of a force-stop (the accessibility/notification services re-bind), so times are measured
#    from the activity launch, and logs are read by pid.
#  - Each run keeps the app alive ~65 s: two deaths within 60 s of start make AvatarSafety switch the app to the eyes face.
source "$(dirname "$0")/lib.sh"
RUNS=${1:-3}; fail=0; push_helper
case "$(call avatar_library '{"action":"list"}')" in
  *"eyes (showing)"*) echo "FAIL the eyes face is showing: select an avatar first (if you did, AvatarSafety switched to eyes after two quick restarts; wait 60 s and select it again)"; exit 1;;
esac
for i in $(seq 1 "$RUNS"); do
  adb shell am force-stop $PKG; sleep 3; PID=$(adb shell pidof $PKG | tr -d '\r')
  FIRST=$(adb shell am start -W -n $PKG/.MainActivity 2>/dev/null | grep TotalTime | tr -dc '0-9')
  [ -n "$PID" ] || PID=$(adb shell pidof $PKG | tr -d '\r')
  sleep 12
  L=$(adb logcat -d --pid="$PID" -s DeskbotVrmPerf | sed 's/^.*DeskbotVrmPerf: //')
  ACT=$(echo "$L" | grep -o 'startup activityOnCreate=[0-9]*' | tail -1 | cut -d= -f2)
  SHOWN=$(echo "$L" | grep -o 'process->firstShown=[0-9]*' | tail -1 | cut -d= -f2)
  LOAD=$(echo "$L" | grep '^load ' | head -1 | grep -o 'total=[0-9]*' | cut -d= -f2)
  if [ -n "$ACT" ] && [ -n "$SHOWN" ]; then echo "run $i: ok first-frame=${FIRST}ms activity->avatar=$((SHOWN - ACT))ms load=${LOAD:-?}ms"
  else echo "run $i: FAIL no avatar shown within 15 s (is an avatar selected? did AvatarSafety switch to eyes?)"; fail=1; fi
  echo "$L" | grep -E '^(startup|load |preload |shown|coldstart)' | sed 's/^/    /' | cut -c1-220
  [ "$i" -lt "$RUNS" ] && sleep 50
done
exit $fail
