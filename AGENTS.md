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

- **v0.6.0-vnc**: UserLAnd-style — guest `Xvfb` + `x11vnc` + `startxfce4`, embedded VNC viewer in app
- No Termux:X11, no kernel spoofing, no guest-bin shims in the start path

## Device testing

Only use `adb` when the user explicitly requests it. Prefer read-only debug files under `files/debug/` via `run-as`.
