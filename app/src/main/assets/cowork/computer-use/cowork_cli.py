#!/usr/bin/env python3
"""CLI for Cowork computer-use actions."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cowork_desktop import CoworkDesktop  # noqa: E402


def main() -> None:
    p = argparse.ArgumentParser(description="Cowork desktop automation CLI")
    p.add_argument("action", choices=[
        "screenshot", "click", "move", "drag", "type", "key", "scroll",
        "ocr", "windows", "size", "json",
    ])
    p.add_argument("--x", type=int)
    p.add_argument("--y", type=int)
    p.add_argument("--x2", type=int)
    p.add_argument("--y2", type=int)
    p.add_argument("--text", default="")
    p.add_argument("--keys", default="")
    p.add_argument("--button", default="left")
    p.add_argument("--direction", default="down")
    p.add_argument("--amount", type=int, default=3)
    p.add_argument("--path")
    p.add_argument("--no-human", action="store_true")
    args = p.parse_args()

    d = CoworkDesktop(human=not args.no_human)

    if args.action == "screenshot":
        print(json.dumps(d.screenshot(args.path)))
    elif args.action == "click":
        d.mouse_move(args.x or 0, args.y or 0)
        print(json.dumps(d.click(args.button)))
    elif args.action == "move":
        print(json.dumps(d.mouse_move(args.x or 0, args.y or 0)))
    elif args.action == "drag":
        print(json.dumps(d.drag(args.x or 0, args.y or 0, args.x2 or 0, args.y2 or 0, args.button)))
    elif args.action == "type":
        print(json.dumps(d.type_text(args.text)))
    elif args.action == "key":
        print(json.dumps(d.key(args.keys)))
    elif args.action == "scroll":
        print(json.dumps(d.scroll(args.direction, args.amount)))
    elif args.action == "ocr":
        print(json.dumps(d.ocr()))
    elif args.action == "windows":
        print(json.dumps(d.window_list()))
    elif args.action == "size":
        s = d.screen_size()
        print(json.dumps({"width": s.width, "height": s.height}))
    elif args.action == "json":
        raw = sys.stdin.read()
        print(json.dumps(d.execute(json.loads(raw))))


if __name__ == "__main__":
    main()
