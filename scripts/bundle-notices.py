#!/usr/bin/env python3
"""Collect licenses from the locked local toolkit dependency installation."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
toolkit=root.parent/'vscode-factoriomod-debug'
lock=json.loads((toolkit/'package-lock.json').read_text())
parts=['Third-party notices for the bundled FMTK CLI\n','This inventory includes build-time dependencies as well as runtime dependencies.\n']
for name,meta in sorted(lock['packages'].items()):
    if not name: continue
    package=toolkit/name
    parts.append(f'\n--- {name} {meta.get("version", "")} ({meta.get("license", "see license text")}) ---\n')
    if package.is_dir():
        for path in sorted(package.iterdir()):
            if path.is_file() and path.name.lower().startswith(('license','licence','copying','notice')):
                parts.append(path.read_text(errors='replace')+'\n')
out=root/'build/notices/THIRD-PARTY-NOTICES.txt';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(''.join(parts))
