#!/usr/bin/env bash
# Patches termux-x11 for embedding inside Proot Cowork (not standalone APK).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
X11="$ROOT/third_party/termux-x11/app/src/main/java/com/termux/x11"

python3 - "$X11" <<'PY'
import sys
from pathlib import Path

x11 = Path(sys.argv[1])

def patch_file(path: Path, replacements: list[tuple[str, str]], label: str) -> None:
    if not path.is_file():
        print(f"{label}: file missing at {path}", file=sys.stderr)
        sys.exit(1)
    text = path.read_text()
    changed = False
    for old, new in replacements:
        if old in text:
            text = text.replace(old, new)
            changed = True
    if changed:
        path.write_text(text)
        print(f"Patched {label}")
    else:
        print(f"{label}: already patched")

cmd_ep = x11 / "CmdEntryPoint.java"
patch_file(cmd_ep, [
    (
        '''        String path = "lib/" + Build.SUPPORTED_ABIS[0] + "/libXlorie.so";
        ClassLoader loader = CmdEntryPoint.class.getClassLoader();
        URL res = loader != null ? loader.getResource(path) : null;
        String libPath = res != null ? res.getFile().replace("file:", "") : null;
        if (libPath != null) {
            try {
                System.load(libPath);
            } catch (Exception e) {
                Log.e("CmdEntryPoint", "Failed to dlopen " + libPath, e);
                System.err.println("Failed to load native library. Did you install the right apk? Try the universal one.");
                System.exit(134);
            }
        } else {
            // It is critical only when it is not running in Android application process
            if (MainActivity.getInstance() == null) {
                System.err.println("Failed to acquire native library. Did you install the right apk? Try the universal one.");
                System.exit(134);
            }
        }''',
        '''        try {
            System.loadLibrary("Xlorie");
        } catch (UnsatisfiedLinkError e) {
            Log.e("CmdEntryPoint", "Failed to load libXlorie", e);
        }''',
    ),
    (
        '''    CmdEntryPoint(String[] args) {
        if (!start(args))
            System.exit(1);

        spawnListeningThread();
        sendBroadcastDelayed();
    }''',
        '''    CmdEntryPoint(String[] args) {
        if (!start(args)) {
            Log.e("CmdEntryPoint", "native start() failed");
            if (getenv("TERMUX_X11_OVERRIDE_PACKAGE") == null
                    && System.getProperty("TERMUX_X11_OVERRIDE_PACKAGE") == null) {
                System.exit(1);
            }
            return;
        }

        spawnListeningThread();
        sendBroadcastDelayed();
    }''',
    ),
    (
        '''        String targetPackage = getenv("TERMUX_X11_OVERRIDE_PACKAGE");
        if (targetPackage == null)
            targetPackage = "com.termux.x11";''',
        '''        String targetPackage = getenv("TERMUX_X11_OVERRIDE_PACKAGE");
        if (targetPackage == null)
            targetPackage = System.getProperty("TERMUX_X11_OVERRIDE_PACKAGE");
        if (targetPackage == null)
            targetPackage = "com.termux.x11";''',
    ),
], "CmdEntryPoint")

main_activity = x11 / "MainActivity.java"
patch_file(main_activity, [
    (
        '''    public static Prefs getPrefs() {
        return prefs;
    }''',
        '''    public static Prefs getPrefs() {
        if (prefs == null && instance != null)
            prefs = new Prefs(instance);
        return prefs;
    }''',
    ),
], "MainActivity")

lorie_view = x11 / "LorieView.java"
patch_file(lorie_view, [
    (
        '''    void getDimensionsFromSettings(int width, int height) {
        Prefs prefs = MainActivity.getPrefs();
        int w = width;
        int h = height;
        switch(prefs.displayResolutionMode.get()) {''',
        '''    void getDimensionsFromSettings(int width, int height) {
        Prefs prefs = MainActivity.getPrefs();
        int w = width;
        int h = height;
        if (prefs == null) {
            p.set(w, h);
            return;
        }
        switch(prefs.displayResolutionMode.get()) {''',
    ),
    (
        '''        if (!prefs.displayStretch.get()) {''',
        '''        if (prefs == null || !prefs.displayStretch.get()) {''',
    ),
    (
        '''        return MainActivity.getInstance().handleKey(event);''',
        '''        MainActivity activity = MainActivity.getInstance();
        return activity != null && activity.handleKey(event);''',
    ),
    (
        '''        if (MainActivity.getPrefs().enforceCharBasedInput.get())''',
        '''        Prefs inputPrefs = MainActivity.getPrefs();
        if (inputPrefs != null && inputPrefs.enforceCharBasedInput.get())''',
    ),
    (
        '''            if (a.useTermuxEKBarBehaviour && a.mExtraKeys != null)''',
        '''            if (a != null && a.useTermuxEKBarBehaviour && a.mExtraKeys != null)''',
    ),
], "LorieView")
PY
