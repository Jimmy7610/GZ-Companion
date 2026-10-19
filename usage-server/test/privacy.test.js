import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { startTestServer, heartbeat, stats, dashboard, TOKEN, ID_A, ID_B } from './helpers.js';
import { loadConfig } from '../src/config.js';
import { isAuthorized } from '../src/auth.js';
import { createLogger } from '../src/log.js';

const SRC = new URL('../src/', import.meta.url);

test('logs never contain installation ids, IPs, headers or payloads - across successes, rejections and errors', async () => {
  const t = await startTestServer({ newInstallationsPerHour: 1 });
  try {
    const ua = 'SecretAgent/9.9 (user=steve)';
    await heartbeat(t.base, { installationId: ID_A, version: '0.1.0-alpha.12' }, { 'User-Agent': ua, 'X-Forwarded-For': '203.0.113.7' });
    await heartbeat(t.base, { installationId: ID_B, version: '0.1.0-alpha.12' }, { 'User-Agent': ua }); // rate-limited new id
    await heartbeat(t.base, `{"installationId":"${ID_B}","version":"1","extra":"payload-marker"}`);
    await heartbeat(t.base, '{"broken": "payload-marker');
    await stats(t.base, 'Bearer wrong');
    await stats(t.base);
    await dashboard(t.base, `Bearer ${TOKEN}`);
    t.db.close();
    await heartbeat(t.base, { installationId: ID_A, version: '0.1.0-alpha.12' }); // storage error
    t.logger.flushCounts();

    const all = t.logs.join('\n');
    assert.ok(all.length > 0, 'something operational was logged');
    for (const forbidden of [ID_A, ID_B, ID_A.toUpperCase(), '127.0.0.1', '203.0.113.7', ua, 'payload-marker', '0.1.0-alpha.12', TOKEN, 'Bearer']) {
      assert.ok(!all.includes(forbidden), `log must not contain ${forbidden}`);
    }
    assert.match(all, /heartbeats accepted=1 rejected=2 rate-limited=1 failed=1/);
  } finally {
    await t.close();
  }
});

test('the logger has no way to log request data: errors are reduced to their code/type', () => {
  const lines = [];
  const logger = createLogger((l) => lines.push(l));
  const err = new Error(`bad id ${ID_A}`);
  err.code = 'ERR_SQLITE_ERROR';
  logger.error('heartbeat storage', err);
  logger.error('request', new TypeError(`leak ${ID_B}`));
  assert.deepEqual(lines, ['[gzcompanion-usage] error in heartbeat storage: ERR_SQLITE_ERROR', '[gzcompanion-usage] error in request: TypeError']);
});

test('the application never reads the client address or persists headers/bodies (source scan)', () => {
  const files = readdirSync(SRC).filter((f) => f.endsWith('.js'));
  assert.ok(files.length >= 8);
  for (const f of files) {
    const src = readFileSync(join(SRC.pathname.replace(/^\/([A-Za-z]:)/, '$1'), f), 'utf8');
    for (const forbidden of ['remoteAddress', 'socket.remote', 'x-forwarded-for', 'cf-connecting-ip', 'x-real-ip', "headers['user-agent']",
      'console.log', 'console.error', 'rawHeaders']) {
      assert.ok(!src.toLowerCase().includes(forbidden.toLowerCase()), `${f} must not use ${forbidden}`);
    }
  }
  const db = readFileSync(join(SRC.pathname.replace(/^\/([A-Za-z]:)/, '$1'), 'db.js'), 'utf8');
  assert.ok(!/\$\{|\+\s*['"`]?\s*(id|version)/.test(db.replace(/\/\*[\s\S]*?\*\//g, '')), 'no string-built SQL in db.js');
});

test('the database holds one table with exactly the four permitted columns', async () => {
  const t = await startTestServer();
  try {
    await heartbeat(t.base, { installationId: ID_A, version: '0.1.0-alpha.12' }, { 'User-Agent': 'ua', 'X-Forwarded-For': '203.0.113.7' });
    assert.deepEqual(t.db.tablesForTesting(), ['installations']);
    assert.deepEqual(Object.keys(t.db.rowForTesting(ID_A)), ['installation_id', 'version', 'first_seen', 'last_seen']);
  } finally {
    await t.close();
  }
});

test('configuration: the stats token must come from the environment and be long enough', () => {
  assert.throws(() => loadConfig({}), /STATS_TOKEN/);
  assert.throws(() => loadConfig({ STATS_TOKEN: 'short' }), /STATS_TOKEN/);
  const c = loadConfig({ STATS_TOKEN: TOKEN });
  assert.equal(c.host, '127.0.0.1', 'binds to localhost by default (behind the tunnel)');
  assert.equal(c.port, 8787);
  assert.throws(() => loadConfig({ STATS_TOKEN: TOKEN, PORT: 'abc' }), /PORT/);
  assert.throws(() => loadConfig({ STATS_TOKEN: TOKEN, HEARTBEATS_PER_MINUTE: '0' }), /HEARTBEATS_PER_MINUTE/);
});

test('token comparison accepts only the exact token, via bearer or basic', () => {
  assert.ok(isAuthorized(`Bearer ${TOKEN}`, TOKEN));
  assert.ok(isAuthorized(`basic ${Buffer.from(`x:${TOKEN}`).toString('base64')}`, TOKEN));
  for (const h of [undefined, '', 'Bearer', `Bearer ${TOKEN}x`, `Bearer ${TOKEN.slice(1)}`, `Basic ${Buffer.from(TOKEN).toString('base64')}`, `Digest ${TOKEN}`]) {
    assert.ok(!isAuthorized(h, TOKEN), String(h));
  }
  assert.ok(!isAuthorized(`Bearer ${TOKEN}`, ''), 'an unset token never authorizes');
});
