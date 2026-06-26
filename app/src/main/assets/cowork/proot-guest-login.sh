#!/usr/bin/env bash
# Interactive login shell inside the Ubuntu proot-distro guest (same entry as proot-xfce-start.sh).
set -euo pipefail

DISTRO="${1:-ubuntu}"
P="${PREFIX:?PREFIX not set}"
PD="$P/bin/proot-distro"

if [[ ! -x "$PD" ]]; then
  echo "proot-distro not found at $PD (PATH=${PATH:-})" >&2
  exit 127
fi

export DISPLAY="${DISPLAY:-:0}"
export GDK_BACKEND=x11
export QT_QPA_PLATFORM=xcb
export XDG_CURRENT_DESKTOP=XFCE
export DESKTOP_SESSION=xfce
export TERM="${TERM:-xterm-256color}"

exec "$PD" login "$DISTRO" --shared-tmp -- bash -l
