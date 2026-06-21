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

# Optional shrink: run apt clean inside guest before export.
# Skipped by default — proot tmp/chmod errors on some Termux builds break export.
if [[ "${CLEAN_BEFORE_EXPORT:-0}" == "1" ]]; then
    mkdir -p "${PREFIX}/tmp"
    echo "==> Cleaning apt cache inside guest (CLEAN_BEFORE_EXPORT=1)..."
    set +e
    proot-distro login "$DISTRO" --shared-tmp -- bash -lc "apt clean"
    CLEAN_STATUS=$?
    set -e
    if [[ $CLEAN_STATUS -ne 0 ]]; then
        echo "WARNING: apt clean failed (proot tmp error). Continuing export anyway."
        echo "         To shrink later: proot-distro login ubuntu -- apt clean"
    fi
else
    echo "==> Skipping apt clean (set CLEAN_BEFORE_EXPORT=1 to try)"
fi

echo "==> Creating tarball (this may take several minutes)..."
echo "    Excluding: proc, sys, dev, run, tmp, var/lib/snapd, snap"

# Remove partial archive from a previous failed export attempt.
rm -f "$OUTPUT"

cd "$ROOTFS_DIR"

# Exclude virtual mounts and snapd paths that cause permission errors in proot rootfs.
# --ignore-failed-read: skip any remaining unreadable files instead of aborting.
tar -czf "$OUTPUT" \
    --ignore-failed-read \
    --exclude='./proc' \
    --exclude='./sys' \
    --exclude='./dev' \
    --exclude='./run' \
    --exclude='./tmp' \
    --exclude='./var/lib/snapd' \
    --exclude='./snap' \
    .

SIZE=$(du -h "$OUTPUT" | cut -f1)
echo "==> Exported: $OUTPUT ($SIZE)"
echo "==> Import this file in Proot Cowork app."
