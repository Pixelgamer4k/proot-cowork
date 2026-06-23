#!/usr/bin/bash
# UserLAnd-style desktop: Xvfb + x11vnc + startxfce4 (embedded VNC viewer in app).
set -euo pipefail

export HOME="${HOME:-/home/cowork}"
export USER="${USER:-cowork}"
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export DISPLAY=:99
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}"
export TMPDIR=/tmp
export LANG="${LANG:-C.UTF-8}"

mkdir -p /tmp/.X11-unix "$HOME" "$XDG_RUNTIME_DIR"
chmod 1777 /tmp 2>/dev/null || true

VNC_PORT="${VNC_PORT:-5900}"
SCREEN="${SCREEN:-1280x720x24}"

for bin in /usr/bin/Xvfb /usr/bin/x11vnc /usr/bin/startxfce4; do
  if [ ! -x "$bin" ]; then
    echo "Missing $bin — install: apt install -y xvfb x11vnc xfce4 dbus-x11"
    exit 1
  fi
done

pkill -x Xvfb 2>/dev/null || true
pkill -f "x11vnc.*:99" 2>/dev/null || true
sleep 0.5

/usr/bin/Xvfb :99 -screen 0 "$SCREEN" -ac +extension GLX +render -noreset &
XVFB_PID=$!
sleep 1

if ! kill -0 "$XVFB_PID" 2>/dev/null; then
  echo "Xvfb failed to start"
  exit 1
fi

/usr/bin/x11vnc -display :99 -localhost -nopw -forever -shared -rfbport "$VNC_PORT" \
  -ipv4 -noshm -noxdamage -noxrecord -noxfixes -noxkb -wait 50 &
X11VNC_PID=$!
sleep 1

echo "VNC_READY port=$VNC_PORT display=:99"

cleanup() {
  kill "$XVFB_PID" 2>/dev/null || true
  kill "$X11VNC_PID" 2>/dev/null || true
}
trap cleanup INT TERM

exec dbus-launch --exit-with-session /usr/bin/startxfce4
