# Proot Cowork

Android-native AI cowork agent with embedded proot Linux desktop. Inspired by Kimi Work, Claude Cowork, and Hermes Agent.

## Features (Phased)

| Phase | Status | Features |
|-------|--------|----------|
| 1 | ✅ Done | Compose UI shell, 16:9 desktop placeholder, settings, agent chat UI |
| 2 | ✅ Current | Proot + X11 desktop, rootfs import, power controls |
| 3 | Planned | Koog agent, OpenAI-compatible API, plan/direct modes |
| 4 | Planned | Agent swarm, skills (SKILL.md), self-improvement |
| 5 | Planned | Schedule mode, file browser, external terminal |
| 6 | Planned | Polish, testing, release |

## Build

**v0.5.0-termux** embeds **Termux:X11** (native X11 on `:0`) and optional **Termux bootstrap** (`files/usr`). Desktop uses `startxfce4` like Termux — not the VNC/Xvfb path.

```bash
# One-time: clone & patch termux-x11 native module
bash scripts/setup-x11-module.sh

# Optional (~100MB): bundle full Termux prefix in APK
bash scripts/fetch-termux-bootstrap.sh

# Local build needs a JDK (not JRE), e.g. Temurin 17:
export JAVA_HOME="$HOME/.local/jdks/temurin-17"   # after installing a JDK
./gradlew assembleDebug
```

Debug APKs are built via GitHub Actions on every push to `main`. First CI build with termux-x11 may take ~30–60 minutes.

```bash
# Trigger manually
gh workflow run build-debug-apk.yml

# Download latest artifact
gh run list --workflow=build-debug-apk.yml --limit 1
gh run download <run-id> -n proot-cowork-debug-apk
```

Local build (requires Android SDK):

```bash
./gradlew assembleDebug
```

## Configuration

1. Install the debug APK on your **ARM64** Android device
2. Open app → tap **Add your rootfs** → select `proot-cowork-rootfs.tar.gz`
3. Wait for import + auto-start (X11 + proot desktop in the top panel)
4. Open **Settings** (gear) → set API URL, key, and model for Phase 3 agents

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/RESEARCH.md](docs/RESEARCH.md).

## Rootfs Setup

Build a custom proot-distro rootfs on Termux using scripts in `rootfs-setup/`, then export and import into the app. No extra commands needed after import — the desktop auto-starts.

## License

Apache 2.0
