#!/usr/bin/bash
# Termux:X11 desktop — DISPLAY=:0 + startxfce4 (same path as proot-distro in Termux).
set -euo pipefail

export HOME="${HOME:-/home/cowork}"
export USER="${USER:-cowork}"
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export DISPLAY=:0
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
chmod 700 "$XDG_RUNTIME_DIR" 2>/dev/null || true

if [ -x /usr/bin/cowork-dbus-launch ]; then
  if [ -x /usr/bin/dbus-launch ] && [ ! -f /usr/bin/dbus-launch.real ]; then
    cp -a /usr/bin/dbus-launch /usr/bin/dbus-launch.real
  fi
  cp /usr/bin/cowork-dbus-launch /usr/bin/dbus-launch
  chmod +x /usr/bin/dbus-launch
fi
if [ -x /usr/bin/cowork-bwrap ]; then
  if [ -x /usr/bin/bwrap ] && [ ! -f /usr/bin/bwrap.real ]; then
    cp -a /usr/bin/bwrap /usr/bin/bwrap.real
  fi
  cp /usr/bin/cowork-bwrap /usr/bin/bwrap
  chmod +x /usr/bin/bwrap
fi
if [ -f /usr/lib/aarch64-linux-gnu/libcowork_gtkshim.so ]; then
  cp /usr/lib/aarch64-linux-gnu/libcowork_gtkshim.so /usr/lib/libcowork_gtkshim.so 2>/dev/null || true
fi

pkill -x dbus-daemon 2>/dev/null || true
rm -f "${XDG_RUNTIME_DIR}/bus" 2>/dev/null || true

_gtk_preload=""
if [ -f /usr/lib/libcowork_gtkshim.so ]; then
  _gtk_preload=/usr/lib/libcowork_gtkshim.so
fi

echo "Desktop: starting startxfce4 on DISPLAY=$DISPLAY"
exec env LD_PRELOAD="${_gtk_preload}" GDK_PIXBUF_DISABLE_GLYCIN=1 \
  dbus-launch --exit-with-session /usr/bin/startxfce4
