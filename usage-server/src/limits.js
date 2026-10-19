/**
 * Lightweight abuse protection WITHOUT identifying anyone: global token buckets in memory only.
 *
 *  - heartbeat bucket: caps total heartbeat requests per minute (protects CPU/disk from floods).
 *  - new-installation bucket: caps how many previously unseen installation ids may be created per
 *    hour (bounds database growth and blunts count inflation with fake ids). Known installations
 *    are never blocked by it.
 *
 * Deliberately NOT per-IP: that would mean processing and retaining client IPs. Per-client
 * limiting, if ever needed, belongs at the edge (Cloudflare rate-limiting rules) - see README.
 */
export class TokenBucket {
  /**
   * @param {number} capacity max tokens
   * @param {number} periodMs time to refill `capacity` tokens
   * @param {() => number} clock
   */
  constructor(capacity, periodMs, clock) {
    this.capacity = capacity;
    this.ratePerMs = capacity / periodMs;
    this.clock = clock;
    this.tokens = capacity;
    this.last = clock();
  }

  tryTake() {
    const now = this.clock();
    this.tokens = Math.min(this.capacity, this.tokens + Math.max(0, now - this.last) * this.ratePerMs);
    this.last = now;
    if (this.tokens < 1) return false;
    this.tokens -= 1;
    return true;
  }
}
