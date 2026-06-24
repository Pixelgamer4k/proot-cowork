#!/data/data/com.termux/files/usr/bin/bash
# Export Ubuntu + XFCE for Proot Cowork (F-Droid Termux entry point).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for candidate in \
  "${PREFIX:-}/share/cowork/proot-xfce-export.sh" \
  "$SCRIPT_DIR/proot-xfce-export.sh" \
  "$SCRIPT_DIR/../app/src/main/assets/cowork/proot-xfce-export.sh"; do
  if [[ -f "$candidate" ]]; then
    exec bash "$candidate" "$@"
  fi
done

echo "ERROR: proot-xfce-export not found." >&2
echo "  Run: bash $SCRIPT_DIR/00-install-termux-scripts.sh" >&2
exit 1
