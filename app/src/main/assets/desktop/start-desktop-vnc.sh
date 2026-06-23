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

paint_background() {
  if [ -x /usr/bin/xsetroot ]; then
    /usr/bin/xsetroot -display :99 -solid "#1e1e2e" && echo "Desktop: solid background set"
  else
    echo "Desktop: xsetroot missing (apt install x11-xserver-utils)"
  fi
  if [ -x /usr/bin/xrefresh ]; then
    /usr/bin/xrefresh -display :99 2>/dev/null || true
  fi
}

launch_visible_apps() {
  if [ -x /usr/bin/xterm ]; then
    echo "Desktop: starting xterm"
    DISPLAY=:99 /usr/bin/xterm -maximized -bg "#1e1e2e" -fg "#cdd6f4" \
      -title "Proot Cowork" -e bash -l &
    return
  fi

  # GTK terminals (xfce4-terminal) abort under app-launched proot: no bwrap.
  if [ -x /usr/bin/xclock ]; then
    echo "Desktop: starting xclock"
    DISPLAY=:99 /usr/bin/xclock -digital -strftime "%H:%M:%S" \
      -geometry 420x48+24+24 -bg "#1e1e2e" -fg "#cdd6f4" -resize -noresize &
  fi
  if [ -x /usr/bin/xmessage ]; then
    echo "Desktop: starting welcome banner"
    DISPLAY=:99 /usr/bin/xmessage -center -default okay -timeout 0 \
      -bg "#1e1e2e" -fg "#cdd6f4" -fn "10x20" \
      "Proot Cowork desktop is running." &
  fi
}

# Build the visible desktop before x11vnc snapshots the framebuffer.
paint_background

WM=""
for candidate in /usr/bin/openbox /usr/bin/fluxbox; do
  if [ -x "$candidate" ]; then
    WM="$candidate"
    break
  fi
done

set +e
wm_pid=""
if [ -n "$WM" ]; then
  echo "Desktop: starting WM $WM"
  DISPLAY=:99 "$WM" &
  wm_pid=$!
  sleep 1
fi

paint_background
launch_visible_apps
sleep 1
set -e

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

wait "$XVFB_PID" "$X11VNC_PID"
kill "$wm_pid" 2>/dev/null || true
pkill -x xterm 2>/dev/null || true
pkill -x xclock 2>/dev/null || true
pkill -x xmessage 2>/dev/null || true
