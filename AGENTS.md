# Agent instructions (Proot Cowork)

## Builds: GitHub Actions only

Do **not** run Gradle or Android builds on the local machine. Past attempts (`./gradlew assembleDebug`, long `gh run watch`) overloaded the host and crashed the PC.

| Task | Where |
|------|--------|
| Compile APK | [Build Debug APK](.github/workflows/build-debug-apk.yml) on `ubuntu-latest` |
| Get APK | Actions artifact `proot-cowork-debug-apk` |
| Local machine | Edit source, `git push`, optional `gh workflow run` — **no gradlew** |

```bash
gh workflow run build-debug-apk.yml
gh run list --workflow=build-debug-apk.yml --limit 1
# User downloads: gh run download <id> -n proot-cowork-debug-apk
```

## Desktop stack (current)

- **v0.7.0-userland**: Vendored **UserLAnd backend** (`BusyboxExecutor`, `execInProot.sh`, `LocalServerManager`, tightvnc `:51` / port 5951)
- Embedded RFB viewer (no external bVNC); VNC password `userland`
- Rootfs at `files/1/`; guest needs `tightvncserver` + `xfce4` (see `rootfs-setup/04-xfce-install.sh`)
- Runtime jni libs fetched in CI via `scripts/fetch-userland-runtime.sh` from UserLAnd v2.8.3 APK

## Device testing

Only use `adb` when the user explicitly requests it. Prefer read-only debug files under `files/debug/` via `run-as`.
