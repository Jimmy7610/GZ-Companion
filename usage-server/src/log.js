/**
 * Privacy-safe logging. Callers may only log fixed operational messages; nothing here accepts a
 * request, header, body, installation id or IP. Heartbeat outcomes are logged only as aggregated
 * counts per interval.
 */

/** @param {(line: string) => void} write */
export function createLogger(write = (line) => process.stdout.write(line + '\n')) {
  const counters = { accepted: 0, rejected: 0, limited: 0, failed: 0 };
  return {
    info(message) {
      write(`[gzcompanion-usage] ${message}`);
    },
    /** Logs an error by its type/code only - never its message, which could echo request data. */
    error(context, err) {
      const kind = err && typeof err === 'object' ? (err.code || err.name || 'Error') : 'Error';
      write(`[gzcompanion-usage] error in ${context}: ${kind}`);
    },
    count(outcome) {
      if (Object.hasOwn(counters, outcome)) counters[outcome] += 1;
    },
    /** Emits and resets the aggregated heartbeat counters (no-op when nothing happened). */
    flushCounts() {
      const { accepted, rejected, limited, failed } = counters;
      if (accepted + rejected + limited + failed === 0) return;
      write(`[gzcompanion-usage] heartbeats accepted=${accepted} rejected=${rejected} rate-limited=${limited} failed=${failed}`);
      counters.accepted = counters.rejected = counters.limited = counters.failed = 0;
    },
  };
}
