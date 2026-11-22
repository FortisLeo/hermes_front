#!/data/data/com.termux/files/usr/bin/sh
# Installs the Termux packages the Deskbot recipes use. Idempotent: skips what is installed. Run inside Termux.
# Backs up ~/.hermes first (config, memories, skills; not caches) so a bad upgrade can be undone.
set -u
BK="$HOME/hermes-backup-$(date +%Y%m%d-%H%M%S).tgz"
tar -czf "$BK" -C "$HOME" --exclude=.hermes/cache --exclude=.hermes/audio_cache --exclude='*.sock' --exclude='*.lock' .hermes 2>/dev/null && echo "ok   backup $BK" || echo "FAIL backup"
# No `pkg upgrade`: it could replace libraries the running Hermes uses. Only add missing packages.
for p in openssh rsync ffmpeg nmap tesseract; do
  if dpkg -s "$p" >/dev/null 2>&1; then echo "ok   $p already installed"
  elif pkg install -y "$p" >"$HOME/pkg-$p.log" 2>&1; then echo "ok   installed $p"
  else echo "FAIL $p ($(tail -n 2 "$HOME/pkg-$p.log" | tr "\n" " "))"; fi
done
echo done
