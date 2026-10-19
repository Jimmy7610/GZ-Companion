# gzcompanion-usage

Anonymous active-installation counter for **GZ Companion** - "how many installations are in use",
never "who". A small standalone Node.js service, unrelated to TarenAgent. See also the client-side
description in [`docs/PRIVACY.md`](../docs/PRIVACY.md).

**Status: deployed at `https://gzcompanion.taren.se` (Ubuntu, systemd, Cloudflare Tunnel); production
end-to-end QA passed.** `deploy/server-setup.sh` is the stop-on-anything-unexpected script that was
used for the deployment (inspect, deploy, local test, tunnel route with backup/rollback, external
test, reset to zero, log check, backup).

## Architecture

Zero npm dependencies - only Node.js 24 built-ins: `node:http`, `node:sqlite`, `node:crypto`,
`node:test`. Nothing to install, nothing native to compile, no third-party supply chain.

| File | Role |
|---|---|
| `src/index.js` | entry point: config, open DB, start server, 15-min aggregated log summary, graceful shutdown |
| `src/config.js` | environment-only configuration (`STATS_TOKEN` required, >= 32 chars) |
| `src/server.js` | routing, method/content-type checks, 1 KB body limit, timeouts, error handling |
| `src/validation.js` | strict heartbeat validation |
| `src/db.js` | SQLite schema + prepared statements (no dynamic SQL) |
| `src/stats.js` | aggregate statistics |
| `src/auth.js` | constant-time token check (bearer or basic) |
| `src/limits.js` | global token-bucket abuse limits (no client identity) |
| `src/log.js` | privacy-safe logger |
| `src/dashboard.js` | server-rendered aggregate dashboard (no JavaScript) |

`node:sqlite` is marked experimental in Node 24 (it works and is stable in practice); the start
command passes `--disable-warning=ExperimentalWarning` to keep the log clean.

## Routes

| Access | Method + path | Purpose |
|---|---|---|
| PUBLIC | `POST /api/gzcompanion/heartbeat` | Body exactly `{"installationId":"<UUID>","version":"<version>"}` -> `200 {"ok":true}` |
| PRIVATE | `GET /api/gzcompanion/stats` | Aggregate JSON |
| PRIVATE | `GET /stats` | Aggregate HTML dashboard (Swedish) |

Heartbeat responses: `200` stored; `400` invalid (malformed JSON, not exactly the two fields, bad
UUID, bad version); `405` not POST (with `Allow: POST`); `413` body over 1 KB; `415` not
`application/json`; `429` global limit (`Retry-After`); `503` database unavailable. Unknown paths
`404`. Never redirects, no CORS headers, `Cache-Control: no-store`, no stack traces in responses.

Validation: `installationId` = canonical UUID (8-4-4-4-12 hex, valid variant), stored lower-case;
`version` = `^[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}$` (e.g. `0.1.0-alpha.12`). Any extra field is rejected.

Stats response (aggregates only - no installation ids, ever):

```json
{"uniqueInstallations":42,"active24h":11,"active7d":26,"active30d":35,
 "versions":[{"version":"0.1.0-alpha.12","active30d":28}]}
```

"Active in the last N" = latest heartbeat inside the window (inclusive). The client sends at most one
heartbeat per 24 h, so `active24h` approximates daily active installations. `versions` groups the
30-day-active installations by their current version, most used first.

## Database

One table, current state only - no event history, no analytics tables:

```sql
CREATE TABLE installations (
  installation_id TEXT    PRIMARY KEY NOT NULL,  -- random UUID from the client
  version         TEXT    NOT NULL,              -- latest reported GZ Companion version
  first_seen      INTEGER NOT NULL,              -- epoch ms, set on insert only
  last_seen       INTEGER NOT NULL               -- epoch ms, updated on every heartbeat
) WITHOUT ROWID;
CREATE INDEX installations_last_seen ON installations (last_seen);
```

Upsert per heartbeat: insert on first sight; afterwards update `version` and `last_seen` (never
backwards), never `first_seen`. WAL mode, `busy_timeout` 5 s, `secure_delete` on.

## Authentication (private routes)

One secret, `STATS_TOKEN`, from the environment (never in git), accepted as
`Authorization: Bearer <token>` (API/scripts) or HTTP Basic with the token as password (any user
name) so a browser can open `/stats` through its normal login prompt. Compared in constant time on
SHA-256 digests. Unauthenticated requests get `401` (`WWW-Authenticate: Bearer` for the API,
`Basic` for the dashboard). **Recommended extra layer:** a Cloudflare Access application on the two
private paths (see `deploy/cloudflared-ingress.example.yml`).

## Privacy

- **Stored:** only `installation_id`, `version`, `first_seen`, `last_seen`.
- **Never stored or logged:** IP addresses, User-Agent or any other header, raw bodies, Minecraft
  username/UUID, Windows/machine information, settlement, coordinates, gameplay data.
- **IP addresses:** any TCP/HTTP connection necessarily carries the client's source IP, so the
  network infrastructure (Cloudflare's edge, the tunnel) sees it while handling the request. The
  application deliberately never reads it (`remoteAddress`, `X-Forwarded-For`, `CF-Connecting-IP`
  are not used anywhere - enforced by a source-scanning test) and never persists it.
- **Logs:** only fixed operational lines ("database opened", "service started", a 15-minute
  aggregated "heartbeats accepted=N rejected=N rate-limited=N failed=N", and errors reduced to their
  error code/type - never the error message, which could echo input). Tested: no installation id,
  IP, header, payload or token ever appears in the log.

### Keeping access logs IP-free (deployment)

- **cloudflared:** keep the default log level (`info`) - it does not log individual requests. Do not
  run the tunnel with `--loglevel debug` for this hostname.
- **Cloudflare:** do not enable Logpush / Instant Logs for `gzcompanion.taren.se`. Cloudflare's own
  dashboard analytics are retained by Cloudflare per its policy; the app adds nothing to them.
- **No other reverse proxy** is needed (the tunnel connects straight to `127.0.0.1:8787`). If one is
  ever added (nginx/Caddy), disable its access log for `/api/gzcompanion/heartbeat`
  (e.g. nginx `access_log off;`).
- **journald:** only the app's operational lines; nothing identifying to retain.

## Abuse protection - and why it is not per-IP

Global in-memory token buckets only: at most `HEARTBEATS_PER_MINUTE` (600) heartbeat requests per
minute in total, and at most `NEW_INSTALLATIONS_PER_HOUR` (500) previously unseen ids per hour
(known installations are never blocked by it). Plus the 1 KB body limit and 10 s request timeout.

Trade-off: per-client limiting would require handling (and at least briefly retaining) client IP
addresses, which this service deliberately avoids. The consequence is that someone could inflate
the count with fabricated random ids - bounded by the new-installation cap - and a flood could
temporarily rate-limit genuine heartbeats (clients simply retry later; nothing breaks). If abuse
ever happens, the right place for per-client limits is Cloudflare's edge (a rate-limiting rule on
`/api/gzcompanion/heartbeat`), where the app still never sees or stores an IP.

## Local development

```bash
cd usage-server
npm test                                   # 26 tests, temporary SQLite databases
STATS_TOKEN=<32+ random chars> npm start   # http://127.0.0.1:8787, DB in ./data/
```

## Deployment (prepared - not executed)

Target: Ubuntu 24.04, Node.js 24 (`/usr/bin/node`), Cloudflare Tunnel already running.

```bash
# 1. Service user (no login, no home)
sudo useradd --system --no-create-home --shell /usr/sbin/nologin gzcompanion-usage

# 2. Code (read-only for the service): copy usage-server/ to /srv/gzcompanion-usage
sudo mkdir -p /srv/gzcompanion-usage
sudo rsync -a --delete --exclude data --exclude test ./usage-server/ /srv/gzcompanion-usage/
sudo chown -R root:root /srv/gzcompanion-usage
sudo chmod 0755 /srv/gzcompanion-usage /srv/gzcompanion-usage/deploy/backup.sh

# 3. Secret configuration
sudo mkdir -p /etc/gzcompanion-usage
sudo cp /srv/gzcompanion-usage/.env.example /etc/gzcompanion-usage/env
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"   # paste as STATS_TOKEN
sudo nano /etc/gzcompanion-usage/env
sudo chown root:gzcompanion-usage /etc/gzcompanion-usage/env
sudo chmod 0640 /etc/gzcompanion-usage/env

# 4. systemd (creates /var/lib/gzcompanion-usage, mode 0750, owned by the service user)
sudo cp /srv/gzcompanion-usage/deploy/gzcompanion-usage.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now gzcompanion-usage
systemctl status gzcompanion-usage
journalctl -u gzcompanion-usage -n 20

# 5. Local checks
curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"installationId":"00000000-0000-4000-8000-000000000000","version":"deploy-check"}' \
  http://127.0.0.1:8787/api/gzcompanion/heartbeat          # {"ok":true}
TOKEN=$(sudo sed -n 's/^STATS_TOKEN=//p' /etc/gzcompanion-usage/env)
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8787/api/gzcompanion/stats

# 6. Cloudflare Tunnel: merge deploy/cloudflared-ingress.example.yml into the existing config, then
cloudflared tunnel route dns <TUNNEL-NAME> gzcompanion.taren.se
sudo systemctl restart cloudflared
curl -s -o /dev/null -w '%{http_code}\n' https://gzcompanion.taren.se/api/gzcompanion/stats   # 401
#    (optional) Cloudflare Access application for /stats and /api/gzcompanion/stats

# 7. Daily backup (root crontab), keeps 30 days in /var/backups/gzcompanion-usage (mode 0700)
(sudo crontab -l 2>/dev/null; echo '15 3 * * * /srv/gzcompanion-usage/deploy/backup.sh') | sudo crontab -
sudo /srv/gzcompanion-usage/deploy/backup.sh && sudo ls -l /var/backups/gzcompanion-usage
```

The deploy-check heartbeat in step 5 creates one test row (version `deploy-check`). It leaves the
active windows after 30 days but stays in `uniqueInstallations`; to remove it right away:

```bash
sudo -u gzcompanion-usage /usr/bin/node --disable-warning=ExperimentalWarning -e '
const { DatabaseSync } = require("node:sqlite");
const db = new DatabaseSync("/var/lib/gzcompanion-usage/usage.sqlite");
console.log(db.prepare("DELETE FROM installations WHERE installation_id = ?")
  .run("00000000-0000-4000-8000-000000000000").changes);
db.close();'
```

Database location: `/var/lib/gzcompanion-usage/usage.sqlite` (systemd `StateDirectory`, `0750`,
service user only; the process runs with `UMask=0027`). Backups: `/var/backups/gzcompanion-usage`,
`0700` root, files `0600`.
