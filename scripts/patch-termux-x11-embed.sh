#!/usr/bin/env bash
# Patch termux-x11 for in-process embed without launching MainActivity.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LORIE="$ROOT/third_party/termux-x11/app/src/main/java/com/termux/x11/LorieView.java"
TOUCH="$ROOT/third_party/termux-x11/app/src/main/java/com/termux/x11/input/TouchInputHandler.java"

python3 - "$LORIE" "$TOUCH" <<'PY'
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

patch_lorie(Path(sys.argv[1]))
patch_touch(Path(sys.argv[2]))
print("==> termux-x11 embed patches applied")
PY
