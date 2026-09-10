# Changelog

All notable changes to GZ Companion will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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