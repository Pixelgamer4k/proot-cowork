#!/usr/bin/env bash
# HTTP dispatch server for phone → desktop Cowork tasks.
set -euo pipefail

ROOT="${COWORK_ROOT:-/opt/cowork}"
PY="$ROOT/computer-use/cowork_dispatch.py"
export DISPLAY="${DISPLAY:-:0}"
export COWORK_DISPATCH_HOST="${COWORK_DISPATCH_HOST:-0.0.0.0}"
export COWORK_DISPATCH_PORT="${COWORK_DISPATCH_PORT:-8765}"

if [[ ! -f "$PY" ]]; then
  PY="$(dirname "$0")/../computer-use/cowork_dispatch.py"
fi

exec python3 "$PY"
