#!/bin/sh
# Pushes the Hermes bridge token into the Deskbot app (debug builds) without printing it. Run from the PC.
PKG=com.rishitgoklani.hermes_front_mobile
adb shell "su -c 'T=\$(tr -d \"[:space:]\" < /data/data/com.termux/files/home/.hermes/deskbot-bridge-token); am start -n $PKG/.MainActivity --es bridge_token \"\$T\" >/dev/null && echo token-synced'"
