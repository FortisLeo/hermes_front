#!/system/bin/sh
# Grant the Deskbot app everything it needs. Run as root on the phone:
#   adb shell su -c 'sh /sdcard/grant_all.sh'   (after: adb push scripts/grant_all.sh /sdcard/)
# Re-run after every app update. Each step is best-effort; verify with
#   dumpsys package $PKG | grep granted    and    appops get $PKG
PKG=com.rishitgoklani.hermes_front_mobile
step() { "$@" >/dev/null 2>&1 && echo "ok   $*" || echo "FAIL $*"; }

for p in CAMERA RECORD_AUDIO POST_NOTIFICATIONS ACCESS_FINE_LOCATION READ_MEDIA_IMAGES READ_MEDIA_VIDEO READ_MEDIA_AUDIO BLUETOOTH_CONNECT BLUETOOTH_SCAN NEARBY_WIFI_DEVICES READ_LOGS READ_CALENDAR WRITE_CALENDAR READ_CONTACTS WRITE_CONTACTS ACCESS_COARSE_LOCATION ACTIVITY_RECOGNITION; do
  step pm grant $PKG android.permission.$p
done
for op in SYSTEM_ALERT_WINDOW WRITE_SETTINGS MANAGE_EXTERNAL_STORAGE GET_USAGE_STATS \
          REQUEST_INSTALL_PACKAGES SCHEDULE_EXACT_ALARM; do
  step appops set $PKG $op allow
done
step pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
step cmd notification allow_dnd $PKG
step dumpsys deviceidle whitelist +$PKG
# Services below only exist once the matching phase has shipped:
step cmd notification allow_listener $PKG/$PKG.data.notifications.DeskbotNotificationListener
# Accessibility is enabled by the app itself (WRITE_SECURE_SETTINGS); uncomment to force from root:
# step settings put secure enabled_accessibility_services $PKG/$PKG.data.ui.DeskbotAccessibilityService
# HyperOS/MIUI: allow autostart so BOOT_COMPLETED reaches the app (op 10008). No-op on other ROMs.
step appops set $PKG 10008 allow
