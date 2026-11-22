#!/bin/sh
# Restarts the Hermes gateway (kills the running `hermes gateway run`, recreates tmux window deskbot-gateway). Run as root: adb shell su --mount-master -c "sh /data/local/tmp/restart_gateway.sh". Announce it first: it interrupts the bot for ~10 s.
T=/data/data/com.termux/files; U=$(stat -c %u $T/home); SOCK=$T/usr/var/run/tmux-$U/default
PID=$(ps -A -o PID,ARGS | grep "hermes gateway run" | grep -v grep | grep -v "sh -c" | awk '{print $1}' | head -1)
echo "old gateway pid: ${PID:-none}"
[ -n "$PID" ] && kill -TERM $PID && sleep 6
ps -A -o PID,ARGS | grep "hermes gateway run" | grep -v grep | head -2
su $U -c "$T/usr/bin/tmux -S $SOCK kill-window -t hermes:deskbot-gateway" 2>/dev/null
su $U -c "$T/usr/bin/tmux -S $SOCK new-window -d -t hermes -n deskbot-gateway \"env HOME=$T/home PREFIX=$T/usr PATH=$T/usr/bin:$T/usr/bin/applets TMPDIR=$T/usr/tmp LD_PRELOAD=$T/usr/lib/libtermux-exec.so $T/usr/bin/hermes gateway run\""; echo "new window rc=$?"
