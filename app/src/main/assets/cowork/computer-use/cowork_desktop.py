#!/usr/bin/env python3
"""
Cowork computer-use executor — Claude/Kimi-style desktop automation.

Implements screenshot, mouse, keyboard, drag, scroll, OCR with human-like timing.
"""
from __future__ import annotations

import base64
import json
import math
import os
import random
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

DISPLAY = os.environ.get("DISPLAY", ":0")


def _run(cmd: list[str], **kw: Any) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.setdefault("DISPLAY", DISPLAY)
    return subprocess.run(cmd, capture_output=True, text=True, env=env, **kw)


def _xdotool(*args: str) -> None:
    r = _run(["xdotool"] + list(args))
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip() or f"xdotool failed: {args}")


@dataclass
class ScreenInfo:
    width: int
    height: int


class CoworkDesktop:
    """Desktop automation with optional human-like motion."""

    def __init__(self, human: bool = True) -> None:
        self.human = human
        self._mouse_x = 0
        self._mouse_y = 0

    def screen_size(self) -> ScreenInfo:
        r = _run(["xdotool", "getdisplaygeometry"])
        if r.returncode == 0:
            w, h = r.stdout.strip().split()
            return ScreenInfo(int(w), int(h))
        return ScreenInfo(1280, 720)

    def screenshot(self, path: str | None = None, fmt: str = "png") -> dict[str, Any]:
        out = path or f"/tmp/cowork-screen-{int(time.time())}.{fmt}"
        Path(out).parent.mkdir(parents=True, exist_ok=True)
        for tool in (
            ["scrot", "-o", out],
            ["maim", out],
            ["import", "-window", "root", out],
        ):
            r = _run(tool)
            if r.returncode == 0 and Path(out).is_file():
                data = Path(out).read_bytes()
                return {
                    "path": out,
                    "width": self.screen_size().width,
                    "height": self.screen_size().height,
                    "base64": base64.b64encode(data).decode("ascii"),
                }
        raise RuntimeError("screenshot failed (install scrot or maim)")

    def _bezier_points(
        self, x0: int, y0: int, x1: int, y1: int, steps: int
    ) -> list[tuple[int, int]]:
        cx1 = x0 + (x1 - x0) * random.uniform(0.2, 0.5) + random.randint(-40, 40)
        cy1 = y0 + (y1 - y0) * random.uniform(0.1, 0.4) + random.randint(-30, 30)
        cx2 = x0 + (x1 - x0) * random.uniform(0.5, 0.8) + random.randint(-40, 40)
        cy2 = y0 + (y1 - y0) * random.uniform(0.6, 0.9) + random.randint(-30, 30)
        pts: list[tuple[int, int]] = []
        for i in range(steps + 1):
            t = i / steps
            u = 1 - t
            x = u**3 * x0 + 3 * u**2 * t * cx1 + 3 * u * t**2 * cx2 + t**3 * x1
            y = u**3 * y0 + 3 * u**2 * t * cy1 + 3 * u * t**2 * cy2 + t**3 * y1
            pts.append((int(x), int(y)))
        return pts

    def mouse_move(self, x: int, y: int, human: bool | None = None) -> dict[str, int]:
        human = self.human if human is None else human
        x += random.randint(-1, 1) if human else 0
        y += random.randint(-1, 1) if human else 0
        if human:
            steps = max(12, int(math.hypot(x - self._mouse_x, y - self._mouse_y) / 18))
            for px, py in self._bezier_points(self._mouse_x, self._mouse_y, x, y, steps):
                _xdotool("mousemove", "--sync", str(px), str(py))
                time.sleep(random.uniform(0.004, 0.014))
        else:
            _xdotool("mousemove", "--sync", str(x), str(y))
        self._mouse_x, self._mouse_y = x, y
        return {"x": x, "y": y}

    def click(self, button: str = "left", repeat: int = 1) -> dict[str, str]:
        btn = {"left": 1, "middle": 2, "right": 3}.get(button, 1)
        for _ in range(repeat):
            if self.human:
                time.sleep(random.uniform(0.05, 0.12))
            _xdotool("click", str(btn))
        return {"button": button, "repeat": str(repeat)}

    def double_click(self) -> dict[str, str]:
        return self.click("left", repeat=2)

    def drag(
        self, x1: int, y1: int, x2: int, y2: int, button: str = "left"
    ) -> dict[str, Any]:
        btn = {"left": 1, "middle": 2, "right": 3}.get(button, 1)
        self.mouse_move(x1, y1)
        if self.human:
            time.sleep(random.uniform(0.08, 0.18))
        _xdotool("mousedown", str(btn))
        self.mouse_move(x2, y2, human=self.human)
        if self.human:
            time.sleep(random.uniform(0.06, 0.14))
        _xdotool("mouseup", str(btn))
        return {"from": [x1, y1], "to": [x2, y2], "button": button}

    def scroll(self, direction: str = "down", amount: int = 3) -> dict[str, Any]:
        button = {"up": 4, "down": 5, "left": 6, "right": 7}.get(direction, 5)
        for _ in range(amount):
            _xdotool("click", str(button))
            if self.human:
                time.sleep(random.uniform(0.04, 0.1))
        return {"direction": direction, "amount": amount}

    def type_text(self, text: str, human: bool | None = None) -> dict[str, str]:
        human = self.human if human is None else human
        if human:
            for ch in text:
                _xdotool("type", "--delay", str(random.randint(25, 95)), ch)
        else:
            _xdotool("type", "--", text)
        return {"typed": text[:80] + ("..." if len(text) > 80 else "")}

    def key(self, keys: str) -> dict[str, str]:
        _xdotool("key", keys.replace("+", "+"))
        return {"keys": keys}

    def wait(self, seconds: float) -> dict[str, float]:
        time.sleep(seconds)
        return {"waited": seconds}

    def window_list(self) -> list[str]:
        r = _run(["wmctrl", "-l"])
        if r.returncode != 0:
            return []
        return [ln.strip() for ln in r.stdout.splitlines() if ln.strip()]

    def window_activate(self, title: str) -> dict[str, Any]:
        r = _run(["wmctrl", "-a", title])
        ok = r.returncode == 0
        if not ok:
            r = _run(["xdotool", "search", "--name", title, "windowactivate"])
            ok = r.returncode == 0
        return {"title": title, "activated": ok}

    def ocr(self, region: list[int] | None = None) -> dict[str, Any]:
        shot = self.screenshot()
        path = shot["path"]
        try:
            import pytesseract
            from PIL import Image

            img = Image.open(path)
            if region and len(region) == 4:
                img = img.crop(tuple(region))
            text = pytesseract.image_to_string(img).strip()
            return {"text": text, "region": region}
        except Exception as e:
            return {"text": "", "error": str(e)}

    def execute(self, action: dict[str, Any]) -> dict[str, Any]:
        name = action.get("action") or action.get("type")
        if not name:
            raise ValueError("missing action")
        method = getattr(self, name, None)
        if name == "screenshot":
            return {"ok": True, "result": self.screenshot(action.get("path"))}
        if name == "mouse_move":
            return {"ok": True, "result": self.mouse_move(int(action["x"]), int(action["y"]), action.get("human"))}
        if name == "click":
            return {"ok": True, "result": self.click(action.get("button", "left"), int(action.get("repeat", 1)))}
        if name == "double_click":
            return {"ok": True, "result": self.double_click()}
        if name == "drag":
            f, t = action["from"], action["to"]
            return {"ok": True, "result": self.drag(int(f[0]), int(f[1]), int(t[0]), int(t[1]), action.get("button", "left"))}
        if name == "scroll":
            return {"ok": True, "result": self.scroll(action.get("direction", "down"), int(action.get("amount", 3)))}
        if name == "type":
            return {"ok": True, "result": self.type_text(action.get("text", ""), action.get("human"))}
        if name == "key":
            return {"ok": True, "result": self.key(action["keys"])}
        if name == "wait":
            return {"ok": True, "result": self.wait(float(action.get("seconds", 1)))}
        if name == "ocr":
            return {"ok": True, "result": self.ocr(action.get("region"))}
        if name == "window_list":
            return {"ok": True, "result": self.window_list()}
        if name == "window_activate":
            return {"ok": True, "result": self.window_activate(action["title"])}
        if name == "screen_size":
            s = self.screen_size()
            return {"ok": True, "result": {"width": s.width, "height": s.height}}
        raise ValueError(f"unknown action: {name}")


def main() -> None:
    desktop = CoworkDesktop(human=os.environ.get("COWORK_HUMAN", "1") != "0")
    if len(sys.argv) > 1 and sys.argv[1] == "--json":
        raw = sys.stdin.read().strip()
        req = json.loads(raw) if raw else {}
        try:
            out = desktop.execute(req)
            print(json.dumps(out))
        except Exception as e:
            print(json.dumps({"ok": False, "error": str(e)}))
            sys.exit(1)
        return
    if len(sys.argv) > 1:
        cmd = sys.argv[1]
        if cmd == "screenshot":
            print(json.dumps(desktop.screenshot()))
        elif cmd == "size":
            s = desktop.screen_size()
            print(f"{s.width}x{s.height}")
        else:
            print("Usage: cowork_desktop.py [--json | screenshot | size]", file=sys.stderr)
            sys.exit(2)
        return
    print(json.dumps({"screen": desktop.screen_size().__dict__, "windows": desktop.window_list()}))


if __name__ == "__main__":
    main()
