#!/usr/bin/env bash
# Finish cowork automation only: purge themes, install agent+firefox, skip slow dpkg repair.
set -uo pipefail
export DEBIAN_FRONTEND=noninteractive

echo "==> [cowork] Force-remove theme packages"
dpkg --remove --force-remove-reinstreq --force-depends \
  papirus-icon-theme orchis-gtk-theme fonts-inter fonts-inter-variable \
  gnome-accessibility-themes gnome-themes-extra gnome-themes-extra-data \
  plank picom rofi 2>/dev/null || true
rm -rf /usr/share/icons/Papirus /usr/share/themes/Orchis* /opt/cowork/config 2>/dev/null || true

echo "==> [cowork] Automation packages"
apt-get update -qq
apt-get install -y -qq \
  xdotool wmctrl xclip scrot maim imagemagick \
  tesseract-ocr tesseract-ocr-eng \
  python3-pil python3-pip python3-numpy python3-opencv python3-xlib \
  curl wget falkon \
  x11-xserver-utils x11-utils xinput dbus-x11 2>/dev/null || true

echo "==> [cowork] Firefox"
FIREFOX_DIR="/opt/firefox"
if [[ ! -x "$FIREFOX_DIR/firefox" ]]; then
  tmp="$(mktemp -d)"
  curl -fsSL "https://download.mozilla.org/?product=firefox-latest-ssl&os=linux64-aarch64&lang=en-US" -o "$tmp/ff.tar.xz"
  rm -rf "$FIREFOX_DIR" && mkdir -p "$FIREFOX_DIR"
  tar -xJf "$tmp/ff.tar.xz" -C "$FIREFOX_DIR" --strip-components=1
  rm -rf "$tmp"
fi
ln -sf "$FIREFOX_DIR/firefox" /usr/local/bin/firefox

echo "==> [cowork] Python + agent"
pip3 install --break-system-packages --no-cache-dir pytesseract pyautogui 2>/dev/null || true
COWORK_ROOT=/opt/cowork
mkdir -p "$COWORK_ROOT/bin" "$COWORK_ROOT/computer-use"
if [[ -d /cowork-bundle/cowork-assets ]]; then
  rsync -a --exclude=config /cowork-bundle/cowork-assets/ "$COWORK_ROOT/"
fi
chmod +x "$COWORK_ROOT/bin/"* "$COWORK_ROOT/computer-use/"*.py 2>/dev/null || true
for link in cowork-agent cowork-dispatch cowork-desktop-test; do
  [[ -x "$COWORK_ROOT/bin/$link" ]] && ln -sf "$COWORK_ROOT/bin/$link" /usr/local/bin/$link
done
ln -sf "$COWORK_ROOT/computer-use/cowork_cli.py" /usr/local/bin/cowork-cli 2>/dev/null || true

echo "==> [cowork] Done"
test -f "$COWORK_ROOT/computer-use/cowork_desktop.py" && echo AGENT_OK
test -x "$FIREFOX_DIR/firefox" && firefox --version | head -1
