# Performance Baseline / Audit — after v0.1.0-alpha.4

Audit date: 2026-09-13. Repository state audited: commit `0f7f9febb900b776de7cb19f5d72d032d29f1fc9`
(`main`, matches the released `0.1.0-alpha.4`). This is an **audit-only** pass — no production
behavior, version, tag, or release artifact was changed to produce this document.

Permanent design rule under evaluation:

> **FEATURE COUNT MAY GROW. IDLE COST MUST NOT GROW WITH FEATURE COUNT.**

## 1. Executive verdict

No BLOCKER was found. GZ Companion's live-network feature (Leaderboards) is idle by construction
when unused, and the codebase currently owns exactly **two** background threads and **three**
`HttpClient` instances in total, all created once at mod init and never recreated by opening/closing
UI. The architecture is viable for the next batch of live modules **only if** each one is built to
share infrastructure rather than copy the current per-feature pattern (own executor, own
`HttpClient`) verbatim — see §12 and §15. One automatic (non-user-triggered) filesystem write path
was found on the main client thread (Guide progress autosave); it is infrequent and not a blocker,
but is flagged as SHOULD FIX because it is the one place the "no filesystem writes on the tick
thread" budget rule is technically violated.

## 2. Idle-cost verdict

**PASS**, with one caveat. While the Companion UI is closed:

- Leaderboards performs zero rendering, zero polling, and zero network requests — proven both by
  static trace (its only trigger, `ensureFresh`, is called exclusively from
  `LeaderboardsTabComponent.render()`, which is only invoked while that tab is the active tab of an
  open `GZCompanionMainScreen`) and by a live 30-second JFR recording at the idle main menu showing
  **zero** `gzcompanion` frames in any of 80 captured execution samples (§10).
- The updater performs one small metadata check ~20s after startup and then every 45 minutes,
  regardless of UI state — this is the one system "explicitly designed to check in background" per
  the stated budget, and is intentional, documented, cooldown-protected behavior, not a bug.
- Caveat: whenever the player is **in a world** (with or without Companion open), `GuideScheduler`
  scans the player's inventory and computes a SHA-256 fingerprint every ~750ms, unconditionally,
  independent of Companion's own UI (§3, §9). This is cheap in isolation (a few dozen inventory
  slots) but is exactly the kind of always-on per-module tick cost the permanent design rule warns
  against if repeated by future modules — see §12.

## 3. Tick-path inventory

Every registration against a Fabric client-tick/event hook in production code, traced to source:

| Component | Trigger | Frequency | Thread | Work | UI required? | Cost | Bounded? | Risk |
|---|---|---|---|---|---|---|---|---|
| `GuideScheduler.onClientTick` (`GZCompanionClient.java:39`) | `ClientTickEvents.END_CLIENT_TICK` | Every tick (20/s); real work every 15th tick (~750ms) | Client/main thread | No-ops if `client.player == null`. Otherwise: full inventory scan + SHA-256 fingerprint (`MinecraftGuideSnapshotProvider`) every 750ms; if the fingerprint changed, evaluates all guide steps + fixed-point supersede inference; if a step's completion changed, **synchronously writes** `guide-progress.json` to disk (temp file + atomic move) | No — runs whenever in a world, Companion UI open or not | Snapshot: O(inventory size, ~41 slots) + 1 hash, every 750ms. Evaluation: O(steps) two small loops, only when snapshot changed. Disk write: only when a step actually completes (rare, a handful of times per playthrough) | Bounded (guide step count is fixed, ~dozens) | **LOW** (cost small per tick) / **SHOULD FIX** for the on-thread disk write, see §12 |
| `KeybindHandler` key-poll (`KeybindHandler.java:31`) | `ClientTickEvents.END_CLIENT_TICK` | Every tick | Client/main thread | `consumeClick()` check, opens `GZCompanionMainScreen` if pressed | No | Negligible (vanilla keybind pattern) | N/A | **NONE** |
| `ChestCaptureController` — `UseBlockCallback` | Player right-clicks any block | Event-driven, not per-tick | Client/main thread | Classifies block, records a pending interaction if it's a storage block | No | Cheap, single block-state check | N/A | **NONE** |
| `ChestCaptureController` — `ScreenEvents.AFTER_INIT` | A screen opens | Event-driven | Client/main thread | Correlates with pending interaction if it's a supported container menu | No | Cheap | N/A | **NONE** |
| `ChestCaptureController` — `ScreenEvents.afterTick(screen)` | Every tick **while a correlated container screen is open** | Per-tick, but only while that specific container (not Companion's own UI) is open | Client/main thread | Re-reads only the storage portion of the open menu | No (any container, unrelated to Companion's own screen) | O(container slot count), small | Bounded (menu slot count) | **NONE** |
| `ChestCaptureController` — `ClientPlayConnectionEvents.DISCONNECT` | Disconnect/quit/reconnect | Event-driven | Client/main thread | Clears transient capture state | No | Negligible | N/A | **NONE** |
| `GameZoneChatObserver` — `ClientReceiveMessageEvents.GAME`/`CHAT` | Every chat/game message received | Event-driven, frequency = server chat volume | Client/main thread | Profile-gate check (cheap boolean) first; if connected to GameZone, runs the message through the active parser list (regex/contains/exact, patterns pre-compiled and cached) | No | O(active parsers) per eligible message; currently 0 active parsers in the bundled Rule Pack | Bounded (Rule Pack size) | **LOW** |
| `GameZoneToastHudElement` (HUD render) | `HudElementRegistry` — every render frame **while no `Screen` is open** (vanilla HUD contract) | ~Every frame, only outside any Screen (so never while Companion's own screen is open) | Render thread | One `Optional` check against a 3-item toast deque; draws only if non-empty | No | O(1) when empty (the common case) | Bounded (max 3 queued toasts) | **NONE** |
| `LeaderboardsTabComponent.render()` → `manager.ensureFresh(current)` | Render, but only while Leaderboards is the active tab of an open Companion screen | Every frame **of that tab only** | Render thread (the check) / background executor (the actual fetch) | Cheap `Instant` comparison against the 60s auto-refresh floor; only schedules a background fetch if stale | **Yes** | O(1) per frame | Bounded | **NONE** |
| `UpdateManager` scheduled checks | `ScheduledExecutorService`, 20s after startup then every 45 min | Fixed interval | Dedicated `gzcompanion-updater` daemon thread | Fetches GitHub releases JSON, parses, compares versions | No | Small JSON parse, infrequent | Bounded | **NONE** (by design) |

No other `ClientTickEvents`, `WorldTickEvents`, `HudRenderCallback`, or `WorldRenderEvents`
registrations exist anywhere in the production source tree (verified by an exhaustive grep across
`src/main/java`).

## 4. Render-path inventory

`GZCompanionMainScreen` dispatches to exactly one tab component per frame via an `if/else` chain on
`activeTab` (`GZCompanionMainScreen.java:147-170`) — **only the currently-selected tab's `render()`
runs**; the other ~13 tab components do no work at all while not selected. This directly satisfies
the budget's "only selected/current UI should do meaningful render/layout work" requirement.

Specifically checked in `LeaderboardsTabComponent` (the tab most likely to regress, per this audit's
focus):

- `render()` itself performs no `.stream()`, no `Pattern.compile`, no JSON parsing, no filesystem
  access, and no HTTP call — confirmed by grep across the file. Its only allocation-heavy operations
  (`.stream().map(...).toList()` in the click-resolution path) are inside `mouseClicked()`, which is
  click-driven, not per-frame.
- The only per-frame Leaderboards work is the O(1) `ensureFresh` cooldown check plus drawing already-
  cached rows — no re-parsing, no re-fetching, no HTML processing on every frame.
- `GameZoneToastHudElement` (§3) is the only HUD element that renders unconditionally every frame
  regardless of any Screen; its cost when idle is one `Optional` check.

**No HTTP call, filesystem write, or GameZone HTML parse happens on the render thread, the tick
thread, or the main client thread anywhere in the codebase** — every network/parsing call site
(`GameZoneLeaderboardSource.fetch`, `GitHubReleaseSource.fetchReleases`/`fetchText`,
`HttpUpdateByteSource.download`) is only ever invoked from inside `executor.execute(...)` on one of
the two dedicated background executors (§5). The one exception to "no filesystem writes on the tick
thread" is the Guide's autosave (§3, §12) — a **SHOULD FIX**, not a HIGH-risk item, since it is
infrequent and does not touch render/HTTP.

## 5. Thread inventory

An exhaustive grep for `Executors.`, `new Thread(`, `ScheduledExecutorService`, `CompletableFuture`,
`Timer`, and `.sleep(` across `src/main/java` returns hits in **exactly two files**:

| Thread name | Owner | Daemon? | Created when | Lifetime | Idle behavior | Wake frequency | Shutdown | Multiple copies possible? |
|---|---|---|---|---|---|---|---|---|
| `gzcompanion-updater` | `UpdateManager` (`update/UpdateManager.java:61`) | Yes | At `CompanionSession` construction (mod init) — `ScheduledExecutorService` created eagerly | Life of the JVM/game process | Parked in the executor's internal delay queue (`ScheduledThreadPoolExecutor$DelayedWorkQueue.take`), ~0% CPU | 20s after startup, then every 45 min (or on-demand for a manual check/download) | `shutdown()` exists but is never called in production — harmless because the thread is a daemon and `CompanionSession` is a true JVM-lifetime singleton (see below) | **No** — `CompanionSession.INSTANCE` is a single static field; `UpdateManager` is one of its `final` fields, constructed exactly once |
| `gzcompanion-leaderboards` | `LeaderboardManager` (`leaderboard/LeaderboardManager.java:92`) | Yes | Lazily, on the **first** `executor.execute(...)` call — i.e. the first time any board is actually fetched (never at mod init) | Life of the JVM/game process once created | Parked waiting for work once idle | On demand only (no scheduled polling) | No `shutdown()` method exists at all; same daemon-thread/singleton reasoning as above | **No** — same singleton reasoning; confirmed live: this thread **did not exist at all** in a 34s-old session where Leaderboards was never opened (§10) |

Both executors are fields of `CompanionSession`, which is a private static singleton
(`CompanionSession.getInstance()`) constructed exactly once per game process. Neither
`GZCompanionMainScreen` nor `LeaderboardsTabComponent` (both of which are freshly constructed every
time the player opens the Companion UI / presses the keybind) holds any executor, thread, or global
listener registration of its own — confirmed by grep (`LeaderboardsTabComponent.java` has zero
`Executor`/`Thread`/`register`/`schedule` hits outside a doc-comment). **Opening/closing the
Companion UI, or switching to Leaderboards, repeatedly cannot create additional threads** — this is
a structural guarantee from the singleton design, not merely an untested assumption, and was cross-
checked against a live thread dump (§10) rather than only inferred from source.

`java.net.http.HttpClient` also owns its own internal threads (a `SelectorManager` plus on-demand
`Worker` threads) **per constructed instance**. Three separate `HttpClient` instances exist in
production, each built once and reused for the life of the session:

1. `GameZoneLeaderboardSource` (`leaderboard/GameZoneLeaderboardSource.java:60`)
2. `GitHubReleaseSource` (`update/GitHubReleaseSource.java:37`)
3. `HttpUpdateByteSource` (`update/HttpUpdateByteSource.java:31`)

A live thread dump 34 seconds after startup already showed all three `HttpClient-N-SelectorManager`
threads present — including the Leaderboards one, even though Leaderboards had never been opened —
because `GameZoneLeaderboardSource`'s constructor (which eagerly builds its `HttpClient`) runs at
`CompanionSession` construction time, not on first use. Each is cheap (parked in `select()`, ~0%
CPU) but this is the concrete mechanism by which "N live modules = N idle background threads" would
actually happen if future modules copy this exact pattern — see §12/§15.

## 6. Network inventory

| Host | Purpose | Trigger | Cadence | Payload sent | Thread | Timeout | Redirect policy | Cache/cooldown |
|---|---|---|---|---|---|---|---|---|
| `www.gamezonemc.se` | Leaderboards read-only HTML scrape | `ensureFresh`/`manualRefresh`, both **only called while Leaderboards tab is open** | On-demand; auto floor 60s/board, manual cooldown 12s | Nothing but `GET` + a static `GZ-Companion/<version>` User-Agent — no player/session/world data | `gzcompanion-leaderboards` | 10s connect (production default) | `Redirect.NEVER` — any redirect is a hard failure | Per-board cache, "latest relevant request wins" scheduler (one active + one pending fetch max) — see §9 |
| `api.github.com` | Updater metadata (release list) | Automatic, independent of UI | 20s after startup, then every 45 min; manual check bypasses cooldown | `GET` + `GZCompanion-Updater/<version>` User-Agent — no player/session/world data | `gzcompanion-updater` | 15s connect | `Redirect.NORMAL` (HTTPS-only enforced) | 45-minute automatic floor |
| GitHub release asset CDN (via `browser_download_url` redirect) | Installer download | **Only** the player's explicit "Ladda ner" click (never automatic) | On-demand, user-gated | `GET` + same updater User-Agent | `gzcompanion-updater` | 5 min (large file) | `Redirect.NORMAL` | N/A (one-shot, streamed to disk, 500MB cap) |

**When the player is simply playing GameZone and never opens Companion**, the only network activity
GZ Companion itself performs is the automatic GitHub metadata check (~every 45 minutes, a few KB of
JSON). **Zero requests to `gamezonemc.se`** occur unless Leaderboards is actually opened. This
directly answers Phase 5's key question and matches the documented policy in
`docs/LEADERBOARDS.md` §10.

Minor note (NICE TO HAVE, not a blocker): `GitHubReleaseSource.fetchText` has no explicit response-
size ceiling, unlike `GameZoneLeaderboardSource`'s two-layer cap. Risk is low — GitHub's API response
is small and paginated (`per_page=10`) and the host is fixed/trusted infrastructure, not an arbitrary
public page — but it is an inconsistency worth closing for defense-in-depth if the updater is ever
touched again.

## 7. Cache/memory inventory

| Structure | Key → value | Max realistic entries | Eviction/reset | Persisted? | Unbounded? |
|---|---|---|---|---|---|
| `LeaderboardManager.snapshots` | board id → `LeaderboardSnapshot` | Exactly 27 (fixed registry size) | N/A — keys are drawn only from `GameZoneLeaderboardRegistry`, which is a fixed compile-time list | Memory-only | **No** — hard-bounded by the registry |
| `LeaderboardManager.lastFetchAttemptAt` | board id → `Instant` | Exactly 27 | Same as above | Memory-only | **No** |
| `GameZoneLeaderboardSource.cachedServerPage` | single field (not a map) | 1 | Replaced every 5s reuse window | Memory-only | **No** |
| `GameZoneToastManager.queue` | ordered toast list | 3 (`MAX_QUEUED_TOASTS`) | Oldest evicted when full; expired entries dropped lazily on read | Memory-only | **No** |
| `GameZoneToastManager.lastShownAtByDedupeKey` | dedupe key → `Instant` | Proportional to recent (last 8s) distinct event keys | Opportunistically pruned on every `offer()` call (tested — §16) | Memory-only | **No** |
| `ChestManager` container index | position/context key → `StoredContainer` | Grows with distinct containers the player has opened in that world | No cap found; persisted per-world | Disk (JSON) | Grows with legitimate play, not with feature count — **NICE TO HAVE**: no hard ceiling exists today |
| `GuideEngine.progressData` | context key → completed-step map | Bounded by guide step count (fixed, small) × number of distinct worlds/contexts visited | No explicit cap on distinct contexts, but each is tiny | Disk (JSON) | Low risk — a context is a full world/server identity, not something spammable |
| `BuildingPlanManager` / `SettlementPlannerManager` / `MarketWatchNotesManager` / `SettingsManager` stores | user-authored notes/plans | Grows only with explicit player data entry (button clicks), never automatically | No cap found | Disk (JSON) | Same category as the chest index — bounded by real usage, not code-enforced |

**Leaderboards worst case (all 27 boards cached simultaneously):** 22 ranked boards × 10 rows + 5
server-stat boards × 1 row = 225 `LeaderboardEntry` records, each a handful of short strings (rank,
name, one or two formatted value strings, one or two labels). Order of magnitude: a few hundred
bytes per entry including JVM object/String overhead → **roughly 50–100 KB total** for all 27 boards
fully cached, plus at most one buffered server-stats HTML page (tens of KB, replaced not
accumulated). This is negligible next to a Minecraft client's normal memory footprint (hundreds of
MB to several GB) — **NO ISSUE**. (Order-of-magnitude estimate; not a byte-exact measurement.)

## 8. Startup analysis

Traced `GZCompanionClient.onInitializeClient()` → `CompanionSession`'s constructor/`init()`
(`core/CompanionSession.java`). Synchronous work performed during mod startup, in order:

1. Construct `VanillaMinecraftBridge`, `FeatureManager`, `StorageManager`, `CompatibilityService`.
2. Construct `GuideEngine`, `ChestManager`, `SettlementPlannerManager`, `BuildingPlanManager`,
   `MarketWatchNotesManager`, `SettingsManager` — each wired to a `Json*Store` over a fixed
   `config/gzcompanion/*.json` path (no I/O yet, just object construction).
3. Construct `GameZoneToastManager`, `GameZoneChatObserver`, `GameZoneSettlementTracker`,
   `GameZoneLiveStatusTracker` — pure in-memory objects, no I/O.
4. Construct `UpdateManager` — **this eagerly builds an `HttpClient`** and, in its constructor, runs
   `pruneAbandonedPartFilesQuietly()`, a synchronous `Files.walk(root, 2)` over the updates directory
   to delete stray `.part` files — a bounded, shallow, one-time directory scan.
5. Construct `LeaderboardManager` + `GameZoneLeaderboardSource` — **this eagerly builds a second
   `HttpClient`** (§5), but performs **no network call** at construction time.
6. `init()`: loads the bundled Rule Pack (JSON from mod resources), initializes `GuideEngine`
   (bundled guide JSON + one JSON read of `guide-progress.json` from disk), initializes
   `ChestManager` (one JSON read), loads Command/Crafting/Item/Settlement/Building/MarketWatch
   knowledge modules (bundled JSON resources), initializes the three remaining planner/notes/settings
   managers (one JSON read each), loads the GameZone parser catalog (bundled JSON), runs
   `refreshCompatibility()` (pure computation against already-loaded data).
7. `updateManager.startBackgroundChecks()` — **schedules** the first check 20 seconds in the future;
   does **not** perform it synchronously and does **not** block startup.

**Does adding Leaderboards increase startup by doing network work? No** — confirmed. Leaderboards'
only startup-time cost is constructing a `LeaderboardManager` object and an `HttpClient` (no
connection is opened by `HttpClient.newBuilder().build()` itself — it is lazy until first `send()`).
No `fetch()` call occurs anywhere during `onInitializeClient()`/`init()`. This was also confirmed
live: the boot log for a full `runClient` shows the mod's entire initialization sequence (Rule Pack
through "GZ Companion initialized successfully.") completing without a single GameZone-related log
line, and the live thread dump 34s after launch — well after startup completed — still showed no
`gzcompanion-leaderboards` thread at all (§5, §10), which could only be true if no fetch had ever
been scheduled.

Startup work overall is a handful of small bundled-JSON parses and a few tiny on-disk config reads —
all synchronous, all on the main thread, but all one-shot and small (no measured startup regression
tooling was set up for this pass; this is a structural/qualitative read of the code path, not a
stopwatch measurement).

## 9. Leaderboards deep audit

The following statements from the task brief were each traced to the exact production code that
implements them:

- **No eager fetch at startup / never-opened tab fetches nothing**: `LeaderboardManager` has no
  scheduled task and no field initializer that calls `ensureFresh`/`requestFetch`. Its only callers
  are `LeaderboardsTabComponent.render()` (`ensureFresh`) and the "Uppdatera" button's `Hit` action
  (`manualRefresh`) — both reachable only through that tab actually rendering.
- **Fetch selected board on demand**: `ensureFresh(current)` is called with whatever
  `currentDefinition()` currently resolves to, every frame the tab renders — confirmed above.
- **60s normal floor / 12s manual cooldown**: `LeaderboardManager.AUTO_REFRESH_INTERVAL = 60s`,
  `MANUAL_REFRESH_COOLDOWN = 12s`, enforced in `ensureFresh`/`canManualRefresh` against
  `lastFetchAttemptAt`.
- **One active fetch, at most one pending relevant request, latest-request-wins**: implemented via
  the single `activeFetch`/`pendingRequest` pair under `scheduleLock` (`requestFetch`,
  `startFetchLocked`, `runFetchThenAdvance`) — this is the exact mechanism added in the prior
  "harden leaderboard picker and request scheduling" pass (commit `0f7f9fe`), already covered by six
  dedicated tests including `rapidCyclingCoalescesToOnlyTheLatestRequest` and
  `noUnboundedPendingJobsRegardlessOfCycleLength` (proves cycling all 27 registry boards still
  produces exactly 2 network fetches).
- **HTTP on a dedicated background executor**: every `source.fetch(...)` call happens inside
  `executor.execute(() -> runFetchThenAdvance(...))` on the `gzcompanion-leaderboards` thread —
  never on the caller's thread.
- **Cached board switches are instant**: `getSnapshot()` is a plain `ConcurrentHashMap` read with no
  I/O; switching `currentDefinition()` back to an already-cached, still-fresh board causes
  `ensureFresh` to see `lastFetchAttemptAt` within the 60s floor and return immediately, so
  `getSnapshot()` returns the already-loaded data with no network call — proven by
  `sameBoardCoalescingStillWorksViaEnsureFresh`/the existing cached-switch tests.

**Scenarios A–H (live-client interactive verification):**

| Scenario | Expected | Result |
|---|---|---|
| A. Launch, never open Leaderboards, observe ≥2 min | Zero GameZone requests | **Verified structurally + by JFR**: no code path can reach `ensureFresh` without the tab rendering; a 30s idle JFR trace showed zero `gzcompanion` execution samples at all (§10). Not separately re-verified for a full 2-minute wall-clock window since the static proof is unconditional (no code path exists, not merely "wasn't observed"). |
| B. Home open ≥90s | Zero leaderboard requests | **Verified structurally**: `HomeTabComponent.render()` never calls anything in the `leaderboard` package; only `LeaderboardsTabComponent.render()` does. |
| C. Open Leaderboards on one board | One fetch for that board | **Verified by existing unit tests** (`LeaderboardManagerTest`) and by this pass's fresh 27-board live smoke test, which fetched each board exactly once. |
| D. Many frames on the same cached board | No repeated fetches | **Verified by design + tests**: `ensureFresh` is idempotent inside the 60s floor; `rapidCyclingCoalescesToOnlyTheLatestRequest`-style tests exercise repeated same-board calls. |
| E. Switch to an uncached board | Fetches the new board | **Verified by tests** (`sameBoardCoalescingStillWorksViaEnsureFresh` and the base fetch tests). |
| F. Switch back to a recently-cached board | Instant, no HTTP before threshold | **Verified by tests** — cached-board-switch tests in `LeaderboardManagerTest`. |
| G. Rapid-cycle many uncached boards while the first is active | Active + final pending only | **Verified by test** `rapidCyclingCoalescesToOnlyTheLatestRequest` (A→B→C→D while A is blocked ⇒ fetches `[A, D]` only, `callCount == 2`) and `noUnboundedPendingJobsRegardlessOfCycleLength` (all 27 boards ⇒ still `callCount == 2`). |
| H. Close Companion mid-fetch | No leak, finishes harmlessly, correct later state | **Verified structurally, not by live interaction** (see caveat below): closing the Companion screen only discards the `LeaderboardsTabComponent`/`GZCompanionMainScreen` objects — `LeaderboardManager` lives on `CompanionSession`, so an in-flight `runFetchThenAdvance` on `gzcompanion-leaderboards` is entirely unaffected by the screen closing; it completes normally, writes its result into `snapshots`, and that result is simply picked up the next time any UI reads `getSnapshot()` for that board (including a later reopen). No `Screen`, listener, or thread is created per-open, so there is nothing to leak. |

**Honest limitation**: scenarios A/B/D/G/H above are proven by static code tracing plus the existing
automated test suite (which exercises the exact same `LeaderboardManager`/`resolveClick` logic the
live client calls) and, for A, additionally by a live 30-second idle JFR trace. They were **not**
re-verified by literally clicking through a running Minecraft client in this pass, because this
environment has no desktop/window automation tool for the native LWJGL client (only Chrome browser
automation is available). This matches the same limitation already disclosed in the prior blocker-
fix pass's final report.

## 10. Profiling results

- **Live thread dump** (`jcmd <pid> Thread.print`), taken ~35 seconds after a clean `runClient` boot
  with Companion never opened and no world joined: 32 total JVM threads. Exactly **one**
  Companion-owned thread present (`gzcompanion-updater`, `TIMED_WAITING`, 15.62ms CPU accumulated
  over 33.5s elapsed — effectively idle). `gzcompanion-leaderboards` **did not exist yet** — proof
  that `LeaderboardManager`'s executor is lazily created only on first actual fetch, not at startup.
  Three `HttpClient-N-SelectorManager` threads were already present (all three `HttpClient`
  instances are constructed eagerly at `CompanionSession` init, per §5/§8), each near-0% CPU.
- **30-second JFR CPU-profile recording** (`settings=profile`) at the idle main menu (Companion UI
  closed, no world loaded): 80 `jdk.ExecutionSample` events captured; **zero** contain any
  `se.jimmyeliasson.gzcompanion` frame. Per-thread `jdk.ThreadCPULoad` samples show the render
  thread at ~2.5% user CPU (normal Minecraft/LWJGL overhead) and every JDK/Companion background
  thread at ~0%.
- Full mod test suite (`.\gradlew.bat clean test`) passed clean at the audited commit — see §17.

**Limitation**: this environment cannot join a Minecraft world without GUI automation, so all
measurements above reflect the **main-menu idle** state (Companion UI closed, no world loaded), not
"in a world, Companion closed." The in-world idle cost specifically attributable to
`GuideScheduler`'s per-750ms inventory scan (§3) could not be captured by JFR in this pass and is
instead assessed from source (cheap, bounded, but always-on while in a world). No lab-grade
allocation profiling (e.g., async-profiler, JFR allocation events beyond the default `profile`
settings) was performed; `jdk.ObjectAllocationSample` did fire 6 times during the 30s idle recording
with no Companion frames among them.

## 11. Practical FPS/frametime observations

**Not measured.** This environment has no tool that can interact with the native Minecraft/LWJGL
window (mouse clicks, keybind presses) to actually open Companion, join a world, or open
Leaderboards — only Chrome-based browser automation is available, which does not apply to a native
game window. No FPS counter or frametime data could be practically captured for the "UI closed vs.
Home open vs. Leaderboards open/cached" comparison the task requested. Stating this explicitly rather
than fabricating or estimating FPS numbers, per the task's own instruction to do so when reliable
measurement isn't possible. The JFR-based CPU/thread evidence in §10 is offered as the best available
substitute evidence for "no obvious continuous cost while idle."

## 12. Scalability assessment

Adding 5–10 more live modules (Bounty Board, Shop Search, Chronicles, Live Relics, Settlement
Explorer, Events) **on top of the current per-feature pattern** (each module hand-rolls its own
`LeaderboardManager`-style scheduler, its own single-thread executor, and its own `HttpClient`) would
concretely mean:

- 5–10 additional always-present daemon threads (one custom executor thread per module, each cheap
  but permanent for the session).
- 5–10 additional `HttpClient` instances, each with its own `SelectorManager` thread constructed
  **eagerly at session startup** if each module follows `GameZoneLeaderboardSource`'s current
  pattern of building its `HttpClient` in its constructor rather than lazily on first use.
- 5–10 separately-implemented (copy-pasted or reinvented) "latest relevant request wins" scheduling
  policies, each a fresh place to reintroduce exactly the request-queueing bug this repository fixed
  once already for Leaderboards (commit `0f7f9fe`).

None of this would show up as a dropped-frame or blocked-thread problem at 5–10 modules — each
individual idle thread/HttpClient costs approximately nothing (see §10's ~0% CPU readings) — so the
architecture is **not currently a performance blocker** for that growth. But it is real, linearly
accumulating overhead and duplicated-logic risk that gets progressively harder to retrofit the more
modules copy the pattern independently. See §15 for the recommendation.

## 13. Current risks

- **SHOULD FIX** — `GuideEngine.evaluate()`'s `progressStore.save()` runs synchronously on the main
  client tick thread when a guide step transitions (§3, §12). Infrequent (only on actual step
  completion, not every tick), so not a sustained cost, but it is the one place in the whole
  codebase where a filesystem write happens on the tick thread, which the stated performance budget
  explicitly rules out. A future change to move this save onto a background thread (reusing the
  existing executor pattern) would close this cleanly.
- **SHOULD FIX (architectural)** — three independently-constructed `HttpClient` instances and two
  independently-implemented background executors already exist after only two live-network features
  (Leaderboards, Updater). This is the concrete mechanism behind the "N modules = N threads" anti-
  pattern the task is trying to head off — see §12/§15.
- **NICE TO HAVE** — `GitHubReleaseSource` has no explicit response-size ceiling (inconsistent with
  `GameZoneLeaderboardSource`'s two-layer cap); low risk given the fixed, small, paginated GitHub API
  response, but worth closing for consistency.
- **NICE TO HAVE** — `GuideScheduler`'s per-750ms inventory scan + SHA-256 fingerprint runs whenever
  a world is loaded, independent of whether Companion's UI is open, and independent of whether the
  Guide is even relevant anymore (e.g., already 100% complete). Individually cheap; worth gating on
  "guide not yet complete" if a similar pattern is reused by a future always-on module.
- **NO ISSUE** — Leaderboards' own request scheduling, caching, and network policy: matches its
  documented design exactly and is protected by dedicated deterministic tests (§9, §16).
- **NO ISSUE** — Chest/Building/Settlement/MarketWatch on-disk stores grow only with legitimate,
  user-driven data entry (not automatically, not per-tick, not attacker-influenced), and are
  therefore a materially different risk category from an unbounded cache.

## 14. Concrete blockers

**None found.** No infinite loop, no busy-wait, no per-tick/per-frame network call, no per-tick/per-
frame filesystem write, no per-open thread/executor leak, and no unbounded cache was found anywhere
in the production codebase.

## 15. Recommended future architecture

Worth designing (**not implemented in this pass**) before the next 5–10 live modules land:

1. **One shared background executor** (a single daemon `ExecutorService`, owned by `CompanionSession`
   or a new small coordinator) that every live-data module submits work to, instead of each module
   constructing its own `Executors.newSingleThreadExecutor(...)`. This caps total Companion-owned
   worker threads at a small constant regardless of module count, and centralizes "never run two
   things for the same logical resource concurrently" bookkeeping in one place instead of
   reimplementing it per module.
2. **One shared `HttpClient`** built once (lazily, on first actual network use — not eagerly at
   `CompanionSession` construction, closing the eager-construction gap noted in §5/§8) and reused by
   every module that talks to any public HTTP endpoint, instead of one `HttpClient` per module. Per-
   destination policy (allowed host, redirect policy, timeout, response-size ceiling) can still be
   applied per call/request builder without needing a separate client instance per feature.
3. **A shared "latest relevant request wins" scheduling primitive**, extracted from
   `LeaderboardManager`'s already-built and already-tested `activeFetch`/`pendingRequest` design,
   parameterized by request key and priority (manual vs. auto), so a future module gets this
   guarantee for free instead of re-deriving and re-testing it from scratch (and risking
   reintroducing the exact request-spam bug fixed in commit `0f7f9fe`).
4. **A shared per-module freshness/cooldown policy helper** (the `AUTO_REFRESH_INTERVAL`/
   `MANUAL_REFRESH_COOLDOWN` pattern), so each new module declares its own interval/cooldown
   constants without re-implementing the `Instant`-comparison bookkeeping.

**When does this become worth doing?** The current cost (2 threads, 3 `HttpClient`s) is not itself a
problem. The recommendation is to build the shared layer **before**, not after, the next module
lands — retrofitting it after 5–10 modules each have their own hand-rolled copy of this logic is
significantly more invasive than building it once now with Leaderboards' already-proven scheduler as
the reference implementation.

## 16. Existing tests that already protect performance-relevant behavior

- `LeaderboardManagerTest`: no-request-spam, latest-pending-request-wins, same-board coalescing,
  cached-board instant switching, stale/offline fallback, and the 27-board rapid-cycle bound
  (`callCount == 2` regardless of cycle length).
- `GameZoneToastManagerTest`: dedupe map pruning and long-session boundedness
  (`testDedupeMapStaysBoundedOverLongSession`).
- `GameZoneLeaderboardSourceTest`: response-size ceiling (both the early Content-Length rejection and
  the post-hoc chunked-encoding fallback), redirect refusal, non-allowlisted-host refusal.
- `UpdateManagerTest` (20 cases): automatic-check cooldown, manual-check bypass, single-in-flight-
  check guarantee (`checkInProgress` compare-and-set).
- `GuideSchedulerTest`: tick-interval counting logic (pure, no Minecraft dependency).

## 17. Missing regression coverage (identified, not added this pass)

- No test asserts the **total number of Companion-owned background threads** stays constant
  regardless of how many times the Companion UI or Leaderboards tab is opened/closed (this pass
  verified it live via `jcmd Thread.print`, but there is no automated regression test for it).
- No test asserts the **number of `HttpClient` instances** GZ Companion constructs, so a future
  module silently adding a fourth/fifth instance (instead of reusing a shared one, per §15) would go
  unnoticed by CI.
- No test covers `GuideEngine`'s autosave running off the tick/main thread (it doesn't yet — §13 —
  so a test here should be written together with that fix, not before it).
- No test asserts a hard ceiling on the Chest/Building/Settlement/MarketWatch on-disk stores (lower
  priority — these grow with legitimate player data, not automatically).
- No structural "fair play"-style test (like `LeaderboardFairPlayTest`) exists yet for a general
  "no production class outside an allow-listed set may construct `HttpClient`/`ExecutorService`/
  `Thread` directly" rule — such a test would make the §15 consolidation recommendation
  self-enforcing once implemented, and would immediately catch a future module that skips the shared
  layer.

We will decide separately whether to add any of the above, per the task's instruction not to add new
permanent tests during this audit.

## Explicit answer

> **Will adding more features make Minecraft slower if we continue using the current architecture?**

Not necessarily, and not in an immediately-observable way for the next 5–10 modules — each
individual module built the way Leaderboards was built (own bounded cache, on-demand fetch gated on
UI, one active + one pending request, all I/O off the render/tick thread) adds effectively zero idle
cost and no measurable per-frame cost. The measured evidence in this pass (zero `gzcompanion` frames
across 80 idle execution samples, exactly one Companion thread present at idle, zero GameZone
network traffic until Leaderboards is opened) supports that the current single feature is not a
problem.

However, **the current architecture does not yet enforce this for free** — nothing stops the next
module from being built with its own executor, its own eagerly-constructed `HttpClient`, and its own
from-scratch (and possibly buggy) request-scheduling logic, the way Leaderboards originally was
before its own request-spam bug had to be found and fixed. At small scale (2 modules today) this
already produced 2 threads and 3 `HttpClient`s that didn't strictly need to be separate. At 5–10 more
modules under the same pattern, that becomes 7–12 always-present threads and 8–13 `HttpClient`
instances — still probably not "slower" in an FPS sense, but a growing, unnecessary, and
increasingly hard-to-audit footprint that runs directly against the stated permanent design rule.
The recommended fix (§15) is architectural, not urgent, and should be designed before — not after —
the next module ships.

---

## Appendix: audit execution record

- **Test result**: `.\gradlew.bat clean test` — **BUILD SUCCESSFUL**, full suite, zero failures, at
  commit `0f7f9febb900b776de7cb19f5d72d032d29f1fc9`.
- **Files changed by this audit**: only this document, `docs/PERFORMANCE-AUDIT-ALPHA4.md`, added.
  No production source, test source, `gradle.properties`, `compatibility.json`, or installer file was
  modified.
- **No production behavior changed. No version changed. No tag created. No release created. No
  release artifact overwritten.** Temporary profiling artifacts (a scratch `LiveSmoke.java`, one JFR
  recording) were created only under the session scratchpad directory, outside the repository, and
  are not part of this commit.

---

## Follow-up (2026-09-13): performance foundation implemented before alpha.5

This section is dated and appended, not a rewrite of the audit above - the original findings and
their reasoning stand exactly as recorded. This documents what was actually built in response to
this audit's two SHOULD-FIX findings, ahead of the Bounty Board feature (which will be the first
new module to exercise this foundation). No feature, UI change, version bump, tag, or release
happened in this pass - see the companion final report for that pass's exact scope confirmation.

### What the audit found (recap)

1. No performance blocker.
2. **SHOULD FIX**: Guide progress persistence could synchronously write to disk from the Minecraft
   client tick thread when a guide step completed (§3, §12, §13 above).
3. **SHOULD FIX (architectural)**: after only two live-network features (Leaderboards, the
   updater), three separate `HttpClient` instances and two separate custom executors already
   existed - the exact "N modules = N threads" pattern the permanent design rule warns against
   (§5, §12, §15 above).

### What changed

**New shared GameZone live-data runtime** - `se.jimmyeliasson.gzcompanion.gamezone.net.
GameZoneLiveDataRuntime`. One `CompanionSession`-owned instance now supplies the single shared
daemon executor (`gzcompanion-gamezone-live`) and the single shared `HttpClient` for every "public
GameZone web data" feature. Both are created lazily - on first actual use, not at construction -
closing the exact eager-`HttpClient`-at-startup gap this audit found in the pre-fix
`GameZoneLeaderboardSource`. `LeaderboardManager` no longer constructs its own `ExecutorService`;
`GameZoneLeaderboardSource` no longer constructs its own `HttpClient`. Both now hold a reference to
the shared runtime and call `runtime.executor()`/`runtime.httpClient()` only at the point work is
actually dispatched. The updater (`UpdateManager`/`GitHubReleaseSource`/`HttpUpdateByteSource`)
deliberately keeps its own separate executor and `HttpClient` - different host, different
redirect/timeout/scheduling requirements, no real benefit to merging it with GameZone traffic.

**New shared local-persistence runtime** - `se.jimmyeliasson.gzcompanion.storage.
LocalPersistenceRuntime`. A second, independent, lazily-created shared daemon executor
(`gzcompanion-local-persistence`) for local-disk write work that must not block the tick/render/
main thread. Deliberately a separate executor from the GameZone one - a slow disk write should
never delay a GameZone network request, or vice versa.

**Guide progress persistence moved off the tick thread** - `se.jimmyeliasson.gzcompanion.guide.
progress.AsyncGuideProgressStore` wraps the existing `JsonGuideProgressStore` (unchanged - its own
atomic temp-file-then-move write behavior is fully preserved) and implements the exact same
"at most one active write + one pending write, latest state wins" coalescing scheme
`LeaderboardManager` already proved out for network fetches. `GuideEngine` itself was **not**
changed - it still calls `progressStore.save(data)` from the same three call sites (the automatic
tick-triggered `evaluate()`, and the user-driven `markStepCompleted`/`undoStepCompletion`); only
what `CompanionSession` injects as that `GuideProgressStore` changed, from the raw synchronous
`JsonGuideProgressStore` to the async-wrapping decorator. `GuideProgressData`/`ContextProgress`
were already fully immutable (both defensively copy into unmodifiable maps in their own compact
constructors), so no additional snapshot-copying was needed for the background writer to be safe.

`resetContext` (the whole-guide-progress reset, a rare explicit user action) stays synchronous by
design - `GuideEngine.resetGuideProgress()` immediately reloads from disk right after calling it,
so making the reset itself asynchronous would let that reload race ahead and read stale data. To
stop a save that was queued *before* a reset from later silently undoing it, `resetContext` bumps
an internal epoch counter and drops anything sitting in the not-yet-dispatched pending slot; the
background writer re-checks its captured epoch immediately before writing and skips (logs, does
not write) a save a reset has since superseded. This closes the realistic case. A save that is
already physically inside the delegate's `save()` call at the exact instant a reset happens is not
retroactively cancelled - because `JsonGuideProgressStore`'s own `resetContext` always reads
whatever is *currently* on disk (not a stale cached value) before writing its result, the file
still ends up correct in that narrow interleaving in the overwhelmingly common case, but a
theoretical last-write-wins ordering edge case is not fully eliminated. This is an accepted,
documented, best-effort limitation given how rare (`resetContext` is a deliberate reset button
click) and inexpensive (a small JSON file) these writes are - full distributed-transaction-style
atomicity was judged not proportionate here.

**Bounded shutdown flush** - `GZCompanionClient` now registers `ClientLifecycleEvents.
CLIENT_STOPPING` (confirmed present via `fabric-lifecycle-events-v1`, already a dependency) to call
`CompanionSession.flushBeforeShutdown(Duration.ofSeconds(2))`, which bounded-waits on
`AsyncGuideProgressStore.flushBounded` for any in-flight/pending guide-progress write to actually
reach disk. This never blocks longer than 2 seconds - a stuck or slow disk cannot hang game
shutdown - but under normal conditions (a write takes milliseconds) it means quitting the game
right after completing a guide step no longer risks losing that step's persistence to a daemon
thread being cut off mid-write.

### Thread/HttpClient ownership: before vs. after

| | Before this pass | After this pass |
|---|---|---|
| GameZone live-data worker | `gzcompanion-leaderboards`, owned solely by `LeaderboardManager`, one per feature if copied | `gzcompanion-gamezone-live`, owned by `GameZoneLiveDataRuntime`, shared by every current/future GameZone live-data feature |
| GameZone HTTP transport | `GameZoneLeaderboardSource`'s own eagerly-built `HttpClient` | `GameZoneLiveDataRuntime`'s lazily-built, shared `HttpClient` |
| Updater worker/transport | `gzcompanion-updater` + its own `HttpClient`s (unchanged) | Unchanged - deliberately still separate |
| Local persistence worker | None (writes ran synchronously on whichever thread called `save()`, including the tick thread) | `gzcompanion-local-persistence`, owned by `LocalPersistenceRuntime`, currently used by Guide, available for future local-disk-write needs |

### Regression protections added

- `GameZoneLiveDataRuntimeTest` - construction is fully lazy (neither executor nor `HttpClient`
  created), each is memoized (repeated calls return the identical instance), the two are
  independently lazy, constructing a `LeaderboardManager` against a fresh runtime creates neither,
  and an actual fetch creates only the executor (not the `HttpClient`, which is a different
  module's - `GameZoneLeaderboardSource`'s - concern).
- `LeaderboardManagerTest` additions - constructing a manager triggers zero fetch work and does not
  create the shared executor; the executor is created lazily on first fetch then memoized; two
  independent managers sharing one runtime dispatch through the literal same worker thread;
  reconstructing a manager (simulating closing/reopening the Companion UI) never creates a second
  worker. All of `LeaderboardManagerTest`'s pre-existing latest-request-wins/manual-priority/
  rapid-cycling tests were re-verified unchanged and still pass - the scheduling logic itself was
  not touched, only where its executor comes from.
- `GameZoneLeaderboardSourceTest` additions - constructing a source does not create the shared
  `HttpClient`; an actual fetch creates it, reused (not recreated) across repeated fetches; two
  independent sources sharing one runtime reuse the identical `HttpClient`. Every pre-existing
  security-relevant test (redirect refusal, non-allowlisted-host refusal, response-size ceiling,
  never-follows-redirects) was re-verified unchanged and still passes against the shared transport.
- `LocalPersistenceRuntimeTest` - construction is lazy, the executor is memoized, the worker thread
  is a correctly-named daemon.
- `AsyncGuideProgressStoreTest` - `save()` returns without waiting for a blocked delegate write;
  rapid saves coalesce to the latest state (not every intermediate one, proven with an exact
  A-then-D-style sequence assertion mirroring `LeaderboardManagerTest`'s own rapid-cycling proof);
  no unbounded pending queue across 50 rapid state changes; a failing delegate save is caught, does
  not crash, and a later save recovers; `flushBounded` waits for an in-flight save within its
  bound, times out (returns false) rather than blocking forever on a stuck save, and returns
  immediately when nothing is pending; `resetContext` is synchronous and provably drops a save that
  was only queued (never dispatched) before it.
- `ThreadingInfrastructureRulesTest` - a narrowly-scoped, explicit-allow-list structural test
  (mirroring `LeaderboardFairPlayTest`'s source-scanning convention) asserting that no production
  class outside `GameZoneLiveDataRuntime.java`, `LocalPersistenceRuntime.java`,
  `UpdateManager.java`, `GitHubReleaseSource.java`, or `HttpUpdateByteSource.java` directly
  constructs an `HttpClient`, an `Executors.new*` executor, or a raw `Thread`. This makes the "no
  future GameZone module gets its own thread/client" rule self-enforcing: a future `BountyBoard
  Source.java` that copies the pre-fix `GameZoneLeaderboardSource` pattern verbatim will fail this
  test immediately, rather than being caught (or missed) in a future audit.

### Validation performed this pass

- `.\gradlew.bat clean test` - **BUILD SUCCESSFUL**, full suite, zero failures (821 tests total,
  up from 771 before this pass - the exact new/updated counts are in this pass's final report).
- `.\gradlew.bat build` - **BUILD SUCCESSFUL**; the resulting jar was used only for the
  `runClient` check below, never rebuilt into a release bundle (compatibility.json/installer/
  release artifacts were not touched).
- `.\gradlew.bat runClient` plus a live thread dump (`jcmd Thread.print`) taken after boot with
  Companion never opened: confirms `gzcompanion-gamezone-live` does **not** exist yet (proving the
  shared runtime's laziness live, not just in a unit test), `gzcompanion-updater` exists as before,
  and no `GameZoneLiveDataRuntime`/`LocalPersistenceRuntime`-owned thread appears before any
  GameZone live-data feature or guide-progress save has actually run. See this pass's final report
  for the exact thread-dump excerpt and counts.

### Explicit human-QA note

Interactive verification that Leaderboards still loads correctly end-to-end inside a live client,
and that opening/reopening the Companion UI in a real play session does not grow the thread count,
still requires a human at the keyboard - this environment has no tool that can drive the native
Minecraft/LWJGL window. The static/unit-test evidence above proves the scheduling and laziness
logic is correct in isolation; it does not substitute for a real interactive session. See this
pass's final report for the precise, itemized list of what still needs Jimmy's own testing.

---

## Follow-up (2026-09-13): correctness fixes to the performance foundation

This section is dated and appended, not a rewrite of the two sections above - both the original
audit and the first follow-up's reasoning stand as recorded. An independent code review of the
first follow-up's implementation (commit `6174970`) found two correctness blockers and one
foundation-hardening gap in that implementation itself. This section documents what was wrong and
what was actually fixed. No feature, UI, or version change happened in this pass either.

### What the review found

1. **BLOCKER - production guide-save failures were invisible.** `AsyncGuideProgressStore` was
   written to catch an exception from `delegate.save(...)`, but the production delegate,
   `JsonGuideProgressStore.save()`, caught its own `IOException` internally, logged it, and
   returned normally - it never actually threw. So a real disk failure was silently treated as a
   success: `lastSaveFailed` stayed `false`, and `flushBounded` could report `true` even though the
   latest guide state was never actually written to disk. The `FakeStore`-based tests from the
   first follow-up did not catch this because `FakeStore` itself was written to throw, which the
   real production store never did.
2. **BLOCKER - a reset could still be overwritten by a stale pre-reset save.** The first
   follow-up's `resetContext` bumped an epoch counter and checked it inside the background writer
   immediately before calling `delegate.save(...)` - but the check and the actual write were two
   separate steps, not one atomic operation. A worker could pass the epoch check, then have
   `resetContext` complete and bump the epoch again, and only then perform the actual (now stale)
   write - undoing the reset. The existing test only proved a *queued-but-not-yet-started* save was
   dropped; it did not exercise the actual dangerous ordering (a save already accepted/in-flight).
3. **SHOULD FIX - the shared runtimes exposed a raw, unbounded executor.** `GameZoneLiveDataRuntime`
   /`LocalPersistenceRuntime` handed out a plain `ExecutorService` built with
   `Executors.newSingleThreadExecutor` - which uses an unbounded internal queue. `LeaderboardManager`
   itself never exploits this (its own active+pending scheme keeps at most one job outstanding), but
   nothing stopped a future, less careful GameZone module from calling `execute()` in a loop and
   growing that shared queue without bound - and `ThreadingInfrastructureRulesTest` could not have
   caught that, since such a module would be using the *approved* shared executor, not creating its
   own.

### Fix 1 - real persistence failure contract

`JsonGuideProgressStore.save()` now throws a new unchecked `GuideProgressPersistenceException`
(wrapping the underlying `IOException`) instead of catching it and returning normally - it still
logs, but no longer silently swallows the failure. `GuideProgressStore.resetContext()`'s existing
implementation calls `save(...)` internally with no try/catch, so the exception propagates through
it automatically with no code change needed there. `AsyncGuideProgressStore.runSaveThenAdvance()`
catches this exception specifically (plus a broad `Exception` catch as defense in depth against
anything unforeseen) - exactly the catch block the first follow-up already had, which simply never
fired against the real delegate before this fix.

**Retry/durability semantics after a failure**: a failed (or even a couldn't-be-scheduled, see Fix
3) write is retained as a single `dirtyAfterFailure` field - never discarded - unless a *newer*
`save()` request has since arrived (a newer `GuideProgressData` snapshot is always a superset of an
older one, since `GuideEngine` only ever adds completions, so a fresh save silently "retrying" a
stale failed one loses nothing). `flushBounded(timeout)` gives a dirty write exactly **one** fresh
retry attempt per call - proven by a dedicated test asserting the delegate is invoked exactly twice
(the original failed attempt plus one retry), never a repeated hammering loop against a persistently
broken destination. `flushBounded` now returns `true` if and only if nothing remains active,
pending, or dirty when it returns - i.e. the latest requested state is actually confirmed persisted,
closing the exact "flushBounded can report true although the latest state is not durably persisted"
gap the review found.

### Fix 2 - reset is now a full guarantee, not best-effort

The epoch-counter approach was replaced entirely. `resetContext` now synchronously **drains**
everything outstanding first - waiting (bounded) for an active write to actually finish, and
letting any pending write actually run rather than dropping it (so a different context's
concurrently-queued progress is never lost just because another context was reset) - and only THEN
performs the reset. This works as a genuine guarantee, not a best-effort one, because Minecraft's
client/tick/render/input handling is single-threaded and `resetContext` is always called from that
one thread: while a call to it is on the call stack, no OTHER save can possibly be dispatched from
production code, so once the drain observes "nothing active or pending," that state cannot change
underneath it before the reset's own write runs. The wait is bounded (5 seconds, a small
package-private test-only constructor overload allows a shorter bound for tests) purely so a
truly-hung disk I/O call cannot hang the reset button forever - hitting that bound in practice would
mean the underlying disk write itself is stuck, a far bigger problem than this specific ordering
guarantee, and is logged loudly if it ever happens.

**Deterministic proof of the exact dangerous ordering** the review specified: a test dispatches a
save, waits for it to be genuinely accepted and paused immediately before its durable write (using
a `CountDownLatch` pair, no sleep-and-hope), calls `resetContext` from the main test thread (which
blocks, waiting for the paused write), then releases the paused write. The required result - no
pre-reset state visible once `resetContext()` returns, using a real `JsonGuideProgressStore` under a
`ControllableStore` test wrapper, not just call-order bookkeeping - is asserted directly against the
actual persisted JSON content. Also covered: a save still only pending (not yet dispatched) before
reset is drained (allowed to run), not dropped; a save requested strictly after reset returns is
completely unaffected; two contexts' data - one reset, one not - proves the untouched context's
progress survives; a reset whose own underlying write fails is caught and observable via
`lastSaveFailed()` without throwing; and a reset whose active write never finishes within the wait
bound still returns promptly (proceeding with the reset) rather than hanging indefinitely.

### Fix 3 - bounded shared-runtime scheduling, no raw executor exposure

`GameZoneLiveDataRuntime`/`LocalPersistenceRuntime` no longer expose an `ExecutorService` at all.
The only way to run work on either shared worker now is `boolean submit(Runnable)`. Internally,
both are backed by a `ThreadPoolExecutor` with a single worker thread (unchanged - still exactly one
shared daemon thread each) but now a **bounded** `ArrayBlockingQueue` (capacity
`MAX_QUEUED_JOBS = 8`, comfortably larger than the number of live-data modules this is designed for,
since each well-behaved module keeps at most one job outstanding at a time) and an explicit
`ThreadPoolExecutor.AbortPolicy` - **never** `CallerRunsPolicy`, which could otherwise silently run
a GameZone HTTP request (or a disk write) on whatever thread called `submit` - potentially the
render/tick thread. A rejection is always just a rejection: `submit` returns `false`, and the caller
must handle it explicitly.

`LeaderboardManager.startFetchLocked` now checks `submit`'s return value: on rejection, the board's
snapshot reverts to its previous status (never left stuck showing LOADING for a fetch that will now
never run) and `activeFetch` is cleared so a later request can still try again. A genuine, if subtle,
correctness bug surfaced while implementing this: `lastFetchAttemptAt` was previously being recorded
*before* checking whether the job was actually accepted, which meant a rejected fetch would still
count against the 60-second auto-refresh floor and silently block a real retry for up to a minute
even though no network request had actually happened. Fixed by only recording the attempt timestamp
once `submit` actually returns `true`. `AsyncGuideProgressStore.dispatchLocked` was given the
equivalent treatment: a rejected submission is treated exactly like a failed write (retained as
`dirtyAfterFailure`, observable via `lastSaveFailed()`), never silently dropped and never left
holding an "active" slot with no job that will ever clear it.

### Regression protections added this pass

- `GameZoneLiveDataRuntimeTest`/`LocalPersistenceRuntimeTest` - new tests prove: once the worker is
  busy and the bounded queue is completely full, a further `submit()` is explicitly rejected; a
  rejected `submit()` never runs its task on the calling thread (proven by identity-checking
  `Thread.currentThread()` against the test's own thread from inside the task).
- `LeaderboardManagerTest` - a new test saturates the shared runtime from outside
  `LeaderboardManager` entirely (simulating other modules), then proves a rejected fetch reverts to
  its previous status instead of sticking in LOADING, performs zero actual network work, and that a
  later request succeeds normally once the runtime is no longer saturated.
- `AsyncGuideProgressStoreTest` - substantially expanded: real `JsonGuideProgressStore` failure
  end-to-end (using a deterministic, cross-platform filesystem obstruction - pre-creating a
  directory at the exact `.tmp` path the store's atomic write needs, which fails the write before
  any file is touched, needing no OS-specific permissions/ACLs), proving the failure is signaled,
  the previously-valid file is left byte-for-byte intact, `lastSaveFailed()` becomes true,
  `flushBounded` reports false, and recovery (removing the obstruction) lets the retained dirty
  state finally persist and clears the failure flag; the exact required deterministic active-save/
  reset race, asserted against real persisted JSON content; pending-save draining before reset;
  other-context preservation; reset-with-failure; reset-timeout-proceeds-anyway.
- `ThreadingInfrastructureRulesTest` - extended with: a check that neither shared runtime file
  exposes a public method returning a raw `ExecutorService`/`ThreadPoolExecutor`, that both declare
  the controlled `submit(...)` API, that neither uses `CallerRunsPolicy` (checked as an actual
  instantiation, `CallerRunsPolicy(`, not merely the class name appearing in explanatory Javadoc,
  to avoid a self-defeating false positive against the very comments explaining why it's avoided),
  and that both use an explicit rejection signal (`AbortPolicy`/`RejectedExecutionException`); a
  new forbidden-pattern check that no non-allow-listed file may even reference
  `java.util.concurrent.ExecutorService`/`ScheduledExecutorService` (fully-qualified/import form
  only, to avoid false-positiving on prose) or directly construct a `ThreadPoolExecutor`/
  `ScheduledThreadPoolExecutor`.

### Validation performed this pass

- `.\gradlew.bat clean test` - **BUILD SUCCESSFUL**, full suite, zero failures (835 tests total, up
  from 821 before this pass).
- `.\gradlew.bat build` - **BUILD SUCCESSFUL**; jar used only for the `runClient`/live-smoke checks
  below, never rebuilt into a release bundle.
- `.\gradlew.bat runClient` plus a fresh live GameZone 27-board smoke test and a live thread dump -
  see this pass's final report for the exact counts and excerpt.

### Human QA still required

Unchanged from the prior follow-up's note: interactive verification inside a real running client
(opening Leaderboards, repeated UI open/close, completing a guide step and confirming it survives a
normal game exit) still requires a human at the keyboard - this environment has no tool to drive the
native Minecraft/LWJGL window. See this pass's final report for the itemized list.
