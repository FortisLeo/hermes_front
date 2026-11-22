#!/data/data/com.termux/files/usr/bin/sh
# Deskbot start-up for Termux. Starts, idempotently (safe to run any time, also by hand):
#   1. the Termux wake lock (keeps Android from freezing Termux)
#   2. the tmux session "hermes"
#   3. sshd on port 8022 (for the PC tunnels, see docs/guides)
#   4. the Hermes gateway (window deskbot-gateway)
#   5. the Hermes dashboard on 127.0.0.1:9119 (window dashboard)
#   6. the watchdog (window watchdog), if ~/bin/deskbot-watchdog.sh exists
# As a boot script: install the Termux:Boot app, open it once, then copy this file to ~/.termux/boot/10-deskbot.sh
# By hand: run  sh ~/bin/deskbot-up   (setup_device.sh installs this file there too). Log: ~/deskbot-boot.log
LOG="$HOME/deskbot-boot.log"
say() { echo "$(date '+%F %T') $*" | tee -a "$LOG"; }

termux-wake-lock 2>/dev/null && say "wake lock on" || say "wake lock failed (is Termux:API / termux-wake-lock available?)"

tmux has-session -t hermes 2>/dev/null || { tmux new-session -d -s hermes -c "$HOME" && say "tmux session hermes created"; }

if pgrep -x sshd >/dev/null; then say "sshd already running"; else sshd && say "sshd started (port 8022)" || say "sshd FAILED (run: ssh-keygen -A, then sshd)"; fi

start_window() { # name, command, process pattern (skip when that process already runs, wherever it was started)
  if pgrep -f "$3" >/dev/null; then say "$1 already running"
  elif tmux list-windows -t hermes -F '#W' 2>/dev/null | grep -qx "$1"; then say "$1 window already exists"
  else tmux new-window -d -t hermes -n "$1" "$2" && say "$1 started"; fi
}
start_window deskbot-gateway "hermes gateway run" "gateway run"
start_window dashboard "hermes dashboard --no-open" "hermes_cli.*dashboard|dashboard --no-open"
[ -x "$HOME/bin/deskbot-watchdog.sh" ] && start_window watchdog "$HOME/bin/deskbot-watchdog.sh" "deskbot-watchdog"

say "done: windows = $(tmux list-windows -t hermes -F '#W' | tr '\n' ' ')"
