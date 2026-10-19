import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { startTestServer, heartbeat, stats, dashboard, TOKEN, DAY, ID_A, ID_B, ID_C } from './helpers.js';
import { computeStats, lastCalendarDays, zonedMidnight } from '../src/stats.js';
import { escapeHtml, jsonForScript, renderDashboard } from '../src/dashboard.js';

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

test('activity buckets are mutually exclusive, use the current last_seen, and add up to the total', async () => {
  const t = await startTestServer();
  try {
    seed(t, [
      [IDS[0], '1', 1 * HOUR], [IDS[1], '1', 23 * HOUR], [IDS[2], '1', 25 * HOUR], [IDS[3], '1', 6 * DAY],
      [IDS[4], '1', 8 * DAY], [IDS[5], '1', 29 * DAY], [IDS[6], '1', 31 * DAY], [IDS[7], '1', 400 * DAY],
    ]);
    const s = computeStats(t.db, t.clock.now);
    assert.deepEqual(s.activityBuckets, { active24h: 2, day2to7: 2, day8to30: 2, inactive30dPlus: 2 });
    const b = s.activityBuckets;
    assert.equal(b.active24h + b.day2to7 + b.day8to30 + b.inactive30dPlus, s.uniqueInstallations);
    // A heartbeat moves an installation into the 24 h bucket - it is never counted twice.
    t.db.recordHeartbeat(IDS[7], '1', t.clock.now);
    assert.deepEqual(computeStats(t.db, t.clock.now).activityBuckets,
      { active24h: 3, day2to7: 2, day8to30: 2, inactive30dPlus: 1 });
  } finally {
    await t.close();
  }
});

test('activity bucket edges: exactly 24h / 7d / 30d belong to the more recent bucket', async () => {
  const t = await startTestServer();
  try {
    const ages = [DAY, DAY + 1, 7 * DAY, 7 * DAY + 1, 30 * DAY, 30 * DAY + 1];
    seed(t, ages.map((age, i) => [IDS[i], '1', age]));
    const b = computeStats(t.db, t.clock.now).activityBuckets;
    assert.deepEqual(b, { active24h: 1, day2to7: 2, day8to30: 2, inactive30dPlus: 1 });
    assert.equal(b.active24h + b.day2to7 + b.day8to30 + b.inactive30dPlus, ages.length);
  } finally {
    await t.close();
  }
});

test('versions aggregate active-30d installations by CURRENT version with counts and percentages, most used first', async () => {
  const t = await startTestServer();
  try {
    seed(t, [
      [IDS[0], '0.1.0-alpha.12', HOUR], [IDS[1], '0.1.0-alpha.12', 2 * DAY], [IDS[2], '0.1.0-alpha.12', 10 * DAY],
      [IDS[3], '0.1.0-alpha.11', 3 * DAY], [IDS[4], '0.1.0-alpha.10', 5 * DAY], [IDS[5], '0.1.0-alpha.11', 31 * DAY],
    ]);
    // IDS[4] upgrades: it now counts under the new version only.
    t.db.recordHeartbeat(IDS[4], '0.1.0-alpha.12', t.clock.now);
    const s = computeStats(t.db, t.clock.now);
    assert.deepEqual(s.versions, [
      { version: '0.1.0-alpha.12', active30d: 4, percent: 80 },
      { version: '0.1.0-alpha.11', active30d: 1, percent: 20 },
    ]);
    assert.equal(s.versionCount, 2);
    assert.equal(s.versions.reduce((n, v) => n + v.active30d, 0), s.active30d);
  } finally {
    await t.close();
  }
});

test('version percentages are rounded to one decimal and ties sort by version name', async () => {
  const t = await startTestServer();
  try {
    seed(t, [[IDS[0], 'b', HOUR], [IDS[1], 'a', HOUR], [IDS[2], 'c', HOUR]]);
    assert.deepEqual(computeStats(t.db, t.clock.now).versions, [
      { version: 'a', active30d: 1, percent: 33.3 },
      { version: 'b', active30d: 1, percent: 33.3 },
      { version: 'c', active30d: 1, percent: 33.3 },
    ]);
  } finally {
    await t.close();
  }
});

test('latestHeartbeat = MAX(last_seen) and newestInstallation = MAX(first_seen)', async () => {
  const t = await startTestServer();
  try {
    const now = t.clock.now;
    t.db.recordHeartbeat(IDS[0], '1', now - 10 * DAY); // old installation ...
    t.db.recordHeartbeat(IDS[0], '1', now - HOUR);     // ... heard from recently
    t.db.recordHeartbeat(IDS[1], '1', now - 2 * DAY);  // newer installation, not heard from since
    const s = computeStats(t.db, now);
    assert.equal(s.latestHeartbeat, now - HOUR);
    assert.equal(s.newestInstallation, now - 2 * DAY);
  } finally {
    await t.close();
  }
});

test('new installations: 30 Swedish calendar days, oldest first, zero-filled, counted by first_seen', async () => {
  const t = await startTestServer(); // now = 2026-10-19 14:00 Swedish time
  try {
    const at = (iso) => Date.parse(iso);
    t.db.recordHeartbeat(IDS[0], '1', at('2026-10-18T22:30:00Z')); // 00:30 on 19 Oct in Sweden
    t.db.recordHeartbeat(IDS[1], '1', at('2026-10-18T21:59:00Z')); // 23:59 on 18 Oct in Sweden
    t.db.recordHeartbeat(IDS[2], '1', at('2026-10-05T08:00:00Z'));
    t.db.recordHeartbeat(IDS[3], '1', at('2026-10-05T18:00:00Z'));
    t.db.recordHeartbeat(IDS[4], '1', at('2026-09-19T22:00:00Z')); // 00:00 on 20 Sep: first day, included
    t.db.recordHeartbeat(IDS[5], '1', at('2026-09-19T21:59:59Z')); // 23:59 on 19 Sep: outside the window
    t.db.recordHeartbeat(IDS[2], '1', t.clock.now); // a later heartbeat never moves first_seen

    const days = computeStats(t.db, t.clock.now).newInstallations30d;
    assert.equal(days.length, 30);
    assert.equal(days[0].date, '2026-09-20');
    assert.equal(days[29].date, '2026-10-19');
    for (let i = 1; i < days.length; i++) {
      assert.equal(Date.parse(days[i].date) - Date.parse(days[i - 1].date), DAY, 'consecutive dates, none missing');
    }
    const byDate = Object.fromEntries(days.map((d) => [d.date, d.count]));
    assert.equal(byDate['2026-10-19'], 1);
    assert.equal(byDate['2026-10-18'], 1);
    assert.equal(byDate['2026-10-05'], 2);
    assert.equal(byDate['2026-09-20'], 1);
    assert.equal(days.filter((d) => d.count === 0).length, 26, 'every other day is present with count 0');
    assert.equal(days.reduce((n, d) => n + d.count, 0), 5);
    for (const d of days) assert.deepEqual(Object.keys(d).sort(), ['count', 'date']);
  } finally {
    await t.close();
  }
});

test('calendar days follow Swedish time across the daylight-saving change', () => {
  assert.equal(zonedMidnight(2026, 10, 19), Date.parse('2026-10-18T22:00:00Z')); // summer time, UTC+2
  assert.equal(zonedMidnight(2026, 11, 10), Date.parse('2026-11-09T23:00:00Z')); // winter time, UTC+1
  const days = lastCalendarDays(Date.parse('2026-11-10T12:00:00Z'), 30);
  const dstDay = days.find((d) => d.date === '2026-10-25');
  assert.equal(dstDay.start, Date.parse('2026-10-24T22:00:00Z'));
  assert.equal(dstDay.end - dstDay.start, 25 * HOUR, 'the day the clocks go back has 25 hours');
  for (let i = 1; i < days.length; i++) assert.equal(days[i].start, days[i - 1].end, 'days are contiguous');
});

test('an empty database gives zeros, nulls and 30 zero days', async () => {
  const t = await startTestServer();
  try {
    const s = computeStats(t.db, t.clock.now);
    assert.equal(s.newInstallations30d.length, 30);
    assert.ok(s.newInstallations30d.every((d) => d.count === 0));
    const { newInstallations30d, ...rest } = s;
    assert.deepEqual(rest, {
      generatedAt: t.clock.now, timeZone: 'Europe/Stockholm',
      uniqueInstallations: 0, active24h: 0, active7d: 0, active30d: 0,
      latestHeartbeat: null, newestInstallation: null, versionCount: 0,
      activityBuckets: { active24h: 0, day2to7: 0, day8to30: 0, inactive30dPlus: 0 },
      versions: [],
    });
    assert.ok(renderDashboard(s).includes('Unika installationer'), 'the dashboard renders for an empty database');
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
      const challenge = res.headers.get('www-authenticate');
      assert.match(challenge, /Basic realm="gzcompanion-usage"/, 'browsers can re-send their dashboard login');
      assert.match(challenge, /Bearer realm="gzcompanion-usage"/);
      assert.ok(!(await res.text()).includes('uniqueInstallations'));
    }
    const res = await stats(t.base);
    assert.equal(res.status, 200);
    const text = await res.text();
    const body = JSON.parse(text);
    assert.deepEqual(Object.keys(body).sort(), ['active24h', 'active30d', 'active7d', 'activityBuckets', 'generatedAt',
      'latestHeartbeat', 'newInstallations30d', 'newestInstallation', 'timeZone', 'uniqueInstallations', 'versionCount', 'versions']);
    assert.equal(body.uniqueInstallations, 3);
    assert.deepEqual(Object.keys(body.activityBuckets).sort(), ['active24h', 'day2to7', 'day8to30', 'inactive30dPlus']);
    for (const d of body.newInstallations30d) assert.deepEqual(Object.keys(d).sort(), ['count', 'date']);
    for (const v of body.versions) assert.deepEqual(Object.keys(v).sort(), ['active30d', 'percent', 'version']);
    for (const id of [ID_A, ID_B, ID_C]) {
      assert.ok(!text.toLowerCase().includes(id), 'no installation id in the response');
      assert.ok(!text.includes(id.slice(0, 8)), 'not even a fragment of one');
    }
    assert.equal(res.headers.get('cache-control'), 'no-store');
    assert.equal(res.headers.get('access-control-allow-origin'), null, 'no CORS');
    assert.equal((await fetch(`${t.base}/api/gzcompanion/stats`, { method: 'POST', headers: { Authorization: `Bearer ${TOKEN}` } })).status, 405);
    const basic = await stats(t.base, `Basic ${Buffer.from(`admin:${TOKEN}`).toString('base64')}`);
    assert.equal(basic.status, 200, 'the dashboard login (Basic) works for the API the page refreshes from');
  } finally {
    await t.close();
  }
});

test('dashboard requires the same token and shows only aggregates, with no token and no external resources', async () => {
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
      for (const label of ['GZ Companion', 'Användningsstatistik', 'Online', 'Senast uppdaterad', 'Uppdatera',
        'Unika installationer', 'Aktiva 24 timmar', 'Aktiva 7 dagar', 'Aktiva 30 dagar', 'Senaste heartbeat',
        'Nyaste installation', 'Versioner i bruk', 'Nya installationer – senaste 30 dagarna', 'Versioner', 'Aktivitet',
        'Kan inte uppdatera', '0.1.0-alpha.12']) {
        assert.ok(html.includes(label), label);
      }
      for (const id of [ID_A, ID_B]) {
        assert.ok(!html.toLowerCase().includes(id), 'no installation ids on the dashboard');
        assert.ok(!html.includes(id.slice(0, 8)), 'not even a fragment of one');
      }
      for (const secret of [TOKEN, TOKEN.slice(0, 16), 'Authorization', 'Bearer', 'STATS_TOKEN', 'localStorage', 'sessionStorage', 'document.cookie']) {
        assert.ok(!html.includes(secret), `dashboard must not contain ${secret}`);
      }
      assert.ok(!/<link|<img|<iframe|\ssrc=/i.test(html), 'no external resources');
      const urls = html.match(/https?:\/\/[^\s'"<>)]+/g) || [];
      assert.deepEqual([...new Set(urls)], ['http://www.w3.org/2000/svg'], 'only the SVG namespace, no CDN or analytics');
      assert.ok(html.includes("const API = '/api/gzcompanion/stats'"), 'refreshes from the same-origin private API');
      assert.ok(!/\?[a-z_]*token/i.test(html), 'no token in any query string');

      const csp = res.headers.get('content-security-policy');
      assert.match(csp, /default-src 'none'/);
      assert.match(csp, /connect-src 'self'/);
      const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
      assert.equal(scripts.length, 1, 'exactly one executable inline script');
      const hash = createHash('sha256').update(scripts[0], 'utf8').digest('base64');
      assert.ok(csp.includes(`script-src 'sha256-${hash}'`), 'the CSP allows exactly that script by hash');
      assert.ok(!/script-src[^;]*unsafe-inline/.test(csp), 'no unsafe-inline scripts');
      assert.equal(res.headers.get('x-frame-options'), 'DENY');
    }
  } finally {
    await t.close();
  }
});

test('dashboard output is HTML-escaped and embedded JSON cannot break out of its script element', async () => {
  assert.equal(escapeHtml('<b>"x"&\'y\'</b>'), '&lt;b&gt;&quot;x&quot;&amp;&#39;y&#39;&lt;/b&gt;');
  const evil = '</script><script>alert(1)</script>& ';
  const json = jsonForScript({ version: evil });
  assert.ok(!json.includes('<') && !json.includes('>') && !json.includes('&'));
  assert.deepEqual(JSON.parse(json), { version: evil }, 'round-trips exactly');

  const t = await startTestServer();
  try {
    const s = computeStats(t.db, t.clock.now);
    s.versions = [{ version: evil, active30d: 1, percent: 100 }];
    const html = renderDashboard(s);
    assert.equal((html.match(/<\/script>/g) || []).length, 2, 'only the two real closing script tags');
    assert.ok(!html.includes('alert(1)</script>'));
  } finally {
    await t.close();
  }
});
