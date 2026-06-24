#!/usr/bin/env bash
# Merge Termux .debs into bootstrap prefix at CI build time (no dpkg on device).
set -euo pipefail

PREFIX="${1:?prefix dir}"
MAIN_PKG="${2:-/tmp/termux-main-Packages}"
X11_PKG="${3:-/tmp/termux-x11-Packages}"
MAIN_BASE="${TERMUX_MAIN_APT:-https://packages-cf.termux.dev/apt/termux-main}"
X11_BASE="${TERMUX_X11_APT:-https://packages-cf.termux.dev/apt/termux-x11}"

if [[ ! -f "$MAIN_PKG" ]]; then
  curl -fsSL "$MAIN_BASE/dists/stable/main/binary-aarch64/Packages" -o "$MAIN_PKG"
fi
if [[ ! -f "$X11_PKG" ]]; then
  curl -fsSL "$X11_BASE/dists/x11/main/binary-aarch64/Packages" -o "$X11_PKG"
fi

declare -A SEEN=()
declare -a QUEUE=()
declare -a ORDER=()

queue_add() {
  local p
  for p in "$@"; do
    [[ -z "$p" ]] && continue
    [[ -n "${SEEN[$p]+x}" ]] && continue
    SEEN[$p]=1
    QUEUE+=("$p")
  done
}

field_for() {
  local pkg=$1 field=$2 file=$3
  awk -v pkg="$pkg" -v field="$field" '
    $1 == "Package:" && $2 == pkg { found=1 }
    found && $1 == field":" { sub(/^[^:]*: */, ""); print; exit }
    found && $1 == "Package:" && $2 != pkg { exit }
  ' "$file"
}

repo_for() {
  local pkg=$1
  local hit
  hit=$(field_for "$pkg" Package "$X11_PKG")
  if [[ -n "$hit" ]]; then
    echo x11
  else
    echo main
  fi
}

download_deb() {
  local pkg=$1 repo
  repo=$(repo_for "$pkg")
  local file base
  if [[ "$repo" == x11 ]]; then
    file=$(field_for "$pkg" Filename "$X11_PKG")
    base=$X11_BASE
  else
    file=$(field_for "$pkg" Filename "$MAIN_PKG")
    base=$MAIN_BASE
  fi
  [[ -n "$file" ]] || { echo "missing package: $pkg" >&2; return 1; }
  local out="$DL_DIR/$(basename "$file")"
  if [[ ! -f "$out" ]]; then
    curl -fsSL "$base/$file" -o "$out"
  fi
  echo "$out"
}

extract_deb() {
  local deb=$1
  local work="$EXTRACT_DIR/work"
  rm -rf "$work"
  mkdir -p "$work"
  ( cd "$work" && ar x "$deb" && tar -xJf data.tar.xz )
  if [[ -d "$work/data/data/com.termux/files/usr" ]]; then
    cp -a --update=none "$work/data/data/com.termux/files/usr/." "$PREFIX/"
  fi
  rm -rf "$work"
}

resolve_deps() {
  local pkg=$1 repo=$2
  local depends
  if [[ "$repo" == x11 ]]; then
    depends=$(field_for "$pkg" Depends "$X11_PKG")
  else
    depends=$(field_for "$pkg" Depends "$MAIN_PKG")
  fi
  [[ -z "$depends" ]] && return
  local tok
  IFS=',' read -ra parts <<< "$depends"
  for dep in "${parts[@]}"; do
    dep="${dep//|*/}"
    dep="${dep// /}"
    dep="${dep%%(*}"
    [[ -z "$dep" ]] && continue
    queue_add "$dep"
  done
}

DL_DIR="$(mktemp -d)"
EXTRACT_DIR="$(mktemp -d)"
trap 'rm -rf "$DL_DIR" "$EXTRACT_DIR"' EXIT

# pkg/apt essentials + X11 clients + proot-distro toolchain.
queue_add \
  termux-keyring resolv-conf openssl libc++ libandroid-glob \
  x11-repo xorg-xsetroot aterm \
  proot-distro wget curl unzip gnupg ca-certificates pulseaudio dbus xorg-xrandr

while ((${#QUEUE[@]} > 0)); do
  pkg="${QUEUE[0]}"
  QUEUE=("${QUEUE[@]:1}")
  repo=$(repo_for "$pkg")
  resolve_deps "$pkg" "$repo"
  ORDER+=("$pkg")
done

echo "==> Bundling ${#ORDER[@]} packages into bootstrap"
for pkg in "${ORDER[@]}"; do
  echo "  + $pkg"
  deb=$(download_deb "$pkg")
  extract_deb "$deb"
done

# Ensure x11 mirror list exists even if x11-repo deb only dropped metadata.
mkdir -p "$PREFIX/etc/apt/sources.list.d"
if [[ ! -f "$PREFIX/etc/apt/sources.list.d/x11.list" ]]; then
  cat > "$PREFIX/etc/apt/sources.list.d/x11.list" <<EOF
# Proot-Cowork bundled x11-repo
deb ${X11_BASE}/ x11 main
EOF
fi

echo "==> Bundled X clients:"
ls -la "$PREFIX/bin/aterm" "$PREFIX/bin/xsetroot" 2>/dev/null || true
