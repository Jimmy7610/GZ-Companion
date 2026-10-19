import { test } from 'node:test';
import assert from 'node:assert/strict';
import { request } from 'node:http';
import { startTestServer, heartbeat, stats, ID_A, ID_B, DAY } from './helpers.js';

async function json(res) {
  return JSON.parse(await res.text());
}

test('first heartbeat creates exactly one installation', async () => {
  const t = await startTestServer();
  try {
    const res = await heartbeat(t.base, { installationId: ID_A, version: '0.1.0-alpha.12' });
    assert.equal(res.status, 200);
    assert.deepEqual(await json(res), { ok: true });
    assert.equal(t.db.countAll(), 1);
    const row = t.db.rowForTesting(ID_A);
    assert.equal(row.version, '0.1.0-alpha.12');
    assert.equal(row.first_seen, t.clock.now);
    assert.equal(row.last_seen, t.clock.now);
  } finally {
    await t.close();
  }
});

test('repeat heartbeats keep one installation, update last_seen and version, never first_seen', async () => {
  const t = await startTestServer();
  try {
    const first = t.clock.now;
    await heartbeat(t.base, { installationId: ID_A, version: '0.1.0-alpha.12' });
    t.clock.now += DAY + 5000;
    await heartbeat(t.base, { installationId: ID_A, version: '0.1.0-alpha.12' });
    assert.equal(t.db.countAll(), 1, 'unique count unchanged');
    assert.equal(t.db.rowForTesting(ID_A).last_seen, t.clock.now, 'last_seen updated');
    assert.equal(t.db.rowForTesting(ID_A).first_seen, first, 'first_seen unchanged');

    t.clock.now += DAY;
    await heartbeat(t.base, { installationId: ID_A, version: '0.1.0-alpha.13' });
    const row = t.db.rowForTesting(ID_A);
    assert.equal(row.version, '0.1.0-alpha.13', 'version updated');
    assert.equal(row.first_seen, first);
    assert.equal(t.db.countAll(), 1);
  } finally {
    await t.close();
  }
});

test('ids are case-normalised so one installation is never counted twice', async () => {
  const t = await startTestServer();
  try {
    await heartbeat(t.base, { installationId: ID_A.toUpperCase(), version: '1.0.0' });
    await heartbeat(t.base, { installationId: ID_A, version: '1.0.0' });
    assert.equal(t.db.countAll(), 1);
  } finally {
    await t.close();
  }
});

test('malformed UUIDs are rejected and nothing is stored', async () => {
  const t = await startTestServer();
  try {
    for (const id of ['not-a-uuid', '', '3f2b1c4d5e6f4a1b9c2d0e1f2a3b4c5d', '3f2b1c4d-5e6f-4a1b-9c2d-0e1f2a3b4c5', 'gggggggg-5e6f-4a1b-9c2d-0e1f2a3b4c5d',
      '3f2b1c4d-5e6f-0a1b-9c2d-0e1f2a3b4c5d', '3f2b1c4d-5e6f-4a1b-1c2d-0e1f2a3b4c5d', ` ${ID_A}`, 42, null, true, [ID_A], { id: ID_A }]) {
      const res = await heartbeat(t.base, { installationId: id, version: '1.0.0' });
      assert.equal(res.status, 400, `id ${JSON.stringify(id)}`);
      assert.equal((await json(res)).ok, false);
    }
    assert.equal(t.db.countAll(), 0);
  } finally {
    await t.close();
  }
});

test('malformed versions are rejected', async () => {
  const t = await startTestServer();
  try {
    for (const version of ['', ' ', 'x'.repeat(65), '<script>', '1.0 beta', '-1.0', '1.0\n', 'versión', '1;2', 12, null, ['1.0'], { v: 1 }]) {
      const res = await heartbeat(t.base, { installationId: ID_A, version });
      assert.equal(res.status, 400, `version ${JSON.stringify(version)}`);
    }
    for (const version of ['0.1.0-alpha.12', '1.0.0', '2.0.0+build.7', '1.2.3_rc1', 'x'.repeat(64)]) {
      assert.equal((await heartbeat(t.base, { installationId: ID_A, version })).status, 200, version);
    }
  } finally {
    await t.close();
  }
});

test('missing fields, extra fields and non-object bodies are rejected', async () => {
  const t = await startTestServer();
  try {
    const bodies = [
      {}, { installationId: ID_A }, { version: '1.0.0' },
      { installationId: ID_A, version: '1.0.0', username: 'Steve' },
      { installationId: ID_A, version: '1.0.0', ip: '1.2.3.4' },
      '{"installationId":"' + ID_A + '","version":"1.0.0","__proto__":{"x":1}}',
      '[]', '"text"', '42', 'null', 'true',
    ];
    for (const body of bodies) {
      const res = await heartbeat(t.base, body);
      assert.equal(res.status, 400, JSON.stringify(body));
    }
    assert.equal(t.db.countAll(), 0);
  } finally {
    await t.close();
  }
});

test('malformed JSON is rejected', async () => {
  const t = await startTestServer();
  try {
    for (const body of ['{', '{"installationId":', "{'installationId':'x'}", 'installationId=x&version=1', '{"a":1}}', '']) {
      const res = await heartbeat(t.base, body);
      assert.equal(res.status, 400, JSON.stringify(body));
      assert.deepEqual(await json(res), { ok: false, error: 'malformed JSON' });
    }
  } finally {
    await t.close();
  }
});

test('oversized bodies are rejected with 413 (declared and streamed)', async () => {
  const t = await startTestServer();
  try {
    const big = JSON.stringify({ installationId: ID_A, version: '1.0.0', pad: 'x'.repeat(5000) });
    assert.equal((await heartbeat(t.base, big)).status, 413);

    // Chunked upload without Content-Length: the limit is enforced while streaming.
    const status = await new Promise((resolve, reject) => {
      const url = new URL(`${t.base}/api/gzcompanion/heartbeat`);
      const req = request({ host: url.hostname, port: url.port, path: url.pathname, method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Transfer-Encoding': 'chunked' } }, (res) => {
        res.resume();
        resolve(res.statusCode);
      });
      req.on('error', (err) => (err.code === 'ECONNRESET' || err.code === 'EPIPE' ? resolve(413) : reject(err)));
      for (let i = 0; i < 10; i++) req.write('x'.repeat(512));
      req.end();
    });
    assert.equal(status, 413);
    assert.equal(t.db.countAll(), 0);
  } finally {
    await t.close();
  }
});

test('only POST with application/json is accepted on the heartbeat route', async () => {
  const t = await startTestServer();
  try {
    for (const method of ['GET', 'PUT', 'DELETE', 'PATCH']) {
      const res = await fetch(`${t.base}/api/gzcompanion/heartbeat`, { method, redirect: 'manual' });
      assert.equal(res.status, 405, method);
      assert.equal(res.headers.get('allow'), 'POST');
    }
    const res = await heartbeat(t.base, JSON.stringify({ installationId: ID_A, version: '1.0.0' }), { 'Content-Type': 'text/plain' });
    assert.equal(res.status, 415);
    assert.equal(t.db.countAll(), 0);
  } finally {
    await t.close();
  }
});

test('SQL-injection-like input is rejected by validation and cannot affect the database', async () => {
  const t = await startTestServer();
  try {
    await heartbeat(t.base, { installationId: ID_A, version: '1.0.0' });
    const attacks = [
      { installationId: "x'; DROP TABLE installations; --", version: '1.0.0' },
      { installationId: ID_B, version: "1.0'); DROP TABLE installations; --" },
      { installationId: ID_B, version: '1 OR 1=1' },
    ];
    for (const body of attacks) assert.equal((await heartbeat(t.base, body)).status, 400);
    assert.equal(t.db.countAll(), 1);

    // Even bypassing validation, values are bound as parameters - stored literally, schema intact.
    const evil = "'); DELETE FROM installations; --";
    t.db.recordHeartbeat(evil, evil, t.clock.now);
    assert.equal(t.db.countAll(), 2);
    assert.equal(t.db.rowForTesting(evil).version, evil);
    assert.deepEqual(t.db.tablesForTesting(), ['installations'], 'no other tables, nothing dropped');
  } finally {
    await t.close();
  }
});

test('unknown routes 404 and responses never redirect or leak stack traces', async () => {
  const t = await startTestServer();
  try {
    for (const path of ['/', '/api', '/api/gzcompanion/heartbeat/', '/api/gzcompanion/heartbeats', '/admin', '/stats.json']) {
      const res = await fetch(t.base + path, { redirect: 'manual' });
      assert.equal(res.status, 404, path);
      assert.equal(res.headers.get('location'), null);
    }
  } finally {
    await t.close();
  }
});

test('a database failure answers 503 without internals', async () => {
  const t = await startTestServer();
  try {
    t.db.close();
    const res = await heartbeat(t.base, { installationId: ID_A, version: '1.0.0' });
    assert.equal(res.status, 503);
    const text = await res.text();
    assert.deepEqual(JSON.parse(text), { ok: false, error: 'temporarily unavailable' });
    assert.ok(!/at |\.js|sqlite|Error:/i.test(text), 'no stack trace or internals');
    const s = await stats(t.base);
    assert.equal(s.status, 503);
  } finally {
    await t.close();
  }
});

test('abuse limits are global (no client identity) and never block known installations from the new-id cap', async () => {
  const t = await startTestServer({ heartbeatsPerMinute: 1000, newInstallationsPerHour: 2 });
  try {
    const ids = ['11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', '33333333-3333-4333-8333-333333333333'];
    assert.equal((await heartbeat(t.base, { installationId: ids[0], version: '1.0.0' })).status, 200);
    assert.equal((await heartbeat(t.base, { installationId: ids[1], version: '1.0.0' })).status, 200);
    const limited = await heartbeat(t.base, { installationId: ids[2], version: '1.0.0' });
    assert.equal(limited.status, 429, 'third new id this hour is limited');
    assert.equal((await heartbeat(t.base, { installationId: ids[0], version: '1.0.1' })).status, 200, 'known installation still accepted');
    assert.equal(t.db.countAll(), 2);
    t.clock.now += 60 * 60 * 1000;
    assert.equal((await heartbeat(t.base, { installationId: ids[2], version: '1.0.0' })).status, 200, 'refills over time');
  } finally {
    await t.close();
  }

  const t2 = await startTestServer({ heartbeatsPerMinute: 3 });
  try {
    for (let i = 0; i < 3; i++) assert.equal((await heartbeat(t2.base, { installationId: ID_A, version: '1.0.0' })).status, 200);
    const res = await heartbeat(t2.base, { installationId: ID_A, version: '1.0.0' });
    assert.equal(res.status, 429);
    assert.equal(res.headers.get('retry-after'), '60');
  } finally {
    await t2.close();
  }
});
