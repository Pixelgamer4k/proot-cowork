#!/usr/bin/env bash
# Install full Ubuntu + XFCE inside a Docker arm64 root (CI / export build).
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y ca-certificates rsync software-properties-common

# Universe + multiverse for codecs, extra fonts, and desktop extras.
add-apt-repository -y universe 2>/dev/null || true
add-apt-repository -y multiverse 2>/dev/null || true
apt-get update

echo "==> Installing full XFCE desktop + tools"
apt-get install -y \
  task-xfce-desktop \
  xfce4-goodies \
  dbus dbus-x11 sudo \
  thunar thunar-archive-plugin thunar-media-tags-plugin thunar-volman \
  libreoffice-gtk3 \
  build-essential git vim curl wget nano less tree htop file \
  openssh-client rsync unzip zip jq \
  python3 python3-pip python3-venv python3-dev \
  net-tools iputils-ping dnsutils \
  mesa-utils libgl1-mesa-dri libglx-mesa0 libegl-mesa0 libgbm1 libgl1 \
  greybird-gtk-theme elementary-xfce-icon-theme librsvg2-common gtk2-engines-pixbuf \
  fonts-dejavu fonts-liberation fonts-noto fonts-noto-color-emoji fontconfig \
  pulseaudio pavucontrol \
  xfce4-screenshooter xfce4-taskmanager xfce4-notes xfce4-power-manager \
  xfce4-clipman-plugin xfce4-pulseaudio-plugin xfce4-netload-plugin \
  ristretto parole xarchiver file-roller evince mousepad \
  vlc gimp galculator \
  x11-xserver-utils x11-utils xdotool

# Optional extras (non-fatal in Docker/QEMU builds).
apt-get install -y ubuntu-restricted-extras || true

echo "==> Cowork computer-use automation layer"
if [[ -d /cowork-bundle ]]; then
  bash /cowork-bundle/cowork-guest-layer.sh
else
  bash "$(dirname "$0")/cowork-guest-layer.sh"
fi

apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

echo "==> Exporting rootfs to /out"
mkdir -p /out
rsync -aHAXx --delete \
  --exclude=/out \
  --exclude=/proc \
  --exclude=/sys \
  --exclude=/dev \
  --exclude=/tmp \
  --exclude=/run \
  --exclude=/var/lib/snapd \
  --exclude=/snap \
  / /out/

echo "==> Guest install complete"
echo "    Packages: $(dpkg -l | grep -c ^ii || true)"
test -f /out/usr/bin/xfce4-session || test -f /out/usr/bin/startxfce4
test -f /out/opt/cowork/computer-use/cowork_desktop.py
test -x /out/opt/firefox/firefox || test -x /out/usr/local/bin/firefox || test -x /out/usr/bin/falkon
test -f /out/usr/bin/xdotool
