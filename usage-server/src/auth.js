/**
 * The private boundary for the stats API and the dashboard: one secret (STATS_TOKEN), accepted as
 *  - `Authorization: Bearer <token>` (scripts, the API), or
 *  - `Authorization: Basic base64(<any user>:<token>)` (so a browser can open the dashboard via
 *    its normal login prompt).
 * Compared in constant time on SHA-256 digests, so neither content nor length leaks via timing.
 */
import { createHash, timingSafeEqual } from 'node:crypto';

function digest(value) {
  return createHash('sha256').update(value, 'utf8').digest();
}

/** @returns {string|null} the presented secret, if any */
export function presentedSecret(authorizationHeader) {
  if (typeof authorizationHeader !== 'string') return null;
  const [scheme, value] = authorizationHeader.split(' ', 2);
  if (!value) return null;
  if (scheme.toLowerCase() === 'bearer') return value.trim();
  if (scheme.toLowerCase() === 'basic') {
    let decoded;
    try {
      decoded = Buffer.from(value.trim(), 'base64').toString('utf8');
    } catch {
      return null;
    }
    const colon = decoded.indexOf(':');
    return colon >= 0 ? decoded.slice(colon + 1) : null;
  }
  return null;
}

/** True only if the request presents exactly the configured token. */
export function isAuthorized(authorizationHeader, token) {
  const secret = presentedSecret(authorizationHeader);
  if (secret === null || !token) return false;
  return timingSafeEqual(digest(secret), digest(token));
}
