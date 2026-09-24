# Changelog

All notable changes to GZ Companion will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [0.1.0-alpha.7] - 2026-09-24

### Added
- **Kistor 2.0** - the Kistor tab is rebuilt around "where are my things?", still built only on
  storage the player has legitimately opened (the M3 capture system is unchanged). See
  `docs/CHEST-MANAGER.md`.
  - **SAKER** (default mode): an aggregated last-known item index across the current context's
    storage (`chest.index.ChestItemIndex`), with real vanilla item icons, totals, "Finns i N
    förvaringar", and per-item detail listing every storage location with amount, freshness,
    distance and **Hitta**; **Hitta närmaste** picks the nearest known same-dimension storage.
  - **FÖRVARING**: storage cards with top items and freshness, favorites pinned under FÄSTA, and a
    full detail pane (type/shape, dimension, coordinates, group, location note, contents with
    icons, "Sedan förra öppningen", actions). "Glöm" is now a quiet secondary action that still
    needs confirmation.
  - Local **favorites**, one-level **groups** and **location notes** per storage - local only,
    sanitized and length-capped; searchable and filterable.
  - One global local search ("Vad letar du efter?") matching item names/ids, labels, groups,
    notes, storage type, dimension and coordinates.
  - **Freshness** badges (`ChestFreshness`) and a bounded one-step **previous snapshot** with a
    "Sedan förra öppningen" diff (`ChestSnapshotDiff`).
  - **Capture feedback** toasts after legitimate finalization: "Ny förvaring sparad", "<namn>
    uppdaterad" (only when contents changed) and "✓ <namn> hittad"; respects the Companion
    notification setting, deduped and bounded.
  - **HITTA navigation** to ONE explicitly selected known storage: a top-center HUD
    (`KistorNavigationHudElement`, `HudElementRegistry`) with a smoothly rotating camera-relative
    arrow, straight-line distance ("fågelvägen"), vertical difference, near/arrived states and an
    honest wrong-dimension state; a NAVIGERAR banner with Stoppa in Kistor. Ends when the exact
    target is legitimately opened, and stops safely on forget/reset/context change/disconnect.
  - **Hitta material i kistor** from Settlement (Material) and Byggplaner (building detail), and a
    local **Hämtningslista** grouped by storage with a documented deterministic allocation rule and
    estimated shortages - gated on the existing "use last-known chest data in planners" setting.
  - Pure domain packages `chest.index`, `chest.nav`, `chest.material`, plus `KistorRuntime`;
    `KistorFairPlayTest` structurally forbids world/chunk/block-entity access, raytracing, world
    rendering, player automation, chat/commands/packets and networking in all Kistor 2.0 code.
  - Diagnostics now include the Kistor index schema/count and whether navigation is active (never
    the target or any coordinates).

### Changed
- `chest-index.json` schema **v1 → v2** (optional `favorite`, `group`, `locationNote`, `previous`
  per storage). v1 files load losslessly in memory and are rewritten as v2 on the next legitimate
  save; a future schema still fails closed without being touched. An older v1-only build will
  treat a v2 file as incompatible (fails closed, never overwrites).
- Unknown/future item ids no longer resolve to "Air" in Kistor display names.

---

## [0.1.0-alpha.6] - 2026-09-17

Turns the Settlement tab from an offline-only planner into a real **LIVE** GameZone settlement
dashboard, verified end-to-end in a real Minecraft Launcher session against a live GameZone
server, plus the scroll/layout fixes that human QA found along the way.

### Added
- **Live Settlement dashboard**: the Settlement tab now shows a real **LIVE** GameZone settlement
  state (name, level, level name, role, bonus, treasury, and currently-online same-settlement
  players) when connected and a settlement is safely recognized - reusing the exact same shared
  `GameZoneLiveStatusTracker`/`GameZoneSettlementTracker` Home/Online already use, never a new
  parser or network source. A raw live level is only trusted for automatic Progression/Material
  planning once confirmed aligned with the bundled Rule Pack (`LiveSettlementLevel`); a mismatch
  is still shown honestly, never hidden, and planning fails closed to the manual planner. See
  `docs/SETTLEMENT-COMPANION.md`.
- `gamezone.GameZoneLiveContext`/`GameZoneLiveContextBuilder`: the one shared entry point Home,
  Online, and Settlement all now call to refresh live data, so opening any one of them first is
  never required to "prime" another's live data.
- `settlement.LiveLevelAlignment`/`LiveSettlementLevel`/`EffectiveCurrentLevel`/`SettlementLiveView`:
  small, pure, Minecraft-API-free view types combining live GameZone facts with Rule Pack
  alignment - `EffectiveCurrentLevel` is a pure read-time decision with no access to
  `SettlementPlannerManager`, so it can never mutate the player's manually saved current level.
- Progression now derives PAST/DU ÄR HÄR/NÄSTA/MÅL state per level from the effective current
  level (never persisted); Material automatically starts its range at the effective current level
  and shows its source ("... (LIVE)" or "... (lokal planering)"); Medlemmar shows an "ONLINE FRÅN
  DITT SETTLEMENT" section listing only currently-visible same-settlement players, never a full
  roster or an inferred offline state.
- `SettlementFairPlayTest`: structural source-scanning (mirroring `BountyFairPlayTest`) proving
  the live Settlement dashboard never references entity/world/chunk-scanning or coordinate APIs,
  never sends a command/chat message automatically, and never constructs a new network client or
  thread.

### Changed
- `HomeTabComponent`/`OnlineTabComponent` now refresh their live trackers via the same shared
  `GameZoneLiveContextBuilder.refresh(...)` Settlement uses, instead of each calling
  `tracker.update(...)` independently - purely an internal refactor, no behavior change for either
  tab.
- A trusted live settlement level now becomes the effective current level for Progression/Material
  planning without ever overwriting the player's manually saved offline current level.
- Canonical product version is now `0.1.0-alpha.6`. Minecraft/Fabric versions are unchanged from
  `0.1.0-alpha.5` (Minecraft 26.1.2, Fabric Loader 0.19.5, Fabric API 0.155.3+26.1.2), so the
  in-app updater uses the same safe fast path it used for `0.1.0-alpha.5`.

### Fixed
Found and fixed during real human gameplay QA against a live GameZone server:
- **Settlement Overview overflow/scrolling**: Översikt had no scroll offset at all, so content
  longer than the panel was clipped behind the footer with no way to reach it. It now scrolls
  fully to the actual bottom.
- **Overview content-height correctness**: the live-card height estimate silently omitted the
  online-members row, undercounting the real content height and leaving up to 10px of content
  unreachable even after scrolling was added. The estimate and the renderer now share one set of
  named row-height constants so they can't drift apart again.
- **Progression detail viewport**: the scroll viewport used to compute max-scroll included the
  space occupied by the fixed "Sätt som OFFLINE-nuvarande"/"Sätt som mål" button strip, so content
  could hide behind those buttons with no way to scroll further. The viewport and the button strip
  now share one geometry helper.
- **Compact Progression scrolling**: in compact (narrow) layout, the list and detail panes occupy
  the same screen rect (only one renders at a time), and the mouse wheel was always routed to the
  list pane regardless of which one was actually visible. It now follows the same
  `compactShowingDetail` flag the renderer uses, so the wheel always scrolls whichever pane is on
  screen.

### Fair play
- Live settlement facts (name, level, level name, role, bonus, treasury, online same-settlement
  players) come only from data already visible to the client via existing TAB-header/status
  parsing - no new network source, no scraped endpoint.
- No entity/world/chunk scanning, no coordinate/distance/direction display.
- No inference of hidden or offline players - only currently-visible same-settlement players are
  ever shown as online.
- No automatic command or chat message, no server inventory/economy claim beyond what GameZone's
  own client-visible state already shows.
- No new thread, executor, or `HttpClient` - reuses the existing shared live-data infrastructure.

## [0.1.0-alpha.5] - 2026-09-14

Adds live Bounty Board support, and a shared-runtime/async-persistence hardening pass built ahead
of it - the first release after `0.1.0-alpha.4` distributed through GZ Companion's own in-app
updater.

### Added
- **Bounty Board tab**: read-only view of GameZone's own public active-bounty registry (PvE hunts
  for a unique named target, Coin reward, optional public clue, optional expiry) via its genuine
  public JSON API (`/api/bounties`) - the same endpoint GameZone's own wiki page fetches for its
  live widget. On-demand only (60s auto floor, 12s manual cooldown, one fetch at a time,
  coalesced), built entirely on the shared `GameZoneLiveDataRuntime` (no new thread/HttpClient).
  The full public clue is always shown, never truncated at a line limit, and the detail pane
  scrolls (with its own scroll offset, separate from the list's) to safely contain long
  names/clues/expiry text without ever overflowing its own footer. Never scans entities, never
  shows a coordinate/distance/direction, never sends a command automatically - the one
  command-related action is a deliberate clipboard-copy button. See `docs/BOUNTY-BOARD.md`.
- `bounty` package: `BountyEntry`/`BountyStatus`/`BountySnapshot`/`BountyFetchResult`,
  `BountyJsonParser`, `GameZoneBountySource`, `BountyManager`, `BountyFormatter`, `BountyExpiry`.
- New `BOUNTY` icon (`textures/gui/icons/bounty.png`) and `TabType.BOUNTIES` nav entry, positioned
  between MarketWatch and Leaderboards.
- **Shared GameZone live-data runtime** (`GameZoneLiveDataRuntime`): one shared, lazily-created
  daemon executor (`gzcompanion-gamezone-live`) and `HttpClient` for every public GameZone
  web-data feature - Leaderboards and now Bounty Board both reuse it, so a new feature never grows
  its own thread/HttpClient. `LeaderboardManager` no longer owns its own executor;
  `GameZoneLeaderboardSource` no longer eagerly builds its own `HttpClient` at startup.
- **Async Guide persistence** (`LocalPersistenceRuntime` + `AsyncGuideProgressStore`): Guide's
  automatic per-step progress save no longer writes to disk synchronously on the Minecraft tick
  thread - reuses the same active/pending "latest state wins" coalescing scheme already proven by
  `LeaderboardManager`, with a bounded shutdown flush on client exit.
- `ThreadingInfrastructureRulesTest`: a structural, allow-listed regression guard so a future
  GameZone module can't quietly grow its own thread/HttpClient again.

### Fixed
- `MainScreenLayout`'s sidebar tab-height calculation could force a taller-than-available tab
  height once enough nav tabs existed, overflowing the sidebar at small viewports - fixed to never
  exceed what actually fits, regardless of tab count.
- **Bounty Board correctness hardening** (independent review follow-up): a missing/malformed
  `expiresAt` was indistinguishable from GameZone explicitly saying "no time limit" - fixed with a
  new explicit `BountyExpiry` (`NoLimit`/`ExpiresAt`/`Unknown`) model; a nonempty bounty registry
  where every entry failed to parse (e.g. a renamed required field) could silently present as "no
  active bounties" - fixed to report `Incompatible` instead; a `STALE` empty cache could claim
  "there is no active hunt right now," which is only true of the last successful fetch, not
  necessarily the current state - fixed with distinct `STALE`-empty wording. See
  `docs/BOUNTY-BOARD.md` §14.
- **Bounty empty-state follow-up**: the small "X AKTIVA BOUNTIES" count strip still said "INGA
  AKTIVA BOUNTIES" for a `STALE`-empty cache even after the panel-level fix above, restating the
  exact claim just corrected - it is now blank for `STALE`-empty instead. `IDLE` (never
  successfully fetched) also used to fall back to the same "no active bounties" wording as a
  genuine empty result; it now shows its own neutral "VÄNTAR PÅ DATA" state.
- **Bounty detail overflow, fixed by human Minecraft QA**: the compact/wide Bounty detail pane
  could draw content (including the command-copy button) past its own bounds and over the footer
  once real content was long enough - fixed with a scissored, independently-scrollable detail
  region; the "< Lista" back button and the command-copy button are now both pinned outside the
  scrollable area so neither can ever be scrolled out of reach or drawn over the footer.
- **Async persistence correctness** (independent review of the shared-runtime foundation above): a
  real disk write failure was previously invisible to `AsyncGuideProgressStore` (the delegate never
  actually threw on failure) - now surfaced and given exactly one retry per flush;
  `resetContext()` could let a pre-reset write land AFTER a reset and resurrect progress the player
  had just removed - now fully atomic (a reset either fully applies and persists, or does not
  happen at all, never a partial/uncertain state); a rejected shared-runtime submission no longer
  blocks a later retry for up to 60 seconds; `LeaderboardManager.manualRefresh()` now correctly
  reports `false` on a genuine runtime rejection instead of always `true`.
- `GameZoneLiveDataRuntime`/`LocalPersistenceRuntime` no longer expose a raw `ExecutorService` with
  an unbounded queue - only a bounded `submit(Runnable)` with an explicit rejection policy, handled
  explicitly by every caller (never left stuck, never silently dropped).

### Changed
- Canonical product version is now `0.1.0-alpha.5`. Minecraft (26.1.2), Fabric Loader (0.19.5), and
  Fabric API (0.155.3+26.1.2) are unchanged, so the in-app updater takes the safe fast path from
  alpha.4.

### Fair play
Bounty Board shows only information GameZone itself already publishes publicly, via its official
`/api/bounties` endpoint - the same data any visitor to the GameZone wiki already sees. No entity
or world scanning, no radar, no coordinate extraction, no pathfinding, no automatic hunting, and no
automatic command sending are used anywhere in this feature; the one command-related action is a
deliberate, user-clicked clipboard-copy button. See `docs/BOUNTY-BOARD.md` §4.

## [0.1.0-alpha.4] - 2026-09-13

The first real feature release distributed through GZ Companion's own in-app updater, now that the
alpha.2 → alpha.3 self-update path has been proven end to end on a real machine.

### Added
- **Leaderboards tab**: read-only view of GameZone's own public leaderboards (Spelare, Settlements,
  Företag, Servern - 27 boards total), shown as a Companion-native TOP 10 with a podium-style
  ranking, group/board selectors, and local-only "DU" highlighting on player boards. Fetches only
  while open, caches per board, and degrades gracefully (stale-but-usable data, never mislabeled
  LIVE) if GameZone's site is unreachable or changes shape. See `docs/LEADERBOARDS.md`.
- `leaderboard` package: `GameZoneLeaderboardRegistry` (the one place all 27 board definitions live),
  `LeaderboardHtmlParser` (a narrow, hash-resilient HTML adapter - no new external parsing
  dependency), `GameZoneLeaderboardSource`, `LeaderboardManager` (per-board cache/fetch state
  machine), `LeaderboardFormatter`.

### Changed
- Canonical product version is now `0.1.0-alpha.4`. Minecraft (26.1.2), Fabric Loader (0.19.5), and
  Fabric API (0.155.3+26.1.2) are unchanged, so the in-app updater takes the safe fast path from
  alpha.3.

## [0.1.0-alpha.3] - 2026-09-13

A minimal validation release with no gameplay changes - its sole purpose is proving the complete
real-world self-update path end to end (discover on GitHub → download → verify → close Minecraft
safely → update → relaunch on the new version) now that `0.1.0-alpha.2` is live as the bootstrap
release testers installed manually.

### Changed
- Första versionen distribuerad via GZ Companions inbyggda updater.
- Verifierar automatiskt GitHub Release-flödet.
- Verifierar säker nedladdning och installationsbyte.
- Inga gameplay-funktioner ändrade.

## [0.1.0-alpha.2] - 2026-09-12

The final bootstrap release testers must install manually - every release after this one is
deliverable through the new in-app updater (see `docs/UPDATES.md`).

### Added
- **Online tab**: live player list, Favoriter, and automatic GameZone settlement detection
  (MIN SETTLEMENT), parsed entirely from the vanilla TAB header/player-list data GameZone already
  sends the client - no commands, no menu automation, no external APIs. Human-QA-verified against
  the real GameZoneMC server, including real header separators (`•`/`·`) and Unicode settlement
  prefixes (e.g. `[TRÄ]`). See `docs/ONLINE-PLAYERS.md`.
- **Home tab**: a LIVE GAMEZONE dashboard card showing server population, TPS, city, and economy,
  reusing the same TAB header data source. See `docs/LIVE-GAMEZONE-STATUS.md`.
- **Secure in-app updater**: GZ Companion now checks GitHub Releases in the background and can
  download, verify, and apply future updates with one click - no manual download, no visiting
  GitHub, no replacing jars by hand. See `docs/UPDATES.md`.
- `se.jimmyeliasson.gzcompanion.gamezone.settlement` / `gamezone.status` / `update` packages;
  `mixin.PlayerTabOverlayAccessor` (the project's first Mixin, exposing vanilla's private TAB
  header field via a minimal `@Accessor`, no reflection).

### Changed
- Canonical product version is now `0.1.0-alpha.2`, sourced from `gradle.properties`' `mod_version`
  everywhere (mod, Fabric metadata, UI, installer, `compatibility.json`) - see `distribution/RELEASING.md`.
  The installer no longer carries its own independently-editable version literal.

## [Unreleased] - Settings (Inställningar) - 2026-09-10

### Added
- Inställningar tab: general toggles (Companion notifications, GameZone toasts, show technical Minecraft IDs, show unverified knowledge, use last-known Chest Manager data in planners), a Privacy & Fair Play information block, local data management (per-category reset actions plus a two-click-stronger "Rensa ALLT"), a redacted diagnostics summary with "Kopiera diagnostik", and a dynamically-read keybind display.
- `settings` package: `CompanionSettings`/`SettingsManager`/`JsonSettingsStore` (`config/gzcompanion/settings.json`, schema v1, atomic writes, corrupt-recovery, fail-closed on a future schema), `DiagnosticsTextBuilder`.
- `clearContext(contextKey)` added to `ChestManager`, `SettlementPlannerManager`, `BuildingPlanManager`, and `MarketWatchNotesManager` to support the new per-category reset actions, each isolated to the player's current context.
- Every setting defaults to exactly the behavior that already shipped before this milestone existed - installing it changes nothing for a returning player until they explicitly toggle something.
- `docs/SETTINGS.md`: architecture, the real effect of every toggle, and why a "remember planner/search selections" toggle was intentionally NOT shipped (nothing to wire it to yet).

### Changed
- `gamezone-pack/feature-flags.json`: removed stale "(Coming Soon)" descriptions and `false` values for now-completed/implemented systems (Guide, Chest Manager, Settlement, Building Planner, MarketWatch); corrected MarketWatch's description from "marketplace price and auction tracker" to its real "resource demand" semantics. `deathRiskAdvisor` is left honestly unimplemented - it is a distinct feature from the M8 "Vad ska jag göra?" Advisor.

## [Unreleased] - Milestone 9: MarketWatch - 2026-09-10

### Added
- MarketWatch tab: an always-available offline reference to the real GameZone MarketWatch system (resource demand for settlement upgrades, compared against registered settlement inventory - not an auction price list), sourced from the official Wiki, plus a purely local "Mina anteckningar" watchlist.
- `knowledge.economy` package: `MarketWatchInfo`/`MarketWatchKnowledgeLoader` load `gamezone-pack/marketwatch.json` (verified command, category count, purpose, usage steps). The 7 demand categories are read from the already-loaded Settlement `productionCategories()` rather than duplicated, so the two lists can never drift apart.
- `marketwatch`/`marketwatch.storage` package: local, per-context notes (`MarketWatchNote`, `MarketWatchNotesManager`) persisted atomically to `config/gzcompanion/marketwatch-notes.json`, with local search/sort (favorites first, then most recently observed).
- "Kopiera /marketwatch" clipboard-only action; the command is never run automatically.
- `docs/MARKETWATCH.md`: verified facts, the explicit distinction from the separate `/market` shop-price system, and an honest, documented decision to defer the optional visible-GUI capture feature (its exact screen structure is not published, so it was not guessed).

## [Unreleased] - Milestone 8: Contextual Advisor - 2026-09-10

### Added
- Home's "Vad ska jag göra?" button now opens a real contextual Advisor overlay, ranking up to 3 explainable suggestions (Vad/Varför/Nästa steg) generated from actual Guide/Settlement/Building state - never a hardcoded fake tip. No new top-level tab was added.
- `advisor` package: `AdvisorContext` (plain data), `AdvisorEngine` (pure, deterministic rule evaluator with a fixed priority order and a safe generic fallback), `AdvisorContextBuilder` (the sole bridge to `CompanionSession`).
- `docs/ADVISOR.md`: architecture and current rule list.

## [Unreleased] - Milestone 7: Building Planner - 2026-09-10

### Added
- Byggplaner tab: a local reference/planner covering all 19 buildings currently in the active GameZone Building System 1.0 progression (Stadskärna through Myntverk), sourced from the official Wiki's physical-buildings page. Underverk (level 50) is explicitly excluded, per the source's own disclaimer that it is not yet part of the active progression.
- `knowledge.building` package: `SettlementBuilding`, `BuildingRequirement`, `GlobalBuildingRules`, `BuildingKnowledgeBase` (with `search(query)`), `BuildingKnowledgeLoader`.
- `building`/`building.storage` package: local, per-context building plans (`BuildingPlan`, `BuildingPlanManager`) persisted atomically to `config/gzcompanion/building-plans.json`, with a fixed six-item local checklist (`BuildingRequirementKey`: License/Nivå/Storlek/Väggar/Tak/Specialkrav) that never claims server-side completion.
- A rectangular Structure Calculator ("Planeringsestimat") computing floor/roof/wall area and the published minimum wall/roof coverage block counts from the building's global rules, with an explicit reminder that this is a local estimate, not a guarantee of GameZone's actual approval.
- `docs/BUILDING-PLANNER.md`: architecture, the explicitly-documented per-building minimum-footprint data gap, and fair-play/privacy guarantees.

## [Unreleased] - Milestone 6: Settlement Companion - 2026-09-10

### Added
- Settlement tab: a local reference/planner/calculator/organizer covering the current "Settlement Levels 1.0" progression (50 levels, 49 upgrades, max level Imperium), sourced from the GameZone Wiki's current canonical settlement-upgrades page — the obsolete 15-level model documented on some older Wiki articles is never used.
- `knowledge.settlement` package: `SettlementLevel`, `ItemRequirement` (nullable `itemId` for genuinely ambiguous categories), `SettlementFoundation`, `ProductionCategory`, `SettlementCatalog` (with `levelRange(from, to)` aggregation), `SettlementKnowledgeLoader`.
- `settlement`/`settlement.storage` package: local, per-context planner state (`SettlementPlannerProfile`, `SettlementPlannerManager`) persisted atomically to `config/gzcompanion/settlement-planner.json`, mirroring Chest Manager's corrupt-recovery and future-schema-safety guarantees.
- Four Settlement tab modes: Översikt, Progression, Material (with an optional, explicitly-labeled "last-known" Chest Manager estimate), and Medlemmar (a purely local member organizer, never the live server roster).
- `docs/SETTLEMENT-COMPANION.md`: architecture, source policy, the 15-vs-50-level conflict resolution, and fair-play/privacy guarantees.
- `SettlementKnowledgeLoaderTest.bundledRulePackIsCurrentFiftyLevelEngine`: a permanent anti-regression guard against the bundled Rule Pack ever reverting to the obsolete 15-level settlement model.

### Fixed
- `SettlementCatalog.levelRange(...)` no longer throws a `NullPointerException` when merging a distinct-variant material requirement (e.g. Wool color) with a later plain requirement for the same item — a mixed `int`/`Integer` ternary was forcing an illegal unboxing of a legitimately-null value.

## [Unreleased] - Milestone 5: GameZone Adapter - 2026-09-10

### Added
- Read-only GameZone event engine (`gamezone.events`, `gamezone.parsing`, `gamezone.toast`, `gamezone.bridge`): observes chat via Fabric's non-cancellable `ClientReceiveMessageEvents.GAME`/`CHAT` and never cancels, rewrites, hides, or responds to any message.
- Rule Pack-driven parser definitions (`parsers.json`, schema v1) with a hard `enabled && VERIFIED` activation gate — "better inactive than false-positive." Ships with zero active parsers today; no GameZone chat pattern is guessed.
- `GameZoneToastManager`: a small, dedupe-windowed, settings-gated local notification queue with no persistent chat history and no raw message text ever logged.

## [Unreleased] - Milestone 4 confirmed complete via human gameplay QA - 2026-09-10

### Changed
- Milestone 4 (GameZone Knowledge Base) marked **Completed** after human gameplay QA confirmed: Home shows Crafting/Kommandon as Aktiv, all 107 verified commands load and are searchable, typing "g" in command search does not close Companion, command detail shows the verification/source/date trail, "Kopiera kommando" copies without executing, Crafting shows legitimate client recipe-book data and all 50 verified relics, crafting-table recipes render with real item icons (multi-alternative "+" included), and relic detail shows tier/culture/serial/base item/enchants with its own verification trail.

## [Unreleased] - Milestone 4: GameZone Knowledge Base - 2026-09-10

### Added
- Kommandon tab: a searchable, category-filtered catalog of 107 GameZone commands across 8 categories, each VERIFIED against the official GameZone Wiki, with a clipboard-only "Kopiera kommando" action.
- Crafting tab: legitimately-unlocked client/server-synced crafting-table recipes read from the player's own Minecraft recipe book, a (currently empty) GameZone crafting-override registry, and the 50-relic GameZone item registry (all 50 VERIFIED), switchable via an Alla/Recept/GameZone-föremål filter.
- New `knowledge.*` package: `VerificationStatus`/`VerificationMetadata` (fact-level trust, separate from schema-level `CompatibilityStatus`), `KnowledgeModuleStatus`, `KnowledgeLoadResult`, and independent loaders/in-memory indexes for commands, crafting overrides, and custom items.
- `TextInputHandler` interface generalizing the M3 "G shouldn't close Companion while a search field is focused" fix so Kommandon and Crafting get it for free instead of a third copy.
- `docs/KNOWLEDGE-BASE.md`: full M4 architecture, verification model, source trust policy, schemas, and dataset counts.

### Fixed
- `RulePackLoader.parseCommands()` no longer defaults a missing/malformed command `status` to `VERIFIED` (now `UNVERIFIED`), closing a latent path where an edited legacy data file could silently assert an unconfirmed fact as confirmed.

### Changed
- Milestone 3 (Chest Manager) marked Completed following successful manual gameplay QA.

## [0.1.0-alpha.1] - 2026-09-09

### Added
- Initial project foundation for Minecraft 26.1.2 on Java 25 using Fabric Loader 0.19.5.
- Core architecture establishing the rule: *"Java understands Minecraft. Data understands GameZone."*
- Versioned GameZone Rule Pack engine (`gamezone-pack/`) with independent schema versioning and manifest verification metadata.
- Safe client-side server detection for `play.gamezonemc.se` (supports port variations, case-insensitivity, and singleplayer).
- Modern Swedish in-game UI following the authoritative design system (`docs/design/DESIGN-SYSTEM.md` and UI reference):
  - Centered dark translucent modal canvas.
  - Emerald green branding and status badges.
  - 9 navigation tabs: Hem, Guide, Crafting, Kistor, Settlement, Byggplaner, MarketWatch, Kommandon, Inställningar.
  - Dynamic player name greeting and live server connection status.
  - 4 version badges: Minecraft, GZ Companion, Rule Pack, and Compatibility.
  - "Nästa uppgift" checklist card with interactive action buttons.
  - Live module status indicators.
  - Polished placeholder views for developing modules.
- Keybind registration (`G` by default) using Fabric KeyMapping API.
- Local-first configuration storage in `.minecraft/config/gzcompanion/config.json` with corruption recovery.
- Full JUnit 5 automated test suite covering Rule Pack loading, fallback degradation, feature flags, server detection, compatibility diagnostics, and storage.
- Comprehensive documentation suite: Product Bible, Architecture Guide, Rule Pack Specification, Design System, and Development Roadmap.