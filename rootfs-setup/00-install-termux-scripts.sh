#!/data/data/com.termux/files/usr/bin/bash
# Install Proot Cowork helper scripts into F-Droid Termux ($PREFIX/bin).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSET_DIR="$REPO_ROOT/app/src/main/assets/cowork"
SHARE_DIR="${PREFIX:-/data/data/com.termux/files/usr}/share/cowork"
BIN_DIR="${PREFIX:-/data/data/com.termux/files/usr}/bin"

install_script() {
  local name="$1"
  local src=""
  for candidate in \
    "$SCRIPT_DIR/$name" \
    "$ASSET_DIR/$name" \
    "$ASSET_DIR/${name%.sh}.sh"; do
    if [[ -f "$candidate" ]]; then
      src="$candidate"
      break
    fi
  done
  if [[ -z "$src" ]]; then
    echo "ERROR: missing $name (expected in rootfs-setup/ or app assets)" >&2
    exit 1
  fi
  local dest="$SHARE_DIR/$(basename "$src")"
  mkdir -p "$SHARE_DIR" "$BIN_DIR"
  cp "$src" "$dest"
  chmod 700 "$dest"
  local link_name="${name%.sh}"
  rm -f "$BIN_DIR/$link_name"
  ln -sf "$dest" "$BIN_DIR/$link_name"
  echo "  $link_name -> $dest"
}

install_templates() {
  local template_src="$SCRIPT_DIR/templates/ubuntu"
  local template_dest="$SHARE_DIR/templates/ubuntu"
  if [[ ! -d "$template_src" ]]; then
    echo "WARNING: templates missing at $template_src" >&2
    return 0
  fi
  mkdir -p "$template_dest/sysdata"
  cp -f "$template_src/manifest.json" "$template_dest/manifest.json"
  cp -a "$template_src/sysdata/." "$template_dest/sysdata/"
  cp -f "$template_src/manifest.json" "$SHARE_DIR/ubuntu-container-manifest.json"
  mkdir -p "$SHARE_DIR/ubuntu-sysdata"
  cp -a "$template_src/sysdata/." "$SHARE_DIR/ubuntu-sysdata/"
  echo "  templates -> $template_dest"
}

echo "==> Installing Proot Cowork scripts for F-Droid Termux"
echo "    PREFIX=${PREFIX:-/data/data/com.termux/files/usr}"
install_script "proot-xfce-install.sh"
install_script "proot-xfce-export.sh"
install_script "proot-xfce-start.sh"
install_templates
echo "==> Done. Commands available:"
echo "    proot-xfce-install ubuntu"
echo "    proot-xfce-export ubuntu"
echo "    proot-xfce-start ubuntu   # in Cowork after import"
