#!/usr/bin/env bash
# Build proot-distro Ubuntu + XFCE container (aarch64) for Cowork import.
# Output: proot-cowork-ubuntu.tar.gz (ubuntu/manifest.json + ubuntu/rootfs/)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="${OUTPUT:-$ROOT/dist/proot-cowork-ubuntu.tar.gz}"
MANIFEST_TEMPLATE="$ROOT/scripts/ubuntu-container-manifest.json"
FORCE="${FORCE:-0}"

if [[ "$FORCE" != "1" && -f "$OUTPUT" ]]; then
  echo "==> Rootfs already exists: $OUTPUT (set FORCE=1 to rebuild)"
  ls -lh "$OUTPUT"
  exit 0
fi

if ! command -v docker >/dev/null; then
  echo "docker is required to build the Ubuntu XFCE rootfs" >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "==> Building Ubuntu 24.04 + full XFCE desktop (arm64)"
echo "    Output: $OUTPUT"
mkdir -p "$WORKDIR/ubuntu/rootfs"
cp -a "$ROOT/scripts/ubuntu-container-sysdata/." "$WORKDIR/ubuntu/sysdata/"
cp "$MANIFEST_TEMPLATE" "$WORKDIR/ubuntu/manifest.json"

chmod +x "$ROOT/scripts/ubuntu-xfce-guest-install.sh"

CID="$(docker create --platform linux/arm64 ubuntu:24.04 sleep infinity)"
trap 'docker rm -f "$CID" >/dev/null 2>&1 || true; rm -rf "$WORKDIR"' EXIT
docker start "$CID"
docker cp "$ROOT/scripts/ubuntu-xfce-guest-install.sh" "$CID:/install.sh"
docker exec "$CID" bash /install.sh
docker cp "$CID:/out/." "$WORKDIR/ubuntu/rootfs/"

echo "==> Verifying guest rootfs"
test -f "$WORKDIR/ubuntu/rootfs/usr/bin/bash"
test -f "$WORKDIR/ubuntu/rootfs/usr/bin/startxfce4" || test -f "$WORKDIR/ubuntu/rootfs/usr/bin/xfce4-session"
test -f "$WORKDIR/ubuntu/rootfs/usr/share/backgrounds/xfce/xfce-blue.jpg"

mkdir -p "$(dirname "$OUTPUT")"
echo "==> Packing $(basename "$OUTPUT")"
tar -C "$WORKDIR" -czf "$OUTPUT" \
  --exclude='ubuntu/rootfs/var/lib/snapd' \
  --exclude='ubuntu/rootfs/snap' \
  --exclude='ubuntu/rootfs/proc' \
  --exclude='ubuntu/rootfs/sys' \
  --exclude='ubuntu/rootfs/dev' \
  --exclude='ubuntu/rootfs/run' \
  --exclude='ubuntu/rootfs/tmp' \
  ubuntu
ls -lh "$OUTPUT"
echo "==> Rootfs ready for Cowork import: $OUTPUT"
