#!/usr/bin/env bash
# Quick smoke test for Cowork computer-use stack.
set -euo pipefail

export DISPLAY="${DISPLAY:-:0}"
ROOT="${COWORK_ROOT:-/opt/cowork}"
CLI="$ROOT/computer-use/cowork_cli.py"

echo "==> Cowork desktop test (DISPLAY=$DISPLAY)"
command -v xdotool >/dev/null || { echo "FAIL: xdotool missing"; exit 1; }
command -v scrot >/dev/null || command -v maim >/dev/null || { echo "FAIL: scrot/maim missing"; exit 1; }

python3 "$CLI" size
python3 "$CLI" screenshot --path /tmp/cowork-test.png | head -c 120
echo ""
python3 "$CLI" move --x 200 --y 200
python3 "$CLI" click --x 200 --y 200
echo "==> OK"
