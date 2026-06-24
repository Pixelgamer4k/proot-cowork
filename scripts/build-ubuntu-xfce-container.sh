#!/usr/bin/env bash
# Build a full Ubuntu + XFCE proot-distro container (aarch64) for APK bundling.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MARKER="$ROOT/app/src/main/assets/.ubuntu_xfce_container_v1"
OUTPUT="$ROOT/app/src/main/assets/ubuntu-xfce-container.tar.gz"
MANIFEST_TEMPLATE="$ROOT/scripts/ubuntu-container-manifest.json"

if [[ -f "$MARKER" && -f "$OUTPUT" ]]; then
  echo "==> Ubuntu XFCE container already built ($MARKER)"
  ls -lh "$OUTPUT"
  exit 0
fi

if ! command -v docker >/dev/null; then
  echo "docker is required to build the Ubuntu XFCE container" >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "==> Building Ubuntu 24.04 + full XFCE (arm64) — this takes a while"
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

mkdir -p "$(dirname "$OUTPUT")"
echo "==> Packing ubuntu-xfce-container.tar.gz"
tar -C "$WORKDIR" -czf "$OUTPUT" ubuntu
touch "$MARKER"
ls -lh "$OUTPUT"
echo "==> Ubuntu XFCE container ready"
