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

install_guest_stubs() {
  mkdir -p /usr/local/bin
  local stub=/usr/local/bin/cowork-bwrap-stub
  cat > "$stub" <<'EOF'
#!/usr/bin/bash
for i in "${!@}"; do
  if [ "${!i}" = "--" ]; then
    exec "${@:$((i + 1))}"
  fi
done
target=""
tail_args=()
for arg in "$@"; do
  if [[ "$arg" == /* ]] && [ -e "$arg" ]; then
    if [ -z "$target" ]; then
      target="$arg"
      continue
    fi
  fi
  if [ -n "$target" ]; then
    tail_args+=("$arg")
  fi
done
if [ -n "$target" ]; then
  exec "$target" "${tail_args[@]}"
fi
exit 1
EOF
  cat > /usr/local/bin/dbus-launch <<'EOF'
#!/usr/bin/bash
if [ "${1:-}" = "--exit-with-session" ]; then
  shift
fi
exec "$@"
EOF
  chmod +x "$stub" /usr/local/bin/dbus-launch
  if [ -x /usr/bin/bwrap ] && [ ! -f /usr/bin/bwrap.real ]; then
    mv /usr/bin/bwrap /usr/bin/bwrap.real
  fi
  cp "$stub" /usr/bin/bwrap
  chmod +x /usr/bin/bwrap
  if [ -x /usr/bin/dbus-launch ] && [ ! -f /usr/bin/dbus-launch.real ]; then
    mv /usr/bin/dbus-launch /usr/bin/dbus-launch.real
  fi
  cp /usr/local/bin/dbus-launch /usr/bin/dbus-launch
  chmod +x /usr/bin/dbus-launch
  echo "Desktop: guest stubs installed (/usr/bin/bwrap and dbus-launch replaced)"
}

launch_visible_apps() {
  install_guest_stubs

  if [ -x /usr/bin/xterm ]; then
    echo "Desktop: starting xterm"
    DISPLAY=:99 /usr/bin/xterm -maximized -bg "#1e1e2e" -fg "#cdd6f4" \
      -title "Proot Cowork" -e bash -l &
    return
  fi

  if [ -x /usr/bin/xfce4-terminal ]; then
    echo "Desktop: starting xfce4-terminal"
    export GTK_USE_PORTAL=0
    export NO_AT_BRIDGE=1
    export GDK_PIXBUF_DISABLE_GLYCIN=1
    export GTK_ICON_THEME_NAME=HighContrast
    DISPLAY=:99 /usr/bin/xfce4-terminal \
      --maximize \
      --title="Proot Cowork" \
      --color-bg="#1e1e2e" \
      --color-text="#cdd6f4" \
      --font="Monospace 14" \
      -e bash &
    sleep 2
    if DISPLAY=:99 xwininfo -root -tree 2>/dev/null | grep -qi xfce4-terminal; then
      echo "Desktop: xfce4-terminal window visible"
      return
    fi
    echo "Desktop: xfce4-terminal failed, trying fallback"
    pkill -x xfce4-terminal 2>/dev/null || true
  fi

  if [ -x /usr/bin/xclock ]; then
    echo "Desktop: starting xclock (install xterm in rootfs for a shell)"
    DISPLAY=:99 /usr/bin/xclock -digital -strftime "%H:%M:%S" \
      -geometry 1280x48+0+0 -bg "#1e1e2e" -fg "#cdd6f4" -resize -noresize &
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
pkill -x xfce4-terminal 2>/dev/null || true
pkill -x xclock 2>/dev/null || true
