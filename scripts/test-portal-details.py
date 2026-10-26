#!/usr/bin/env python3
"""Check the details CLI against a loopback portal and disposable local files."""
import hashlib
import http.server
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
from email import policy
from email.parser import BytesParser

ROOT = Path(__file__).resolve().parents[1]
CLI = Path(sys.argv[1]).resolve()
base = ROOT / "build/test-work/portal-details"
base.mkdir(parents=True, exist_ok=True)
work = Path(tempfile.mkdtemp(prefix="run-", dir=base))
requests = []


class Portal(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        requests.append({"method": "GET", "path": self.path})
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"name": "details-test", "images": []}).encode())

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        message = BytesParser(policy=policy.default).parsebytes(
            ("Content-Type: " + self.headers["Content-Type"] + "\r\n\r\n").encode() + body
        )
        fields = {
            part.get_param("name", header="content-disposition"): part.get_payload(decode=True).decode()
            for part in message.iter_parts()
        }
        requests.append({"method": "POST", "path": self.path, "fields": fields})
        valid = self.path == "/api/v2/mods/edit_details" and self.headers.get("Authorization") == "Bearer fake-details-test-key"
        self.send_response(200 if valid else 403)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"success": valid}).encode())


portal = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Portal)
threading.Thread(target=portal.serve_forever, daemon=True).start()
try:
    mod = work / "mod"
    mod.mkdir()
    (mod / "portal").mkdir()
    hook = "node -e \"require('fs').writeFileSync('unexpected-hook','called')\""
    info = {
        "name": "details-test", "version": "3.4.3", "title": "Updated title",
        "homepage": "https://example.invalid/project", "description": "Updated summary",
        "package": {
            "git_publish_branch": "master", "sync_portal_details": False,
            "readme": "portal/description.md", "faq": "portal/questions.md",
            "scripts": {name: hook for name in ["prepublish", "compile", "publish", "postpublish", "version"]},
        },
    }
    (mod / "info.json").write_text(json.dumps(info, indent=2))
    (mod / "changelog.txt").write_text("Version: 3.4.3\n  Changes:\n    - Local pending change\n")
    (mod / "portal/description.md").write_text("# Description\n\nUpdated long description.\n")
    (mod / "portal/questions.md").write_text("# FAQ\n\nUpdated questions.\n")
    snapshot = lambda: {str(p.relative_to(mod)): hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in mod.rglob("*") if p.is_file()}
    before = snapshot()
    fake_bin = work / "bin"
    fake_bin.mkdir()
    (fake_bin / "git").write_text('#!/bin/sh\nprintf "unexpected git call\\n" >> "$DETAILS_TEST_GIT_CALLS"\nexit 91\n')
    (fake_bin / "git").chmod(0o755)
    config = work / "config.json"
    config.write_text("{}")
    env = os.environ.copy()
    env.update(FACTORIO_UPLOAD_API_KEY="fake-details-test-key",
               FACTORIO_TEST_PORTAL=f"http://127.0.0.1:{portal.server_port}",
               FMTK_CONFIG=str(config), TMPDIR=str(work),
               PATH=str(fake_bin) + os.pathsep + env.get("PATH", ""),
               DETAILS_TEST_GIT_CALLS=str(work / "git-calls"))
    command = [shutil.which("node"), "--import", str(ROOT / "scripts/portal-interceptor.mjs"), str(CLI), "details"]
    result = subprocess.run(command, cwd=mod, env=env, text=True, capture_output=True, timeout=45)
    (work / "command.log").write_text(result.stdout + result.stderr)
    (work / "requests.json").write_text(json.dumps(requests, indent=2) + "\n")
    assert result.returncode == 0, result.stdout + result.stderr
    assert snapshot() == before, "Details changed local files or ran a hook"
    assert not (work / "git-calls").exists(), "Details invoked Git"
    posts = [request for request in requests if request["method"] == "POST"]
    assert len(posts) == 1 and posts[0]["path"] == "/api/v2/mods/edit_details", requests
    fields = posts[0]["fields"]
    assert fields == {"mod": "details-test", "title": "Updated title",
                      "homepage": "https://example.invalid/project", "summary": "Updated summary",
                      "description": "Updated long description.\r\n", "faq": "Updated questions.\r\n"}, fields
    receipt = {"status": "PASS", "cli": str(CLI), "cliSha256": hashlib.sha256(CLI.read_bytes()).hexdigest(),
               "command": command, "requests": requests, "localFilesBeforeAndAfter": before,
               "gitInvocations": 0, "releaseUploads": 0, "version": "3.4.3", "hooksExecuted": 0}
    (work / "results.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print("Metadata-only details checks passed:", work)
finally:
    portal.shutdown()
    portal.server_close()
