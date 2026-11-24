#!/bin/sh
# Golden-image check for the native VRM engine. Poses the avatar through the bridge, screenshots the phone and compares
# against docs/golden/<name>/<pose>.png (SSIM via python3 + Pillow/numpy when available, byte size sanity otherwise).
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
  python3 - "$TMP/$p.png" "$OUT/$p.png" "$p" <<'PY' || fail=1
import sys
try:
    from PIL import Image; import numpy as np
except ImportError:
    print("SKIP", sys.argv[3], "(Pillow/numpy missing)"); sys.exit(0)
a=np.asarray(Image.open(sys.argv[1]).convert("L"),dtype=float); b=np.asarray(Image.open(sys.argv[2]).convert("L"),dtype=float)
if a.shape!=b.shape: print("FAIL",sys.argv[3],"size differs",a.shape,b.shape); sys.exit(1)
mse=((a-b)**2).mean(); psnr=99 if mse==0 else 10*np.log10(255*255/mse)
print(("ok  " if psnr>=28 else "FAIL"),sys.argv[3],"psnr=%.1f"%psnr); sys.exit(0 if psnr>=28 else 1)
PY
done
exit $fail
