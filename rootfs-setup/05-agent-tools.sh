#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
echo "==> Installing agent tools in $DISTRO"

proot-distro login "$DISTRO" --shared-tmp -e bash -lc '
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt install -y python3 python3-pip python3-venv nodejs npm \
    build-essential jq unzip zip htop screen tmux

apt install -y firefox || apt install -y chromium-browser || true

if id cowork &>/dev/null; then
    mkdir -p /home/cowork/workspace /home/cowork/artifacts
    chown -R cowork:cowork /home/cowork
fi

echo "==> Agent tools installed"
'

echo "==> Done. Run 06-export-rootfs.sh next."
