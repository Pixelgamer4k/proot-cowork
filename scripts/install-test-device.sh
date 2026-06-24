#!/usr/bin/env bash
# Post-install setup on test device (APK install is manual by default).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="${PKG:-com.proot}"
ADB_DEVICE="${ADB_DEVICE:-192.168.1.108:37155}"
APK_DIR="${APK_DIR:-/tmp/cowork-apk}"
SCREENSHOT="${SCREENSHOT:-/tmp/cowork_screen.png}"
SKIP_INSTALL="${SKIP_INSTALL:-1}"

adb connect "$ADB_DEVICE" >/dev/null 2>&1 || true
ADB=(adb -s "$ADB_DEVICE")

if [[ "$SKIP_INSTALL" == "1" ]]; then
  echo "==> Manual install mode (SKIP_INSTALL=1)"
  if ! "${ADB[@]}" shell pm path "$PKG" 2>/dev/null | grep -q .; then
    echo "ERROR: $PKG not on $ADB_DEVICE — install the APK manually, then re-run."
    exit 1
  fi
else
  echo "==> Uninstalling old packages"
  "${ADB[@]}" uninstall com.proot.cowork.debug 2>/dev/null || true
  "${ADB[@]}" uninstall com.proot.cowork 2>/dev/null || true
  "${ADB[@]}" uninstall "$PKG" 2>/dev/null || true

  APK="$(find "$APK_DIR" -name '*.apk' | head -1)"
  if [[ -z "$APK" ]]; then
    echo "No APK in $APK_DIR — run: gh run download <run-id> -n proot-cowork-debug-apk -D $APK_DIR"
    exit 1
  fi

  echo "==> Installing $APK"
  "${ADB[@]}" install -r "$APK"
fi

echo "==> Granting storage (Android 11+)"
"${ADB[@]}" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
"${ADB[@]}" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
"${ADB[@]}" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "==> Launching app"
"${ADB[@]}" shell am start -n "$PKG/com.proot.cowork.MainActivity" >/dev/null
sleep 8

echo "==> Screenshot -> $SCREENSHOT"
"${ADB[@]}" shell screencap -p /sdcard/cowork_test.png
"${ADB[@]}" pull /sdcard/cowork_test.png "$SCREENSHOT" >/dev/null
echo "Saved $SCREENSHOT"

echo "==> Bootstrap markers"
"${ADB[@]}" shell "run-as $PKG ls -la files/usr/.termux_* 2>/dev/null | head -15" || true

echo "==> Done. Full debug: ADB_DEVICE=$ADB_DEVICE bash scripts/debug-termux-device.sh all"
