#!/usr/bin/env python3
"""Lightweight heuristic for obvious accidentally committed secrets."""
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1]
skip={".git","node_modules",".expo","coverage","android","ios"}
patterns=[re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",re.I),re.compile(r"\b(?:ghp|github_pat)_[A-Za-z0-9_]{20,}\b"),re.compile(r"\b(?:sk_live|rk_live)_[A-Za-z0-9]{16,}\b")]
bad=[]
for p in root.rglob("*"):
 if not p.is_file() or any(x in skip for x in p.parts) or p.suffix in {".png",".jpg",".lock"}: continue
 try: body=p.read_text(encoding="utf-8")
 except (UnicodeDecodeError,OSError): continue
 if any(rx.search(body) for rx in patterns): bad.append(str(p.relative_to(root)))
if bad: print("Potential secret pattern:",*bad,sep="\n- ");sys.exit(1)
print("No obvious private-key or common token patterns detected.")
