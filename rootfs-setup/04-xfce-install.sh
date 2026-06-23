#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
echo "==> Installing XFCE4 + UserLAnd VNC stack in $DISTRO"

proot-distro login "$DISTRO" --shared-tmp -- bash -lc '
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt install -y xfce4 xfce4-terminal thunar mousepad tightvncserver expect xterm dbus-x11

# UserLAnd xfce desktop entries for tightvnc
USER_NAME="${USER_NAME:-cowork}"
if id "$USER_NAME" &>/dev/null; then
  HOME_DIR=$(eval echo "~$USER_NAME")
  mkdir -p "$HOME_DIR/.vnc"
  cat > "$HOME_DIR/.vnc/xstartup" << "XEOF"
#!/bin/sh
xrdb $HOME/.Xresources
xsetroot -solid grey
/usr/bin/startxfce4
XEOF
  chmod +x "$HOME_DIR/.vnc/xstartup"
  cp "$HOME_DIR/.vnc/xstartup" "$HOME_DIR/.xinitrc"
  chown -R "$USER_NAME:$USER_NAME" "$HOME_DIR/.vnc" "$HOME_DIR/.xinitrc" 2>/dev/null || true
fi

echo "==> XFCE + tightvnc installed (UserLAnd backend)"
'

echo "==> Done. Run 05-agent-tools.sh next, then 06-export-rootfs.sh"
