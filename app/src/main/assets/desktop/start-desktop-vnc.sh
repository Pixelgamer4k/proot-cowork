#!/usr/bin/bash
# VNC desktop — runs entirely inside proot (no host X11).
set -euo pipefail

export HOME="${HOME:-/home/cowork}"
export USER="${USER:-cowork}"
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export DISPLAY=:99
export XDG_RUNTIME_DIR=/tmp/cowork-runtime
export TMPDIR=/tmp
export GDK_PIXBUF_DISABLE_GLYCIN=1
export GTK_USE_PORTAL=0
export NO_AT_BRIDGE=1
export GDK_BACKEND=x11
export GTK_ICON_THEME_NAME=HighContrast
export XDG_CURRENT_DESKTOP=XFCE
export LANG="${LANG:-C.UTF-8}"

mkdir -p /tmp/.X11-unix "$XDG_RUNTIME_DIR" "$HOME"
chmod 1777 /tmp 2>/dev/null || true
chmod 700 "$XDG_RUNTIME_DIR" 2>/dev/null || true
pkill -x dbus-daemon 2>/dev/null || true
rm -f "${XDG_RUNTIME_DIR}/bus" 2>/dev/null || true
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
pkill -x xfce4-session 2>/dev/null || true
pkill -x xfwm4 2>/dev/null || true
pkill -x xfdesktop 2>/dev/null || true
pkill -x xfce4-panel 2>/dev/null || true
sleep 0.5

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

install_guest_stubs() {
  mkdir -p /usr/local/bin /etc/profile.d
  cat > /etc/profile.d/cowork-gtk.sh <<'EOF'
export GDK_PIXBUF_DISABLE_GLYCIN=1
export GTK_USE_PORTAL=0
export NO_AT_BRIDGE=1
export GDK_BACKEND=x11
export GTK_ICON_THEME_NAME=HighContrast
EOF

  local bwrap_stub=/usr/local/bin/cowork-bwrap-stub
  if [ -x /usr/bin/cowork-bwrap ]; then
    cp /usr/bin/cowork-bwrap "$bwrap_stub"
  else
    cat > "$bwrap_stub" <<'EOF'
#!/usr/bin/bash
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  if [[ "${args[i]}" == /usr/libexec/glycin-loaders/* ]]; then
    exec "${args[i]}" "${args[@]:i+1}"
  fi
done
for ((i = 0; i < ${#args[@]}; i++)); do
  if [ "${args[i]}" = "--" ]; then
    exec "${args[@]:i+1}"
  fi
done
for ((i = ${#args[@]} - 1; i >= 0; i--)); do
  if [[ "${args[i]}" == /* ]] && [ -x "${args[i]}" ]]; then
    exec "${args[i]}"
  fi
done
exit 1
EOF
    chmod +x "$bwrap_stub"
  fi

  if [ -x /usr/bin/bwrap ] && [ ! -f /usr/bin/bwrap.real ]; then
    mv /usr/bin/bwrap /usr/bin/bwrap.real
  fi
  cp "$bwrap_stub" /usr/bin/bwrap
  chmod +x /usr/bin/bwrap

  local dbus_stub=/usr/local/bin/cowork-dbus-launch-stub
  if [ -x /usr/bin/cowork-dbus-launch ]; then
    cp /usr/bin/cowork-dbus-launch "$dbus_stub"
  else
    cat > "$dbus_stub" <<'EOF'
#!/usr/bin/bash
runtime="${XDG_RUNTIME_DIR:-/tmp/cowork-runtime}"
mkdir -p "$runtime"
chmod 700 "$runtime" 2>/dev/null || true
start_bus() {
  if [ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ]; then return 0; fi
  if [ -S "$runtime/bus" ]; then
    export DBUS_SESSION_BUS_ADDRESS="unix:path=$runtime/bus"
    return 0
  fi
  if [ ! -x /usr/bin/dbus-daemon ]; then return 1; fi
  if dbus-daemon --session --nopidfile --address="unix:path=$runtime/bus"; then
    export DBUS_SESSION_BUS_ADDRESS="unix:path=$runtime/bus"
  fi
}
case "${1:-}" in
  --sh-syntax)
    start_bus
    printf "DBUS_SESSION_BUS_ADDRESS='%s'; export DBUS_SESSION_BUS_ADDRESS;\n" "${DBUS_SESSION_BUS_ADDRESS:-}"
    exit 0 ;;
  --autolaunch|--autolaunch=*|--binary-syntax|--csh-syntax)
    start_bus; exit 0 ;;
esac
start_bus
if [ "${1:-}" = "--exit-with-session" ]; then shift; fi
exec "$@"
EOF
    chmod +x "$dbus_stub"
  fi
  if [ -x /usr/bin/dbus-launch ] && [ ! -f /usr/bin/dbus-launch.real ]; then
    mv /usr/bin/dbus-launch /usr/bin/dbus-launch.real
  fi
  cp "$dbus_stub" /usr/bin/dbus-launch
  chmod +x /usr/bin/dbus-launch
  if [ -f /usr/lib/aarch64-linux-gnu/libcowork_gtkshim.so ]; then
    cp /usr/lib/aarch64-linux-gnu/libcowork_gtkshim.so /usr/lib/libcowork_gtkshim.so 2>/dev/null || true
    rm -f /etc/ld.so.preload 2>/dev/null || true
  fi
  echo "Desktop: guest stubs installed (bwrap + dbus-launch)"
}

restore_glycin_loaders() {
  [ -d /usr/libexec/glycin-loaders ] || return 0
  local loader best
  shopt -s nullglob
  for loader in /usr/libexec/glycin-loaders/*/*; do
    [[ "$loader" == *.real* ]] && continue
    [ -f "$loader" ] || continue
    best="$loader"
    while [ -f "${best}.real" ]; do
      best="${best}.real"
    done
    if [ "$best" != "$loader" ]; then
      cp -a "$best" "$loader" 2>/dev/null || true
      chmod +x "$loader" 2>/dev/null || true
    fi
  done
  shopt -u nullglob
}

setup_desktop_config() {
  install_guest_stubs
  mkdir -p "$HOME"
  rm -rf "$HOME/.cache/glycin" "$HOME/.cache" 2>/dev/null || true
  restore_glycin_loaders

  local src=/usr/share/proot-cowork
  mkdir -p "$HOME/.config/xfce4/xfconf/xfce-perchannel-xml"
  mkdir -p "$HOME/.config/gtk-3.0" "$HOME/.config/gtk-4.0"

  if [ -f "$src/.config/xfce4/xfconf/xfce-perchannel-xml/xfdesktop.xml" ]; then
    cp "$src/.config/xfce4/xfconf/xfce-perchannel-xml/xfdesktop.xml" \
      "$HOME/.config/xfce4/xfconf/xfce-perchannel-xml/"
  fi
  if [ -f "$src/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml" ]; then
    cp "$src/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml" \
      "$HOME/.config/xfce4/xfconf/xfce-perchannel-xml/"
  fi
  if [ -f "$src/.config/xfce4/xinitrc" ]; then
    cp "$src/.config/xfce4/xinitrc" "$HOME/.config/xfce4/xinitrc"
    chmod +x "$HOME/.config/xfce4/xinitrc"
  fi
  if [ -f "$src/.config/gtk-3.0/settings.ini" ]; then
    cp "$src/.config/gtk-3.0/settings.ini" "$HOME/.config/gtk-3.0/"
  fi
  if [ -f "$src/.config/gtk-4.0/settings.ini" ]; then
    cp "$src/.config/gtk-4.0/settings.ini" "$HOME/.config/gtk-4.0/"
  fi
  if [ -f "$src/.xsessionrc" ]; then
    cp "$src/.xsessionrc" "$HOME/.xsessionrc"
    chmod +x "$HOME/.xsessionrc"
  fi
  rm -rf "$HOME/.config/openbox" 2>/dev/null || true
  rm -rf "$HOME/.cache/xfce4" 2>/dev/null || true
  echo "Desktop: cowork XFCE config installed"
}

start_session_dbus() {
  mkdir -p "$XDG_RUNTIME_DIR"
  chmod 700 "$XDG_RUNTIME_DIR" 2>/dev/null || true
  pkill -x dbus-daemon 2>/dev/null || true
  rm -f "${XDG_RUNTIME_DIR}/bus" 2>/dev/null || true
  unset DBUS_SESSION_BUS_ADDRESS

  if [ -x /usr/bin/cowork-dbus-launch ]; then
    eval "$(/usr/bin/cowork-dbus-launch --sh-syntax 2>&1)" || true
  fi
  if [ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ] && [ -S "${XDG_RUNTIME_DIR}/bus" ]; then
    echo "Desktop: session dbus ready"
    return 0
  fi

  if [ ! -x /usr/bin/dbus-daemon ]; then
    echo "Desktop: dbus-daemon missing"
    return 1
  fi
  dbus-daemon --session --nopidfile \
    --address="unix:path=${XDG_RUNTIME_DIR}/bus" >/dev/null 2>&1 &
  _dbus_pid=$!
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if [ -S "${XDG_RUNTIME_DIR}/bus" ] && kill -0 "$_dbus_pid" 2>/dev/null; then
      export DBUS_SESSION_BUS_ADDRESS="unix:path=${XDG_RUNTIME_DIR}/bus"
      echo "Desktop: session dbus ready"
      return 0
    fi
    sleep 0.2
  done
  echo "Desktop: dbus-daemon failed (no session socket)"
  return 1
}

launch_desktop_session() {
  setup_desktop_config

  if [ ! -x /usr/bin/xfwm4 ] || [ ! -x /usr/bin/xfdesktop ]; then
    echo "Desktop: XFCE missing, falling back to xterm"
    DISPLAY=:99 /usr/bin/xterm -maximized -bg "#1e1e2e" -fg "#cdd6f4" \
      -title "Proot Cowork" -e /usr/bin/bash &
    return
  fi

  _gtk_preload=""
  if [ -f /usr/lib/libcowork_gtkshim.so ]; then
    _gtk_preload=/usr/lib/libcowork_gtkshim.so
  fi

  start_session_dbus || true

  XFCONF_D=/usr/lib/aarch64-linux-gnu/xfce4/xfconf/xfconfd
  if [ ! -x "$XFCONF_D" ]; then
    XFCONF_D=$(command -v xfconfd 2>/dev/null || true)
  fi
  if [ -n "$XFCONF_D" ] && [ -x "$XFCONF_D" ] && [ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ]; then
    DISPLAY=:99 "$XFCONF_D" &
    sleep 3
  elif [ -z "${DBUS_SESSION_BUS_ADDRESS:-}" ]; then
    echo "Desktop: skipping xfconfd (no session dbus)"
  else
    echo "Desktop: xfconfd missing"
  fi

  echo "Desktop: starting full XFCE desktop"
  DISPLAY=:99 LD_PRELOAD="${_gtk_preload}" GDK_PIXBUF_DISABLE_GLYCIN=1 \
    DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-}" \
    /usr/bin/bash -c '
      export GDK_PIXBUF_DISABLE_GLYCIN=1 GTK_USE_PORTAL=0 NO_AT_BRIDGE=1
      export GDK_BACKEND=x11 GTK_ICON_THEME_NAME=HighContrast
      export XDG_CURRENT_DESKTOP=XFCE DESKTOP_SESSION=xfce XDG_MENU_PREFIX=xfce-
      export HOME="${HOME:-/home/cowork}"
      export XDG_CONFIG_HOME="${XDG_CONFIG_HOME:-$HOME/.config}"
      export XDG_CACHE_HOME="${XDG_CACHE_HOME:-$HOME/.cache}"
      export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/cowork-runtime}"
      export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-}"
      mkdir -p "$HOME" "$XDG_CONFIG_HOME" "$XDG_CACHE_HOME" "$XDG_RUNTIME_DIR"

      xfsettingsd &
      sleep 2
      xfwm4 &
      sleep 3
      xfdesktop &
      sleep 3
      if [ -x /usr/bin/xfce4-panel ]; then
        xfce4-panel &
      fi
      wait
    ' &

  set +e
  for _ in $(seq 1 60); do
    if DISPLAY=:99 xwininfo -root -tree 2>/dev/null | grep -qE 'xfce4-panel|xfdesktop'; then
      echo "Desktop: XFCE desktop visible"
      break
    fi
    sleep 0.5
  done
  sleep 20
  set -e
}

launch_desktop_session

set +e
sleep 1
set -e

vnc_listening() {
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | grep -q ":${VNC_PORT} "
    return
  fi
  (echo >/dev/tcp/127.0.0.1/"$VNC_PORT") >/dev/null 2>&1
}

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
pkill -x xfce4-session 2>/dev/null || true
pkill -x xfwm4 2>/dev/null || true
pkill -x xfdesktop 2>/dev/null || true
pkill -x xfce4-panel 2>/dev/null || true
pkill -x xfsettingsd 2>/dev/null || true
pkill -x xfconfd 2>/dev/null || true
pkill -x thunar 2>/dev/null || true
pkill -x xterm 2>/dev/null || true
