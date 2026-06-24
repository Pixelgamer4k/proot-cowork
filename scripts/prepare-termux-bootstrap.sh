#!/usr/bin/env bash
# Termux bootstrap: libbash.so in jniLibs + bootstrap.bin asset (gzip tar of prefix + xkb + proot).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNILIBS="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSET="$ROOT/app/src/main/assets/bootstrap.bin"
MARKER="$ROOT/app/src/main/assets/.bootstrap_prepared_v3"
ARCH="aarch64"
BOOTSTRAP_URL="${TERMUX_BOOTSTRAP_URL:-https://github.com/termux/termux-packages/releases/latest/download/bootstrap-${ARCH}.zip}"
XKB_DEB_URL="${TERMUX_XKB_DEB_URL:-https://packages-cf.termux.dev/apt/termux-x11/pool/main/x/xkeyboard-config/xkeyboard-config_2.48_all.deb}"
PROOT_DEB_URL="${TERMUX_PROOT_DEB_URL:-https://packages-cf.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.80_aarch64.deb}"
LIBTALLOC_DEB_URL="${TERMUX_LIBTALLOC_DEB_URL:-https://packages-cf.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb}"
LIBSHMEM_DEB_URL="${TERMUX_LIBSHMEM_DEB_URL:-https://packages-cf.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb}"
PROOT_ASSET="$ROOT/app/src/main/assets/termux-proot.tar.gz"

if [[ -f "$MARKER" && -f "$ASSET" && -f "$JNILIBS/libbash.so" && -f "$PROOT_ASSET" ]]; then
  echo "==> Termux bootstrap already prepared ($MARKER)"
  ls -lh "$ASSET" "$JNILIBS/libbash.so"
  exit 0
fi

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

echo "==> Downloading Termux bootstrap"
curl -fsSL "$BOOTSTRAP_URL" -o "$tmpdir/bootstrap.zip"
unzip -q "$tmpdir/bootstrap.zip" -d "$tmpdir/prefix"

if [[ -f "$tmpdir/prefix/SYMLINKS.txt" ]]; then
  while IFS='←' read -r target linkpath; do
    rm -f "$tmpdir/prefix/$linkpath"
    mkdir -p "$(dirname "$tmpdir/prefix/$linkpath")"
    ln -sf "$target" "$tmpdir/prefix/$linkpath"
  done < "$tmpdir/prefix/SYMLINKS.txt"
  rm -f "$tmpdir/prefix/SYMLINKS.txt"
fi

echo "==> Downloading proot (bind-mount helper for apt/pkg hardcoded paths)"
curl -fsSL "$PROOT_DEB_URL" -o "$tmpdir/proot.deb"
curl -fsSL "$LIBTALLOC_DEB_URL" -o "$tmpdir/libtalloc.deb"
curl -fsSL "$LIBSHMEM_DEB_URL" -o "$tmpdir/libandroid-shmem.deb"
for deb in proot libtalloc libandroid-shmem; do
  mkdir -p "$tmpdir/${deb}-extract"
  ( cd "$tmpdir/${deb}-extract" && ar x "$tmpdir/${deb}.deb" )
  tar -xJf "$tmpdir/${deb}-extract/data.tar.xz" -C "$tmpdir/${deb}-extract"
  cp -a "$tmpdir/${deb}-extract/data/data/com.termux/files/usr/." "$tmpdir/prefix/"
done
mkdir -p "$tmpdir/proot-bundle/bin" "$tmpdir/proot-bundle/lib" "$tmpdir/proot-bundle/libexec"
cp -a "$tmpdir/prefix/bin/proot" "$tmpdir/proot-bundle/bin/"
cp -a "$tmpdir/prefix/lib"/libtalloc.so* "$tmpdir/proot-bundle/lib/" 2>/dev/null || true
cp -a "$tmpdir/prefix/lib"/libandroid-shmem.so* "$tmpdir/proot-bundle/lib/" 2>/dev/null || true
cp -a "$tmpdir/prefix/libexec/proot" "$tmpdir/proot-bundle/libexec/" 2>/dev/null || true
tar -czf "$PROOT_ASSET" -C "$tmpdir/proot-bundle" .

echo "==> Downloading xkeyboard-config for X11 server"
curl -fsSL "$XKB_DEB_URL" -o "$tmpdir/xkeyboard-config.deb"
mkdir -p "$tmpdir/xkb-extract"
( cd "$tmpdir/xkb-extract" && ar x "$tmpdir/xkeyboard-config.deb" )
tar -xJf "$tmpdir/xkb-extract/data.tar.xz" -C "$tmpdir/xkb-extract"
cp -a "$tmpdir/xkb-extract/data/data/com.termux/files/usr/share/xkeyboard-config-2" "$tmpdir/prefix/share/"
mkdir -p "$tmpdir/prefix/share/X11"
ln -sfn ../xkeyboard-config-2 "$tmpdir/prefix/share/X11/xkb"

mkdir -p "$JNILIBS"
bash_src="$tmpdir/prefix/bin/bash"
[[ -L "$bash_src" ]] && bash_src="$(readlink -f "$bash_src")"
cp "$bash_src" "$JNILIBS/libbash.so"
chmod +x "$JNILIBS/libbash.so"
rm -f "$tmpdir/prefix/bin/bash"

mkdir -p "$(dirname "$ASSET")"
tar -czf "$ASSET" -C "$tmpdir/prefix" .
touch "$MARKER"
echo "==> Wrote $ASSET (with XKB + proot), $PROOT_ASSET, and $JNILIBS/libbash.so"
ls -lh "$ASSET" "$PROOT_ASSET" "$JNILIBS/libbash.so"
