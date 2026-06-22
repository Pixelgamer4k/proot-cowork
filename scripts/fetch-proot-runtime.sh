#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/runtime/aarch64"
JNILIBS="$ROOT/app/src/main/jniLibs/arm64-v8a"

if [[ -f "$JNILIBS/libproot_exec.so" && -f "$JNILIBS/libtalloc.so.2" ]]; then
  echo "==> proot runtime already present in jniLibs"
  exit 0
fi

mkdir -p "$ASSETS/bin" "$ASSETS/lib" "$JNILIBS"
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

curl -fsSL "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.80_aarch64.deb" -o "$tmpdir/proot.deb"
curl -fsSL "https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb" -o "$tmpdir/libtalloc.deb"
curl -fsSL "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb" -o "$tmpdir/libshmem.deb"

dpkg-deb -x "$tmpdir/proot.deb" "$tmpdir/pe"
dpkg-deb -x "$tmpdir/libtalloc.deb" "$tmpdir/le"
dpkg-deb -x "$tmpdir/libshmem.deb" "$tmpdir/se"

proot_src="$tmpdir/pe/data/data/com.termux/files/usr/bin/proot"
talloc_src="$tmpdir/le/data/data/com.termux/files/usr/lib/libtalloc.so.2.4.3"
shmem_src="$tmpdir/se/data/data/com.termux/files/usr/lib/libandroid-shmem.so"

# Legacy assets path (unused at runtime; kept for reference tooling).
cp "$proot_src" "$ASSETS/bin/proot"
chmod +x "$ASSETS/bin/proot"
cp -P "$tmpdir/le/data/data/com.termux/files/usr/lib/libtalloc.so"* "$ASSETS/lib/"
cp "$shmem_src" "$ASSETS/lib/"

# Android 10+ W^X: execute from nativeLibraryDir via linker64.
cp "$proot_src" "$JNILIBS/libproot_exec.so"
cp "$talloc_src" "$JNILIBS/libtalloc.so"
cp "$talloc_src" "$JNILIBS/libtalloc.so.2"
cp "$shmem_src" "$JNILIBS/libandroid-shmem.so"

echo "==> Bundled proot runtime to jniLibs/arm64-v8a"
