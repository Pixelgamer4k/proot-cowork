#!/usr/bin/env bash
# ADB helpers for com.proot.cowork.debug (debug APK only).
set -euo pipefail

PKG="${PKG:-com.proot.cowork.debug}"
RECEIVER="${PKG}/com.proot.cowork.debug.DebugCommandReceiver"
ACTION="com.proot.cowork.debug.COMMAND"

broadcast() {
  local cmd="$1"
  shift
  adb shell am broadcast -a "$ACTION" -n "$RECEIVER" --es cmd "$cmd" "$@"
}

case "${1:-help}" in
  status)
    broadcast DUMP_STATUS
    sleep 0.5
    adb shell run-as "$PKG" cat files/debug/status.json
    ;;
  start)
    broadcast START_DESKTOP
    ;;
  stop)
    broadcast STOP_DESKTOP
    ;;
  reboot)
    broadcast REBOOT_DESKTOP
    ;;
  logs)
    lines="${2:-80}"
    broadcast TAIL_LOGS --es lines "$lines"
    sleep 0.3
    adb shell run-as "$PKG" cat files/debug/last-command-result.txt
    ;;
  proot-cmd)
    broadcast DUMP_PROOT_CMD
    sleep 0.3
    adb shell run-as "$PKG" cat files/debug/last-proot-command.txt
    ;;
  import)
    path="${2:-}"
    if [[ -z "$path" ]]; then
      adb shell mkdir -p "/sdcard/Android/data/${PKG}/files"
      adb shell "test -f /sdcard/Android/data/${PKG}/files/proot-cowork-rootfs.tar.gz || cp /sdcard/proot-cowork-rootfs.tar.gz /sdcard/Android/data/${PKG}/files/proot-cowork-rootfs.tar.gz"
      path="/sdcard/Android/data/${PKG}/files/proot-cowork-rootfs.tar.gz"
    fi
    adb shell am start -n "${PKG}/com.proot.cowork.MainActivity" >/dev/null 2>&1 || true
    sleep 2
    broadcast IMPORT_ROOTFS --es path "$path"
    ;;
  shell)
    cmd="${2:-echo PROOT_OK}"
    adb shell "am broadcast -a '$ACTION' -n '$RECEIVER' --es cmd RUN_PROOT_SHELL --es command '$cmd'"
    ;;
  result)
    adb shell run-as "$PKG" cat files/debug/last-command-result.txt
    ;;
  proot-log)
  adb shell run-as "$PKG" tail -n "${2:-60}" files/debug/last-proot.log
    ;;
  logcat)
    adb logcat -d | grep -iE "ProotCowork|cowork|proot|VNC" | tail -n "${2:-80}"
    ;;
  help|*)
    cat <<EOF
Usage: $(basename "$0") <command> [args]

  status              Write + print files/debug/status.json
  start|stop|reboot   Control desktop service
  logs [n]            Tail session + proot logs (default 80 lines)
  proot-cmd           Dump argv used to launch proot
  proot-log [n]       Tail files/debug/last-proot.log
  import [path]       Import rootfs tarball (default /sdcard/proot-cowork-rootfs.tar.gz)
  shell "cmd"         Run bash -lc inside proot
  result              Last broadcast result text
  logcat [n]          Filtered logcat

Package: $PKG

Live iteration (no APK rebuild): ./scripts/live-proot.sh help
EOF
    ;;
esac
