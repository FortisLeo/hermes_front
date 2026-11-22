#!/data/data/com.termux/files/usr/bin/sh
# Termux:Boot entry. Install to ~/.termux/boot/10-deskbot.sh (requires the Termux:Boot app, run once to enable).
termux-wake-lock
tmux has-session -t hermes 2>/dev/null || tmux new-session -d -s hermes -c "$HOME"
tmux has-window -t hermes:deskbot-gateway 2>/dev/null || tmux new-window -d -t hermes -n deskbot-gateway "hermes gateway run"
tmux new-window -d -t hermes -n watchdog "$HOME/bin/deskbot-watchdog.sh"
# Remote access: SSH server for tunnels (port 8022). The dashboard is only started if you created the flag file: touch ~/.deskbot-dashboard
pgrep sshd >/dev/null || sshd
[ -f "$HOME/.deskbot-dashboard" ] && tmux new-window -d -t hermes -n dashboard "hermes dashboard --no-open"
