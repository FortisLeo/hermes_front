#!/bin/sh
# Pure decision helpers for the Deskbot watchdog (sourced by watchdog.sh, unit-tested by test_watchdog_lib.sh).

# tools_drifted <registered_count> <current_count>
# True when Hermes registered a different number of bridge tools at its last start than the app exposes now.
tools_drifted() { [ -n "$1" ] && [ -n "$2" ] && [ "$2" -gt 0 ] && [ "$1" != "$2" ]; }

# gateway_idle <activity_file> <now_epoch> <idle_secs>
# True when the gateway's activity file (its agent log) has not changed for idle_secs, i.e. nobody is mid-conversation.
gateway_idle() {
  [ -f "$1" ] || return 0
  m=$(stat -c %Y "$1" 2>/dev/null) || return 1
  [ $(( $2 - m )) -ge "$3" ]
}

# should_restart_gateway <registered> <current> <optin_file> <activity_file> <now_epoch> <idle_secs>
# Restart only when: the user opted in (the file exists), the tool list drifted, and the gateway is idle.
should_restart_gateway() {
  [ -f "$3" ] || return 1
  tools_drifted "$1" "$2" || return 1
  gateway_idle "$4" "$5" "$6"
}
