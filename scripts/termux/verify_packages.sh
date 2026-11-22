#!/data/data/com.termux/files/usr/bin/sh
# Smoke-tests the tools the skills rely on, with real (local, harmless) work. Run inside Termux. Prints ok/FAIL per tool.
T=$(mktemp -d)
chk() { name=$1; shift; if "$@" >"$T/out" 2>&1; then echo "ok   $name"; else echo "FAIL $name: $(head -c 80 "$T/out" | tr '\n' ' ')"; fi; }
chk "ssh version"        sh -c 'ssh -V'
chk "ssh-keygen"         sh -c "ssh-keygen -q -t ed25519 -N '' -f $T/k && test -s $T/k.pub"
chk "rsync local copy"   sh -c "mkdir $T/a && echo hi > $T/a/f && rsync -a $T/a/ $T/b/ && test -s $T/b/f"
chk "ffmpeg make+probe"  sh -c "ffmpeg -loglevel error -f lavfi -i sine=frequency=440:duration=1 $T/t.wav && ffprobe -loglevel error -show_entries format=duration $T/t.wav"
chk "nmap localhost"     sh -c 'nmap -sT -p 8643 127.0.0.1 | grep -q "8643"'
chk "tesseract version"  sh -c 'tesseract --version'
rm -rf "$T"; echo done
