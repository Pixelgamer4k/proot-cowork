#!/usr/bin/env bash
# Extract UserLAnd v2.8.3 arm64 runtime (proot, busybox, support scripts) from the official APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNILIBS="$ROOT/app/src/main/jniLibs/arm64-v8a"
GUEST="$ROOT/app/src/main/assets/userland/guest-support"
ULA_APK_URL="${ULA_APK_URL:-https://github.com/CypherpunkArmory/UserLAnd/releases/download/v2.8.3/app-release.apk}"
ULA_TAG="${ULA_TAG:-v2.8.3}"

if [[ -f "$JNILIBS/lib_proot.so" && -f "$JNILIBS/lib_busybox.so" && -f "$JNILIBS/lib_execInProot.sh.so" ]] \
  && [[ ! -f "$JNILIBS/libproot_exec.so" ]]; then
  echo "==> UserLAnd runtime already present in jniLibs"
else
  mkdir -p "$JNILIBS"
  rm -f "$JNILIBS"/*
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT
  echo "==> Downloading UserLAnd $ULA_TAG APK"
  curl -fsSL "$ULA_APK_URL" -o "$tmpdir/userland.apk"
  unzip -qo "$tmpdir/userland.apk" 'lib/arm64-v8a/*' -d "$tmpdir"
  cp -a "$tmpdir/lib/arm64-v8a/." "$JNILIBS/"
  echo "==> Extracted UserLAnd jniLibs to $JNILIBS"
fi

mkdir -p "$GUEST"
UBUNTU_ASSETS="https://raw.githubusercontent.com/CypherpunkArmory/UserLAnd-Assets-Ubuntu/master/assets/all"
for name in startVNCServer.sh startVNCServerStep2.sh ld.so.preload nosudo userland_profile.sh; do
  dest="$GUEST/$name"
  if [[ ! -f "$dest" ]]; then
    curl -fsSL "$UBUNTU_ASSETS/$name" -o "$dest"
    echo "==> Fetched guest-support/$name"
  fi
done

UBUNTU_ARM64="https://raw.githubusercontent.com/CypherpunkArmory/UserLAnd-Assets-Ubuntu/master/assets/arm64"
for name in libdisableselinux.so; do
  dest="$GUEST/$name"
  if [[ ! -f "$dest" ]]; then
    curl -fsSL "$UBUNTU_ARM64/$name" -o "$dest"
    echo "==> Fetched guest-support/$name"
  fi
done

echo "==> UserLAnd runtime fetch complete (BSD-2-Clause — see third_party/USERLAND-NOTICE.md)"
