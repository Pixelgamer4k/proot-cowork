# Proot Cowork Rootfs Setup

Build a Ubuntu proot-distro rootfs for Proot Cowork **v0.7+** (UserLAnd backend).

## Prerequisites

Termux from **F-Droid** + `proot-distro`. Termux:X11 is **not** required.

## Quick setup

```bash
bash 01-termux-bootstrap.sh
bash 02-install-distro.sh
bash 03-guest-provision.sh
bash 04-xfce-install.sh   # xfce4 + tightvncserver + expect
bash 05-agent-tools.sh    # optional
bash 06-export-rootfs.sh
```

Export `proot-cowork-rootfs.tar.gz` and import in the app.

## What the app does (UserLAnd fork)

1. Extract rootfs to `files/1/` (UserLAnd filesystem layout)
2. Inject UserLAnd guest `support/` scripts (`startVNCServer.sh`, etc.)
3. Run **UserLAnd's** `execInProot.sh` + `BusyboxExecutor` + `LocalServerManager`
4. Guest runs **tightvncserver :51** (port **5951**) with XFCE
5. Embedded VNC viewer connects with password `userland`

## Required guest packages

- `xfce4`, `dbus-x11`
- `tightvncserver`, `expect` (VNC password setup)
- User `cowork` (from `03-guest-provision.sh`)

Script `04-xfce-install.sh` installs these and configures `~cowork/.vnc/xstartup`.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Missing tightvncserver | Re-run `04-xfce-install.sh` |
| VNC auth failed | Password is `userland` (UserLAnd default) |
| UserLAnd runtime missing | Reinstall APK from CI (includes UserLAnd jni libs) |
| Old rootfs at `files/rootfs` | App auto-migrates to `files/1` on startup |

See [third_party/USERLAND-NOTICE.md](../third_party/USERLAND-NOTICE.md).
