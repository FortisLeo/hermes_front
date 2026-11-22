# Runs a script from ~/bin inside the Termux tmux session as the Termux user. Run as root on the phone (su --mount-master).
# usage: sh run_in_termux.sh <script-in-~/bin> <logfile-name-in-~>
T=/data/data/com.termux/files
U=$(stat -c %u $T/home)
export HOME=$T/home PREFIX=$T/usr PATH=$T/usr/bin:/system/bin TMPDIR=$T/usr/tmp LD_PRELOAD=$T/usr/lib/libtermux-exec.so
rm -f $T/home/$2; : > $T/home/$2; chown $U:$U $T/home/$2
SOCK=$T/usr/var/run/tmux-$U/default
su $U -c "$T/usr/bin/tmux -S $SOCK has-session -t hermes || $T/usr/bin/tmux -S $SOCK new-session -d -s hermes"
su $U -c "$T/usr/bin/tmux -S $SOCK new-window -d -t hermes -n pkgsetup \"env HOME=$T/home PREFIX=$T/usr PATH=$T/usr/bin:$T/usr/bin/applets TMPDIR=$T/usr/tmp LD_PRELOAD=$T/usr/lib/libtermux-exec.so $T/usr/bin/sh $T/home/bin/$1 > $T/home/$2 2>&1\""; echo rc=$?
