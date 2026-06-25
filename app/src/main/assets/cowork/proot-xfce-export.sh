#!/usr/bin/env bash
# Export Ubuntu + XFCE proot-distro container for Proot Cowork import.
# Works in F-Droid Termux and Cowork embedded Termux.
# Output: proot-cowork-ubuntu.tar.gz  (ubuntu/manifest.json + sysdata + rootfs/)
set -euo pipefail

script_dir() {
  local source="${BASH_SOURCE[0]}"
  while [[ -L "$source" ]]; do
    local dir
    dir="$(cd "$(dirname "$source")" && pwd)"
    source="$(readlink "$source")"
    [[ "$source" != /* ]] && source="$dir/$source"
  done
  cd "$(dirname "$source")" && pwd
}

SCRIPT_DIR="$(script_dir)"
DISTRO="${1:-${DISTRO:-ubuntu}}"
OUTPUT="${OUTPUT:-${HOME:-/root}/proot-cowork-ubuntu.tar.gz}"

# F-Droid Termux default; Cowork sets PREFIX to its embedded prefix.
if [[ -z "${PREFIX:-}" ]]; then
  if [[ -d "${HOME:-}/../usr" && -x "${HOME:-}/../usr/bin/pkg" ]]; then
    PREFIX="$(cd "${HOME}/../usr" && pwd)"
  else
    PREFIX="/data/data/com.termux/files/usr"
  fi
fi

RUNTIME_DIR="$PREFIX/var/lib/proot-distro"
CONTAINER_DIR="$RUNTIME_DIR/containers/$DISTRO"
LEGACY_ROOTFS="$RUNTIME_DIR/installed-rootfs/$DISTRO"
COWORK_SHARE="$PREFIX/share/cowork"

usage() {
  cat <<EOF
Usage: proot-xfce-export [distro]

Export Ubuntu + XFCE from F-Droid Termux for Proot Cowork import.

  distro   Container name (default: ubuntu)
  OUTPUT   Output path (default: ~/proot-cowork-ubuntu.tar.gz)

Environment:
  CLEAN_BEFORE_EXPORT=1   apt clean inside guest before packing
  SKIP_VALIDATION=1       skip XFCE checks

F-Droid Termux setup (once):
  git clone https://github.com/Pixelgamer4k/Proot-Cowork.git
  cd Proot-Cowork/rootfs-setup
  bash 00-install-termux-scripts.sh
  bash 01-termux-bootstrap.sh && bash 02-install-distro.sh
  proot-xfce-install ubuntu && proot-xfce-export ubuntu

Import in Cowork:
  Android/data/com.proot/files/proot-cowork-ubuntu.tar.gz
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! command -v tar >/dev/null; then
  echo "ERROR: tar not found (pkg install tar)" >&2
  exit 1
fi

resolve_rootfs_dir() {
  if [[ -f "$CONTAINER_DIR/rootfs/usr/bin/bash" ]]; then
    echo "$CONTAINER_DIR/rootfs"
    return 0
  fi
  if [[ -f "$LEGACY_ROOTFS/usr/bin/bash" ]]; then
    echo "$LEGACY_ROOTFS"
    return 0
  fi
  return 1
}

find_manifest_template() {
  local candidate
  for candidate in \
    "$CONTAINER_DIR/manifest.json" \
    "$COWORK_SHARE/ubuntu-container-manifest.json" \
    "$COWORK_SHARE/templates/ubuntu/manifest.json" \
    "$SCRIPT_DIR/templates/ubuntu/manifest.json" \
    "$SCRIPT_DIR/../rootfs-setup/templates/ubuntu/manifest.json"; do
    if [[ -f "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

find_sysdata_dir() {
  local candidate
  for candidate in \
    "$CONTAINER_DIR/sysdata" \
    "$COWORK_SHARE/ubuntu-sysdata" \
    "$COWORK_SHARE/templates/ubuntu/sysdata" \
    "$SCRIPT_DIR/templates/ubuntu/sysdata" \
    "$SCRIPT_DIR/../rootfs-setup/templates/ubuntu/sysdata"; do
    if [[ -d "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

write_embedded_manifest() {
  local dest="$1"
  cat >"$dest/manifest.json" <<'EOF'
{
  "image_ref": "ubuntu",
  "arch": "aarch64",
  "bundled_by": "proot-cowork-termux-export",
  "manifest": {
    "schemaVersion": 2,
    "mediaType": "application/vnd.oci.image.manifest.v1+json",
    "annotations": {
      "org.opencontainers.image.title": "ubuntu",
      "org.opencontainers.image.description": "Exported Ubuntu + XFCE for Proot Cowork",
      "org.opencontainers.image.version": "exported-xfce"
    }
  },
  "image_config": {
    "architecture": "arm64",
    "os": "linux",
    "config": {
      "Env": ["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],
      "Cmd": ["/bin/bash"]
    },
    "rootfs": { "type": "layers", "diff_ids": [] }
  }
}
EOF
}

ROOTFS_DIR="$(resolve_rootfs_dir || true)"
if [[ -z "$ROOTFS_DIR" ]]; then
  echo "ERROR: $DISTRO container not found. Tried:" >&2
  echo "  $CONTAINER_DIR/rootfs" >&2
  echo "  $LEGACY_ROOTFS" >&2
  echo "" >&2
  echo "F-Droid Termux:" >&2
  echo "  proot-distro install $DISTRO" >&2
  echo "  proot-xfce-install $DISTRO" >&2
  echo "" >&2
  echo "Install helpers: bash rootfs-setup/00-install-termux-scripts.sh" >&2
  proot-distro list 2>/dev/null || ls -la "$RUNTIME_DIR/containers/" 2>/dev/null || true
  exit 1
fi

if [[ "${SKIP_VALIDATION:-0}" != "1" ]]; then
  if [[ ! -f "$ROOTFS_DIR/usr/bin/xfce4-session" && ! -f "$ROOTFS_DIR/usr/bin/startxfce4" ]]; then
    echo "ERROR: XFCE not installed in $DISTRO." >&2
    echo "       Run: proot-xfce-install $DISTRO" >&2
    exit 1
  fi
fi

if [[ "${CLEAN_BEFORE_EXPORT:-0}" == "1" ]] && command -v proot-distro >/dev/null; then
  echo "==> Cleaning apt cache inside guest..."
  set +e
  proot-distro login "$DISTRO" --shared-tmp -- bash -lc \
    "apt-get clean 2>/dev/null || apt clean 2>/dev/null || true"
  set -e
fi

copy_tree() {
  local src="$1" dest="$2"
  local -a ex=(
    --exclude='var/lib/snapd' --exclude='snap'
    --exclude='proc' --exclude='sys' --exclude='dev' --exclude='run' --exclude='tmp'
    --exclude='mnt' --exclude='data' --exclude='storage' --exclude='sdcard'
    --exclude='apex' --exclude='odm' --exclude='product' --exclude='system'
    --exclude='system_ext' --exclude='vendor' --exclude='linkerconfig'
  )
  if command -v rsync >/dev/null; then
    rsync -aHAXx "${ex[@]}" "$src/" "$dest/"
  else
    rm -rf "$dest"
    mkdir -p "$dest"
    (cd "$src" && tar -cf - "${ex[@]}" .) | (cd "$dest" && tar -xf -)
  fi
}

install_manifest() {
  local dest="$1"
  [[ -f "$dest/manifest.json" ]] && return 0
  local template
  if template="$(find_manifest_template)"; then
    cp "$template" "$dest/manifest.json"
    return 0
  fi
  echo "==> Using embedded manifest template"
  write_embedded_manifest "$dest"
}

install_sysdata() {
  local dest="$1"
  mkdir -p "$dest/sysdata/sys_empty"
  local template_dir
  if template_dir="$(find_sysdata_dir)"; then
    for entry in "$template_dir"/*; do
      [[ -e "$entry" ]] || continue
      local name
      name="$(basename "$entry")"
      [[ -e "$dest/sysdata/$name" ]] && continue
      cp -a "$entry" "$dest/sysdata/$name"
    done
    return 0
  fi
  echo "WARNING: sysdata stubs missing; Cowork will add them on import" >&2
}

try_proot_distro_backup() {
  command -v proot-distro >/dev/null || return 1
  proot-distro backup --help >/dev/null 2>&1 || return 1

  echo "==> Exporting via proot-distro backup"
  rm -f "$OUTPUT"
  set +e
  if proot-distro backup --help 2>/dev/null | grep -q '\-o'; then
    proot-distro backup "$DISTRO" -o "$OUTPUT"
  else
    proot-distro backup "$DISTRO" "$OUTPUT"
  fi
  local status=$?
  set -e
  [[ $status -eq 0 && -f "$OUTPUT" ]]
}

manual_backup() {
  echo "==> Packing proot-distro layout (manifest + sysdata + rootfs/)"
  local workdir
  workdir="$(mktemp -d)"
  trap 'rm -rf "$workdir"' RETURN

  local stage="$workdir/$DISTRO"
  mkdir -p "$stage/rootfs"

  if [[ "$ROOTFS_DIR" == "$CONTAINER_DIR/rootfs" ]]; then
    copy_tree "$CONTAINER_DIR/rootfs" "$stage/rootfs"
    [[ -f "$CONTAINER_DIR/manifest.json" ]] && cp "$CONTAINER_DIR/manifest.json" "$stage/manifest.json"
    [[ -d "$CONTAINER_DIR/sysdata" ]] && copy_tree "$CONTAINER_DIR/sysdata" "$stage/sysdata"
  else
    copy_tree "$ROOTFS_DIR" "$stage/rootfs"
  fi

  install_manifest "$stage"
  install_sysdata "$stage"

  rm -f "$OUTPUT"
  tar -C "$workdir" -czf "$OUTPUT" \
    --exclude="${DISTRO}/rootfs/var/lib/snapd" \
    --exclude="${DISTRO}/rootfs/snap" \
    --exclude="${DISTRO}/rootfs/proc" \
    --exclude="${DISTRO}/rootfs/sys" \
    --exclude="${DISTRO}/rootfs/dev" \
    --exclude="${DISTRO}/rootfs/run" \
    --exclude="${DISTRO}/rootfs/tmp" \
    "$DISTRO"
}

verify_archive() {
  tar -tzf "$OUTPUT" | grep -q "${DISTRO}/rootfs/usr/bin/bash" || {
    echo "ERROR: archive missing ${DISTRO}/rootfs/usr/bin/bash" >&2
    exit 1
  }
  if ! tar -tzf "$OUTPUT" | grep -q "${DISTRO}/manifest.json"; then
    echo "WARNING: archive missing manifest.json" >&2
  fi
  if ! tar -tzf "$OUTPUT" | grep -qE "${DISTRO}/rootfs/usr/bin/(xfce4-session|startxfce4)"; then
    echo "ERROR: archive missing XFCE session binary" >&2
    exit 1
  fi
}

echo "==> Exporting $DISTRO for Proot Cowork"
echo "    Termux PREFIX: $PREFIX"
echo "    Rootfs: $ROOTFS_DIR"
echo "    Output: $OUTPUT"

if try_proot_distro_backup; then
  if ! tar -tzf "$OUTPUT" | grep -q "${DISTRO}/rootfs/usr/bin/bash"; then
    echo "==> proot-distro backup layout unexpected; repacking manually"
    rm -f "$OUTPUT"
    manual_backup
  fi
else
  echo "==> proot-distro backup unavailable; using manual packer"
  manual_backup
fi

verify_archive
SIZE="$(du -h "$OUTPUT" | cut -f1)"
echo "==> Exported: $OUTPUT ($SIZE)"
echo "==> Transfer to Cowork:"
echo "    adb push \"$OUTPUT\" /sdcard/Download/"
echo "    or copy to Android/data/com.proot/files/proot-cowork-ubuntu.tar.gz"
echo "==> In Cowork: Import Ubuntu desktop → proot-xfce-start $DISTRO → Show X11"
