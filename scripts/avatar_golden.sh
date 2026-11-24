#!/bin/sh
# Golden-image check for the native VRM engine. Poses the avatar through the bridge, screenshots the phone and compares
# against docs/golden/<name>/<pose>.png (PSNR via ImageMagick).
#   scripts/avatar_golden.sh <library-id> <name> [--update]
# --update writes new goldens instead of comparing. Run on a phone with the avatar slot reachable.
set -eu
ID="${1:?library id}"; NAME="${2:?name}"; MODE="${3:-compare}"
B="$(dirname "$0")/../.tmp/b.sh"; OUT="$(dirname "$0")/../docs/golden/$NAME"; TMP="$(dirname "$0")/../.tmp/golden"
mkdir -p "$OUT" "$TMP"
sh "$B" avatar_library "{\"action\":\"select\",\"id\":\"$ID\"}" 120 >/dev/null; sleep 6
shot() { adb exec-out screencap -p > "$TMP/$1.png"; }
pose() { sh "$B" avatar_pose "$2" 100 >/dev/null; sleep 2; shot "$1"; }
sh "$B" avatar_pose '{}' 100 >/dev/null; sleep 2; shot rest
pose arms '{"bones":{"leftUpperArm":[0,0,0.2],"rightUpperArm":[0,0,-0.2]},"blend_ms":0}'
pose mouth '{"expressions":{"aa":1},"blend_ms":0}'
pose happy '{"expressions":{"happy":1},"blend_ms":0}'
pose headturn '{"bones":{"head":[0,0.5,0]},"blend_ms":0}'
fail=0
for p in rest arms mouth happy headturn; do
  if [ "$MODE" = "--update" ]; then cp "$TMP/$p.png" "$OUT/$p.png"; echo "golden $NAME/$p updated"; continue; fi
  [ -f "$OUT/$p.png" ] || { echo "FAIL $p: no golden (run with --update)"; fail=1; continue; }
  # PSNR via ImageMagick (inf means identical); >= 28 dB passes.
  psnr=$(magick compare -metric PSNR "$TMP/$p.png" "$OUT/$p.png" null: 2>&1 || true)
  case "$psnr" in inf*) psnr=99;; esac
  if awk -v v="$psnr" 'BEGIN{exit !(v+0>=28)}'; then echo "ok   $p psnr=$psnr"; else echo "FAIL $p psnr=$psnr"; fail=1; fi
done
exit $fail
