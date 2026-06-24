#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "==> Termux bootstrap"
pkg update -y
pkg install -y proot-distro rsync tar x11-repo pulseaudio

echo "==> Done. Run 02-install-distro.sh next."
