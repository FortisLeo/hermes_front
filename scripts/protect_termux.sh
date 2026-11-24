#!/system/bin/sh
# Make Termux (and its children: sshd, tmux, Hermes) hard to kill. Run as root. Idempotent.
# Usage: sh protect_termux.sh          apply once
#        sh protect_termux.sh --watch  re-apply OOM protection every 30s (new processes start at 0)
# Does not restart anything. Non-destructive: only settings, whitelists, oom_score_adj, cgroup placement and one
# backed-up Hermes config key.
PKG=com.termux
step() { if "$@" >/dev/null 2>&1; then echo "ok   $*"; else echo "FAIL $*"; fi; }

apply_once() {
  # Doze / battery optimization exemption
  step dumpsys deviceidle whitelist +$PKG
  # Background execution allowed
  step cmd appops set $PKG RUN_IN_BACKGROUND allow
  step cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow
  # Never put into restricted/rare standby bucket
  step am set-standby-bucket $PKG active
  # Android 12+ phantom process killer (kills Termux child processes)
  step settings put global settings_enable_monitor_phantom_procs false
  step device_config set_sync_disabled_for_tests persistent
  step device_config put activity_manager max_phantom_processes 2147483647
}

protect_procs() {
  uid=$(stat -c %u /data/data/$PKG 2>/dev/null)
  [ -z "$uid" ] && { echo "FAIL termux uid"; return; }
  n=0
  for d in /proc/[0-9]*; do
    [ "$(stat -c %u $d 2>/dev/null)" = "$uid" ] || continue
    cur=$(cat $d/oom_score_adj 2>/dev/null)
    [ "$cur" = "-1000" ] && continue
    echo -1000 > $d/oom_score_adj 2>/dev/null && n=$((n+1))
  done
  echo "ok   oom_score_adj=-1000 set on $n new process(es)"
}

# Why oom_score_adj=-1000 alone is not enough (see docs/memory-pressure-test.md): lmkd kills the Termux *app*
# process (adj ~200, foreground service) and Android then kills its whole cgroup (/apps/uid_N/pid_P), taking
# tmux, Hermes and sshd with it. So move every Termux-owned process except the app process itself into a cgroup
# outside /apps. Survivors then outlive an lmkd kill of the app. New children inherit the cgroup from their parent.
CG=/sys/fs/cgroup/deskbot-hermes
escape_app_cgroup() {
  uid=$(stat -c %u /data/data/$PKG 2>/dev/null)
  [ -z "$uid" ] && return
  [ -d $CG ] || mkdir $CG 2>/dev/null || { echo "FAIL cgroup mkdir $CG"; return; }
  app=$(pidof $PKG)
  n=0
  for d in /proc/[0-9]*; do
    [ "$(stat -c %u $d 2>/dev/null)" = "$uid" ] || continue
    pid=${d#/proc/}
    case " $app " in *" $pid "*) continue ;; esac
    case "$(cat $d/cgroup 2>/dev/null)" in *"/apps/uid_"*) ;; *) continue ;; esac
    echo $pid > $CG/cgroup.procs 2>/dev/null && n=$((n+1))
  done
  echo "ok   moved $n Termux process(es) out of the app cgroup"
}

# Hermes auto-started gateways (`gateway run --idle-exit`, started by the TUI/dashboard client) exit after
# gateway.unmanaged_idle_exit_seconds (default 600) with no attached client, and the aiohttp API on 8642 does
# not count as client activity. Disable it. Read before write; keep a backup; idempotent.
CFG=/data/data/com.termux/files/home/.hermes/config.yaml
disable_idle_exit() {
  [ -f $CFG ] || { echo "FAIL no $CFG"; return; }
  if grep -q '^  unmanaged_idle_exit_seconds: 0' $CFG; then echo "ok   idle-exit already disabled"; return; fi
  if grep -q '^gateway:' $CFG; then echo "FAIL config already has a gateway: section, edit by hand"; return; fi
  [ -f $CFG.before-idle-exit ] || cp -p $CFG $CFG.before-idle-exit
  printf '\ngateway:\n  unmanaged_idle_exit_seconds: 0\n' >> $CFG && echo "ok   idle-exit disabled (backup config.yaml.before-idle-exit)"
}

apply_once
protect_procs
escape_app_cgroup
disable_idle_exit
if [ "$1" = "--watch" ]; then
  while true; do sleep 30; protect_procs >/dev/null; escape_app_cgroup >/dev/null; done
fi
