#!/data/data/com.termux/files/usr/bin/sh
# Termux:Boot entry. Install to ~/.termux/boot/10-deskbot.sh (requires the Termux:Boot app, run once to enable).
termux-wake-lock
tmux has-session -t hermes 2>/dev/null || tmux new-session -d -s hermes -c "$HOME"
tmux has-window -t hermes:deskbot-gateway 2>/dev/null || tmux new-window -d -t hermes -n deskbot-gateway "hermes gateway run"
tmux new-window -d -t hermes -n watchdog "$HOME/bin/deskbot-watchdog.sh"
