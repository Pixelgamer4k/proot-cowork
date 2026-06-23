#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a/libcowork_linkshim.so"
SRC="$ROOT/native/linkshim/linkshim.c"

mkdir -p "$(dirname "$OUT")"

if command -v aarch64-linux-gnu-gcc >/dev/null 2>&1; then
  CC=aarch64-linux-gnu-gcc
elif [[ -n "${ANDROID_NDK_HOME:-}" && -x "${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang" ]]; then
  CC="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang"
else
  echo "No aarch64 cross compiler found (need gcc-aarch64-linux-gnu or ANDROID_NDK_HOME)" >&2
  exit 1
fi

"$CC" -shared -fPIC -O2 -o "$OUT" "$SRC" -ldl
echo "==> Built $OUT"
