# Proot Cowork Rootfs Setup

Build Ubuntu + XFCE on **Termux**, export once, import into **Proot Cowork** (small APK + separate rootfs).

## Termux stack workflow (recommended)

Build the desktop in the official **Termux** app (F-Droid), then import into Cowork.

```bash
# In Termux (not Cowork yet)
pkg install proot-distro
proot-distro install ubuntu
proot-xfce-install ubuntu    # from Cowork scripts, or run 04-xfce-x11.sh steps manually
bash 07-export-proot-container.sh
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
| `01-termux-bootstrap.sh` | pkg + proot-distro in Termux |
| `02-install-distro.sh` | `proot-distro install ubuntu` |
| `03-guest-provision.sh` | user + sudo (optional) |
| `04-xfce-x11.sh` | XFCE + Mesa for embedded X11 |
| `07-export-proot-container.sh` | Export for Cowork import |

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
