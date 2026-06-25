#!/usr/bin/env bash
# Start Cowork computer-use JSON agent (stdin/stdout, one JSON action per line).
set -euo pipefail

ROOT="${COWORK_ROOT:-/opt/cowork}"
PY="$ROOT/computer-use/cowork_desktop.py"
export DISPLAY="${DISPLAY:-:0}"

if [[ ! -f "$PY" ]]; then
  PY="$(dirname "$0")/../computer-use/cowork_desktop.py"
fi

echo "cowork-agent ready (DISPLAY=$DISPLAY). Send JSON lines, e.g.:"
echo '{"action":"screenshot"}'
echo '{"action":"mouse_move","x":640,"y":360}'
echo '{"action":"click"}'

while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" ]] && continue
  echo "$line" | python3 "$PY" --json
done
