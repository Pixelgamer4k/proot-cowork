#!/usr/bin/env bash
# Download official Termux bootstrap into app assets (user asked for full Termux inside).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/termux"
mkdir -p "$OUT"
URL="https://github.com/termux/termux-app/releases/download/v0.118.3/bootstrap-aarch64.zip"
echo "Downloading Termux bootstrap (~100MB)..."
curl -fL "$URL" -o "$OUT/bootstrap-aarch64.zip"
echo "Saved $OUT/bootstrap-aarch64.zip"
ls -lh "$OUT/bootstrap-aarch64.zip"
