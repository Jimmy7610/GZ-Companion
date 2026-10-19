/**
 * The private dashboard: one server-rendered HTML page of aggregate counts. No JavaScript, no
 * external assets, no installation ids, no IPs, no per-user data. Every value is HTML-escaped.
 */

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) => ESCAPES[c]);
}

function fmt(n) {
  return escapeHtml(Number(n).toLocaleString('sv-SE'));
}

/**
 * @param {ReturnType<import('./stats.js').computeStats>} stats
 * @param {number} nowMs
 */
export function renderDashboard(stats, nowMs) {
  const max = Math.max(1, ...stats.versions.map((v) => v.active30d));
  const rows = stats.versions.length === 0
    ? '<tr><td colspan="3" class="empty">Inga aktiva installationer de senaste 30 dagarna.</td></tr>'
    : stats.versions.map((v) => `
        <tr>
          <td class="ver">${escapeHtml(v.version)}</td>
          <td class="bar"><span style="width:${Math.round((v.active30d / max) * 100)}%"></span></td>
          <td class="num">${fmt(v.active30d)}</td>
        </tr>`).join('');
  const updated = escapeHtml(new Date(nowMs).toISOString().replace('T', ' ').slice(0, 16) + ' UTC');
  return `<!doctype html>
<html lang="sv">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>GZ Companion – användning</title>
<style>
  :root { color-scheme: dark; --bg:#0b1118; --card:#141e29; --border:#2a3746; --text:#f8fafc; --muted:#a7b4c4; --accent:#34d399; }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--text); font:15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
  main { max-width: 920px; margin: 0 auto; padding: 24px 16px 40px; }
  h1 { font-size: 22px; margin: 0 0 4px; }
  h1 span { color: var(--accent); }
  p.sub { margin: 0 0 20px; color: var(--muted); font-size: 13px; }
  .grid { display:grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap:12px; margin-bottom: 24px; }
  .card { background:var(--card); border:1px solid var(--border); border-radius:10px; padding:16px; }
  .label { color: var(--muted); font-size: 13px; }
  .value { font-size: 32px; font-weight: 700; margin-top: 4px; font-variant-numeric: tabular-nums; }
  h2 { font-size: 16px; margin: 0 0 10px; }
  table { width:100%; border-collapse: collapse; }
  td { padding: 8px 4px; border-top:1px solid var(--border); }
  td.ver { white-space: nowrap; font-family: ui-monospace, Consolas, monospace; font-size: 13px; }
  td.num { text-align:right; font-variant-numeric: tabular-nums; width: 64px; }
  td.bar { width: 100%; }
  td.bar span { display:block; height:8px; border-radius:4px; background:var(--accent); min-width:2px; }
  td.empty { color: var(--muted); text-align:center; }
  footer { margin-top: 20px; color: var(--muted); font-size: 12px; }
</style>
</head>
<body>
<main>
  <h1><span>GZ Companion</span> – anonyma aktiva installationer</h1>
  <p class="sub">Endast sammanräknade siffror. Inga installations-ID, IP-adresser eller enskilda användare visas eller lagras här.</p>
  <section class="grid" aria-label="Översikt">
    <div class="card"><div class="label">Unika installationer totalt</div><div class="value">${fmt(stats.uniqueInstallations)}</div></div>
    <div class="card"><div class="label">Aktiva senaste 24 timmarna</div><div class="value">${fmt(stats.active24h)}</div></div>
    <div class="card"><div class="label">Aktiva senaste 7 dagarna</div><div class="value">${fmt(stats.active7d)}</div></div>
    <div class="card"><div class="label">Aktiva senaste 30 dagarna</div><div class="value">${fmt(stats.active30d)}</div></div>
  </section>
  <section class="card" aria-label="Per version">
    <h2>Aktiva per GZ Companion-version (senaste 30 dagarna)</h2>
    <table>${rows}
    </table>
  </section>
  <footer>Uppdaterad ${updated}. Ladda om sidan för nya siffror.</footer>
</main>
</body>
</html>`;
}
