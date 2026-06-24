#!/usr/bin/env bash
# Clone Phoshdroid-patched termux-x11 + termux-app forks for in-process embed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
X11_DIR="$ROOT/third_party/termux-x11"
APP_DIR="$ROOT/third_party/termux-app"

if [[ ! -d "$X11_DIR/.git" ]]; then
  mkdir -p "$ROOT/third_party"
  git clone --recurse-submodules --shallow-submodules --depth 1 \
    -b phoshdroid-integration \
    https://github.com/zweck/termux-x11.git "$X11_DIR"
else
  git -C "$X11_DIR" submodule update --init --recursive --depth 1
fi

if [[ ! -d "$APP_DIR/.git" ]]; then
  git clone --depth 1 -b phoshdroid-integration \
    https://github.com/zweck/termux-app.git "$APP_DIR"
fi

bash "$ROOT/scripts/patch-termux-x11-embed.sh"

echo "==> termux-x11 + termux-app ready"
