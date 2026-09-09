# Changelog

All notable changes to GZ Companion will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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