# Proot Cowork Rootfs Setup

Step-by-step instructions to build a custom proot-distro rootfs that Proot Cowork can import and auto-run.

## Prerequisites

Install on your Android device (from **F-Droid**, not Play Store):

1. [Termux](https://f-droid.org/en/packages/com.termux/)
2. [Termux:X11](https://github.com/termux/termux-x11/releases)
3. Inside Termux: `pkg update && pkg install proot-distro`

## Quick Setup (Ubuntu + XFCE)

Run these scripts in order from Termux:

```bash
# 1. Bootstrap Termux host packages
bash 01-termux-bootstrap.sh

# 2. Install Ubuntu proot-distro
bash 02-install-distro.sh

# 3. Provision guest (user, sudo nopasswd, packages)
bash 03-guest-provision.sh

# 4. Install XFCE desktop
bash 04-xfce-install.sh

# 5. Install agent tools (git, python, node, curl, etc.)
bash 05-agent-tools.sh
```

## Export Rootfs for Proot Cowork

```bash
bash 06-export-rootfs.sh
```

This creates `proot-cowork-rootfs.tar.gz` in your home directory.

**Note:** proot-distro v5+ stores rootfs at `containers/<name>/rootfs/` (not the old `installed-rootfs/` path). The export script auto-detects both layouts.

**Do not use** `proot-distro backup` for Proot Cowork — that wraps files in `ubuntu/rootfs/` subfolders. We need a flat tarball with `start-desktop.sh` at the top level.

Transfer it to your phone storage, then in Proot Cowork tap **Add your rootfs** → Import.

## Required Guest Files

The export must include `start-desktop.sh` at the rootfs root:

```bash
#!/bin/bash
export DISPLAY=:0
export XDG_RUNTIME_DIR=/tmp
dbus-launch --exit-with-session startxfce4
```

And `start-desktop.sh` must be executable (`chmod +x`).

## Proot Cowork Auto-Start Sequence

When you import the tarball, the app will:

1. Extract to `files/rootfs/`
2. Start embedded X11 server
3. Run: `proot -r files/rootfs/ -b /dev -b /proc -b /sys --shared-tmp ...`
4. Execute `/start-desktop.sh` inside guest
5. Display desktop in the 16:9 panel

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `/etc/sudoers.d/cowork: No such file or directory` | Fixed in script v2: installs `sudo` first, then `mkdir -p /etc/sudoers.d`. Re-run `03-guest-provision.sh` |
| `can't sanitize binding /proc/self/fd/0` | Harmless proot warning on Termux; scripts use `-- bash -lc` instead of heredoc stdin |
| `Rootfs not found at .../installed-rootfs/ubuntu` | proot-distro v5 stores rootfs at `containers/ubuntu/rootfs/` — use updated `06-export-rootfs.sh` |
| Black X11 screen | Ensure `--shared-tmp` and `DISPLAY=:0` |
| Desktop won't start | Check `start-desktop.sh` exists and is executable |
| apt errors in guest | Re-run `03-guest-provision.sh` |
| Large tarball | Remove apt cache: `apt clean` before export |
| `unrecognized option: '-lc'` | proot-distro v5.3+ uses `-- bash -c` not `-e bash -lc` — pull latest scripts |
| `proot error: can't chmod .../tmp/proot-*` | Harmless during optional apt clean — pull latest `06-export-rootfs.sh` (skips clean) or export manually with `tar` |

See also: [docs/RESEARCH.md](../docs/RESEARCH.md) section on X11 requirements.
