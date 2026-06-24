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


def must_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text or label in text:
            return text
        raise SystemExit(f"{label}: patch target missing")
    return text.replace(old, new, 1)


def patch_lorie(path: Path) -> None:
    text = path.read_text()

    if "COWORK_TRIGGER_CALLBACK_NULL" not in text:
        text = must_replace(
            text,
            """        Rect r = getHolder().getSurfaceFrame();
        MainActivity.getInstance().runOnUiThread(() -> mSurfaceCallback.surfaceChanged(getHolder(), PixelFormat.BGRA_8888, r.width(), r.height()));
    }""",
            """        Rect r = getHolder().getSurfaceFrame();
        Runnable work = () -> mSurfaceCallback.surfaceChanged(getHolder(), PixelFormat.BGRA_8888, r.width(), r.height());
        MainActivity activity = MainActivity.getInstance();
        if (activity != null) {
            activity.runOnUiThread(work);
        } else {
            post(work);
        }
    } // COWORK_TRIGGER_CALLBACK_NULL""",
            "triggerCallback",
        )

    if "COWORK_GET_DIMENSIONS_NULL" not in text:
        text = must_replace(
            text,
            """    void getDimensionsFromSettings() {
        Prefs prefs = MainActivity.getPrefs();""",
            """    void getDimensionsFromSettings() {
        Prefs prefs = MainActivity.getPrefs();
        if (prefs == null) {
            p.set(Math.max(getMeasuredWidth(), 1), Math.max(getMeasuredHeight(), 1));
            return;
        } // COWORK_GET_DIMENSIONS_NULL""",
            "getDimensionsFromSettings",
        )

    if "COWORK_ON_MEASURE_NULL" not in text:
        text = must_replace(
            text,
            """        Prefs prefs = MainActivity.getPrefs();
        if (prefs.displayStretch.get()""",
            """        Prefs prefs = MainActivity.getPrefs();
        if (prefs == null) {
            getHolder().setSizeFromLayout();
            return;
        } // COWORK_ON_MEASURE_NULL
        if (prefs.displayStretch.get()""",
            "onMeasure",
        )

    if "COWORK_DISPATCH_KEY_NULL" not in text:
        text = must_replace(
            text,
            """        return MainActivity.getInstance().handleKey(event);
    }""",
            """        MainActivity activity = MainActivity.getInstance();
        if (activity == null) {
            return false;
        }
        return activity.handleKey(event);
    } // COWORK_DISPATCH_KEY_NULL""",
            "dispatchKeyEventPreIme",
        )

    if "COWORK_IME_PREFS_NULL" not in text:
        text = must_replace(
            text,
            """        if (MainActivity.getPrefs().enforceCharBasedInput.get())""",
            """        Prefs prefs = MainActivity.getPrefs();
        if (prefs != null && prefs.enforceCharBasedInput.get()) // COWORK_IME_PREFS_NULL""",
            "onCreateInputConnection",
        )

    if "COWORK_EMBED_REPLACE_TEXT_NULL" not in text:
        text = must_replace(
            text,
            """            if (a.useTermuxEKBarBehaviour && a.mExtraKeys != null)
                a.mExtraKeys.unsetSpecialKeys();""",
            """            if (a != null && a.useTermuxEKBarBehaviour && a.mExtraKeys != null)
                a.mExtraKeys.unsetSpecialKeys(); // COWORK_EMBED_REPLACE_TEXT_NULL""",
            "replaceText",
        )

    if "COWORK_EMBED_PATCH" not in text:
        text = text.replace(
            "public class LorieView extends SurfaceView implements InputStub {",
            "public class LorieView extends SurfaceView implements InputStub { // COWORK_EMBED_PATCH",
            1,
        )

    path.write_text(text)


def patch_touch(path: Path) -> None:
    text = path.read_text()
    if "COWORK_EMBED_PATCH" in text:
        return

    text = must_replace(
        text,
        """        LorieView.requestStylusEnabled(stylusAvailable.get());
        MainActivity.getInstance().setExternalKeyboardConnected(externalKeyboardAvailable.get());
    }""",
        """        LorieView.requestStylusEnabled(stylusAvailable.get());
        MainActivity activity = MainActivity.getInstance();
        if (activity != null) {
            activity.setExternalKeyboardConnected(externalKeyboardAvailable.get());
        }
    } // COWORK_EMBED_PATCH""",
        "refreshInputDevices",
    )
    path.write_text(text)


def patch_cmd(path: Path) -> None:
    text = path.read_text()
    if "COWORK_EMBED_PATCH" in text:
        return

    text = must_replace(
        text,
        """    if (!getenv("XKB_CONFIG_ROOT")) {
        // chroot case
        const char *root_dir = dirname(getenv("TMPDIR"));
        char current_path[1024] = {0};
        snprintf(current_path, sizeof(current_path), "%s/usr/share/X11/xkb", root_dir);
        if (access(current_path, F_OK) == 0)
            setenv("XKB_CONFIG_ROOT", current_path, 1);
    }""",
        """    if (!getenv("XKB_CONFIG_ROOT")) {
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
    } // COWORK_EMBED_PATCH""",
        "cmdentrypoint",
    )
    path.write_text(text)


def patch_cmdentrypoint_java(path: Path) -> None:
    text = path.read_text()
    if "COWORK_EMBED_PATCH" in text:
        return

    text = must_replace(
        text,
        "        handler = new Handler();",
        "        handler = new Handler(Looper.getMainLooper()); // COWORK_EMBED_PATCH",
        "CmdEntryPoint handler",
    )
    path.write_text(text)


patch_lorie(Path(sys.argv[1]))
patch_touch(Path(sys.argv[2]))
patch_cmd(Path(sys.argv[3]))
patch_cmdentrypoint_java(Path(sys.argv[4]))
print("==> termux-x11 embed patches applied")
PY

grep -q "COWORK_TRIGGER_CALLBACK_NULL" "$LORIE" || {
  echo "ERROR: LorieView triggerCallback embed patch missing" >&2
  exit 1
}
