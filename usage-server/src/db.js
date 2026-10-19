/**
 * SQLite storage: ONE table holding only the current state per installation. No event history,
 * no IPs, no headers, no request bodies. Every statement is a prepared, parameterized statement
 * created once here - there is no dynamic SQL anywhere in the service.
 */
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const SCHEMA = `
CREATE TABLE IF NOT EXISTS installations (
  installation_id TEXT    PRIMARY KEY NOT NULL,
  version         TEXT    NOT NULL,
  first_seen      INTEGER NOT NULL,
  last_seen       INTEGER NOT NULL
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS installations_last_seen ON installations (last_seen);
`;

/**
 * Opens (creating if needed) the usage database.
 * @param {string} path file path, or ':memory:'
 */
export function openDatabase(path) {
  if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
  const db = new DatabaseSync(path);
  db.exec('PRAGMA journal_mode = WAL;');
  db.exec('PRAGMA busy_timeout = 5000;');
  db.exec('PRAGMA secure_delete = ON;');
  db.exec(SCHEMA);

  const exists = db.prepare('SELECT 1 FROM installations WHERE installation_id = ?');
  // first_seen is written only on insert; later heartbeats update version + last_seen only.
  const upsert = db.prepare(`
    INSERT INTO installations (installation_id, version, first_seen, last_seen)
    VALUES (?, ?, ?, ?)
    ON CONFLICT (installation_id) DO UPDATE SET
      version   = excluded.version,
      last_seen = MAX(installations.last_seen, excluded.last_seen)`);
  const countAll = db.prepare('SELECT COUNT(*) AS n FROM installations');
  const countSince = db.prepare('SELECT COUNT(*) AS n FROM installations WHERE last_seen >= ?');
  const versionsSince = db.prepare(`
    SELECT version, COUNT(*) AS active30d FROM installations
    WHERE last_seen >= ?
    GROUP BY version
    ORDER BY active30d DESC, version ASC`);
  const countFirstSeenBetween = db.prepare('SELECT COUNT(*) AS n FROM installations WHERE first_seen >= ? AND first_seen < ?');
  const maxLastSeen = db.prepare('SELECT MAX(last_seen) AS t FROM installations');
  const maxFirstSeen = db.prepare('SELECT MAX(first_seen) AS t FROM installations');

  return {
    /** @returns {boolean} true if this installation was not stored before */
    isNew(installationId) {
      return exists.get(installationId) === undefined;
    },
    recordHeartbeat(installationId, version, nowMs) {
      upsert.run(installationId, version, nowMs, nowMs);
    },
    countAll() {
      return Number(countAll.get().n);
    },
    countActiveSince(sinceMs) {
      return Number(countSince.get(sinceMs).n);
    },
    versionsActiveSince(sinceMs) {
      return versionsSince.all(sinceMs).map((r) => ({ version: r.version, active30d: Number(r.active30d) }));
    },
    /** Installations whose first heartbeat is in [startMs, endMs). */
    countFirstSeenBetween(startMs, endMs) {
      return Number(countFirstSeenBetween.get(startMs, endMs).n);
    },
    /** Latest heartbeat time of any installation (aggregate MAX), or null when empty. */
    maxLastSeen() {
      const t = maxLastSeen.get().t;
      return t === null ? null : Number(t);
    },
    /** Newest installation's first heartbeat time (aggregate MAX), or null when empty. */
    maxFirstSeen() {
      const t = maxFirstSeen.get().t;
      return t === null ? null : Number(t);
    },
    /** Runs {@code fn} inside one read transaction so all its queries see the same snapshot. */
    readSnapshot(fn) {
      db.exec('BEGIN');
      try {
        return fn();
      } finally {
        db.exec('COMMIT');
      }
    },
    /** Test-only inspection of one row. */
    rowForTesting(installationId) {
      return db.prepare('SELECT installation_id, version, first_seen, last_seen FROM installations WHERE installation_id = ?').get(installationId);
    },
    /** Test-only: every table in the database. */
    tablesForTesting() {
      return db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").all().map((r) => r.name);
    },
    close() {
      db.close();
    },
  };
}
