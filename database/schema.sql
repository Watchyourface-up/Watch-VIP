-- SQLCipher required. Apply PRAGMA key before any schema reads.
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY NOT NULL, applied_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS links(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,url TEXT NOT NULL,description TEXT,category TEXT NOT NULL CHECK(category IN ('docs','tools','projects','references')),color TEXT,favorite INTEGER NOT NULL DEFAULT 0 CHECK(favorite IN (0,1)),created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS idx_links_category ON links(category);
CREATE INDEX IF NOT EXISTS idx_links_favorite_updated ON links(favorite,updated_at DESC);
INSERT OR IGNORE INTO schema_migrations(version,applied_at) VALUES(1,CAST(strftime('%s','now') AS INTEGER)*1000);
