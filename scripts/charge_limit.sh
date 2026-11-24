#!/system/bin/sh
# Hardware charge cap, only when the kernel exposes a documented charge-control node (no ACC module needed).
# Run as root on the phone:  sh charge_limit.sh status | set <50-100> | reset
# Read before write: status is read-only; set/reset only write a node that exists, is writable and already holds a number.
# Idempotent. Prints ok/FAIL/skip per step. Never touches block devices, partitions or anything outside /sys/class/power_supply.
# The previous value is saved once to /data/local/tmp/charge_limit.before-<node> so reset restores it.
# If no node exists (ACC absent and no sysfs control) this prints "skip no charge-control node" and exits 0:
# the app's software cap alert (bridge tool charge_limit) is the fallback.
BASE=/sys/class/power_supply
NODES="battery/charge_control_end_threshold battery/charge_limit battery/input_suspend"
ok() { echo "ok   $*"; }
fail() { echo "FAIL $*"; }

find_node() {
  for n in battery/charge_control_end_threshold; do
    [ -w "$BASE/$n" ] && grep -Eq '^[0-9]+$' "$BASE/$n" 2>/dev/null && { echo "$BASE/$n"; return 0; }
  done
  return 1
}

cmd="${1:-status}"
node="$(find_node)"
case "$cmd" in
  status)
    for n in $NODES; do [ -e "$BASE/$n" ] && echo "node $n = $(cat "$BASE/$n" 2>/dev/null) writable=$([ -w "$BASE/$n" ] && echo yes || echo no)"; done
    [ -n "$node" ] && ok "usable node $node" || echo "skip no charge-control node"
    echo "level $(cat $BASE/battery/capacity 2>/dev/null) status $(cat $BASE/battery/status 2>/dev/null)"
    ;;
  set)
    pct="$2"
    case "$pct" in ''|*[!0-9]*) fail "percent must be a number"; exit 1;; esac
    [ "$pct" -ge 50 ] && [ "$pct" -le 100 ] || { fail "percent must be 50-100"; exit 1; }
    [ -n "$node" ] || { echo "skip no charge-control node"; exit 0; }
    bak="/data/local/tmp/charge_limit.before-$(echo "$node" | tr '/' '_')"
    [ -e "$bak" ] || cat "$node" > "$bak"
    [ "$(cat "$node")" = "$pct" ] && { ok "already $pct"; exit 0; }
    echo "$pct" > "$node" && [ "$(cat "$node")" = "$pct" ] && ok "set $node $pct" || fail "set $node $pct"
    ;;
  reset)
    [ -n "$node" ] || { echo "skip no charge-control node"; exit 0; }
    bak="/data/local/tmp/charge_limit.before-$(echo "$node" | tr '/' '_')"
    [ -e "$bak" ] || { echo "skip no backup, nothing to restore"; exit 0; }
    echo "$(cat "$bak")" > "$node" && ok "restored $(cat "$bak")" || fail "restore"
    ;;
  *) fail "usage: status | set <50-100> | reset"; exit 1;;
esac
