# GZ Companion Development Roadmap

## Milestone Overview

### M1 — Foundation (Completed)
- **Scope**: Core architecture, Java 25 / Minecraft 26.1.2 Fabric toolchain, GameZone Rule Pack engine, safe server detection, Swedish UI with 9 navigation tabs, keybind G, and JUnit 5 test suite.
- **Definition of Done**: Builds on JDK 25, passes all unit tests, creates valid production JAR, opens UI on keybind G, gracefully degrades on invalid data, and includes complete documentation.

### M2 — Guide Engine (Completed)
- **Scope**: Data-driven beginner guide engine with 5 chapters and 22 survival steps, live inventory condition evaluation (items, tags, food), progression inference (`supersededBy`), dynamic keybinding token resolution, local-first progress storage (`guide-progress.json`), responsive 2-pane UI with reset confirmation, and Home dashboard objective synchronization.
- **Definition of Done**: Builds on JDK 25 and Fabric 26.1.2, passes 100% unit tests, validates guide content schemas and acyclic prerequisite graphs, accurately tracks inventory evidence without network calls, and synchronizes dynamic next steps with the Home tab.

### M3 — Chest Manager (Fair-Play) — *Completed*
- **Scope**: Indexing chest/barrel/shulker box/hopper/dispenser/dropper inventories legitimately opened by the player, search/filter/sort by item name, item ID, label, or coordinates, local labels, and copy-coordinates.
- **Definition of Done**: Local container snapshot storage keyed by a stable context/dimension/position/kind identity; zero wall-scanning or packet probing; a physical block interaction must correlate with a compatible opened menu before anything is indexed. See [Chest Manager](CHEST-MANAGER.md) for full detail. Confirmed via manual gameplay QA.

### M4 — GameZone Knowledge Base — *Completed*
- **Scope**: Searchable command catalog (Kommandon tab) and a Crafting tab combining legitimately-unlocked client/server-synced recipes, documented GameZone crafting overrides, and the 50-relic GameZone item registry — all sourced from the official GameZone Wiki. See [Knowledge Base](KNOWLEDGE-BASE.md) for the full architecture, verification model, and sourcing policy.
- **Definition of Done**: Functional "Kommandon" and "Crafting" tabs driven entirely by Rule Pack data (`commands.json`, `crafting-overrides.json`, `item-overrides.json`), each fact carrying explicit verification metadata; zero runtime network access; zero automation of commands or crafting. Confirmed via human gameplay QA: Home shows both tabs Aktiv, all 107 commands load and are searchable, G-in-search-field safety holds, command detail shows the full verification/source/date trail, Copy Command copies without executing, Crafting shows real client recipe-book data plus all 50 verified relics, crafting-table recipes and real item icons render correctly (including multi-alternative "+"), and relic detail shows tier/culture/serial/base item/enchants with its own verification trail.

### M5 — GameZone Adapter & safe event engine — *Implemented, pending human gameplay QA*
- **Scope**: A read-only, client-side GameZone event-observation layer (`gamezone.events`/`gamezone.parsing`/`gamezone.toast`) that turns legitimately-received chat/system messages into optional local Companion toasts, driven entirely by verified Rule Pack parser definitions.
- **Definition of Done**: Uses `fabric-message-api-v1`'s non-cancellable `ClientReceiveMessageEvents.GAME`/`CHAT` observation hooks only (never `ALLOW_GAME`/`ALLOW_CHAT`) - never cancels, rewrites, hides, or responds to a message, and never sends a command. Ships with zero active parser patterns, since no canonical GameZone source publishes the exact incoming chat text for any event - "engine ready, patterns awaiting live verification" rather than guessed regexes. See [GameZone Adapter](GAMEZONE-ADAPTER.md).

### M6 — Settlement Companion — *Implemented, pending human gameplay QA*
- **Scope**: A local settlement reference/planner/calculator (Översikt, Progression, Material, Medlemmar) covering the full current Settlement Levels 1.0 progression (50 levels, 49 upgrades, max level Imperium) and settlement foundation facts, all sourced from the official GameZone Wiki.
- **Definition of Done**: Interactive Settlement tab loaded from `settlements.json` and `settlement-levels.json`; a level/target planner aggregating Coins and materials between two chosen levels; an optional, clearly-labeled last-known Chest Manager estimate; a local (not live-server) member organizer. See [Settlement Companion](SETTLEMENT-COMPANION.md).

### M7 — Building Planner — *Implemented, pending human gameplay QA*
- **Scope**: A local building reference/planner covering all 19 currently active Building System 1.0 buildings (Stadskärna through Myntverk), a rectangular structure/material estimate calculator, and local per-plan requirement checklists.
- **Definition of Done**: Functional "Byggplaner" tab allowing players to browse verified building requirements, calculate a footprint estimate, and track local plans - never claiming GameZone's own server-side approval. See [Building Planner](BUILDING-PLANNER.md).

### M8 — "Vad ska jag göra nu?" Advisor — *Implemented, pending human gameplay QA*
- **Scope**: A contextual beginner advisor combining Guide progress, Settlement planner state, Building plan state, and connection status into a small ranked set of real, data-driven suggestions - reachable from Home's existing "Vad ska jag göra?" action rather than a new top-level tab.
- **Definition of Done**: Deterministic, explainable suggestions ("Vad / Varför / Nästa steg") with an optional copy-only verified command; never a hardcoded fake tip. See [Advisor](ADVISOR.md).

### M9 — MarketWatch — *Implemented, pending human gameplay QA*
- **Scope**: An offline-first reference for GameZone's real MarketWatch concept (settlement resource demand, not a price ticker) plus a local personal watchlist/trade-notes tool.
- **Definition of Done**: Works fully offline; shows the verified `/marketwatch` command and category reference; local notes clearly labeled "Mina anteckningar"; copy-only command convenience. Safe visible-GUI capture of the in-game MarketWatch menu was deliberately deferred - see [MarketWatch](MARKETWATCH.md) for exactly why.

### Settings (Inställningar) — *Implemented, pending human gameplay QA*
- **Scope**: A complete local settings/privacy/data-management screen - notification toggles, a fair-play/privacy statement, per-category local-data reset actions (with a stronger two-step confirmation for "clear everything"), a redacted diagnostics summary with copy-to-clipboard, and the Companion's actual current keybind.
- **Definition of Done**: `config/gzcompanion/settings.json`, schema v1, atomic writes, corrupt-file recovery, future-schema fail-closed; every destructive action requires confirmation; diagnostics never include chat text, coordinates, or personal notes. See [Settings](SETTINGS.md).

### M10 — Hardening & Release
- **Scope**: Comprehensive QA, performance profiling, accessibility audits, and community beta release.
- **Definition of Done**: Zero memory leaks, sub-1ms tick overhead, full localization coverage.