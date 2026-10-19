/**
 * HTTP layer for gzcompanion-usage.
 *
 * PUBLIC   POST /api/gzcompanion/heartbeat   {installationId, version} -> {ok:true}
 * PRIVATE  GET  /api/gzcompanion/stats       aggregate JSON (STATS_TOKEN)
 * PRIVATE  GET  /stats                       aggregate HTML dashboard (STATS_TOKEN); its script
 *                                             refreshes from the stats API with the browser's own
 *                                             Basic credentials (the token is never in the page)
 *
 * The application never reads the client address, never stores or logs headers, bodies or ids,
 * and never sends stack traces. No redirects, no CORS (the dashboard only calls its own origin).
 */
import { createServer as createHttpServer } from 'node:http';
import { parseHeartbeat, ValidationError } from './validation.js';
import { computeStats, HOUR_MS } from './stats.js';
import { isAuthorized } from './auth.js';
import { TokenBucket } from './limits.js';
import { renderDashboard, DASHBOARD_CSP } from './dashboard.js';

export const MAX_BODY_BYTES = 1024;
const HEARTBEAT_PATH = '/api/gzcompanion/heartbeat';
const STATS_PATH = '/api/gzcompanion/stats';
const DASHBOARD_PATH = '/stats';
const REALM = 'realm="gzcompanion-usage"';
// The dashboard page asks for Basic; the API offers Basic too, so a browser re-sends the dashboard
// login for its same-origin refresh calls, and Bearer for scripts.
const DASHBOARD_CHALLENGE = `Basic ${REALM}, charset="UTF-8"`;
const API_CHALLENGES = [DASHBOARD_CHALLENGE, `Bearer ${REALM}`];

const BASE_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'Cache-Control': 'no-store',
};

function send(res, status, body, headers = {}) {
  if (res.headersSent) return;
  const isHtml = (headers['Content-Type'] || '').startsWith('text/html');
  const payload = isHtml ? body : JSON.stringify(body);
  res.writeHead(status, {
    ...BASE_HEADERS,
    'Content-Type': isHtml ? 'text/html; charset=utf-8' : 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    ...headers,
  });
  res.end(payload);
}

class BodyTooLarge extends Error {}

/** Reads the body, aborting as soon as it exceeds MAX_BODY_BYTES. */
function readBody(req) {
  return new Promise((resolve, reject) => {
    const declared = Number(req.headers['content-length']);
    if (Number.isFinite(declared) && declared > MAX_BODY_BYTES) {
      reject(new BodyTooLarge());
      return;
    }
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new BodyTooLarge());
        req.pause();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

/**
 * @param {{db: ReturnType<import('./db.js').openDatabase>, config: ReturnType<import('./config.js').loadConfig>,
 *          logger: ReturnType<import('./log.js').createLogger>, clock?: () => number}} deps
 */
export function createServer({ db, config, logger, clock = Date.now }) {
  const heartbeatBucket = new TokenBucket(config.heartbeatsPerMinute, 60 * 1000, clock);
  const newInstallBucket = new TokenBucket(config.newInstallationsPerHour, HOUR_MS, clock);

  async function handleHeartbeat(req, res) {
    if (req.method !== 'POST') return send(res, 405, { ok: false, error: 'method not allowed' }, { Allow: 'POST' });
    const type = (req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
    if (type !== 'application/json') return send(res, 415, { ok: false, error: 'content-type must be application/json' });
    if (!heartbeatBucket.tryTake()) {
      logger.count('limited');
      return send(res, 429, { ok: false, error: 'too many requests' }, { 'Retry-After': '60' });
    }
    let body;
    try {
      body = await readBody(req);
    } catch (err) {
      logger.count('rejected');
      if (err instanceof BodyTooLarge) return send(res, 413, { ok: false, error: 'body too large' }, { Connection: 'close' });
      throw err;
    }
    let heartbeat;
    try {
      heartbeat = parseHeartbeat(body);
    } catch (err) {
      if (err instanceof ValidationError) {
        logger.count('rejected');
        return send(res, 400, { ok: false, error: err.message });
      }
      throw err;
    }
    try {
      if (db.isNew(heartbeat.installationId) && !newInstallBucket.tryTake()) {
        logger.count('limited');
        return send(res, 429, { ok: false, error: 'too many requests' }, { 'Retry-After': '3600' });
      }
      db.recordHeartbeat(heartbeat.installationId, heartbeat.version, clock());
    } catch (err) {
      logger.count('failed');
      logger.error('heartbeat storage', err);
      return send(res, 503, { ok: false, error: 'temporarily unavailable' });
    }
    logger.count('accepted');
    return send(res, 200, { ok: true });
  }

  function handlePrivate(req, res, html) {
    if (req.method !== 'GET' && req.method !== 'HEAD') return send(res, 405, { ok: false, error: 'method not allowed' }, { Allow: 'GET, HEAD' });
    if (!isAuthorized(req.headers.authorization, config.statsToken)) {
      return html
          ? send(res, 401, '<!doctype html><title>401</title><p>Inloggning krävs.</p>',
              { 'Content-Type': 'text/html', 'WWW-Authenticate': DASHBOARD_CHALLENGE })
          : send(res, 401, { ok: false, error: 'unauthorized' }, { 'WWW-Authenticate': API_CHALLENGES });
    }
    let stats;
    try {
      stats = computeStats(db, clock());
    } catch (err) {
      logger.error('stats', err);
      return send(res, 503, { ok: false, error: 'temporarily unavailable' });
    }
    if (!html) return send(res, 200, stats);
    return send(res, 200, renderDashboard(stats), {
      'Content-Type': 'text/html',
      'Content-Security-Policy': DASHBOARD_CSP,
      'X-Frame-Options': 'DENY',
    });
  }

  const server = createHttpServer(async (req, res) => {
    try {
      const path = (req.url || '').split('?')[0];
      if (path === HEARTBEAT_PATH) return await handleHeartbeat(req, res);
      if (path === STATS_PATH) return handlePrivate(req, res, false);
      if (path === DASHBOARD_PATH) return handlePrivate(req, res, true);
      return send(res, 404, { ok: false, error: 'not found' });
    } catch (err) {
      logger.error('request', err);
      return send(res, 500, { ok: false, error: 'internal error' });
    }
  });
  server.requestTimeout = 10_000;
  server.headersTimeout = 5_000;
  server.keepAliveTimeout = 5_000;
  server.maxHeadersCount = 50;
  return server;
}
