#!/usr/bin/env bash
# Patch termux terminal-view for live IME echo (composing keyboards).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TERMINAL_VIEW="$ROOT/third_party/termux-app/terminal-view/src/main/java/com/termux/view/TerminalView.java"

if [[ ! -f "$TERMINAL_VIEW" ]]; then
  echo "TerminalView.java not found at $TERMINAL_VIEW" >&2
  exit 1
fi

python3 - "$TERMINAL_VIEW" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text()
marker_v2 = "COWORK_LIVE_COMPOSE_V2"

if marker_v2 in text:
    print("==> terminal-view live-compose v2 patch already applied")
    sys.exit(0)

# Remove v1 patch if present.
text = re.sub(
    r"\n\s*@Override\n\s*public boolean setComposingText\(CharSequence text, int newCursorPosition\) \{"
    r".*?// COWORK_LIVE_COMPOSE_COMMIT\n",
    "\n",
    text,
    count=1,
    flags=re.DOTALL,
)

field_needle = "    int mCombiningAccent;\n"
field_insert = (
    "    int mCombiningAccent;\n\n"
    "    /** Tracks IME composing text for delta commits. */\n"
    "    private CharSequence mCoworkLastComposing = \"\"; // "
    + marker_v2
    + "\n"
)
if field_needle not in text:
    raise SystemExit("field insertion target missing")
text = text.replace(field_needle, field_insert, 1)

finish_needle = """            @Override
            public boolean finishComposingText() {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logInfo(LOG_TAG, "IME: finishComposingText()");"""
finish_insert = """            @Override
            public boolean finishComposingText() {
                mCoworkLastComposing = "";
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logInfo(LOG_TAG, "IME: finishComposingText()");"""
if finish_needle not in text:
    raise SystemExit("finishComposingText patch target missing")
text = text.replace(finish_needle, finish_insert, 1)

commit_needle = """            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {"""
commit_insert = """            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                mCoworkLastComposing = "";
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {"""
if commit_needle not in text:
    raise SystemExit("commitText patch target missing")
text = text.replace(commit_needle, commit_insert, 1)

compose_needle = """            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logInfo(LOG_TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");
                }"""
compose_insert = """            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                CharSequence seq = text == null ? "" : text;
                if (seq.length() > mCoworkLastComposing.length()) {
                    CharSequence delta = seq.subSequence(mCoworkLastComposing.length(), seq.length());
                    mCoworkLastComposing = seq;
                    if (delta.length() > 0) {
                        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                            mClient.logInfo(LOG_TAG, "IME: setComposingText delta(\\"" + delta + "\\")");
                        }
                        sendTextToTerminal(delta);
                        super.setComposingText("", newCursorPosition);
                        return true;
                    }
                } else if (seq.length() < mCoworkLastComposing.length()) {
                    int delCount = mCoworkLastComposing.length() - seq.length();
                    mCoworkLastComposing = seq;
                    KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                    for (int i = 0; i < delCount; i++) sendKeyEvent(deleteKey);
                    return super.setComposingText(seq, newCursorPosition);
                }
                mCoworkLastComposing = seq;
                return super.setComposingText(seq, newCursorPosition);
            } // """ + marker_v2 + """

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logInfo(LOG_TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");
                }"""
if compose_needle not in text:
    raise SystemExit("setComposingText insertion target missing")
text = text.replace(compose_needle, compose_insert, 1)

redraw_needle = """                    inputCodePoint(codePoint, ctrlHeld, false);
                }
            }

        };
    }"""
redraw_insert = """                    inputCodePoint(codePoint, ctrlHeld, false);
                }
                if (mEmulator != null) {
                    TerminalView.this.onScreenUpdated();
                }
            }

        };
    }"""
if redraw_needle not in text:
    raise SystemExit("sendTextToTerminal redraw patch target missing")
text = text.replace(redraw_needle, redraw_insert, 1)

path.write_text(text)
print("==> Applied terminal-view live-compose v2 patch")
PY
