#!/usr/bin/env python3
"""Heuristic scan for obvious hard-coded credentials. This is not a full secret scanner."""
from pathlib import Path
import re, sys

ROOT = Path(__file__).resolve().parents[1]
SKIP = {".git", "node_modules", ".expo", "coverage", "android", "ios"}
PATTERNS = [
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", re.I),
    re.compile(r"\b(?:ghp|github_pat)_[A-Za-z0-9_]{20,}\b"),
    re.compile(r"\b(?:sk_live|rk_live)_[A-Za-z0-9]{16,}\b"),
    re.compile(r"""(?i)(?:client[_-]?secret|access[_-]?token|api[_-]?key)\s*[:=]\s*['"][A-Za-z0-9_./+=-]{20,}['"]"""),
]
failures = []
for path in ROOT.rglob("*"):
    if not path.is_file() or any(part in SKIP for part in path.parts):
        continue
    if path.suffix.lower() in {".png", ".jpg", ".jpeg", ".lock"}:
        continue
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    if any(pattern.search(content) for pattern in PATTERNS):
        failures.append(str(path.relative_to(ROOT)))
if failures:
    print("Potential hard-coded secret pattern:", *sorted(set(failures)), sep="\n- ")
    sys.exit(1)
print("No obvious high-confidence hard-coded secrets detected.")
