#!/data/data/com.termux/files/usr/bin/sh
# Deskbot watchdog (runs inside Termux, outside the app so it survives app crashes).
# Every 30 s: ensure the Hermes gateway is up and the Deskbot app service is running.
# Install: copy to ~/bin/deskbot-watchdog.sh, then start from ~/.termux/boot/10-deskbot.sh (see boot.sh).
APP=com.rishitgoklani.hermes_front_mobile
LOG=$HOME/.hermes/logs/watchdog.log
log() { echo "$(date '+%F %T') $*" >> "$LOG"; }
. "$(dirname "$0")/watchdog_lib.sh"
REG=$HOME/.hermes/.deskbot-tools-registered        # tool count Hermes saw at its last start
OPTIN=$HOME/.hermes/.deskbot-auto-restart           # touch this file to let the watchdog restart an idle gateway on tool drift
ACT=$HOME/.hermes/logs/agent.log                    # changes whenever the bot is working
IDLE_SECS=${DESKBOT_IDLE_SECS:-600}
bridge_tool_count() { printf '{"method":"list","token":"%s"}\n' "$(tr -d '[:space:]' < "$HOME/.hermes/deskbot-bridge-token")" | timeout 3 nc 127.0.0.1 8643 2>/dev/null | grep -o '"name"' | wc -l; }
restart_gateway() { tmux has-session -t hermes 2>/dev/null || tmux new-session -d -s hermes -c "$HOME"; tmux kill-window -t hermes:deskbot-gateway 2>/dev/null; tmux new-window -d -t hermes -n deskbot-gateway "hermes gateway run"; }

while true; do
  # Gateway: restart in its tmux window if the health endpoint is dead.
  if ! curl -fs -m 5 http://127.0.0.1:8642/health >/dev/null 2>&1; then
    log "gateway unhealthy, restarting"
    restart_gateway
    N0=$(bridge_tool_count); [ "$N0" -gt 0 ] && echo "$N0" > "$REG"
  fi
  # App: the bridge port answers only when BotService is alive.
  if ! (echo '{}' | timeout 3 nc 127.0.0.1 8643 >/dev/null 2>&1); then
    log "bridge down, starting app service"
    su -c "am start-foreground-service -n $APP/.BotService" >/dev/null 2>&1 || su -c "am start -n $APP/.MainActivity" >/dev/null 2>&1
  fi
  # Tool-list drift: the app may have gained tools since the gateway registered them (Hermes registers tools only at start).
  # Restarting interrupts a conversation, so it is opt-in (touch ~/.hermes/.deskbot-auto-restart) and only when the bot has been idle.
  N=$(bridge_tool_count)
  [ -f "$REG" ] || { [ "$N" -gt 0 ] && echo "$N" > "$REG"; }
  if [ -n "$N" ] && [ "$N" != "$(cat "$HOME/.hermes/.deskbot-tools-seen" 2>/dev/null)" ]; then log "bridge now exposes $N tools (Hermes registered $(cat "$REG" 2>/dev/null))"; echo "$N" > "$HOME/.hermes/.deskbot-tools-seen"; fi
  if should_restart_gateway "$(cat "$REG" 2>/dev/null)" "$N" "$OPTIN" "$ACT" "$(date +%s)" "$IDLE_SECS"; then
    log "tool drift and gateway idle for ${IDLE_SECS}s: restarting gateway so Hermes sees $N tools"
    restart_gateway; echo "$N" > "$REG"
  fi
  sleep 30
done
