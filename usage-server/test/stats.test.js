import { test } from 'node:test';
import assert from 'node:assert/strict';
import { startTestServer, heartbeat, stats, dashboard, TOKEN, DAY, ID_A, ID_B, ID_C } from './helpers.js';
import { computeStats } from '../src/stats.js';
import { escapeHtml } from '../src/dashboard.js';

const HOUR = 60 * 60 * 1000;

/** Seeds installations whose LAST heartbeat was `ageMs` before now. */
function seed(t, entries) {
  for (const [id, version, ageMs] of entries) t.db.recordHeartbeat(id, version, t.clock.now - ageMs);
}

const IDS = Array.from({ length: 8 }, (_, i) => `${String(i + 1).repeat(8)}-0000-4000-8000-${String(i + 1).repeat(12)}`);

test('24h / 7d / 30d windows and the unique total are computed from last_seen', async () => {
  const t = await startTestServer();
  try {
    seed(t, [
      [IDS[0], '0.1.0-alpha.12', 1 * HOUR],
      [IDS[1], '0.1.0-alpha.12', 23 * HOUR],
      [IDS[2], '0.1.0-alpha.12', 25 * HOUR],
      [IDS[3], '0.1.0-alpha.11', 6 * DAY],
      [IDS[4], '0.1.0-alpha.11', 8 * DAY],
      [IDS[5], '0.1.0-alpha.10', 29 * DAY],
      [IDS[6], '0.1.0-alpha.10', 31 * DAY],
      [IDS[7], '0.1.0-alpha.9', 400 * DAY],
    ]);
    const s = computeStats(t.db, t.clock.now);
    assert.equal(s.uniqueInstallations, 8);
    assert.equal(s.active24h, 2);
    assert.equal(s.active7d, 4);
    assert.equal(s.active30d, 6);
  } finally {
    await t.close();
  }
});

test('window edges are inclusive at exactly 24h / 7d / 30d', async () => {
  const t = await startTestServer();
  try {
    seed(t, [[IDS[0], '1', DAY], [IDS[1], '1', 7 * DAY], [IDS[2], '1', 30 * DAY], [IDS[3], '1', 30 * DAY + 1]]);
    const s = computeStats(t.db, t.clock.now);
    assert.deepEqual([s.active24h, s.active7d, s.active30d], [1, 2, 3]);
  } finally {
    await t.close();
  }
});

test('versions aggregate active-30d installations by their CURRENT version, most used first', async () => {
  const t = await startTestServer();
  try {
    seed(t, [
      [IDS[0], '0.1.0-alpha.12', HOUR], [IDS[1], '0.1.0-alpha.12', 2 * DAY], [IDS[2], '0.1.0-alpha.12', 10 * DAY],
      [IDS[3], '0.1.0-alpha.11', 3 * DAY], [IDS[4], '0.1.0-alpha.10', 5 * DAY], [IDS[5], '0.1.0-alpha.11', 31 * DAY],
    ]);
    // IDS[4] upgrades: it now counts under the new version only.
    t.db.recordHeartbeat(IDS[4], '0.1.0-alpha.12', t.clock.now);
    assert.deepEqual(computeStats(t.db, t.clock.now).versions, [
      { version: '0.1.0-alpha.12', active30d: 4 },
      { version: '0.1.0-alpha.11', active30d: 1 },
    ]);
  } finally {
    await t.close();
  }
});

test('an empty database gives zeros', async () => {
  const t = await startTestServer();
  try {
    assert.deepEqual(computeStats(t.db, t.clock.now),
      { uniqueInstallations: 0, active24h: 0, active7d: 0, active30d: 0, versions: [] });
  } finally {
    await t.close();
  }
});

test('stats API requires the token and returns only aggregate numbers, never installation ids', async () => {
  const t = await startTestServer();
  try {
    for (const id of [ID_A, ID_B, ID_C]) await heartbeat(t.base, { installationId: id, version: '0.1.0-alpha.12' });
    for (const auth of [null, 'Bearer wrong-token', 'Bearer ', `Basic ${Buffer.from('user:wrong').toString('base64')}`, TOKEN, `Token ${TOKEN}`]) {
      const res = await stats(t.base, auth);
      assert.equal(res.status, 401, String(auth));
      assert.match(res.headers.get('www-authenticate'), /^Bearer/);
      const text = await res.text();
      assert.ok(!text.includes('uniqueInstallations'));
    }
    const res = await stats(t.base);
    assert.equal(res.status, 200);
    const text = await res.text();
    const body = JSON.parse(text);
    assert.deepEqual(Object.keys(body).sort(), ['active24h', 'active30d', 'active7d', 'uniqueInstallations', 'versions']);
    assert.equal(body.uniqueInstallations, 3);
    for (const v of body.versions) assert.deepEqual(Object.keys(v).sort(), ['active30d', 'version']);
    for (const id of [ID_A, ID_B, ID_C]) assert.ok(!text.toLowerCase().includes(id), 'no installation id in the response');
    assert.equal(res.headers.get('cache-control'), 'no-store');
    assert.equal(res.headers.get('access-control-allow-origin'), null, 'no CORS');
    assert.equal((await fetch(`${t.base}/api/gzcompanion/stats`, { method: 'POST', headers: { Authorization: `Bearer ${TOKEN}` } })).status, 405);
  } finally {
    await t.close();
  }
});

test('dashboard requires the same token (basic auth prompt for browsers) and shows only aggregates', async () => {
  const t = await startTestServer();
  try {
    for (const id of [ID_A, ID_B]) await heartbeat(t.base, { installationId: id, version: '0.1.0-alpha.12' });
    const anon = await dashboard(t.base);
    assert.equal(anon.status, 401);
    assert.match(anon.headers.get('www-authenticate'), /^Basic realm="gzcompanion-usage"/);
    assert.ok(!(await anon.text()).includes('Unika installationer'));
    assert.equal((await dashboard(t.base, `Basic ${Buffer.from('admin:nope').toString('base64')}`)).status, 401);

    for (const auth of [`Basic ${Buffer.from(`admin:${TOKEN}`).toString('base64')}`, `Bearer ${TOKEN}`]) {
      const res = await dashboard(t.base, auth);
      assert.equal(res.status, 200, auth.split(' ')[0]);
      const html = await res.text();
      for (const label of ['Unika installationer totalt', 'Aktiva senaste 24 timmarna', 'Aktiva senaste 7 dagarna', 'Aktiva senaste 30 dagarna', '0.1.0-alpha.12']) {
        assert.ok(html.includes(label), label);
      }
      for (const id of [ID_A, ID_B]) assert.ok(!html.toLowerCase().includes(id), 'no installation ids on the dashboard');
      assert.ok(!/<script/i.test(html), 'no JavaScript');
      assert.match(res.headers.get('content-security-policy'), /default-src 'none'/);
      assert.equal(res.headers.get('x-frame-options'), 'DENY');
    }
  } finally {
    await t.close();
  }
});

test('dashboard output is HTML-escaped', () => {
  assert.equal(escapeHtml('<b>"x"&\'y\'</b>'), '&lt;b&gt;&quot;x&quot;&amp;&#39;y&#39;&lt;/b&gt;');
});
