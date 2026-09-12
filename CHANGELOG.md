# Changelog

All notable changes to GZ Companion will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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