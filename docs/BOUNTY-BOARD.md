# Bounty Board

**Status: implemented on `main`, unreleased.** This feature is intended for `0.1.0-alpha.5`, but
that version has not been cut yet - `mod_version` remains `0.1.0-alpha.4` until code review and
human Minecraft QA are complete (see the "Version" section at the end of this document).

## 1. Purpose

Bounty Board is a read-only view of GameZone's own public active-bounty registry - the same PvE
hunt data any visitor can already see documented at
[gamezonemc.se/wiki/bounties/bounties](https://www.gamezonemc.se/wiki/bounties/bounties), shown
natively inside GZ Companion's own UI, in the same visual language as Leaderboards/Guide/Settlement.
It adds no gameplay mechanic and changes no server state; it is purely a convenience window onto
information that was already public. See §8 for the full fair-play boundary - in particular, this
feature never scans entities, never shows a coordinate/distance/direction, and never sends a
command automatically.

## 2. What a GameZone bounty is (official facts)

Per GameZone's own wiki (verified 2026-09-13, Engine status "Bounty System 1.0"):

- A bounty targets one **unique, specific, already-existing entity** - never "any mob of this
  type." GameZone's own example: if a Warden named Gorgash is wanted, only that exact Gorgash
  satisfies the bounty.
- The bounty creature's name is shown in **red** above it in-game while the bounty is active, so a
  player can visually distinguish it from ordinary mobs of the same type.
- A published bounty may include: a Coin reward, an optional **public clue**, and an optional
  expiry (a bounty "kan sakna tidsgräns helt" - can have no time limit at all).
- When the correct target is killed, the reward is paid automatically, the server announces it,
  and the event is recorded in Chronicles.
- Official player commands: `/bounty`, `/bounty list`, `/bounty info <name>`.
- Every claimed bounty counts toward the **Monsterjägare** leaderboard (kill count primary,
  total bounty Coins earned as a tie-break) - that leaderboard already exists under Leaderboards
  and is NOT duplicated here (see §12).

## 3. Phase 0 - public source discovery

**The wiki page's own live widget was the first clue.** Loading
`https://www.gamezonemc.se/wiki/bounties/bounties` renders a "LIVE FRÅN SERVERN" panel showing the
currently active bounty (or bounties) directly in the page. Inspecting the page's real network
traffic (not its internal Next.js `?_rsc=` Flight/RSC prefetch requests, which - exactly as
already decided for Leaderboards - are deliberately never depended on, being an internal framework
wire format rather than an intentional public contract) revealed the page fetches:

**`GET https://www.gamezonemc.se/api/bounties`**

This is a genuine, dedicated, intentional public JSON API - not an internal payload:

- `Content-Type: application/json`
- `Access-Control-Allow-Origin: *` (explicitly, deliberately public/CORS-open)
- `Cache-Control: public, s-maxage=10800, stale-while-revalidate=21600` (GameZone's own CDN caches
  this response for 3 hours - it is meant to be fetched by any client, not just their own frontend)
- No authentication, no cookies, plain `200 OK`, no redirect
- Server: Vercel (GameZone's own site hosting)

This satisfies Phase 0's preferred-source-order #1 ("stable public JSON/data endpoint used by the
official GameZone page") cleanly - no HTML-scraping adapter was needed for this feature, unlike
Leaderboards (which had no such dedicated endpoint and had to read rendered HTML instead).

### Captured real response (2026-09-13, one active bounty)

```json
{"status":"success","data":{"active":[{"name":"HostileBoss","entityType":"WITHER_SKELETON","reward":7500,"hint":"En armé av fientliga mobs har invaderat byn utanför västra bron!","status":"ACTIVE","createdAt":"2026-08-29T20:42:37Z","expiresAt":null}],"count":1}}
```

### Exact contract mapped (`BountyJsonParser`)

| Field | Type | Presence | Meaning |
|---|---|---|---|
| `status` (top-level) | string | required | Must equal `"success"` - any other value is treated as an incompatible/changed contract (§9), never silently accepted. |
| `data.active` | array | required | The current active-bounty registry. Empty array = zero active bounties (a real success - see §7). |
| `data.count` | number | present, not required to match | Cross-check only, not relied upon for correctness. |
| `active[].name` | string | **required per entry** | The target's given name (GameZone's own "Gorgash"-style identity) - never blank. An entry missing this is skipped, never given a guessed name. |
| `active[].reward` | number | **required per entry** | Coin reward, mapped as a `long` (large values observed possible - see tests for beyond-`int`-range handling). An entry missing or with a negative reward is skipped, never clamped/guessed. |
| `active[].entityType` | string | optional | e.g. `"WITHER_SKELETON"`. Cosmetically underscore-to-space converted for display only - never mapped to a different/guessed value. |
| `active[].hint` | string, nullable | optional | The public clue, shown byte-for-byte. `null`/absent means no clue was published - Companion shows "Ingen offentlig ledtråd," never a placeholder pretending one exists. |
| `active[].status` | string | optional | The source's own per-entry status (observed value: `"ACTIVE"`), preserved as-delivered. |
| `active[].createdAt` | ISO-8601 string, nullable | optional | Publish timestamp. An unparseable value degrades that one field to absent rather than discarding the whole entry. |
| `active[].expiresAt` | ISO-8601 string or `null` | optional | Expiry. `null` means the bounty genuinely has no time limit (GameZone's own documented "kan sakna tidsgräns helt") - Companion never invents an expiry when none was published. |

Behavior at the documented edge cases, all covered by deterministic tests (`BountyJsonParserTest`,
`GameZoneBountySourceTest`):

- **Zero active bounties**: `data.active: []` parses to an empty list - a genuine `Success`, never
  an error (see §7).
- **One / several active bounties**: parse independently; order preserved.
- **A malformed individual entry** (missing `name` or `reward`, a non-object element in the array,
  a negative reward): that one entry is silently skipped; the rest of the registry still loads.
- **A malformed top-level response** (not JSON at all; missing `data`/`data.active`; an unexpected
  top-level `status` value; `data.active` present but not an array): the WHOLE fetch is reported
  `Incompatible` - GameZone most likely changed the contract shape, not a transient network issue.
- **Endpoint unavailable** (offline, timeout, non-2xx, oversized response, a redirect): reported
  `Unavailable` - a network-level failure, distinct from `Incompatible`.

## 4. Fair play

Bounty Board is read-only public information, identical to what any visitor to gamezonemc.se
already sees, and to what any player can already see in-game via GameZone's own `/bounty` commands
and the red name shown above the target. It contacts only
`https://www.gamezonemc.se/api/bounties` - the same public endpoint the official wiki page itself
fetches. It was built without:

- authentication bypasses of any kind (no login, no session, no cookies)
- probing private/admin endpoints (only the one public, CORS-open, publicly-cached JSON endpoint)
- reverse-engineering credentials or tokens (none exist for this public data)
- **entity or world scanning of any kind** - Companion never inspects loaded entities to find the
  bounty target, never checks distance, direction, or coordinates, never tracks the target on a
  map, and never persists its position. The player hunts normally, exactly as GameZone's own
  red-name-above-the-mob mechanic intends.
- **inferring a coordinate from the public clue** - the clue (`hint`) is displayed exactly as
  GameZone published it, verbatim; Companion never attempts to parse or geocode it into a location.
- **automatic command sending of any kind** - the ONE command-related feature is a deliberate,
  user-clicked "Kopiera `/bounty info <name>`" button that copies text to the OS clipboard via
  `Minecraft.getInstance().keyboardHandler.setClipboard(...)` (the exact same convention already
  used by `CommandsTabComponent`) - it never sends the command, never types it into chat, and never
  runs on any timer or automatic trigger. If a bounty's name cannot safely be represented as a
  single unquoted command argument (contains whitespace, a quote, a backslash, or a slash), the
  copy button is not offered at all rather than generating a broken/ambiguous command (see
  `BountyFormatter.isNameSafeForCommand`).
- **automatic hunting, movement, or combat** - none exist; this is a pure information display.
- `LeaderboardFairPlayTest`'s convention is mirrored exactly by `BountyFairPlayTest`, which
  structurally scans this feature's own source for forbidden entity/world/packet/chat/command APIs
  and non-GameZone host literals, so a future change can never quietly reintroduce any of the above
  without an immediately-failing test.

## 5. Privacy

Every request Companion sends is byte-for-byte identical regardless of who runs it or what they're
doing - it never includes the local Minecraft username, UUID, settlement, coordinates, inventory,
or any telemetry. The request carries nothing but the shared GameZone runtime's plain
`GZ-Companion/<version>` User-Agent and an `Accept: application/json` header (verified directly by
`GameZoneBountySourceTest.requestCarriesNoPlayerIdentity`, which asserts no cookie, no
authorization header, and no unexpected header of any kind is ever sent).

## 6. Shared GameZone runtime (no new thread/HttpClient)

Bounty Board is built entirely on top of `GameZoneLiveDataRuntime` - the exact same shared runtime
Leaderboards already uses (see docs/PERFORMANCE-AUDIT-ALPHA4.md). `GameZoneBountySource` holds a
`GameZoneLiveDataRuntime` reference and only calls `runtime.httpClient()`/`runtime.submit(...)` at
the point a request is actually made; `BountyManager` never constructs its own executor. `
CompanionSession` wires `BountyManager` to the SAME `gameZoneLiveDataRuntime` field Leaderboards
uses - there is no `gzcompanion-bounties`/`gzcompanion-bounty-worker` thread, no second GameZone
`HttpClient`, and no new timer/poller anywhere in this feature. This is enforced structurally, not
just by convention: `ThreadingInfrastructureRulesTest`'s allow-list does NOT include any bounty
file, so if a future change tried to give Bounty Board its own executor/HttpClient/raw `Thread`,
that structural test would fail immediately.

## 7. Cache / request scheduling / snapshot states

`BountyManager` holds exactly ONE `BountySnapshot` (not one per board, unlike `LeaderboardManager`
- there is only one bounty registry). Scheduling mirrors Leaderboards' proven policy, simplified
because there is nothing for a "pending" slot to ever refer to that differs from what the active
fetch already produces:

- **On-demand only**: never fetched at startup, never fetched while Bounties has never been
  opened, never fetched just because Home/Guide/Leaderboards is open. The first `render()` call of
  the Bounties tab calls `ensureFresh()`, which is the ONLY path that can ever trigger a fetch.
- **60-second auto-refresh floor**, **12-second manual-refresh cooldown** - identical constants to
  Leaderboards (`BountyManager.AUTO_REFRESH_INTERVAL`/`MANUAL_REFRESH_COOLDOWN`).
- **At most one fetch outstanding, ever** - a request (auto or manual) while one is already active
  simply **coalesces** (a no-op); there is no pending slot at all, since a second request for the
  same single registry has nothing new to remember once the active fetch already covers it.
- **Runtime rejection handled explicitly**: if the shared runtime's bounded queue rejects the
  submission, the snapshot reverts to its previous state (never left stuck LOADING) and the
  attempt timestamp is NOT recorded, so a genuine retry is never silently blocked for up to 60
  seconds by a rejection that never touched the network.
- **Snapshot states**: `IDLE`, `LOADING`, `LOADED`, `STALE`, `UNAVAILABLE`, `ERROR`,
  `INCOMPATIBLE` - the exact same enum shape as `LeaderboardStatus`, including the same
  "never silently upgrade to LIVE" discipline (`BountyStatus.hasUsableData()`/`isLive()`).
- **Zero active bounties is `LOADED`, not an error** - `BountyManager` distinguishes "never
  successfully loaded" from "successfully loaded, registry currently empty" via
  `BountyStatus.hasUsableData()` rather than checking `entries.isEmpty()`, so a refresh failure
  AFTER a genuine empty-but-successful load correctly becomes `STALE` (keeping the "no bounties"
  result visible as cached) rather than `UNAVAILABLE`.
- **A failed refresh with prior valid data becomes `STALE`**, keeping the old bounties (or the old
  "zero active" result) visible, clearly marked cached ("CACHAD" badge / "Cachad data - X sedan"
  footer) - never re-labeled LIVE.

## 8. UI

A new "Bounties" nav entry sits between MarketWatch and Leaderboards, using a new pixel-art skull
icon (`bounty.png`, matching the existing icons' exact 2-color transparent+tint convention - see
`IconId.BOUNTY`) consistent with docs/design/DESIGN-SYSTEM.md's visual language. Layout (`
BountyLayout`, mirroring `GuideLayout`'s pattern exactly):

- **Wide** (content width ≥ 300px, comfortably covers ~700x450 and larger Minecraft windows): a
  compact scrollable list on the left (name, reward, entity type, remaining time per row) and the
  selected bounty's full detail on the right - no horizontal overflow at any tested viewport.
- **Compact** (< 300px): a single-pane Guide-style list → select → detail → "< Lista" back button.
- **Empty state** ("INGA AKTIVA BOUNTIES" / "Det finns ingen aktiv jakt just nu. Kontrollera igen
  senare.") is deliberately NOT styled as a failure - a genuine `LOADED` status with zero entries
  gets this friendly copy, while `UNAVAILABLE`/`ERROR`/`INCOMPATIBLE` get distinct, honest failure
  copy (`BountiesTabComponent.emptyStateHeadline`/`emptyStateBody`, both directly unit-tested).
- **Detail pane**: name, entity type, "BELÖNING" (formatted reward), "LEDTRÅD" (the clue verbatim,
  or "Ingen offentlig ledtråd"), "TID KVAR" (locally-computed remaining time from a real expiry
  timestamp, or "Ingen tidsgräns" when the bounty genuinely has none, or "Kan ha löpt ut -
  uppdatera" once cached data has crossed its known expiry without a fresher server response), and
  the command-copy button (omitted entirely for an unsafe-to-quote name).
- **Footer**: freshness detail line ("Uppdaterad just nu" / "Uppdaterad 34 sek sedan" / "Cachad
  data - X sedan") plus the "Uppdatera" button, disabled during the cooldown/an active fetch -
  identical family of behavior to Leaderboards' footer.
- **No countdown polling**: remaining time is recalculated locally from the already-cached expiry
  timestamp on every render - it never triggers a network request merely to animate a clock.

## 9. Monsterjägare connection

Per this feature's explicit scope, the full Monsterjägare leaderboard is NOT duplicated here - a
future small text reference ("Monsterjägare finns under Leaderboards") is the intended integration
depth for this pass; no new cross-tab routing system was built solely for this.

## 10. Home / Advisor

Home and the Advisor suggestion engine do NOT eagerly fetch bounty data in this pass - Bounty Board
remains entirely opt-in, exactly like Leaderboards. No Advisor integration was added in this pass
(deferred, per this feature's own explicit priority: "core Bounty Board quality is more important").

## 11. GameZone Rule Pack

No live bounty data is bundled into the static Rule Pack JSON - live registry state belongs
entirely in `BountyManager`'s runtime cache, fetched on demand. Nothing about GameZone's Bounty
System 1.0 mechanics was added to the bundled Rule Pack in this pass either, since nothing here
needed a new static fact beyond what §2 already documents inline.

## 12. Testing strategy

- `BountyJsonParserTest` - pure JSON-in/entries-out tests against fixtures shaped exactly like the
  real captured contract (§3): empty/one/many active bounties, reward parsing including
  beyond-`int`-range values, Unicode names, clue present/absent, expiry present/absent, malformed
  individual entries (skipped), malformed top-level responses and an unexpected top-level `status`
  value (both `Incompatible`), unparseable individual timestamps (degrade gracefully).
- `GameZoneBountySourceTest` - HTTP-level tests against a local JDK `HttpServer` (no real network):
  success, non-2xx, offline, redirect refusal, host allowlisting, the two-layer response-size
  ceiling (declared-oversized and chunked-oversized), and a direct assertion that the outgoing
  request carries no cookie/auth header and no header beyond the minimal expected set.
- `BountyManagerTest` - mirrors `LeaderboardManagerTest`'s deterministic latch-based convention:
  constructing the manager causes zero fetches; the first `ensureFresh` fetches once; repeated
  render-loop-style calls within the 60s window never refetch; the 12s manual cooldown; a
  duplicate/manual request while one is active coalesces (never queues); a shared-runtime
  rejection reverts the snapshot AND does not start the cooldown; a failure after prior valid data
  (including a genuine empty-success result) becomes `STALE`; a genuinely empty successful result
  is `LOADED`, never an error; up to 100 rapid concurrent requests while one fetch is active still
  produce exactly one network call.
- `BountyFormatterTest` - reward grouping (including large values), remaining-time formatting for
  every documented shape (days+hours, hours+minutes, minutes-only, under a minute, no expiry,
  crossed-expiry wording), command-copy safety (whitespace/quote/backslash/slash/blank all refused
  cleanly), entity-type cosmetic formatting, and freshness label/detail text.
- `BountiesTabComponentTest` - pure Font-free tests for `clampIndex`, `clampScroll`, the
  empty-state headline/body pairing per status, `rowIndexAt`'s click-to-row resolution (including
  scroll-offset accounting and out-of-bounds clicks), and `BountyLayout`'s wide/compact breakpoint
  and non-overlapping-panes guarantees.
- `BountyFairPlayTest` - structural source-scanning (mirroring `LeaderboardFairPlayTest`) proving
  no entity/world-scanning API, no packet/chat/command-sending API (only `setClipboard`), and no
  non-GameZone host literal is referenced anywhere in this feature's source.
- No native interactive Minecraft QA was performed or claimed - this environment has no tool that
  can drive the actual game window. See the accompanying task's final report for the exact
  human-QA items still required.

## 13. Version

This document describes work implemented on `main` at commit-time. `mod_version` remains
`0.1.0-alpha.4` - this feature is intended for `0.1.0-alpha.5`, which has not been prepared,
tagged, or released. No installer/release artifact was touched by this feature's implementation.
