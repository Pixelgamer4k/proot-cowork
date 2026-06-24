#!/data/data/com.termux/files/usr/bin/bash
# Export a proot-distro Ubuntu + XFCE container for import into Proot Cowork.
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
OUTPUT="${OUTPUT:-$HOME/proot-cowork-ubuntu.tar.gz}"

if ! command -v proot-distro >/dev/null; then
  echo "ERROR: proot-distro not found" >&2
  exit 1
fi

ROOTFS="$PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs"
if [[ ! -f "$ROOTFS/usr/bin/xfce4-session" && ! -f "$ROOTFS/usr/bin/startxfce4" ]]; then
  echo "ERROR: XFCE not installed in $DISTRO. Run: proot-xfce-install $DISTRO" >&2
  exit 1
fi

echo "==> Exporting proot-distro backup for Cowork import"
echo "    Container: $DISTRO"
echo "    Output: $OUTPUT"

if proot-distro backup --help 2>/dev/null | grep -q '\-o'; then
  proot-distro backup "$DISTRO" -o "$OUTPUT"
else
  proot-distro backup "$DISTRO" "$OUTPUT"
fi

ls -lh "$OUTPUT"
echo "==> Copy $OUTPUT to Cowork (Choose file or drop into Android/data/com.proot/files/)"
