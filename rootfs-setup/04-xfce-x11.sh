#!/data/data/com.termux/files/usr/bin/bash
# Install XFCE for direct X11 on embedded Proot-Cowork (:0) — no VNC.
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v proot-xfce-install >/dev/null; then
  exec proot-xfce-install "$DISTRO"
fi

if [[ -f "$SCRIPT_DIR/../app/src/main/assets/cowork/proot-xfce-install.sh" ]]; then
  exec bash "$SCRIPT_DIR/../app/src/main/assets/cowork/proot-xfce-install.sh" "$DISTRO"
fi

echo "ERROR: proot-xfce-install not found. Run: bash $SCRIPT_DIR/00-install-termux-scripts.sh" >&2
exit 1
