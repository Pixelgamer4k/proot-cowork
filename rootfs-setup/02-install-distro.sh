#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
echo "==> Installing proot-distro: $DISTRO"
proot-distro install "$DISTRO"

echo "==> Done. Run 03-guest-provision.sh next."
