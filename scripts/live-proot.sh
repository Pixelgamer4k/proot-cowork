#!/usr/bin/env bash
# Live proot iteration on device — no APK rebuild needed for guest scripts,
# proot argv/bindings, env vars, and libcowork_linkshim.so updates.
#
# Rebuild the APK only when changing Kotlin/Java, AndroidManifest, or packaging.
set -euo pipefail

PKG="${PKG:-com.proot}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="/data/user/0/${PKG}"
FILES="${DATA}/files"

discover() {
  APK=$(adb shell pm path "$PKG" 2>/dev/null | cut -d: -f2 | tr -d '\r')
  [[ -n "$APK" ]] || { echo "Package $PKG not installed" >&2; exit 1; }
  LIBDIR="$(dirname "$APK")/lib/arm64"
  PROOT="${LIBDIR}/libproot_exec.so"
  LOADER="${LIBDIR}/libproot_loader.so"
  LOADER32="${LIBDIR}/libproot_loader32.so"
  ROOTFS="${FILES}/rootfs"
  TMP="${FILES}/tmp"
  MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
  ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r' | tr '-' '_')
  KR="\\Linux\\${MODEL}\\6.17.0-PRoot-Cowork\\#1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000\\${ABI}\\localdomain\\-1\\"
}

run_as() {
  # run-as + sh -c must be one adb shell string; otherwise mkdir/cp lose args on some devices.
  adb shell "run-as $PKG sh -c $(printf '%q' "$1")"
}

push_host() {
  local src="$1"
  local dest="$2"
  local parent
  parent="$(dirname "$dest")"
  run_as "mkdir -p '$parent'"
  adb shell "run-as $PKG sh -c 'cat > \"$dest\"'" <"$src"
  run_as "chmod 700 '$dest' 2>/dev/null || chmod 644 '$dest'"
  if [[ "$dest" == *"start-desktop.sh" ]]; then
    run_as "mkdir -p files/debug && touch files/debug/live-desktop-script"
  fi
}

push_tree() {
  local src_dir="$1"
  local dest_rel="$2"
  local tarball
  tarball=$(mktemp /tmp/guest-tree.XXXXXX.tgz)
  trap 'rm -f "$tarball"' RETURN
  tar czf "$tarball" -C "$src_dir" .
  run_as "mkdir -p files/tmp '$dest_rel'"
  adb shell "run-as $PKG sh -c 'cat > files/tmp/guest-tree.tgz'" <"$tarball"
  run_as "tar xzf files/tmp/guest-tree.tgz -C '$dest_rel' && rm -f files/tmp/guest-tree.tgz"
}

# Build a proot launcher on device (same pattern as ProotProcessLauncher).
launch_proot() {
  local guest_argv="$1"
  discover
  run_as "mkdir -p files/tmp files/tmp/.X11-unix files/debug files/sysdata/sys_empty"

  local launcher
  launcher=$(cat <<EOF
#!/system/bin/sh
set -eu
cd ${FILES}
unset LD_PRELOAD
export PROOT_TMP_DIR='${TMP}'
export PROOT_LOADER='${LOADER}'
export PROOT_LOADER_32='${LOADER32}'
export PROOT_NO_SECCOMP='1'
export TERMUX_APP__DATA_DIR='${DATA}'
export TERMUX_APP__LEGACY_DATA_DIR='/data/data/${PKG}'
export TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE='enable'
export HOME='/home/cowork'
export USER='cowork'
export LANG='C.UTF-8'
export PATH='/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin'
export TERM='xterm-256color'
export TMPDIR='/tmp'
export XDG_RUNTIME_DIR='/tmp'
export VNC_PORT='5900'
LD_LIBRARY_PATH='/system/lib64:/system/lib:${LIBDIR}:${FILES}/exec_libs' exec /system/bin/linker64 '${PROOT}' \\
  --kill-on-exit --link2symlink --sysvipc -L \\
  '--kernel-release=${KR}' \\
  -i 0:0 \\
  -r '${ROOTFS}' \\
  --cwd=/root \\
  -b /dev -b /proc -b /sys \\
  -b /dev/urandom:/dev/random \\
  -b '${TMP}:/tmp' \\
  -b '${TMP}:/dev/shm' \\
  -b '${FILES}/sysdata/sys_empty:/sys/fs/selinux' \\
  -b /apex:/apex \\
  -b '${FILES}/artifacts:/artifacts' \\
  -b '/storage/emulated/0/Android/data/${PKG}/files:/storage' \\
  -b '${LIBDIR}/libcowork_linkshim.so:/usr/lib/libcowork_linkshim.so' \\
  -b '${LIBDIR}/libandroid-shmem.so:/usr/lib/libandroid-shmem.so' \\
  ${guest_argv}
EOF
)

  local staging
  staging=$(mktemp)
  trap 'rm -f "$staging"' RETURN
  printf '%s\n' "$launcher" >"$staging"
  adb shell "run-as $PKG sh -c 'cat > files/tmp/live-launch-proot.sh'" <"$staging"
  run_as "chmod 700 files/tmp/live-launch-proot.sh"
}

case "${1:-help}" in
  paths)
    discover
    cat <<EOF
package=$PKG
data=$DATA
lib=$LIBDIR
proot=$PROOT
rootfs=$ROOTFS
tmp=$TMP
kernel_release=$KR
EOF
    ;;

  push-desktop)
    script="${2:-$ROOT/app/src/main/assets/desktop/start-desktop-vnc.sh}"
    echo "Pushing $script -> files/rootfs/start-desktop.sh"
    push_host "$script" "files/rootfs/start-desktop.sh"
    run_as "head -20 files/rootfs/start-desktop.sh"
    ;;

  push-guest)
    echo "Pushing guest-bin -> files/rootfs"
    push_tree "$ROOT/app/src/main/assets/desktop/guest-bin" "files/rootfs"
    echo "Pushing cowork-config -> files/rootfs/usr/share/proot-cowork"
    push_tree "$ROOT/app/src/main/assets/desktop/cowork-config" "files/rootfs/usr/share/proot-cowork"
    run_as "chmod +x files/rootfs/usr/bin/xterm files/rootfs/usr/bin/start-cowork-xfce files/rootfs/usr/bin/openbox files/rootfs/usr/bin/openbox-session files/rootfs/usr/bin/cowork-bwrap files/rootfs/usr/bin/cowork-dbus-launch 2>/dev/null || true"
    run_as "chmod +x files/rootfs/usr/share/proot-cowork/.config/openbox/autostart 2>/dev/null || true"
    run_as "ls -la files/rootfs/usr/bin/openbox files/rootfs/usr/bin/openbox-session files/rootfs/usr/share/proot-cowork/.config/openbox/autostart"
    ;;

  push-shim)
    shim="${2:-$ROOT/app/src/main/jniLibs/arm64-v8a/libcowork_linkshim.so}"
    echo "Note: shim must be pushed into APK lib dir — use 'adb install' or rebuild."
    echo "For live guest bind test, copy to exec_libs:"
    run_as "mkdir -p files/exec_libs"
    push_host "$shim" "files/exec_libs/libcowork_linkshim.so"
    echo "Update live-proot.sh bind to use files/exec_libs if testing a new shim build."
    ;;

  guest)
    shift
    cmd="${*:-echo PROOT_OK}"
    launch_proot "/usr/bin/bash -lc $(printf '%q' "$cmd")"
    echo "Running: $cmd"
    run_as "sh files/tmp/live-launch-proot.sh" 2>&1 | tee /tmp/live-proot-out.log
    ;;

  desktop)
    launch_proot "/usr/bin/bash /start-desktop.sh"
    echo "Starting desktop (live launcher) — watch for VNC_READY"
    run_as "sh files/tmp/live-launch-proot.sh" 2>&1 | tee /tmp/live-proot-out.log &
    echo "PID $! — tail: adb shell run-as $PKG tail -f files/debug/last-proot.log"
    ;;

  desktop-fg)
    launch_proot "/usr/bin/bash /start-desktop.sh"
    echo "Starting desktop in foreground (Ctrl+C stops host adb only)"
    run_as "sh files/tmp/live-launch-proot.sh"
    ;;

  test-xvfb)
    launch_proot "/usr/bin/bash -lc $(printf '%q' '
rm -f /tmp/.X*-lock
mkdir -p /tmp/.X11-unix
/usr/bin/Xvfb :99 -screen 0 640x480x24 -ac -noreset -extension MIT-SHM 2>&1 | head -5
sleep 2
ls -la /tmp/.X11-unix/X99 2>&1
killall Xvfb 2>/dev/null || true
')"
    run_as "sh files/tmp/live-launch-proot.sh"
    ;;

  import)
    adb shell mkdir -p "/sdcard/Android/data/${PKG}/files"
    adb shell "test -f /sdcard/Android/data/${PKG}/files/proot-cowork-rootfs.tar.gz || cp /sdcard/proot-cowork-rootfs.tar.gz /sdcard/Android/data/${PKG}/files/proot-cowork-rootfs.tar.gz"
    adb shell am start -n "${PKG}/com.proot.cowork.MainActivity" >/dev/null 2>&1 || true
    sleep 2
    "$ROOT/scripts/adb-debug.sh" import
    echo "Polling rootfs..."
    for i in $(seq 1 40); do
      if run_as "test -f files/rootfs/usr/bin/bash" 2>/dev/null; then
        echo "Rootfs ready after ${i} polls"
        exit 0
      fi
      sleep 10
      echo "  poll $i..."
    done
    echo "Import still running or failed — check: adb-debug.sh status" >&2
    exit 1
    ;;

  launcher)
    launch_proot "/usr/bin/bash -lc echo_quoted_ok"
    run_as "cat files/tmp/live-launch-proot.sh"
    ;;

  help|*)
    cat <<EOF
Live proot debug (no APK rebuild for guest-side changes)

  paths                 Print discovered device paths
  push-desktop [file]   Push start-desktop script into rootfs
  push-guest            Push openbox/xterm + cowork openbox config into rootfs
  push-shim [file]      Push libcowork_linkshim.so to app exec_libs
  guest <cmd>           Run shell command inside proot (live launcher)
  desktop               Start /start-desktop.sh in background
  desktop-fg            Start desktop, stream logs to terminal
  test-xvfb             Quick Xvfb + socket smoke test
  import                Import rootfs (keeps app in foreground)
  launcher              Print generated live proot launcher script

Examples:
  ./scripts/live-proot.sh import
  ./scripts/live-proot.sh push-desktop
  ./scripts/live-proot.sh push-guest
  ./scripts/live-proot.sh test-xvfb
  ./scripts/live-proot.sh guest 'echo PROOT_OK; ls /usr/lib/libcowork*'
  ./scripts/live-proot.sh desktop-fg

Rebuild APK only for: Kotlin/Java changes, new jniLibs in APK, manifest, Gradle.
EOF
    ;;
esac
