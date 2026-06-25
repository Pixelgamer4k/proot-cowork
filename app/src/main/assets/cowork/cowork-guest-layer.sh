#!/usr/bin/env bash
# Cowork computer-use layer: automation tools and agent scripts only (no theme/aesthetics).
# Runs inside Ubuntu guest (Docker CI or proot-distro login).
set -uo pipefail

export DEBIAN_FRONTEND=noninteractive

echo "==> [cowork] Repairing partial package state (proot-safe)"
mkdir -p /run /var/run 2>/dev/null || true
dpkg --remove --force-remove-reinstreq geoclue-2.0 2>/dev/null || true
dpkg --configure -a 2>/dev/null || true
apt-get -f install -y 2>/dev/null || true

echo "==> [cowork] Removing theme/aesthetic packages if present"
DEBIAN_FRONTEND=noninteractive apt-get remove -y --purge \
  papirus-icon-theme orchis-gtk-theme fonts-inter fonts-inter-variable \
  gnome-accessibility-themes gnome-themes-extra gnome-themes-extra-data \
  plank picom rofi 2>/dev/null || true
apt-get autoremove -y 2>/dev/null || true
rm -rf /opt/cowork/config/picom.conf /etc/xdg/plank 2>/dev/null || true
for home in /root /home/*; do
  [[ -d "$home" ]] || continue
  rm -f "$home/.config/autostart/cowork-picom.desktop" \
        "$home/.config/autostart/cowork-plank.desktop" 2>/dev/null || true
done

echo "==> [cowork] Installing computer-use and automation packages"
apt-get update
add-apt-repository -y universe 2>/dev/null || true
apt-get update

apt-get install -y \
  xdotool wmctrl xclip xsel scrot maim imagemagick \
  tesseract-ocr tesseract-ocr-eng tesseract-ocr-osd \
  at-spi2-core python3-pyatspi python3-gi gir1.2-atspi-2.0 \
  python3-pil python3-pip python3-venv python3-dev python3-numpy \
  python3-opencv python3-xlib \
  jq curl wget unzip rsync git \
  libnss3 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 \
  libxcomposite1 libxdamage1 libxrandr2 libgbm1 libasound2t64 \
  libpangocairo-1.0-0 libgtk-3-0 \
  falkon mousepad thunar \
  x11-xserver-utils x11-utils x11-apps xinput \
  dbus-x11 || true

dpkg --configure -a 2>/dev/null || true
apt-get -f install -y 2>/dev/null || true

echo "==> [cowork] Installing Firefox (Mozilla aarch64, non-snap)"
FIREFOX_DIR="/opt/firefox"
if [[ ! -x "$FIREFOX_DIR/firefox" ]]; then
  tmp="$(mktemp -d)"
  curl -fsSL \
    "https://download.mozilla.org/?product=firefox-latest-ssl&os=linux64-aarch64&lang=en-US" \
    -o "$tmp/firefox.tar.xz"
  rm -rf "$FIREFOX_DIR"
  mkdir -p "$FIREFOX_DIR"
  tar -xJf "$tmp/firefox.tar.xz" -C "$FIREFOX_DIR" --strip-components=1
  rm -rf "$tmp"
fi
rm -f /usr/bin/firefox /usr/local/bin/firefox 2>/dev/null || true
ln -sf "$FIREFOX_DIR/firefox" /usr/local/bin/firefox
update-alternatives --install /usr/bin/firefox firefox /usr/local/bin/firefox 100 2>/dev/null || true

echo "==> [cowork] Python automation stack"
pip3 install --break-system-packages --no-cache-dir \
  pytesseract pillow numpy opencv-python-headless pyautogui 2>/dev/null \
  || pip3 install --no-cache-dir pytesseract pillow numpy opencv-python-headless pyautogui || true

COWORK_ROOT="${COWORK_ROOT:-/opt/cowork}"
mkdir -p "$COWORK_ROOT/bin" "$COWORK_ROOT/computer-use"

if [[ -d /cowork-assets ]]; then
  rsync -a --exclude='config' /cowork-assets/ "$COWORK_ROOT/"
elif [[ -d /cowork-bundle/cowork-assets ]]; then
  rsync -a --exclude='config' /cowork-bundle/cowork-assets/ "$COWORK_ROOT/"
elif [[ -d "$(dirname "$0")/../app/src/main/assets/cowork/computer-use" ]]; then
  rsync -a "$(dirname "$0")/../app/src/main/assets/cowork/computer-use/" "$COWORK_ROOT/computer-use/"
  for f in cowork-agent cowork-dispatch cowork-desktop-test; do
    src="$(dirname "$0")/../app/src/main/assets/cowork/${f}.sh"
    [[ -f "$src" ]] && install -m 755 "$src" "$COWORK_ROOT/bin/$f"
  done
fi

chmod +x "$COWORK_ROOT/bin/"* 2>/dev/null || true
chmod +x "$COWORK_ROOT/computer-use/"*.py 2>/dev/null || true

for link in cowork-agent cowork-dispatch cowork-desktop-test; do
  [[ -x "$COWORK_ROOT/bin/$link" ]] && ln -sf "$COWORK_ROOT/bin/$link" "/usr/local/bin/$link"
done
ln -sf "$COWORK_ROOT/computer-use/cowork_cli.py" /usr/local/bin/cowork-cli 2>/dev/null || true

apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* 2>/dev/null || true

echo "==> [cowork] Layer complete (automation only)"
command -v firefox >/dev/null && firefox --version | head -1 || true
command -v cowork-agent >/dev/null && echo "    cowork-agent: $(command -v cowork-agent)"
test -f "$COWORK_ROOT/computer-use/cowork_desktop.py" && echo "    computer-use: OK"
