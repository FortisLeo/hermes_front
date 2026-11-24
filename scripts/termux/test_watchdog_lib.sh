#!/bin/sh
# Unit tests for watchdog_lib.sh. Run on any machine: sh scripts/termux/test_watchdog_lib.sh
. "$(dirname "$0")/watchdog_lib.sh"
T=$(mktemp -d); fail=0
check() { if "$@"; then :; else echo "FAIL: $*"; fail=1; fi; }
not() { ! "$@"; }
touch -d '@1000' "$T/log"; touch "$T/optin"
check tools_drifted 100 120
check not tools_drifted 100 100
check not tools_drifted "" 100
check not tools_drifted 100 0
check gateway_idle "$T/log" 2000 600          # idle for 1000 s >= 600 s
check not gateway_idle "$T/log" 1300 600      # only 300 s idle
check gateway_idle "$T/missing" 2000 600      # no log yet counts as idle
check should_restart_gateway 100 120 "$T/optin" "$T/log" 2000 600
check not should_restart_gateway 100 120 "$T/nooptin" "$T/log" 2000 600   # not opted in
check not should_restart_gateway 100 100 "$T/optin" "$T/log" 2000 600     # no drift
check not should_restart_gateway 100 120 "$T/optin" "$T/log" 1300 600     # conversation in progress
rm -rf "$T"
[ $fail = 0 ] && echo "ok   watchdog_lib tests passed" || { echo "FAIL watchdog_lib"; exit 1; }
