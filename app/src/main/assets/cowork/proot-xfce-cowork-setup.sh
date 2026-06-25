#!/usr/bin/env bash
# Install/refresh Cowork automation layer inside existing Ubuntu proot container.
set -euo pipefail

DISTRO="${1:-ubuntu}"

if [[ -n "${TERMUX__PREFIX:-}" ]]; then
  PREFIX="${PREFIX:-$TERMUX__PREFIX}"
elif [[ -d "/data/user/0/com.proot/files/usr" ]]; then
  PREFIX="${PREFIX:-/data/user/0/com.proot/files/usr}"
else
  PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
fi

cowork_termux_env() {
  export TERMUX_APP__PACKAGE_NAME="${TERMUX_APP__PACKAGE_NAME:-com.proot}"
  export TERMUX__PREFIX="$PREFIX"
  export TERMUX__HOME="${TERMUX__HOME:-${PREFIX%/usr}/home}"
  export PATH="$PREFIX/bin:$PATH"
  export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  shopt -s nullglob
  for f in "$PREFIX/lib"/libtermux-exec*.so; do
    export LD_PRELOAD="$f"
  done
}
cowork_termux_env

if ! command -v proot-distro >/dev/null; then
  echo "proot-distro not found (PREFIX=$PREFIX PATH=$PATH)" >&2
  exit 1
fi

SHARE="$PREFIX/share/cowork"
ROOTFS="$PREFIX/var/lib/proot-distro/containers/${DISTRO}/rootfs"
LAYER="$SHARE/cowork-guest-layer.sh"

if [[ ! -f "$LAYER" ]]; then
  LAYER="$(dirname "$0")/cowork-guest-layer.sh"
fi
if [[ ! -f "$LAYER" ]]; then
  echo "ERROR: cowork-guest-layer.sh not found" >&2
  exit 1
fi

ASSETS_TMP="$(mktemp -d)"
trap 'rm -rf "$ASSETS_TMP"' EXIT

mkdir -p "$ASSETS_TMP/cowork-assets/computer-use" "$ASSETS_TMP/cowork-assets/bin"
for src in "$SHARE/computer-use" "$(dirname "$0")/computer-use"; do
  [[ -d "$src" ]] && cp -a "$src/." "$ASSETS_TMP/cowork-assets/computer-use/" && break
done
for f in cowork-agent cowork-dispatch cowork-desktop-test; do
  for src in "$SHARE/${f}.sh" "$(dirname "$0")/${f}.sh"; do
    [[ -f "$src" ]] && cp "$src" "$ASSETS_TMP/cowork-assets/bin/$f" && chmod 755 "$ASSETS_TMP/cowork-assets/bin/$f"
  done
done
cp "$LAYER" "$ASSETS_TMP/cowork-guest-layer.sh"
chmod 755 "$ASSETS_TMP/cowork-guest-layer.sh"

if [[ ! -d "$ROOTFS" ]]; then
  echo "ERROR: container rootfs missing: $ROOTFS" >&2
  exit 1
fi

echo "==> Installing Cowork automation layer in $DISTRO (may take 10–20 min)"
rm -rf "$ROOTFS/cowork-bundle"
cp -a "$ASSETS_TMP" "$ROOTFS/cowork-bundle"

cowork_termux_env

proot-distro login "$DISTRO" --shared-tmp -- bash -lc "
  set -uo pipefail
  export DEBIAN_FRONTEND=noninteractive
  export COWORK_ROOT=/opt/cowork
  bash /cowork-bundle/cowork-guest-layer.sh
"

echo "==> Cowork layer installed"
echo "    Test: proot-distro login $DISTRO -- cowork-desktop-test"
echo "    Agent: cowork-agent"
echo "    Dispatch: cowork-dispatch  (http://DEVICE_IP:8765)"
