#!/data/data/com.termux/files/usr/bin/bash
# Install XFCE for direct X11 on embedded Proot-Cowork (:0) — no VNC.
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
exec proot-xfce-install "$DISTRO"
