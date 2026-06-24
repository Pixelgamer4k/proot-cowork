#!/usr/bin/env bash
# Patch termux-x11 for in-process embed without launching MainActivity.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LORIE="$ROOT/third_party/termux-x11/app/src/main/java/com/termux/x11/LorieView.java"
TOUCH="$ROOT/third_party/termux-x11/app/src/main/java/com/termux/x11/input/TouchInputHandler.java"
CMD="$ROOT/third_party/termux-x11/app/src/main/cpp/lorie/cmdentrypoint.c"
CMDJAVA="$ROOT/third_party/termux-x11/app/src/main/java/com/termux/x11/CmdEntryPoint.java"

python3 - "$LORIE" "$TOUCH" "$CMD" "$CMDJAVA" <<'PY'
import sys
from pathlib import Path

def patch_lorie(path: Path) -> None:
    text = path.read_text()
    marker = "COWORK_EMBED_PATCH"
    if marker in text:
        return

    old = """        Rect r = getHolder().getSurfaceFrame();
        MainActivity.getInstance().runOnUiThread(() -> mSurfaceCallback.surfaceChanged(getHolder(), PixelFormat.BGRA_8888, r.width(), r.height()));
    }"""
    new = """        Rect r = getHolder().getSurfaceFrame();
        Runnable work = () -> mSurfaceCallback.surfaceChanged(getHolder(), PixelFormat.BGRA_8888, r.width(), r.height());
        MainActivity activity = MainActivity.getInstance();
        if (activity != null) {
            activity.runOnUiThread(work);
        } else {
            post(work);
        }
    }"""
    if old not in text:
        raise SystemExit(f"patch target missing in {path}")
    text = text.replace(old, new)

    old = """    void getDimensionsFromSettings() {
        Prefs prefs = MainActivity.getPrefs();"""
    new = """    void getDimensionsFromSettings() {
        Prefs prefs = MainActivity.getPrefs();
        if (prefs == null) {
            p.set(Math.max(getMeasuredWidth(), 1), Math.max(getMeasuredHeight(), 1));
            return;
        }"""
    if old not in text:
        raise SystemExit(f"getDimensionsFromSettings patch target missing in {path}")
    text = text.replace(old, new)

    old = """        Prefs prefs = MainActivity.getPrefs();
        if (prefs.displayStretch.get()"""
    new = """        Prefs prefs = MainActivity.getPrefs();
        if (prefs == null) {
            getHolder().setSizeFromLayout();
            return;
        }
        if (prefs.displayStretch.get()"""
    if old not in text:
        raise SystemExit(f"onMeasure patch target missing in {path}")
    text = text.replace(old, new)

    old = """        return MainActivity.getInstance().handleKey(event);
    }"""
    new = """        MainActivity activity = MainActivity.getInstance();
        if (activity == null) {
            return false;
        }
        return activity.handleKey(event);
    }"""
    if old not in text:
        raise SystemExit(f"dispatchKeyEventPreIme patch target missing in {path}")
    text = text.replace(old, new)

    old = """        if (MainActivity.getPrefs().enforceCharBasedInput.get())"""
    new = """        Prefs prefs = MainActivity.getPrefs();
        if (prefs != null && prefs.enforceCharBasedInput.get())"""
    if old not in text:
        raise SystemExit(f"onCreateInputConnection patch target missing in {path}")
    text = text.replace(old, new)

    text = text.replace(
        "public class LorieView extends SurfaceView implements InputStub {",
        "public class LorieView extends SurfaceView implements InputStub { // " + marker,
    )
    path.write_text(text)

def patch_touch(path: Path) -> None:
    text = path.read_text()
    marker = "COWORK_EMBED_PATCH"
    if marker in text:
        return

    old = """        LorieView.requestStylusEnabled(stylusAvailable.get());
        MainActivity.getInstance().setExternalKeyboardConnected(externalKeyboardAvailable.get());
    }"""
    new = """        LorieView.requestStylusEnabled(stylusAvailable.get());
        MainActivity activity = MainActivity.getInstance();
        if (activity != null) {
            activity.setExternalKeyboardConnected(externalKeyboardAvailable.get());
        }
    } // COWORK_EMBED_PATCH"""
    if old not in text:
        raise SystemExit(f"refreshInputDevices patch target missing in {path}")
    text = text.replace(old, new)
    path.write_text(text)

def patch_cmd(path: Path) -> None:
    text = path.read_text()
    marker = "COWORK_EMBED_PATCH"
    if marker in text:
        return

    old = """    if (!getenv("XKB_CONFIG_ROOT")) {
        // chroot case
        const char *root_dir = dirname(getenv("TMPDIR"));
        char current_path[1024] = {0};
        snprintf(current_path, sizeof(current_path), "%s/usr/share/X11/xkb", root_dir);
        if (access(current_path, F_OK) == 0)
            setenv("XKB_CONFIG_ROOT", current_path, 1);
    }"""
    new = """    if (!getenv("XKB_CONFIG_ROOT")) {
        // embedded Termux prefix (PREFIX=/files/usr)
        const char *prefix = getenv("PREFIX");
        if (prefix) {
            char current_path[1024] = {0};
            snprintf(current_path, sizeof(current_path), "%s/share/xkeyboard-config-2", prefix);
            if (access(current_path, F_OK) == 0)
                setenv("XKB_CONFIG_ROOT", current_path, 1);
            else {
                snprintf(current_path, sizeof(current_path), "%s/share/X11/xkb", prefix);
                if (access(current_path, F_OK) == 0)
                    setenv("XKB_CONFIG_ROOT", current_path, 1);
            }
        }
    }

    if (!getenv("XKB_CONFIG_ROOT")) {
        // chroot case (TMPDIR=<prefix>/tmp)
        const char *root_dir = dirname(getenv("TMPDIR"));
        char current_path[1024] = {0};
        snprintf(current_path, sizeof(current_path), "%s/share/X11/xkb", root_dir);
        if (access(current_path, F_OK) == 0)
            setenv("XKB_CONFIG_ROOT", current_path, 1);
    } // COWORK_EMBED_PATCH"""
    if old not in text:
        raise SystemExit(f"cmdentrypoint patch target missing in {path}")
    text = text.replace(old, new)
    path.write_text(text)

def patch_cmdentrypoint_java(path: Path) -> None:
    text = path.read_text()
    marker = "COWORK_EMBED_PATCH"
    if marker in text:
        return

    old = "        handler = new Handler();"
    new = "        handler = new Handler(Looper.getMainLooper()); // " + marker
    if old not in text:
        raise SystemExit(f"CmdEntryPoint handler patch target missing in {path}")
    text = text.replace(old, new)
    path.write_text(text)

patch_lorie(Path(sys.argv[1]))
patch_touch(Path(sys.argv[2]))
patch_cmd(Path(sys.argv[3]))
patch_cmdentrypoint_java(Path(sys.argv[4]))
print("==> termux-x11 embed patches applied")
PY
