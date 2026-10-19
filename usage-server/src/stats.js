/**
 * Aggregate statistics only - never an installation id or any per-installation row.
 */

export const HOUR_MS = 60 * 60 * 1000;
export const DAY_MS = 24 * HOUR_MS;

/**
 * "Active in the last N" = the installation's latest heartbeat falls inside that window. Clients
 * send at most one heartbeat per 24 h, so active24h approximates daily active installations.
 *
 * @param {ReturnType<import('./db.js').openDatabase>} db
 * @param {number} nowMs
 */
export function computeStats(db, nowMs) {
  return {
    uniqueInstallations: db.countAll(),
    active24h: db.countActiveSince(nowMs - DAY_MS),
    active7d: db.countActiveSince(nowMs - 7 * DAY_MS),
    active30d: db.countActiveSince(nowMs - 30 * DAY_MS),
    versions: db.versionsActiveSince(nowMs - 30 * DAY_MS),
  };
}
