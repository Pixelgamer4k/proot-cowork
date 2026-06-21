#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
echo "==> Provisioning guest inside $DISTRO"

# Use -e instead of heredoc stdin (avoids /proc/self/fd/0 proot warning on Termux)
proot-distro login "$DISTRO" --shared-tmp -e bash -lc '
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt update
apt install -y sudo curl wget git vim nano ca-certificates dbus-x11

mkdir -p /etc/sudoers.d
chmod 755 /etc/sudoers.d

if ! id cowork &>/dev/null; then
    useradd -m -s /bin/bash cowork
fi

echo "cowork ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/cowork
chmod 440 /etc/sudoers.d/cowork

echo "==> Guest provision complete"
'

echo "==> Done. Run 04-xfce-install.sh next."
