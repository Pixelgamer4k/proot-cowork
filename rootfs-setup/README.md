# Proot Cowork Rootfs Setup

Build Ubuntu + XFCE on **Termux**, export once, import into **Proot Cowork** (small APK + separate rootfs).

## F-Droid Termux (full workflow)

Use the official **Termux** app from F-Droid (not Play Store). Clone this repo on the phone or copy `rootfs-setup/` to Termux.

```bash
# One-time: install helper commands into Termux $PREFIX/bin
cd ~/Proot-Cowork/rootfs-setup
bash 00-install-termux-scripts.sh

# Bootstrap + Ubuntu + XFCE
bash 01-termux-bootstrap.sh
bash 02-install-distro.sh
proot-xfce-install ubuntu

# Export for Cowork (~2–3 GB tarball)
proot-xfce-export ubuntu
# or: bash 07-export-proot-container.sh
```

`00-install-termux-scripts.sh` installs `proot-xfce-install`, `proot-xfce-export`, and `proot-xfce-start` plus manifest/sysdata templates under `$PREFIX/share/cowork/` — no Cowork APK required for the build step.

Output: `~/proot-cowork-ubuntu.tar.gz`

```bash
# Optional: shrink before export
CLEAN_BEFORE_EXPORT=1 proot-xfce-export ubuntu

# Custom output path
OUTPUT=/sdcard/Download/ubuntu.tar.gz proot-xfce-export ubuntu
```

## Termux stack workflow (short)

Build the desktop in **Termux**, then import into **Proot Cowork**.

```bash
# After 00-install-termux-scripts.sh
pkg install proot-distro   # if not already
proot-distro install ubuntu
proot-xfce-install ubuntu
proot-xfce-export ubuntu
```

This creates `~/proot-cowork-ubuntu.tar.gz` (proot-distro backup format).

**In Proot Cowork:**

1. Install the APK from CI (Termux bootstrap only — no huge rootfs inside).
2. Tap **Import Ubuntu desktop** → choose `proot-cowork-ubuntu.tar.gz`, or copy it to:
   `Android/data/com.proot/files/`
3. In the app terminal:
   ```bash
   proot-xfce-start ubuntu
   ```
4. Tap **Show X11**.

Re-export after you change packages inside the guest; re-import in Cowork.

## Scripts (run from Termux with Cowork repo or copied scripts)

| Script | Purpose |
|--------|---------|
| `00-install-termux-scripts.sh` | Install `proot-xfce-*` commands on F-Droid Termux |
| `01-termux-bootstrap.sh` | pkg + proot-distro in Termux |
| `02-install-distro.sh` | `proot-distro install ubuntu` |
| `03-guest-provision.sh` | user + sudo (optional) |
| `04-xfce-x11.sh` | XFCE + Mesa for embedded X11 |
| `07-export-proot-container.sh` | Export wrapper → `proot-xfce-export` |
| `proot-xfce-export.sh` | Canonical export script (also bundled in Cowork APK) |

## Legacy UserLAnd VNC path

```bash
bash 04-xfce-install.sh   # xfce4 + tightvncserver
bash 06-export-rootfs.sh    # proot-cowork-rootfs.tar.gz → files/1/
```

## Import formats (Cowork Termux stack)

| Archive layout | Supported |
|----------------|-----------|
| `ubuntu/manifest.json` + `ubuntu/rootfs/` | Yes (proot-distro backup) |
| Flat rootfs (`usr/bin/bash` at top level) | Yes (wrapped as ubuntu) |

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Import fails validation | Run `proot-xfce-install ubuntu` before export |
| Black desktop / missing icons | Re-run `proot-xfce-install` in guest, re-export |
| `proot-distro list` empty but login works | Normal after import; use `proot-xfce-start ubuntu` |

See [third_party/USERLAND-NOTICE.md](../third_party/USERLAND-NOTICE.md) for the legacy VNC backend.
