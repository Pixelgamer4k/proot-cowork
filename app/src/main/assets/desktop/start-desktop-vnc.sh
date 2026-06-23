#!/usr/bin/bash
# VNC desktop — runs entirely inside proot (no host X11).
set -euo pipefail

export HOME="${HOME:-/home/cowork}"
export USER="${USER:-cowork}"
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export DISPLAY=:99
export XDG_RUNTIME_DIR=/tmp/cowork-runtime
export TMPDIR=/tmp

mkdir -p /tmp/.X11-unix "$XDG_RUNTIME_DIR"
chmod 1777 /tmp 2>/dev/null || true
chmod 700 "$XDG_RUNTIME_DIR" 2>/dev/null || true
chmod 700 /tmp/.X11-unix 2>/dev/null || true
rm -f /tmp/.X*-lock

VNC_PORT="${VNC_PORT:-5900}"
SCREEN="${SCREEN:-1280x720x24}"
XVFB=/usr/bin/Xvfb
X11VNC=/usr/bin/x11vnc

for bin in "$XVFB" "$X11VNC"; do
  if [ ! -x "$bin" ]; then
    echo "Missing $bin — rebuild rootfs with: apt install -y xvfb x11vnc xfce4"
    exit 1
  fi
done

pkill -x Xvfb 2>/dev/null || true
pkill -f "x11vnc.*:99" 2>/dev/null || true
sleep 0.5

# linkshim only for Xvfb (app-launched proot); never preload libandroid-shmem globally.
_xvfb_preload=""
if [ -f /usr/lib/libcowork_linkshim.so ]; then
  _xvfb_preload=/usr/lib/libcowork_linkshim.so
fi

if [ -n "$_xvfb_preload" ]; then
  LD_PRELOAD="$_xvfb_preload" "$XVFB" :99 -screen 0 "$SCREEN" -ac +extension GLX +render -noreset -extension MIT-SHM &
else
  "$XVFB" :99 -screen 0 "$SCREEN" -ac +extension GLX +render -noreset -extension MIT-SHM &
fi
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

vnc_listening() {
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | grep -q ":${VNC_PORT} "
    return
  fi
  (echo >/dev/tcp/127.0.0.1/"$VNC_PORT") >/dev/null 2>&1
}

# -noshm avoids proot shm-helper exec errors on Android.
"$X11VNC" -display :99 -localhost -nopw -forever -shared -rfbport "$VNC_PORT" \
  -noshm -noxdamage -noxrecord -noxfixes -noxkb -wait 50 &
X11VNC_PID=$!
for _ in $(seq 1 30); do
  if vnc_listening; then
    break
  fi
  if ! kill -0 "$X11VNC_PID" 2>/dev/null; then
    echo "x11vnc failed to start"
    exit 1
  fi
  sleep 0.2
done

if ! vnc_listening; then
  echo "VNC port $VNC_PORT not listening"
  exit 1
fi

echo "VNC_READY port=$VNC_PORT display=:99"

cleanup() {
  kill "$XVFB_PID" 2>/dev/null || true
  kill "$X11VNC_PID" 2>/dev/null || true
  pkill -f "x11vnc.*:99" 2>/dev/null || true
}
trap cleanup INT TERM

# Solid background instead of the default X crosshatch.
if command -v xsetroot >/dev/null 2>&1; then
  DISPLAY=:99 xsetroot -solid "#1e1e2e" 2>/dev/null || true
fi

# XFCE needs dbus/bwrap which fail under app-launched proot; prefer standalone WMs.
WM=""
for candidate in /usr/bin/openbox /usr/bin/fluxbox; do
  if [ -x "$candidate" ]; then
    WM="$candidate"
    break
  fi
done
if [ -z "$WM" ] && [ -x /usr/bin/xfwm4 ]; then
  WM=/usr/bin/xfwm4
fi

TERM_BIN=""
for candidate in /usr/bin/xterm /usr/bin/x-terminal-emulator; do
  if [ -x "$candidate" ]; then
    TERM_BIN="$candidate"
    break
  fi
done

set +e
wm_pid=""
if [ -n "$WM" ]; then
  DISPLAY=:99 "$WM" &
  wm_pid=$!
  sleep 1
fi

DISPLAY=:99 xsetroot -solid "#1e1e2e" 2>/dev/null || true

if [ -n "$TERM_BIN" ]; then
  DISPLAY=:99 "$TERM_BIN" -geometry 120x35+24+24 -fa Monospace -fs 14 \
    -bg "#1e1e2e" -fg "#cdd6f4" -title "Proot Cowork" -e bash -l &
  sleep 0.5
fi
set -e

# Hold the session open while VNC is serving.
wait "$XVFB_PID" "$X11VNC_PID"
kill "$wm_pid" 2>/dev/null || true
pkill -x xterm 2>/dev/null || true
