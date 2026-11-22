# Open the Hermes dashboard from your PC

## What the dashboard is
`hermes dashboard` starts a small web server on the phone (port **9119**) where you manage Hermes: config, API keys and sessions. You use it from a browser.

## Why you cannot just open it
By default the dashboard listens on **`127.0.0.1`**, which means "this device only". A browser on your PC cannot reach an address that only exists inside the phone. You have two ways to deal with that:

1. **Make it listen on the network** (`--host 0.0.0.0`). Hermes then refuses to start without a login (password or OAuth), and anyone on your Wi-Fi can reach the login page. The dashboard shows API keys, so this is the riskier option and is not covered here.
2. **Keep it private and tunnel to it** (what these guides do). The dashboard stays on `127.0.0.1` inside the phone. A *tunnel* is an encrypted pipe from a port on your PC to a port on the phone. When you open `http://localhost:9119` on the PC, the tunnel carries the request to the phone's `127.0.0.1:9119`, as if you were on the phone. Nothing is exposed to your network, and Hermes's login requirement for network binds never comes into play.

## Pick a variant
| | Variant A: USB (adb) | Variant B: SSH over Wi-Fi |
| --- | --- | --- |
| Needs | USB cable, adb on the PC | Same Wi-Fi network, an SSH client on the PC |
| Setup on the phone | Start the dashboard | Start the dashboard, set a password, start `sshd` |
| Setup on the PC | One command | One command (plus a password or key) |
| Works while moving around | No, the cable must stay in | Yes, as long as both are on the network |
| Good for | Quick one-off access | Leaving the phone on your desk and using it from the PC |

Both variants share step 1: start the dashboard on the phone. It is repeated in each guide so they stand alone.

## Things that apply to both
- **Keep the phone awake.** Run `termux-wake-lock` in Termux and keep the phone charging, otherwise Android may pause Termux and the tunnel drops.
- **Memory.** The phone has little RAM. The dashboard adds load, so stop it when you are done: `hermes dashboard --stop`.
- **Security.** The tunnel is only as private as the way you open it. Do not forward port 9119 to the internet and do not put your SSH password in a file or chat.
