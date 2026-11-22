# Variant A: USB tunnel with adb

**What we are doing:** adb (the tool you already use to install the app) can forward a port over the USB cable. We start the dashboard on the phone, then tell adb "send anything that connects to port 9119 on my PC to port 9119 on the phone". The browser on the PC then talks to the dashboard through the cable.

## Before you start
- USB debugging is on and `adb devices` lists the phone.
- Termux has Hermes installed (it does on a Deskbot phone).

## Steps

### 1. Start the dashboard on the phone
Open Termux and run:

```sh
tmux new-window -d -t hermes -n dashboard 'hermes dashboard --no-open'
```

- `tmux new-window -d -t hermes -n dashboard` creates a new window called `dashboard` in the `hermes` tmux session without switching to it (`-d`). Running it in tmux means it keeps going after you close the Termux screen.
- `hermes dashboard --no-open` starts the web server on `127.0.0.1:9119`. `--no-open` stops Hermes trying to open a browser, which does not make sense on a headless phone.

Check that it is up (the first start may take a minute while it builds the web UI):

```sh
hermes dashboard --status
```

This lists running dashboard processes. If it prints nothing, wait and try again.

### 2. Forward the port on the PC
Connect the phone by USB, then on the PC:

```sh
adb devices                      # the phone must be listed as "device"
adb forward tcp:9119 tcp:9119    # PC port 9119  ->  phone port 9119
```

`adb forward tcp:LOCAL tcp:REMOTE` opens port LOCAL on the PC and sends its traffic over USB to port REMOTE on the phone. Both are 9119 here so the URL stays simple.

### 3. Open it
In a browser on the PC go to:

```
http://localhost:9119
```

`localhost` on the PC is now the start of the tunnel. You should see the Hermes dashboard.

### 4. Stop
On the PC:

```sh
adb forward --remove tcp:9119
```

On the phone:

```sh
hermes dashboard --stop
```

The forward also disappears when you unplug the cable or restart adb.

## Troubleshooting
| What you see | Cause and fix |
| --- | --- |
| `adb: error: no devices/emulators found` | USB debugging is off or the cable is charge-only. Re-plug and accept the prompt on the phone. |
| `error: more than one device` | Add `-s <serial>` from `adb devices`, for example `adb -s d85ead24 forward tcp:9119 tcp:9119`. |
| Browser says "refused to connect" | The dashboard is not running or is still building. Run `hermes dashboard --status` in Termux. |
| Port already in use on the PC | Use another local port: `adb forward tcp:9200 tcp:9119`, then open `http://localhost:9200`. |
