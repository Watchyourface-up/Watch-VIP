#!/usr/bin/env python3
"""Validate SQL syntax/shape only; this does not prove SQLCipher encryption."""
from pathlib import Path
import sqlite3
p=Path(__file__).resolve().parents[1]/"database"/"schema.sql"
db=sqlite3.connect(":memory:")
try:
 db.executescript(p.read_text(encoding="utf-8"))
 tables={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
 assert {"schema_migrations","links"}.issubset(tables), f"missing tables: {tables}"
 columns={r[1] for r in db.execute("PRAGMA table_info(links)")}
 assert {"id","title","url","category","favorite","created_at","updated_at"}.issubset(columns), columns
 print("SQL schema syntax and required columns: OK (SQLite syntax check only; not SQLCipher verification)")
finally: db.close()
