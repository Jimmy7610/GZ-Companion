/**
 * The private dashboard (/stats): one self-contained HTML page of AGGREGATE numbers only - no
 * installation ids, IPs, identities or per-user data, and never the STATS_TOKEN (the renderer does
 * not even receive it). Inline CSS + one inline script (allowed by its SHA-256 hash in the CSP);
 * no external assets, fonts, CDNs or analytics.
 *
 * The initial aggregates are embedded as JSON so the page renders at once; the script then refreshes
 * from /api/gzcompanion/stats every 60 s. That request is same-origin and inside the protection space
 * the browser already authenticated for /stats, so the browser re-sends its Basic credentials by
 * itself - no token in the page, in JavaScript, or in any URL.
 */
import { createHash } from 'node:crypto';
import { TIME_ZONE } from './stats.js';

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) => ESCAPES[c]);
}

/** JSON that is safe inside a <script type="application/json"> element. */
export function jsonForScript(value) {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c').replace(/>/g, '\\u003e').replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');
}

const numberFormat = new Intl.NumberFormat('sv-SE');
const dateTimeFormat = new Intl.DateTimeFormat('sv-SE', {
  timeZone: TIME_ZONE, day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
});
const clockFormat = new Intl.DateTimeFormat('sv-SE', {
  timeZone: TIME_ZONE, hour: '2-digit', minute: '2-digit', second: '2-digit',
});
const fmt = (n) => escapeHtml(numberFormat.format(n));
const fmtTime = (ms) => (ms === null || ms === undefined ? '–' : escapeHtml(dateTimeFormat.format(new Date(ms))));

const STYLE = `
:root{color-scheme:dark;--bg:#0b1118;--panel:#121b26;--panel-2:#172231;--border:#263445;--text:#f1f5f9;
--muted:#a3b1c2;--accent:#34d399;--accent-ink:#052e1f;--blue:#60a5fa;--amber:#fbbf24;--slate:#64748b;--danger:#f87171;
--radius:12px;--font:system-ui,-apple-system,"Segoe UI",Roboto,Ubuntu,sans-serif;--mono:ui-monospace,"Cascadia Mono",Consolas,monospace}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 var(--font)}
.wrap{max-width:1180px;margin:0 auto;padding:20px 16px 40px}
.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap;border:0}
header.top{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}
.brand{display:flex;align-items:center;gap:12px;min-width:0}
.logo{width:40px;height:40px;border-radius:10px;background:linear-gradient(135deg,#34d399,#059669);color:var(--accent-ink);
display:grid;place-items:center;font-weight:800;font-size:15px;flex:none}
.brand-name{margin:0;font-size:13px;font-weight:600;color:var(--accent);letter-spacing:.02em}
h1{margin:0;font-size:22px;line-height:1.2}
.controls{display:flex;flex-wrap:wrap;align-items:center;gap:10px 14px}
.status{display:inline-flex;align-items:center;gap:8px;padding:5px 12px;border-radius:999px;border:1px solid var(--border);
background:var(--panel);font-size:13px;font-weight:600}
.dot{width:9px;height:9px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 3px rgba(52,211,153,.18)}
.status[data-state="loading"] .dot{background:var(--blue);box-shadow:0 0 0 3px rgba(96,165,250,.18)}
.status[data-state="error"]{border-color:rgba(248,113,113,.5)}
.status[data-state="error"] .dot{background:var(--danger);box-shadow:0 0 0 3px rgba(248,113,113,.2)}
.updated{color:var(--muted);font-size:13px;font-variant-numeric:tabular-nums}
button{font:inherit;font-size:14px;font-weight:600;color:var(--accent-ink);background:var(--accent);border:0;border-radius:8px;
padding:8px 16px;cursor:pointer;min-height:38px}
button:hover{background:#6ee7b7}
button:disabled{opacity:.6;cursor:progress}
:focus-visible{outline:3px solid #93c5fd;outline-offset:2px;border-radius:6px}
.notice{display:none;margin:0 0 16px;padding:10px 14px;border-radius:10px;border:1px solid rgba(248,113,113,.45);
background:rgba(248,113,113,.08);color:#fecaca;font-size:14px}
.notice.show{display:block}
.kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:12px}
.card{background:var(--panel);border:1px solid var(--border);border-radius:var(--radius);padding:16px 18px;min-width:0}
.kpi .label{margin:0;color:var(--muted);font-size:13px;font-weight:600}
.kpi .value{margin:6px 0 2px;font-size:36px;font-weight:750;line-height:1.1;font-variant-numeric:tabular-nums}
.kpi .hint{margin:0;color:var(--muted);font-size:12.5px;min-height:1.5em}
.kpi.primary{background:linear-gradient(160deg,rgba(52,211,153,.12),var(--panel) 60%);border-color:rgba(52,211,153,.35)}
.facts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:0 0 20px;padding:0}
.fact{display:flex;flex-direction:column;gap:2px;background:var(--panel-2);border:1px solid var(--border);border-radius:var(--radius);padding:12px 16px;min-width:0}
.fact dt{color:var(--muted);font-size:12.5px;font-weight:600}
.fact dd{margin:0;font-size:16px;font-weight:650;font-variant-numeric:tabular-nums}
.fact dd small{display:block;color:var(--muted);font-size:12px;font-weight:500}
.charts{display:grid;grid-template-columns:minmax(0,3fr) minmax(0,2fr);gap:12px}
.charts .wide{grid-column:1/-1}
.panel-head{display:flex;flex-wrap:wrap;align-items:baseline;justify-content:space-between;gap:4px 12px;margin-bottom:12px}
h2{margin:0;font-size:16px}
.panel-meta{color:var(--muted);font-size:13px}
.chart{position:relative;width:100%;min-height:200px}
.chart svg{display:block;width:100%;height:auto;overflow:visible}
.chart text{fill:var(--muted);font:12px var(--font)}
.chart .grid line{stroke:var(--border);stroke-width:1}
.chart .bar{fill:var(--accent)}
.chart .bar.zero{fill:var(--slate);opacity:.55}
.chart .bar.today{fill:#6ee7b7}
details{margin-top:10px;color:var(--muted);font-size:13px}
summary{cursor:pointer;width:max-content;max-width:100%}
table{width:100%;border-collapse:collapse;margin-top:8px;font-variant-numeric:tabular-nums}
th,td{text-align:left;padding:4px 6px;border-top:1px solid var(--border)}
td.n,th.n{text-align:right}
.table-scroll{max-height:240px;overflow:auto}
.versions{list-style:none;margin:0;padding:0;display:grid;gap:12px}
.versions li{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:4px 12px;align-items:baseline}
.v-name{font-family:var(--mono);font-size:13.5px;overflow-wrap:anywhere}
.v-num{font-size:13.5px;font-variant-numeric:tabular-nums;white-space:nowrap}
.v-num span{color:var(--muted)}
.track{grid-column:1/-1;height:10px;border-radius:5px;background:var(--panel-2);overflow:hidden}
.fill{height:100%;border-radius:5px;background:var(--accent);min-width:3px;transition:width .4s ease}
.stack{display:flex;height:18px;border-radius:9px;overflow:hidden;background:var(--panel-2);margin:4px 0 16px}
.stack span{height:100%;transition:width .4s ease}
.legend{list-style:none;margin:0;padding:0;display:grid;gap:10px}
.legend li{display:grid;grid-template-columns:12px minmax(0,1fr) auto;gap:10px;align-items:center;font-size:14px}
.swatch{width:12px;height:12px;border-radius:3px}
.legend .count{font-variant-numeric:tabular-nums;font-weight:650;white-space:nowrap}
.legend .count span{color:var(--muted);font-weight:500}
.c-24h{background:var(--accent)} .c-7d{background:var(--blue)} .c-30d{background:var(--amber)} .c-old{background:var(--slate)}
.note{margin:14px 0 0;color:var(--muted);font-size:12.5px}
.empty{color:var(--muted);font-size:14px;margin:8px 0}
footer{margin-top:24px;color:var(--muted);font-size:12.5px;line-height:1.6}
@media (max-width:900px){.kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.charts{grid-template-columns:minmax(0,1fr)}}
@media (max-width:560px){.facts{grid-template-columns:minmax(0,1fr)}.kpi .value{font-size:30px}h1{font-size:19px}
.controls{width:100%;justify-content:space-between}.updated{order:3;width:100%}.card{padding:14px}}
@media (max-width:340px){.kpis{grid-template-columns:minmax(0,1fr)}}
@media (prefers-reduced-motion:reduce){.fill,.stack span{transition:none}}
`;

/** The page logic. Static (no interpolation), so its CSP hash is computed once. */
const SCRIPT = `(() => {
  'use strict';
  const API = '/api/gzcompanion/stats';
  const REFRESH_MS = 60000;
  const TZ = 'Europe/Stockholm';
  const nf = new Intl.NumberFormat('sv-SE');
  const pf = new Intl.NumberFormat('sv-SE', { maximumFractionDigits: 1 });
  const clock = new Intl.DateTimeFormat('sv-SE', { timeZone: TZ, hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const stamp = new Intl.DateTimeFormat('sv-SE', { timeZone: TZ, day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  const dayShort = new Intl.DateTimeFormat('sv-SE', { timeZone: 'UTC', day: 'numeric', month: 'short' });
  const dayLong = new Intl.DateTimeFormat('sv-SE', { timeZone: 'UTC', weekday: 'long', day: 'numeric', month: 'long' });
  const rel = new Intl.RelativeTimeFormat('sv', { numeric: 'auto' });
  const $ = (id) => document.getElementById(id);
  const SVG = 'http://www.w3.org/2000/svg';
  let data = null;
  let loading = false;

  function el(tag, attrs, text) {
    const e = document.createElement(tag);
    for (const k in attrs || {}) e.setAttribute(k, attrs[k]);
    if (text !== undefined) e.textContent = text;
    return e;
  }
  function svg(tag, attrs, text) {
    const e = document.createElementNS(SVG, tag);
    for (const k in attrs || {}) e.setAttribute(k, attrs[k]);
    if (text !== undefined) e.textContent = text;
    return e;
  }
  const pct = (part, whole) => (whole > 0 ? (part * 100) / whole : 0);
  const utcDay = (iso) => new Date(iso + 'T12:00:00Z');
  function ago(ms) {
    if (ms === null || ms === undefined) return '';
    const s = Math.round((ms - Date.now()) / 1000);
    const a = Math.abs(s);
    if (a < 60) return 'nyss';
    if (a < 3600) return rel.format(Math.round(s / 60), 'minute');
    if (a < 86400) return rel.format(Math.round(s / 3600), 'hour');
    return rel.format(Math.round(s / 86400), 'day');
  }
  function setFact(id, ms) {
    const dd = $(id);
    dd.textContent = ms === null || ms === undefined ? '–' : stamp.format(new Date(ms));
    if (ms !== null && ms !== undefined) dd.appendChild(el('small', {}, ago(ms)));
  }

  function renderKpis(d) {
    $('k-total').textContent = nf.format(d.uniqueInstallations);
    $('k-24h').textContent = nf.format(d.active24h);
    $('k-7d').textContent = nf.format(d.active7d);
    $('k-30d').textContent = nf.format(d.active30d);
    const share = (n) => (d.uniqueInstallations > 0 ? pf.format(pct(n, d.uniqueInstallations)) + ' % av alla' : '');
    $('h-total').textContent = d.uniqueInstallations === 1 ? '1 installation sedan start' : 'sedan start';
    $('h-24h').textContent = share(d.active24h);
    $('h-7d').textContent = share(d.active7d);
    $('h-30d').textContent = share(d.active30d);
    setFact('f-heartbeat', d.latestHeartbeat);
    setFact('f-newest', d.newestInstallation);
    $('f-versions').textContent = nf.format(d.versionCount);
    $('f-versions').appendChild(el('small', {}, 'aktiva senaste 30 dagarna'));
  }

  function renderDaily(d) {
    const box = $('chart-daily');
    const days = d.newInstallations30d;
    const total = days.reduce((s, x) => s + x.count, 0);
    $('daily-total').textContent = nf.format(total) + (total === 1 ? ' ny installation' : ' nya installationer');
    const w = Math.max(280, Math.round(box.clientWidth || 600));
    const h = w < 480 ? 190 : 230;
    const pad = { l: 30, r: 6, t: 10, b: 26 };
    const pw = w - pad.l - pad.r;
    const ph = h - pad.t - pad.b;
    const max = Math.max(...days.map((x) => x.count), 0);
    const step = Math.max(1, Math.ceil(max / 4));
    const top = step * 4;
    const s = svg('svg', { viewBox: '0 0 ' + w + ' ' + h, width: w, height: h, role: 'img',
      'aria-label': 'Nya installationer per dag, senaste 30 dagarna: totalt ' + total + '.' });
    const grid = svg('g', { class: 'grid' });
    for (let i = 0; i <= 4; i++) {
      const y = pad.t + ph - (ph * i) / 4;
      grid.appendChild(svg('line', { x1: pad.l, x2: w - pad.r, y1: y, y2: y }));
      grid.appendChild(svg('text', { x: pad.l - 8, y: y + 4, 'text-anchor': 'end' }, nf.format(step * i)));
    }
    s.appendChild(grid);
    const slot = pw / days.length;
    const bw = Math.max(3, Math.min(22, slot * 0.68));
    const every = w < 480 ? 7 : w < 760 ? 5 : 3;
    days.forEach((x, i) => {
      const cx = pad.l + slot * i + slot / 2;
      const bh = x.count > 0 ? Math.max(3, (ph * x.count) / top) : 2;
      const cls = x.count === 0 ? 'bar zero' : i === days.length - 1 ? 'bar today' : 'bar';
      const r = svg('rect', { class: cls, x: cx - bw / 2, y: pad.t + ph - bh, width: bw, height: bh, rx: Math.min(3, bw / 3) });
      r.appendChild(svg('title', {}, dayLong.format(utcDay(x.date)) + ': ' + nf.format(x.count)));
      s.appendChild(r);
      if ((days.length - 1 - i) % every === 0) {
        s.appendChild(svg('text', { x: cx, y: h - 6, 'text-anchor': 'middle' }, dayShort.format(utcDay(x.date))));
      }
    });
    box.replaceChildren(s);
    const tbody = $('daily-rows');
    tbody.replaceChildren(...days.slice().reverse().map((x) => {
      const tr = el('tr');
      tr.appendChild(el('td', {}, dayLong.format(utcDay(x.date))));
      tr.appendChild(el('td', { class: 'n' }, nf.format(x.count)));
      return tr;
    }));
  }

  function renderVersions(d) {
    const list = $('versions');
    $('versions-meta').textContent = nf.format(d.active30d) + ' aktiva · 30 dagar';
    if (d.versions.length === 0) {
      list.replaceChildren(el('li', { class: 'empty' }, 'Inga aktiva installationer de senaste 30 dagarna.'));
      return;
    }
    list.replaceChildren(...d.versions.map((v) => {
      const li = el('li');
      li.appendChild(el('span', { class: 'v-name' }, v.version));
      const num = el('span', { class: 'v-num' }, nf.format(v.active30d) + ' ');
      num.appendChild(el('span', {}, '· ' + pf.format(v.percent) + ' %'));
      li.appendChild(num);
      const track = el('div', { class: 'track', role: 'presentation' });
      const fill = el('div', { class: 'fill' });
      fill.style.width = Math.max(0, Math.min(100, v.percent)) + '%';
      track.appendChild(fill);
      li.appendChild(track);
      return li;
    }));
  }

  function renderActivity(d) {
    const b = d.activityBuckets;
    const total = d.uniqueInstallations;
    const rows = [
      ['c-24h', 'Aktiva senaste 24 timmarna', b.active24h],
      ['c-7d', 'Senast aktiva 1–7 dagar sedan', b.day2to7],
      ['c-30d', 'Senast aktiva 7–30 dagar sedan', b.day8to30],
      ['c-old', 'Inaktiva mer än 30 dagar', b.inactive30dPlus],
    ];
    $('stack').replaceChildren(...rows.map(([c, label, n]) => {
      const sp = el('span', { class: c, title: label + ': ' + nf.format(n) });
      sp.style.width = pct(n, total) + '%';
      return sp;
    }));
    $('legend').replaceChildren(...rows.map(([c, label, n]) => {
      const li = el('li');
      li.appendChild(el('span', { class: 'swatch ' + c, 'aria-hidden': 'true' }));
      li.appendChild(el('span', {}, label));
      const count = el('span', { class: 'count' }, nf.format(n) + ' ');
      count.appendChild(el('span', {}, total > 0 ? pf.format(pct(n, total)) + ' %' : '–'));
      li.appendChild(count);
      return li;
    }));
  }

  function render(d) {
    renderKpis(d);
    renderDaily(d);
    renderVersions(d);
    renderActivity(d);
  }

  function setStatus(state, text) {
    $('status').dataset.state = state;
    $('status-text').textContent = text;
  }

  function valid(d) {
    return d && typeof d.uniqueInstallations === 'number' && Array.isArray(d.newInstallations30d)
      && Array.isArray(d.versions) && d.activityBuckets && typeof d.activityBuckets.active24h === 'number';
  }

  async function refresh() {
    if (loading) return;
    loading = true;
    $('refresh').disabled = true;
    setStatus('loading', 'Uppdaterar…');
    try {
      const res = await fetch(API, { cache: 'no-store', credentials: 'same-origin', headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error(res.status === 401 ? 'auth' : 'http');
      const next = await res.json();
      if (!valid(next)) throw new Error('format');
      data = next;
      render(data);
      $('updated-time').textContent = clock.format(new Date());
      setStatus('ok', 'Online');
      $('notice').classList.remove('show');
    } catch (err) {
      setStatus('error', 'Kan inte uppdatera');
      $('notice').textContent = err && err.message === 'auth'
        ? 'Kan inte uppdatera: inloggningen gäller inte längre. Ladda om sidan för att logga in igen. Senast hämtade värden visas.'
        : 'Kan inte uppdatera just nu. Senast hämtade värden visas och ett nytt försök görs automatiskt.';
      $('notice').classList.add('show');
    } finally {
      loading = false;
      $('refresh').disabled = false;
    }
  }

  data = JSON.parse($('gz-initial').textContent);
  render(data);
  $('refresh').addEventListener('click', refresh);
  setInterval(() => { if (!document.hidden) refresh(); }, REFRESH_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  let resizeTimer = 0;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => { if (data) renderDaily(data); }, 150);
  });
  setInterval(() => { if (data) { setFact('f-heartbeat', data.latestHeartbeat); setFact('f-newest', data.newestInstallation); } }, 30000);
})();`;

const SCRIPT_HASH = createHash('sha256').update(SCRIPT, 'utf8').digest('base64');

/** CSP for the dashboard: nothing external, only this page's own script and same-origin fetch. */
export const DASHBOARD_CSP = [
  "default-src 'none'",
  "style-src 'unsafe-inline'",
  `script-src 'sha256-${SCRIPT_HASH}'`,
  "connect-src 'self'",
  "base-uri 'none'",
  "form-action 'none'",
  "frame-ancestors 'none'",
].join('; ');

/**
 * @param {ReturnType<import('./stats.js').computeStats>} stats aggregate numbers only
 */
export function renderDashboard(stats) {
  const s = stats;
  return `<!doctype html>
<html lang="sv">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<meta name="referrer" content="no-referrer">
<title>GZ Companion – Användningsstatistik</title>
<style>${STYLE}</style>
</head>
<body>
<div class="wrap">
  <header class="top">
    <div class="brand">
      <div class="logo" aria-hidden="true">GZ</div>
      <div>
        <p class="brand-name">GZ Companion</p>
        <h1>Användningsstatistik</h1>
      </div>
    </div>
    <div class="controls">
      <div class="status" id="status" data-state="ok" role="status" aria-live="polite">
        <span class="dot" aria-hidden="true"></span><span id="status-text">Online</span>
      </div>
      <span class="updated">Senast uppdaterad <time id="updated-time">${escapeHtml(clockFormat.format(new Date(s.generatedAt)))}</time></span>
      <button type="button" id="refresh">Uppdatera</button>
    </div>
  </header>
  <p class="notice" id="notice" role="alert"></p>
  <main>
    <section aria-labelledby="kpi-heading">
      <h2 id="kpi-heading" class="sr-only">Översikt</h2>
      <div class="kpis">
        <article class="card kpi primary"><p class="label">Unika installationer</p><p class="value" id="k-total">${fmt(s.uniqueInstallations)}</p><p class="hint" id="h-total">sedan start</p></article>
        <article class="card kpi"><p class="label">Aktiva 24 timmar</p><p class="value" id="k-24h">${fmt(s.active24h)}</p><p class="hint" id="h-24h"></p></article>
        <article class="card kpi"><p class="label">Aktiva 7 dagar</p><p class="value" id="k-7d">${fmt(s.active7d)}</p><p class="hint" id="h-7d"></p></article>
        <article class="card kpi"><p class="label">Aktiva 30 dagar</p><p class="value" id="k-30d">${fmt(s.active30d)}</p><p class="hint" id="h-30d"></p></article>
      </div>
      <dl class="facts">
        <div class="fact"><dt>Senaste heartbeat</dt><dd id="f-heartbeat">${fmtTime(s.latestHeartbeat)}</dd></div>
        <div class="fact"><dt>Nyaste installation</dt><dd id="f-newest">${fmtTime(s.newestInstallation)}</dd></div>
        <div class="fact"><dt>Versioner i bruk</dt><dd id="f-versions">${fmt(s.versionCount)}</dd></div>
      </dl>
    </section>
    <div class="charts">
      <section class="card wide" aria-labelledby="daily-heading">
        <div class="panel-head"><h2 id="daily-heading">Nya installationer – senaste 30 dagarna</h2><span class="panel-meta" id="daily-total"></span></div>
        <div class="chart" id="chart-daily"></div>
        <noscript><p class="empty">Diagrammen kräver JavaScript. Nyckeltalen ovan visas ändå.</p></noscript>
        <details><summary>Visa som tabell</summary><div class="table-scroll"><table><thead><tr><th>Dag</th><th class="n">Nya</th></tr></thead><tbody id="daily-rows"></tbody></table></div></details>
      </section>
      <section class="card" aria-labelledby="versions-heading">
        <div class="panel-head"><h2 id="versions-heading">Versioner</h2><span class="panel-meta" id="versions-meta"></span></div>
        <ul class="versions" id="versions"></ul>
      </section>
      <section class="card" aria-labelledby="activity-heading">
        <div class="panel-head"><h2 id="activity-heading">Aktivitet</h2><span class="panel-meta">efter senaste heartbeat</span></div>
        <div class="stack" id="stack" role="presentation"></div>
        <ul class="legend" id="legend"></ul>
        <p class="note">Varje installation räknas i exakt en grupp utifrån när den senast hördes av. Detta är en ögonblicksbild, inte historisk daglig aktivitet.</p>
      </section>
    </div>
  </main>
  <footer>Endast sammanräknade siffror. Inga installations-ID, IP-adresser, Minecraft-identiteter eller enskilda användares aktivitet visas eller lagras. Dagar räknas i svensk tid. Uppdateras automatiskt varje minut.</footer>
</div>
<script type="application/json" id="gz-initial">${jsonForScript(s)}</script>
<script>${SCRIPT}</script>
</body>
</html>`;
}
