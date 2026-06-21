#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DISTRO="${DISTRO:-ubuntu}"
OUTPUT="${OUTPUT:-$HOME/proot-cowork-rootfs.tar.gz}"
RUNTIME_DIR="${PREFIX}/var/lib/proot-distro"

# proot-distro v5+: containers/<name>/rootfs
# proot-distro v4:  installed-rootfs/<name>
NEW_ROOTFS="$RUNTIME_DIR/containers/$DISTRO/rootfs"
LEGACY_ROOTFS="$RUNTIME_DIR/installed-rootfs/$DISTRO"

if [[ -d "$NEW_ROOTFS" ]]; then
    ROOTFS_DIR="$NEW_ROOTFS"
elif [[ -d "$LEGACY_ROOTFS" ]]; then
    ROOTFS_DIR="$LEGACY_ROOTFS"
else
    echo "ERROR: Rootfs not found. Tried:"
    echo "  $NEW_ROOTFS"
    echo "  $LEGACY_ROOTFS"
    echo ""
    echo "Installed containers:"
    proot-distro list 2>/dev/null || ls -la "$RUNTIME_DIR/containers/" 2>/dev/null || true
    exit 1
fi

echo "==> Exporting rootfs from $ROOTFS_DIR"

if [[ ! -f "$ROOTFS_DIR/start-desktop.sh" ]]; then
    echo "ERROR: start-desktop.sh missing. Run 04-xfce-install.sh first."
    exit 1
fi

# Clean apt cache to reduce size
proot-distro login "$DISTRO" --shared-tmp -e bash -lc "apt clean && rm -rf /tmp/* /var/tmp/*" || true

cd "$ROOTFS_DIR"
tar -czf "$OUTPUT" .

SIZE=$(du -h "$OUTPUT" | cut -f1)
echo "==> Exported: $OUTPUT ($SIZE)"
echo "==> Import this file in Proot Cowork app."
