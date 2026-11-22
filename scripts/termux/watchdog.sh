#!/data/data/com.termux/files/usr/bin/sh
# Deskbot watchdog (runs inside Termux, outside the app so it survives app crashes).
# Every 30 s: ensure the Hermes gateway is up and the Deskbot app service is running.
# Install: copy to ~/bin/deskbot-watchdog.sh, then start from ~/.termux/boot/10-deskbot.sh (see boot.sh).
APP=com.rishitgoklani.hermes_front_mobile
LOG=$HOME/.hermes/logs/watchdog.log
log() { echo "$(date '+%F %T') $*" >> "$LOG"; }

while true; do
  # Gateway: restart in its tmux window if the health endpoint is dead.
  if ! curl -fs -m 5 http://127.0.0.1:8642/health >/dev/null 2>&1; then
    log "gateway unhealthy, restarting"
    tmux has-session -t hermes 2>/dev/null || tmux new-session -d -s hermes -c "$HOME"
    tmux kill-window -t hermes:deskbot-gateway 2>/dev/null
    tmux new-window -d -t hermes -n deskbot-gateway "hermes gateway run"
  fi
  # App: the bridge port answers only when BotService is alive.
  if ! (echo '{}' | timeout 3 nc 127.0.0.1 8643 >/dev/null 2>&1); then
    log "bridge down, starting app service"
    su -c "am start-foreground-service -n $APP/.BotService" >/dev/null 2>&1 || su -c "am start -n $APP/.MainActivity" >/dev/null 2>&1
  fi
  # Tool-list drift: the app may have gained tools since the gateway registered them. Log it; restarting is left to the user/skill (it interrupts conversations).
  N=$(printf '{"method":"list","token":"%s"}\n' "$(tr -d '[:space:]' < "$HOME/.hermes/deskbot-bridge-token")" | timeout 3 nc 127.0.0.1 8643 2>/dev/null | grep -o '"name"' | wc -l)
  [ -n "$N" ] && [ "$N" != "$(cat "$HOME/.hermes/.deskbot-tools-seen" 2>/dev/null)" ] && { log "bridge now exposes $N tools (gateway may need a restart to see them)"; echo "$N" > "$HOME/.hermes/.deskbot-tools-seen"; }
  sleep 30
done
