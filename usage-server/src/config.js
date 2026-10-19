/**
 * Configuration from the environment only - no secrets in git. See .env.example.
 */

const DEFAULTS = Object.freeze({
  HOST: '127.0.0.1',
  PORT: 8787,
  DB_PATH: './data/usage.sqlite',
  // Abuse limits (global, never per-IP - see README "Abuse protection").
  HEARTBEATS_PER_MINUTE: 600,
  NEW_INSTALLATIONS_PER_HOUR: 500,
});

const MIN_TOKEN_LENGTH = 32;

function intFrom(env, key) {
  const raw = env[key];
  if (raw === undefined || raw === '') return DEFAULTS[key];
  const n = Number(raw);
  if (!Number.isInteger(n) || n <= 0) throw new Error(`${key} must be a positive integer`);
  return n;
}

/**
 * @param {Record<string, string|undefined>} env
 * @returns {{host: string, port: number, dbPath: string, statsToken: string,
 *            heartbeatsPerMinute: number, newInstallationsPerHour: number}}
 */
export function loadConfig(env = process.env) {
  const statsToken = env.STATS_TOKEN ?? '';
  if (statsToken.length < MIN_TOKEN_LENGTH) {
    throw new Error(`STATS_TOKEN must be set to a random secret of at least ${MIN_TOKEN_LENGTH} characters`);
  }
  const port = env.PORT === undefined || env.PORT === '' ? DEFAULTS.PORT : Number(env.PORT);
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('PORT must be 0-65535');
  return Object.freeze({
    host: env.HOST || DEFAULTS.HOST,
    port,
    dbPath: env.DB_PATH || DEFAULTS.DB_PATH,
    statsToken,
    heartbeatsPerMinute: intFrom(env, 'HEARTBEATS_PER_MINUTE'),
    newInstallationsPerHour: intFrom(env, 'NEW_INSTALLATIONS_PER_HOUR'),
  });
}
