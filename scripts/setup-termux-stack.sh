#!/usr/bin/env bash
# Clone Phoshdroid-patched termux-x11 + termux-app forks for in-process embed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
X11_DIR="$ROOT/third_party/termux-x11"
APP_DIR="$ROOT/third_party/termux-app"

MAX_ATTEMPTS="${TERMUX_STACK_CLONE_ATTEMPTS:-6}"
RETRY_DELAY_SEC="${TERMUX_STACK_RETRY_DELAY_SEC:-45}"

retry() {
  local attempt=1
  local delay="$RETRY_DELAY_SEC"
  while true; do
    if "$@"; then
      return 0
    fi
    if (( attempt >= MAX_ATTEMPTS )); then
      echo "Command failed after ${MAX_ATTEMPTS} attempts: $*" >&2
      return 1
    fi
    echo "Attempt ${attempt}/${MAX_ATTEMPTS} failed; retrying in ${delay}s: $*" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
    delay=$((delay + 30))
  done
}

clone_termux_x11() {
  rm -rf "$X11_DIR"
  GIT_TERMINAL_PROMPT=0 git clone --recurse-submodules --shallow-submodules --depth 1 \
    --jobs 1 \
    -b phoshdroid-integration \
    https://github.com/zweck/termux-x11.git "$X11_DIR"
}

refresh_termux_x11_submodules() {
  git -C "$X11_DIR" submodule sync --recursive
  GIT_TERMINAL_PROMPT=0 git -C "$X11_DIR" submodule update --init --recursive --depth 1 --jobs 1
}

mkdir -p "$ROOT/third_party"

if [[ -d "$X11_DIR/.git" ]] && [[ -f "$X11_DIR/app/src/main/java/com/termux/x11/LorieView.java" ]]; then
  echo "==> Reusing existing termux-x11 checkout"
  retry refresh_termux_x11_submodules
else
  echo "==> Cloning termux-x11 (submodules may be slow when gitlab.freedesktop.org is busy)"
  retry clone_termux_x11
fi

if [[ ! -d "$APP_DIR/.git" ]]; then
  retry git clone --depth 1 -b phoshdroid-integration \
    https://github.com/zweck/termux-app.git "$APP_DIR"
fi

bash "$ROOT/scripts/patch-termux-x11-embed.sh"

echo "==> termux-x11 + termux-app ready"
