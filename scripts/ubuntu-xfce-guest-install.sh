#!/usr/bin/env bash
# Install full Ubuntu + XFCE inside a Docker arm64 root (CI build).
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y ca-certificates rsync

apt-get install -y \
  task-xfce-desktop \
  dbus dbus-x11 sudo \
  firefox \
  thunar thunar-archive-plugin thunar-media-tags-plugin thunar-volman \
  libreoffice-gtk3 \
  build-essential git vim curl wget nano less \
  mesa-utils libgl1-mesa-dri libglx-mesa0 libegl-mesa0 libgbm1 libgl1 \
  greybird-gtk-theme elementary-xfce-icon-theme librsvg2-common gtk2-engines-pixbuf \
  fonts-dejavu fonts-liberation fonts-noto-color-emoji fontconfig \
  pulseaudio pavucontrol \
  xfce4-screenshooter xfce4-taskmanager xfce4-notes \
  ristretto parole xarchiver file-roller \
  x11-xserver-utils x11-utils

# Desktop look-and-feel for root (default proot-distro login user).
for home in /root; do
  xfconf_dir="$home/.config/xfce4/xfconf/xfce-perchannel-xml"
  mkdir -p "$xfconf_dir"

  cat > "$xfconf_dir/xsettings.xml" << "XEOF"
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xsettings" version="1.0">
  <property name="Net" type="empty">
    <property name="ThemeName" type="string" value="Greybird"/>
    <property name="IconThemeName" type="string" value="elementary-xfce"/>
  </property>
  <property name="Gtk" type="empty">
    <property name="FontName" type="string" value="Sans 10"/>
    <property name="MonospaceFontName" type="string" value="Monospace 10"/>
    <property name="ThemeName" type="string" value="Greybird"/>
  </property>
</channel>
XEOF

  cat > "$xfconf_dir/xfdesktop.xml" << "XEOF"
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfdesktop" version="1.0">
  <property name="backdrop" type="empty">
    <property name="screen0" type="empty">
      <property name="monitor0" type="empty">
        <property name="workspace0" type="empty">
          <property name="color-style" type="int" value="0"/>
          <property name="image-style" type="int" value="5"/>
          <property name="last-image" type="string" value="/usr/share/backgrounds/xfce/xfce-blue.jpg"/>
        </property>
      </property>
    </property>
  </property>
</channel>
XEOF

  cat > "$xfconf_dir/xfwm4.xml" << "XEOF"
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="use_compositing" type="bool" value="false"/>
    <property name="vblank_mode" type="string" value="off"/>
    <property name="theme" type="string" value="Default"/>
  </property>
</channel>
XEOF
done

if command -v gtk-update-icon-cache >/dev/null; then
  gtk-update-icon-cache -f /usr/share/icons/elementary-xfce 2>/dev/null || true
  gtk-update-icon-cache -f /usr/share/icons/hicolor 2>/dev/null || true
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
  / /out/

echo "==> Guest install complete"
