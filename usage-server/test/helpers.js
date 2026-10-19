import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDatabase } from '../src/db.js';
import { createLogger } from '../src/log.js';
import { createServer } from '../src/server.js';

export const TOKEN = 'test-token-0123456789abcdef0123456789abcdef';
export const DAY = 24 * 60 * 60 * 1000;

/** A fresh temporary database + server on a random port, with an injectable clock and captured logs. */
export async function startTestServer(overrides = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'gzusage-'));
  const db = openDatabase(join(dir, 'usage.sqlite'));
  const logs = [];
  const logger = createLogger((line) => logs.push(line));
  const clock = { now: Date.parse('2026-10-19T12:00:00Z') };
  const config = {
    host: '127.0.0.1', port: 0, dbPath: join(dir, 'usage.sqlite'), statsToken: TOKEN,
    heartbeatsPerMinute: 10_000, newInstallationsPerHour: 10_000, ...overrides,
  };
  const server = createServer({ db, config, logger, clock: () => clock.now });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  return {
    db, logs, logger, clock, base,
    async close() {
      await new Promise((resolve) => server.close(resolve));
      try { db.close(); } catch { /* already closed */ }
      rmSync(dir, { recursive: true, force: true });
    },
  };
}

export function heartbeat(base, body, headers = {}) {
  return fetch(`${base}/api/gzcompanion/heartbeat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: typeof body === 'string' ? body : JSON.stringify(body),
    redirect: 'manual',
  });
}

export function stats(base, auth = `Bearer ${TOKEN}`) {
  return fetch(`${base}/api/gzcompanion/stats`, { headers: auth ? { Authorization: auth } : {}, redirect: 'manual' });
}

export function dashboard(base, auth) {
  return fetch(`${base}/stats`, { headers: auth ? { Authorization: auth } : {}, redirect: 'manual' });
}

export const ID_A = '3f2b1c4d-5e6f-4a1b-9c2d-0e1f2a3b4c5d';
export const ID_B = '7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d';
export const ID_C = 'c1d2e3f4-a5b6-4c7d-a8e9-f0a1b2c3d4e5';
