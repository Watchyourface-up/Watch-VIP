import * as SQLite from 'expo-sqlite';
import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
import { LinkRecord } from '../domain/link';

const DB_NAME = 'devvault.db';
const KEY_NAME = 'devvault.sqlcipher.key.v1';
const SCHEMA_VERSION = 1;
let connection: Promise<SQLite.SQLiteDatabase> | null = null;

async function openEncrypted(): Promise<SQLite.SQLiteDatabase> {
  if (!await SecureStore.isAvailableAsync()) throw new Error('SECURE_STORE_UNAVAILABLE');

  let key = await SecureStore.getItemAsync(KEY_NAME);
  if (!key) {
    const random = await Crypto.getRandomBytesAsync(32);
    key = Array.from(random, byte => byte.toString(16).padStart(2, '0')).join('');
    await SecureStore.setItemAsync(KEY_NAME, key, {
      keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY
    });
  }
  if (!/^[a-f0-9]{64}$/.test(key)) throw new Error('INVALID_KEY_MATERIAL');

  const database = await SQLite.openDatabaseAsync(DB_NAME);
  try {
    // expo-sqlite applies this pragma in the native SQLCipher build.
    // Refuse to continue when the cipher extension is absent.
    const cipher = await database.getFirstAsync<{ cipher_version: string }>('PRAGMA cipher_version');
    if (!cipher?.cipher_version) throw new Error('SQLCIPHER_UNAVAILABLE');

    await database.execAsync(\`PRAGMA key = "x'\${key}'";\`);
    // Forces the first database page to be read using the provided key.
    await database.getFirstAsync('SELECT count(*) AS count FROM sqlite_master');
    await database.execAsync(\`
      PRAGMA foreign_keys = ON;
      CREATE TABLE IF NOT EXISTS schema_migrations (
        version INTEGER PRIMARY KEY NOT NULL,
        applied_at INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS links (
        id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        url TEXT NOT NULL,
        description TEXT,
        category TEXT NOT NULL CHECK(category IN ('docs','tools','projects','references')),
        color TEXT,
        favorite INTEGER NOT NULL DEFAULT 0 CHECK(favorite IN (0,1)),
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_links_category ON links(category);
      CREATE INDEX IF NOT EXISTS idx_links_favorite_updated ON links(favorite, updated_at DESC);
    \`);
    await database.withTransactionAsync(async () => {
      const current = await database.getFirstAsync<{ version: number | null }>(
        'SELECT MAX(version) AS version FROM schema_migrations'
      );
      if ((current?.version ?? 0) < SCHEMA_VERSION) {
        await database.runAsync(
          'INSERT OR REPLACE INTO schema_migrations(version, applied_at) VALUES(?, ?)',
          SCHEMA_VERSION, Date.now()
        );
      }
    });
    return database;
  } catch (error) {
    await database.closeAsync();
    throw error;
  }
}

async function db(): Promise<SQLite.SQLiteDatabase> {
  if (!connection) connection = openEncrypted().catch(error => { connection = null; throw error; });
  return connection;
}

export interface LinkRepository {
  list(): Promise<LinkRecord[]>;
  add(record: LinkRecord): Promise<void>;
  update(record: LinkRecord): Promise<void>;
  remove(id: string): Promise<void>;
}

export async function createRepository(): Promise<LinkRepository> {
  return {
    async list() {
      const database = await db();
      const rows = await database.getAllAsync<{
        id: string; title: string; url: string; description?: string;
        category: LinkRecord['category']; color?: string; favorite: number;
        createdAt: number; updatedAt: number;
      }>('SELECT id,title,url,description,category,color,favorite,created_at AS createdAt,updated_at AS updatedAt FROM links ORDER BY favorite DESC,updated_at DESC');
      return rows.map(row => ({ ...row, favorite: Boolean(row.favorite) }));
    },
    async add(record) {
      const database = await db();
      await database.runAsync(
        'INSERT INTO links(id,title,url,description,category,color,favorite,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)',
        record.id, record.title, record.url, record.description ?? null, record.category,
        record.color ?? null, record.favorite ? 1 : 0, record.createdAt, record.updatedAt
      );
    },
    async update(record) {
      const database = await db();
      const result = await database.runAsync(
        'UPDATE links SET title=?,url=?,description=?,category=?,color=?,favorite=?,updated_at=? WHERE id=?',
        record.title, record.url, record.description ?? null, record.category,
        record.color ?? null, record.favorite ? 1 : 0, record.updatedAt, record.id
      );
      if (result.changes === 0) throw new Error('NOT_FOUND');
    },
    async remove(id) {
      const database = await db();
      await database.runAsync('DELETE FROM links WHERE id=?', id);
    }
  };
}
