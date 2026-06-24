#!/usr/bin/env bash
# Automatic ADB debugging for com.proot Termux stack (no APK install — manual install only).
set -euo pipefail

PKG="${PKG:-com.proot}"
ADB_DEVICE="${ADB_DEVICE:-192.168.1.108:37155}"
SCREENSHOT="${SCREENSHOT:-/tmp/cowork_screen.png}"
WAIT_BOOTSTRAP="${WAIT_BOOTSTRAP:-90}"

adb connect "$ADB_DEVICE" >/dev/null 2>&1 || true
ADB=(adb -s "$ADB_DEVICE")

usage() {
  cat <<EOF
Usage: $(basename "$0") [command]

Commands (default: all):
  connect     Connect wireless ADB only
  perms       Grant storage + notification permissions
  launch      Start MainActivity
  markers     List bootstrap patch markers
  apt         Inspect apt wrappers + trusted.gpg.d symlinks
  pkg-test    Run apt update / pkg update via run-as
  screenshot  Capture device screen
  all         connect + perms + launch + wait + markers + apt + pkg-test + screenshot

Env:
  ADB_DEVICE=$ADB_DEVICE
  PKG=$PKG
  WAIT_BOOTSTRAP=$WAIT_BOOTSTRAP

Install APK manually, then run: $(basename "$0") all
EOF
}

require_pkg() {
  if ! "${ADB[@]}" shell pm path "$PKG" 2>/dev/null | grep -q .; then
    echo "ERROR: $PKG not installed on $ADB_DEVICE — install APK manually first."
    exit 1
  fi
}

run_as() {
  local script_b64
  script_b64="$(printf '%s' "$1" | base64 -w0)"
  "${ADB[@]}" shell "run-as $PKG sh -c \"echo $script_b64 | base64 -d | sh\""
}

cmd_connect() {
  adb connect "$ADB_DEVICE"
  "${ADB[@]}" get-state
}

cmd_perms() {
  require_pkg
  echo "==> Permissions"
  "${ADB[@]}" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
  "${ADB[@]}" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
  "${ADB[@]}" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
}

cmd_launch() {
  require_pkg
  echo "==> Launch $PKG"
  "${ADB[@]}" shell am start -n "$PKG/com.proot.cowork.MainActivity" >/dev/null
}

cmd_markers() {
  require_pkg
  echo "==> Patch markers"
  "${ADB[@]}" shell "run-as $PKG ls -la files/usr/.termux_* 2>/dev/null" || true
  echo "==> Version"
  "${ADB[@]}" shell dumpsys package "$PKG" 2>/dev/null | grep -E 'versionName|versionCode' | head -2 || true
}

cmd_apt() {
  require_pkg
  echo "==> apt / cowork-apt"
  run_as 'ls -la $PREFIX/bin/apt $PREFIX/bin/apt.real $PREFIX/bin/cowork-apt 2>/dev/null; head -5 $PREFIX/bin/cowork-apt 2>/dev/null'
  echo "==> trusted.gpg.d"
  run_as 'ls -la $PREFIX/etc/apt/trusted.gpg.d/ 2>/dev/null | head -20'
  echo "==> sources.list"
  run_as 'head -5 $PREFIX/etc/apt/sources.list 2>/dev/null'
}

cmd_pkg_test() {
  require_pkg
  echo "==> apt update (via cowork-apt if present)"
  run_as '
. $PREFIX/etc/profile
export PATH=$PREFIX/bin:$PATH
if [ -x $PREFIX/bin/cowork-apt ]; then
  $PREFIX/bin/cowork-apt update 2>&1 | tail -25
else
  $PREFIX/bin/apt update 2>&1 | tail -25
fi
'
  echo "==> pkg update"
  run_as '
. $PREFIX/etc/profile
export PATH=$PREFIX/bin:$PATH
$PREFIX/bin/pkg update -y 2>&1 | tail -30
'
}

cmd_screenshot() {
  echo "==> Screenshot -> $SCREENSHOT"
  "${ADB[@]}" shell screencap -p /sdcard/cowork_test.png
  "${ADB[@]}" pull /sdcard/cowork_test.png "$SCREENSHOT" >/dev/null
  echo "Saved $SCREENSHOT"
}

cmd_all() {
  cmd_connect
  cmd_perms
  cmd_launch
  echo "==> Waiting ${WAIT_BOOTSTRAP}s for bootstrap..."
  sleep "$WAIT_BOOTSTRAP"
  cmd_markers
  cmd_apt
  cmd_pkg_test
  cmd_screenshot
}

main() {
  local sub="${1:-all}"
  case "$sub" in
    connect) cmd_connect ;;
    perms) cmd_perms ;;
    launch) cmd_launch ;;
    markers) cmd_markers ;;
    apt) cmd_apt ;;
    pkg-test) cmd_pkg_test ;;
    screenshot) cmd_screenshot ;;
    all) cmd_all ;;
    help|-h|--help) usage ;;
    *) echo "Unknown command: $sub"; usage; exit 1 ;;
  esac
}

main "$@"
