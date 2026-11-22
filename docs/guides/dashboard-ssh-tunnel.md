# Variant B: SSH tunnel over Wi-Fi

**What we are doing:** SSH is the standard encrypted remote-login tool. We run an SSH server (`sshd`) in Termux on the phone, then from the PC we log in and ask SSH to build a tunnel ("port forward") from PC port 9119 to the phone's own `127.0.0.1:9119`, where the dashboard listens. We never log in to a shell for work; the connection exists only to carry the tunnel. Because the dashboard stays bound to the phone's loopback address, nothing else on your network can see it.

## Before you start
- The PC and phone are on the same Wi-Fi network.
- `openssh` is installed in Termux (the Deskbot setup script installs it). If unsure: `pkg install -y openssh`.
- The PC has an SSH client (`ssh` is built in on Linux and macOS, and on current Windows).

## Part 1: on the phone (Termux), one time

```sh
whoami
```
Prints your Termux user name, for example `u0_a343`. SSH logs in as this user, so note it. (Termux users are numeric app IDs, not "root".)

```sh
passwd
```
Sets the password you will type from the PC. It asks for the new password twice and shows nothing while you type. Termux has no password by default, so SSH password login does not work until you run this. Run it again any time to change the password.

```sh
termux-wake-lock
```
Stops Android putting Termux to sleep, which would break the tunnel.

```sh
sshd
```
Starts the SSH server in the background. On Termux it listens on port **8022**, not the usual 22, because Android does not let apps use ports below 1024.

```sh
pgrep -a sshd
```
Shows the `sshd` process if it is running. No output means it did not start.

Find the phone's address on your network. Use Settings > Wi-Fi > your network > IP address, for example `192.168.0.102`. (Inside Termux, `ip addr` is blocked by Android with "Cannot bind netlink socket", so use the Settings screen.)

## Part 2: start the dashboard on the phone

```sh
tmux new-window -d -t hermes -n dashboard 'hermes dashboard --no-open'
hermes dashboard --status
```

This opens a background tmux window running the dashboard on `127.0.0.1:9119` (see the USB guide for what each flag does) and checks that it started. The first start may take a minute while it builds the web UI.

## Part 3: on the PC

```sh
ssh -N -L 9119:127.0.0.1:9119 -p 8022 u0_a343@192.168.0.102
```

What each part means:
- `ssh ... u0_a343@192.168.0.102`: log in as user `u0_a343` on the phone at that address. Replace both with your values.
- `-p 8022`: connect to Termux's SSH port.
- `-L 9119:127.0.0.1:9119`: **local forward**. "Listen on port 9119 on this PC, and send what arrives to `127.0.0.1:9119` as seen from the phone." The `127.0.0.1` after the first colon is resolved on the phone, so it means the dashboard.
- `-N`: do not start a shell, just hold the tunnel open.

The first time, SSH asks "Are you sure you want to continue connecting?" because it has not seen this phone before. Type `yes`; it remembers the phone's key afterwards. Then enter the password from `passwd`. The window then shows nothing, which is normal. **Leave it open**: closing it closes the tunnel.

Now open this in a browser on the PC:

```
http://localhost:9119
```

## Optional: log in with a key instead of a password
A key is safer than a password and saves typing. On the PC:

```sh
ssh-keygen -t ed25519                               # accept the defaults
ssh-copy-id -p 8022 u0_a343@192.168.0.102           # installs your public key on the phone
```

If `ssh-copy-id` is missing (Windows), append the contents of `~/.ssh/id_ed25519.pub` to `~/.ssh/authorized_keys` in Termux.

To shorten the command, add this to `~/.ssh/config` on the PC:

```
Host deskbot
    HostName 192.168.0.102
    Port 8022
    User u0_a343
    LocalForward 9119 127.0.0.1:9119
```

Then `ssh -N deskbot` does the same thing. To keep the tunnel in the background, use `ssh -f -N deskbot` and stop it with `pkill -f "ssh -f -N deskbot"`.

## Stop
- PC: press Ctrl+C in the tunnel window.
- Phone: `hermes dashboard --stop` and `pkill sshd`.

## Troubleshooting
| What you see | Cause and fix |
| --- | --- |
| `Connection refused` | `sshd` is not running, or the port is wrong. Run `pgrep -a sshd` on the phone, then `sshd`, and use `-p 8022`. |
| `Connection timed out` | Wrong IP, different networks, or the phone is asleep. Re-check the IP and run `termux-wake-lock`. |
| `Permission denied` | Wrong user or password. Run `whoami` and `passwd` again in Termux. |
| `bind: Address already in use` on the PC | Something already uses port 9119. Use `-L 9200:127.0.0.1:9119` and open `http://localhost:9200`. |
| Browser shows nothing | The dashboard is not running or is still building. Check `hermes dashboard --status`. |
| Tunnel drops after a while | The phone slept or Android killed Termux. Keep `termux-wake-lock` on and keep the phone charging. |
| The phone's IP changed | Wi-Fi gave it a new address. Look it up again, or reserve an address for the phone in your router. |

## After the phone restarts
`sshd` and the dashboard do **not** start by themselves after a reboot, so the PC gets `Connection refused` on port 8022. One command starts everything (wake lock, tmux session, `sshd`, the Hermes gateway, the dashboard and the watchdog), and it is safe to run again at any time because it skips what is already running:

```sh
sh ~/bin/deskbot-up
```

then run the `ssh -N -L ...` command on the PC again. Its log is `~/deskbot-boot.log`.

To run it automatically at boot, install the **Termux:Boot** app (from F-Droid, same source as Termux), open it once so Android lets it run at boot, and copy the script into its folder:

```sh
mkdir -p ~/.termux/boot && cp ~/bin/deskbot-up ~/.termux/boot/10-deskbot.sh && chmod +x ~/.termux/boot/10-deskbot.sh
```

The one-click setup script (`scripts/setup_device.sh`) installs both copies for you; it still needs the Termux:Boot app to be installed by hand.
