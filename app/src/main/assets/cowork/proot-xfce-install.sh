#!/usr/bin/env bash
# Install XFCE + Mesa GL stack inside a proot-distro guest for embedded X11 (:0).
set -euo pipefail

DISTRO="${1:-ubuntu}"

if ! command -v proot-distro >/dev/null; then
  echo "proot-distro not found — reinstall app or run: pkg install proot-distro" >&2
  exit 1
fi

echo "==> Installing XFCE desktop + graphics stack in proot-distro: $DISTRO"
echo "    Target: DISPLAY=:0 (1280x720 @ 60Hz embedded X11)"

proot-distro login "$DISTRO" --shared-tmp -- bash -lc '
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

if command -v apt-get >/dev/null; then
  apt-get update
  apt-get install -y \
    xfce4 xfce4-terminal xfce4-goodies xfce4-session \
    dbus dbus-x11 \
    mesa-utils libgl1-mesa-dri libegl1-mesa libgl1 \
    fonts-dejavu fontconfig \
    x11-xserver-utils x11-utils
elif command -v apk >/dev/null; then
  apk update
  apk add xfce4 xfce4-terminal dbus dbus-x11 mesa-dri-gallium mesa-gl mesa-egl \
    mesa-utils font-dejavu fontconfig xorg-server-utils
else
  echo "Unsupported distro — use ubuntu, debian, or alpine" >&2
  exit 1
fi

# Disable compositing for smoother 60fps on software GL.
for home in /root /home/*; do
  [ -d "$home" ] || continue
  dest="$home/.config/xfce4/xfconf/xfce-perchannel-xml/xfwm4.xml"
  mkdir -p "$(dirname "$dest")"
  cat > "$dest" << "XEOF"
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="use_compositing" type="bool" value="false"/>
  <property name="vblank_mode" type="string" value="off"/>
  <property name="frame_drawn" type="bool" value="false"/>
  <property name="sync_to_vblank" type="bool" value="false"/>
</channel>
XEOF
done

echo "==> XFCE + Mesa installed (compositor off for performance)"
'

echo "==> Done. Start with: proot-xfce-start $DISTRO"
