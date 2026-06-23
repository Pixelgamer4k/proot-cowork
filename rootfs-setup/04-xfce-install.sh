#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
echo "==> Installing XFCE4 + VNC stack in $DISTRO"

proot-distro login "$DISTRO" --shared-tmp -- bash -lc '
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt install -y xfce4 xfce4-terminal thunar mousepad xvfb x11vnc dbus-x11 x11-xserver-utils x11-apps xterm openbox

cat > /start-desktop.sh << "EOF"
#!/usr/bin/bash
set -euo pipefail
export HOME="${HOME:-/home/cowork}"
export USER="${USER:-cowork}"
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export DISPLAY=:99
export XDG_RUNTIME_DIR=/tmp
export TMPDIR=/tmp
cd "$HOME" 2>/dev/null || cd /
VNC_PORT="${VNC_PORT:-5900}"
SCREEN="${SCREEN:-1280x720x24}"
pkill -x Xvfb 2>/dev/null || true
pkill -f "x11vnc.*:99" 2>/dev/null || true
sleep 0.5
/usr/bin/Xvfb :99 -screen 0 "$SCREEN" -ac +extension GLX +render -noreset &
sleep 1
/usr/bin/x11vnc -display :99 -localhost -nopw -forever -shared -rfbport "$VNC_PORT" \
  -ipv4 -noshm -noxdamage -noxrecord -noxfixes -noxkb -wait 50 &
sleep 1
echo "VNC_READY port=$VNC_PORT display=:99"
exec dbus-launch --exit-with-session /usr/bin/startxfce4
EOF

chmod +x /start-desktop.sh
if id cowork &>/dev/null; then
    chown cowork:cowork /start-desktop.sh
fi

echo "==> XFCE + VNC installed, start-desktop.sh created"
'

echo "==> Done. Run 05-agent-tools.sh next."
