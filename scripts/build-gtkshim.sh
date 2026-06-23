#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/desktop/guest-bin/usr/lib/aarch64-linux-gnu/libcowork_gtkshim.so"
SRC="$ROOT/native/gtkshim/gtkshim.c"
mkdir -p "$(dirname "$OUT")"
aarch64-linux-gnu-gcc -shared -fPIC -O2 -o "$OUT" "$SRC" -ldl
echo "Built $OUT"
