#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
echo "==> Installing XFCE4 in $DISTRO"

proot-distro login "$DISTRO" --shared-tmp -- bash -lc '
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt install -y xfce4 xfce4-terminal thunar mousepad

cat > /start-desktop.sh << "EOF"
#!/bin/bash
export DISPLAY=:0
export XDG_RUNTIME_DIR=/tmp
cd /home/cowork 2>/dev/null || cd /
exec dbus-launch --exit-with-session startxfce4
EOF

chmod +x /start-desktop.sh
if id cowork &>/dev/null; then
    chown cowork:cowork /start-desktop.sh
fi

echo "==> XFCE installed, start-desktop.sh created"
'

echo "==> Done. Run 05-agent-tools.sh next."
