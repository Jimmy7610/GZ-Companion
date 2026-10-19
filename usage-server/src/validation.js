/**
 * Strict validation of the heartbeat body: a JSON object with EXACTLY the two string fields
 * installationId and version. Anything else is rejected - never stored, never logged.
 */

/** Canonical 8-4-4-4-12 hex UUID with a valid RFC 4122 variant. */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
/** e.g. "0.1.0-alpha.12": starts alphanumeric, then [A-Za-z0-9.+_-], at most 64 characters. */
const VERSION_RE = /^[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}$/;
const ALLOWED_KEYS = ['installationId', 'version'];

export class ValidationError extends Error {}

/**
 * @param {string} body raw request body (already size-limited)
 * @returns {{installationId: string, version: string}} installationId normalized to lower case
 */
export function parseHeartbeat(body) {
  let data;
  try {
    data = JSON.parse(body);
  } catch {
    throw new ValidationError('malformed JSON');
  }
  if (data === null || typeof data !== 'object' || Array.isArray(data)) {
    throw new ValidationError('body must be a JSON object');
  }
  const keys = Object.keys(data);
  if (keys.length !== ALLOWED_KEYS.length || !ALLOWED_KEYS.every((k) => Object.hasOwn(data, k))) {
    throw new ValidationError('body must contain exactly installationId and version');
  }
  const { installationId, version } = data;
  if (typeof installationId !== 'string' || !UUID_RE.test(installationId)) {
    throw new ValidationError('installationId must be a UUID');
  }
  if (typeof version !== 'string' || !VERSION_RE.test(version)) {
    throw new ValidationError('version is invalid');
  }
  return { installationId: installationId.toLowerCase(), version };
}
