#!/usr/bin/bash
# VNC desktop — runs entirely inside proot (no host X11 / termux-x11).
set -euo pipefail

export DISPLAY=:99
export XDG_RUNTIME_DIR=/tmp
cd /home/cowork 2>/dev/null || cd /

VNC_PORT="${VNC_PORT:-5900}"
SCREEN="${SCREEN:-1280x720x24}"

for bin in Xvfb x11vnc startxfce4; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "Missing $bin — rebuild rootfs with: apt install -y xvfb x11vnc xfce4"
    exit 1
  fi
done

pkill -x Xvfb 2>/dev/null || true
pkill -f "x11vnc.*:99" 2>/dev/null || true
sleep 0.5

Xvfb :99 -screen 0 "$SCREEN" -ac +extension GLX +render -noreset &
XVFB_PID=$!
sleep 1

if ! kill -0 "$XVFB_PID" 2>/dev/null; then
  echo "Xvfb failed to start"
  exit 1
fi

x11vnc -display :99 -localhost -nopw -forever -shared -rfbport "$VNC_PORT" -noxdamage -noxrecord -noxfixes &
sleep 1

echo "VNC_READY port=$VNC_PORT display=:99"

cleanup() {
  kill "$XVFB_PID" 2>/dev/null || true
  pkill -f "x11vnc.*:99" 2>/dev/null || true
}
trap cleanup EXIT

if command -v dbus-launch >/dev/null 2>&1; then
  exec dbus-launch --exit-with-session startxfce4
else
  exec startxfce4
fi
