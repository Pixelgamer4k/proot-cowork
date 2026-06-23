#!/usr/bin/bash
# VNC desktop — runs entirely inside proot (no host X11).
set -euo pipefail

export HOME="${HOME:-/home/cowork}"
export USER="${USER:-cowork}"
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export DISPLAY=:99
export XDG_RUNTIME_DIR=/tmp
export TMPDIR=/tmp

# Termux-style preload: Android shm + link() shim for Xvfb lock files under proot.
_guest_preload=""
for lib in /usr/lib/libcowork_linkshim.so /usr/lib/libandroid-shmem.so; do
  if [ -f "$lib" ]; then
    _guest_preload="${_guest_preload:+$_guest_preload:}$lib"
  fi
done
if [ -n "$_guest_preload" ]; then
  export LD_PRELOAD="$_guest_preload${LD_PRELOAD:+:$LD_PRELOAD}"
fi

mkdir -p /tmp/.X11-unix
chmod 1777 /tmp /tmp/.X11-unix 2>/dev/null || true
rm -f /tmp/.X*-lock

VNC_PORT="${VNC_PORT:-5900}"
SCREEN="${SCREEN:-1280x720x24}"
XVFB=/usr/bin/Xvfb
X11VNC=/usr/bin/x11vnc
XFCE=/usr/bin/startxfce4

for bin in "$XVFB" "$X11VNC" "$XFCE"; do
  if [ ! -x "$bin" ]; then
    echo "Missing $bin — rebuild rootfs with: apt install -y xvfb x11vnc xfce4"
    exit 1
  fi
done

pkill -x Xvfb 2>/dev/null || true
pkill -f "x11vnc.*:99" 2>/dev/null || true
sleep 0.5

"$XVFB" :99 -screen 0 "$SCREEN" -ac +extension GLX +render -noreset -extension MIT-SHM &
XVFB_PID=$!
sleep 2

if ! kill -0 "$XVFB_PID" 2>/dev/null; then
  echo "Xvfb failed to start"
  exit 1
fi

if [ ! -S /tmp/.X11-unix/X99 ]; then
  echo "X display socket missing at /tmp/.X11-unix/X99"
  exit 1
fi

# -noshm avoids proot shm-helper exec errors on Android; -ipv4 skips broken IPv6 bind.
"$X11VNC" -display :99 -localhost -nopw -forever -shared -rfbport "$VNC_PORT" \
  -ipv4 -noshm -noxdamage -noxrecord -noxfixes -noxkb -wait 50 &
sleep 1

echo "VNC_READY port=$VNC_PORT display=:99"

cleanup() {
  kill "$XVFB_PID" 2>/dev/null || true
  pkill -f "x11vnc.*:99" 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p /tmp
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=/tmp/dbus-session}"

if command -v dbus-launch >/dev/null 2>&1; then
  exec dbus-launch --exit-with-session "$XFCE"
else
  exec "$XFCE"
fi
