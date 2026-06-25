#!/usr/bin/env python3
"""
HTTP dispatch server for Cowork computer-use (phone → desktop tasks).

POST /action  JSON body → executes one computer-use action
GET  /health  → status
GET  /screenshot → PNG
"""
from __future__ import annotations

import json
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cowork_desktop import CoworkDesktop  # noqa: E402

HOST = os.environ.get("COWORK_DISPATCH_HOST", "0.0.0.0")
PORT = int(os.environ.get("COWORK_DISPATCH_PORT", "8765"))
desktop = CoworkDesktop(human=True)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write(f"[cowork-dispatch] {self.address_string()} - {fmt % args}\n")

    def _json(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/health":
            s = desktop.screen_size()
            self._json(200, {"ok": True, "display": os.environ.get("DISPLAY", ":0"), "screen": s.__dict__})
            return
        if path == "/screenshot":
            shot = desktop.screenshot()
            data = Path(shot["path"]).read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self._json(404, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/action":
            self._json(404, {"ok": False, "error": "not found"})
            return
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode() if length else "{}"
        try:
            req = json.loads(raw)
            out = desktop.execute(req)
            self._json(200, out)
        except Exception as e:
            self._json(400, {"ok": False, "error": str(e)})


def main() -> None:
    server = HTTPServer((HOST, PORT), Handler)
    print(f"cowork-dispatch listening on http://{HOST}:{PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
