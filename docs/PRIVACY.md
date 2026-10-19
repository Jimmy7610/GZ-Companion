# Privacy: anonymous active-installation counter

**Status (Unreleased): implemented, deployed and human-QA-complete.** The client sends to
`https://gzcompanion.taren.se/api/gzcompanion/heartbeat`, served by the `gzcompanion-usage` backend
(`usage-server/`). Production end-to-end QA passed: a real GZ Companion launch and world join
created exactly one installation (0 -> 1), restarting Minecraft and reconnecting kept it at exactly
one (the identity persists, no duplicates), the private stats and dashboard require
authentication, and the application logs contained no installation ids, payloads or tokens.
Synthetic test rows were removed so the production counter started from zero.

## 1. Purpose - why an installation id exists

We want to know **how many** GZ Companion installations are actually in use - not **who** uses
them. GZ Companion therefore sends, at most once a day, a tiny heartbeat containing only a random
installation id and the GZ Companion version. There is no on/off switch; instead it is described
openly in Inställningar ("INTEGRITET OCH FAIR PLAY"). Without some stable id, a count could not tell
one installation sending daily from thirty installations sending once; the random id makes
"unique" and "active in the last N days" countable without knowing anything about the person:

> GZ Companion använder ett slumpmässigt installations-ID och versionsnummer för att räkna anonyma
> aktiva installationer. Ingen Minecraft-identitet eller speldata skickas.

## 2. The installation id

- Generated locally with `UUID.randomUUID()`: a cryptographically secure, random version-4 UUID.
- **Never derived from** the Minecraft username or UUID, the Microsoft account, the Windows user
  name, machine GUID, hardware identifiers, MAC/IP address, GameZone identity, settlement or
  world. No device fingerprinting of any kind (enforced by `UsagePrivacyTest`, which scans the
  `usage` package for any such API).
- Stored in `config/gzcompanion/installation.json` - its own file, so "Återställ inställningar"
  does not change it, and kept across normal GZ Companion updates. It is only created the first
  time a heartbeat is actually due (on the first world/server join), never at game start.
- A corrupt or invalid file is backed up (`installation.json.corrupt.<time>`) and a new random id
  is created - at worst the installation is counted as a new one once.

```json
{"schemaVersion": 1, "installationId": "<random UUID>", "lastAttemptAt": "...", "lastSuccessAt": "..."}
```

## 3. The heartbeat

`POST /api/gzcompanion/heartbeat`, `Content-Type: application/json`, body - and nothing else:

```json
{"installationId": "<random UUID>", "version": "0.1.0-alpha.12"}
```

**Never sent:** Minecraft username/UUID, coordinates, server/world identity, chest data, guide
progress, MarketWatch data, settings, settlement information, online players, commands, event
usage or any other gameplay activity. `HeartbeatPayload` is a record with exactly those two
fields, and its tests fail if a field is ever added. The request carries no cookies or
credentials, only the shared static GZ Companion User-Agent (`GZ-Companion/<version>`).

### IP addresses

Like any network request, the connection necessarily comes from the player's IP address, so the
network infrastructure in front of the service (Cloudflare's edge and tunnel) sees it while
delivering the request. We do not claim otherwise. The GZ Companion usage application itself
never reads, logs or stores IP addresses, User-Agent or any other header, and never stores raw
request bodies - see section 5 and [`usage-server/README.md`](../usage-server/README.md).

## 4. When it is sent

- Triggered when the player joins a world or server - never at game start, never from the render
  or tick thread. The trigger only submits one job to the shared `GameZoneLiveDataRuntime` worker;
  loading/creating the id, the throttle check and the request all happen there.
- At most **once per 24 hours** after a successful heartbeat. After a failed attempt, no new
  attempt for **6 hours**. The attempt time is saved before sending, so even a crash mid-request
  cannot cause a retry loop. At most one attempt per game session.
- Concurrent triggers are coalesced and the id is created under a lock: never two ids, never a
  duplicate heartbeat.
- Network failures (offline, timeout, non-2xx, redirect) are silent and harmless; redirects are
  never followed.
- Reuses the shared GameZone runtime: no executor or HTTP client of its own.

## 5. The backend: gzcompanion-usage

A small standalone service in [`usage-server/`](../usage-server/README.md) (Node.js 24, SQLite, no
third-party dependencies). It stores exactly one row per installation and nothing else:

| Column | Meaning |
|---|---|
| `installation_id` | the random UUID (primary key) |
| `version` | GZ Companion version from the latest heartbeat |
| `first_seen` | first heartbeat time |
| `last_seen` | latest heartbeat time |

No history, no IP addresses, no User-Agent or other headers, no raw bodies. Logs contain only
fixed operational lines and aggregated counts - never an installation id, IP, header or payload.
Abuse protection uses global limits only, deliberately not per-IP.

**What the aggregates are for:** the private, token-protected statistics show only totals -
unique installations, installations active in the last 24 hours / 7 days / 30 days, and active
installations per GZ Companion version. They answer "is the mod used?" and "which versions are
still in use (and need support)?". Individual installations are never listed.

## 6. Activation status

`usage.HeartbeatEndpoint.PRODUCTION` is the single place the endpoint URL is configured:
`https://gzcompanion.taren.se/api/gzcompanion/heartbeat` (live). If the backend is ever unreachable,
attempts fail silently (6-hour retry, at most one per game session). If the value were empty, `UsageHeartbeatService` would be completely inactive (no id
file, no worker thread, no network - covered by tests).
