#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/desktop/guest-bin/usr/bin/cowork-dbus-launch"
SRC="$ROOT/native/cowork-dbus-launch/cowork-dbus-launch.c"
mkdir -p "$(dirname "$OUT")"
aarch64-linux-gnu-gcc -static -O2 -o "$OUT" "$SRC"
echo "Built $OUT"
