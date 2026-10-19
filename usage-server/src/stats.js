/**
 * Aggregate statistics only - never an installation id or any per-installation row. Everything is
 * derived from the current state in the installations table (version, first_seen, last_seen);
 * there is no history, so nothing here pretends to be historical daily-active data.
 */

export const HOUR_MS = 60 * 60 * 1000;
export const DAY_MS = 24 * HOUR_MS;
/** Calendar days for the "new installations" chart are Swedish days. */
export const TIME_ZONE = 'Europe/Stockholm';
export const NEW_INSTALLATION_DAYS = 30;

const zonedFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIME_ZONE, hourCycle: 'h23',
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
});

/** Wall-clock parts of {@code ms} in TIME_ZONE. */
function zonedParts(ms) {
  const parts = {};
  for (const p of zonedFormat.formatToParts(new Date(ms))) {
    if (p.type !== 'literal') parts[p.type] = Number(p.value);
  }
  return parts;
}

/** TIME_ZONE's UTC offset at instant {@code ms}, in ms (e.g. +2 h in Swedish summer time). */
function zoneOffset(ms) {
  const p = zonedParts(ms);
  return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - Math.floor(ms / 1000) * 1000;
}

/** The instant of local midnight starting calendar day y-m-d in TIME_ZONE (DST-safe). */
export function zonedMidnight(year, month, day) {
  const wall = Date.UTC(year, month - 1, day);
  const first = wall - zoneOffset(wall);
  return wall - zoneOffset(first);
}

/**
 * The last {@code n} calendar days in TIME_ZONE, oldest first, today last.
 * @returns {{date: string, start: number, end: number}[]} start inclusive, end exclusive
 */
export function lastCalendarDays(nowMs, n) {
  const today = zonedParts(nowMs);
  const days = [];
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(Date.UTC(today.year, today.month - 1, today.day - i));
    const [y, m, dd] = [d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate()];
    const next = new Date(Date.UTC(y, m - 1, dd + 1));
    days.push({
      date: `${y}-${String(m).padStart(2, '0')}-${String(dd).padStart(2, '0')}`,
      start: zonedMidnight(y, m, dd),
      end: zonedMidnight(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate()),
    });
  }
  return days;
}

function percent(part, whole) {
  return whole > 0 ? Math.round((part * 1000) / whole) / 10 : 0;
}

/**
 * "Active in the last N" = the installation's latest heartbeat falls inside that window. Clients
 * send at most one heartbeat per 24 h, so active24h approximates daily active installations.
 * All queries run in one read snapshot, so the numbers are mutually consistent.
 *
 * @param {ReturnType<import('./db.js').openDatabase>} db
 * @param {number} nowMs
 */
export function computeStats(db, nowMs) {
  return db.readSnapshot(() => {
    const total = db.countAll();
    const active24h = db.countActiveSince(nowMs - DAY_MS);
    const active7d = db.countActiveSince(nowMs - 7 * DAY_MS);
    const active30d = db.countActiveSince(nowMs - 30 * DAY_MS);
    const versionRows = db.versionsActiveSince(nowMs - 30 * DAY_MS);
    const versionTotal = versionRows.reduce((sum, v) => sum + v.active30d, 0);
    return {
      generatedAt: nowMs,
      timeZone: TIME_ZONE,
      uniqueInstallations: total,
      active24h,
      active7d,
      active30d,
      latestHeartbeat: db.maxLastSeen(),
      newestInstallation: db.maxFirstSeen(),
      versionCount: versionRows.length,
      // Mutually exclusive buckets of the CURRENT last_seen; they always add up to the total.
      activityBuckets: {
        active24h,
        day2to7: active7d - active24h,
        day8to30: active30d - active7d,
        inactive30dPlus: total - active30d,
      },
      newInstallations30d: lastCalendarDays(nowMs, NEW_INSTALLATION_DAYS).map((d) => ({
        date: d.date,
        count: db.countFirstSeenBetween(d.start, d.end),
      })),
      versions: versionRows.map((v) => ({ ...v, percent: percent(v.active30d, versionTotal) })),
    };
  });
}
