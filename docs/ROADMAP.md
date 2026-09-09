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

### M4 — GameZone Knowledge Base — *Implemented, pending human gameplay QA*
- **Scope**: Searchable command catalog (Kommandon tab) and a Crafting tab combining legitimately-unlocked client/server-synced recipes, documented GameZone crafting overrides, and the 50-relic GameZone item registry — all sourced from the official GameZone Wiki. See [Knowledge Base](KNOWLEDGE-BASE.md) for the full architecture, verification model, and sourcing policy.
- **Definition of Done**: Functional "Kommandon" and "Crafting" tabs driven entirely by Rule Pack data (`commands.json`, `crafting-overrides.json`, `item-overrides.json`), each fact carrying explicit verification metadata; zero runtime network access; zero automation of commands or crafting. Pending final human gameplay QA in-game.

### M5 — GameZone Adapter & Chat Engine
- **Scope**: Client-side chat event parsing (balance, settlement invites, whispers) with user feedback toasts.
- **Definition of Done**: Safe regex-based chat listener without message tampering or chat spam.

### M6 — Settlement Companion
- **Scope**: Settlement claim calculator, member list organizer, progression requirements checklist.
- **Definition of Done**: Interactive settlement tools loaded from `settlements.json` and `settlement-levels.json`.

### M7 — Building Planner
- **Scope**: Material cost calculator, structure dimension helper, block palette recommendations.
- **Definition of Done**: Functional "Byggplaner" tab allowing players to calculate resource costs for claims.

### M8 — "Vad ska jag göra nu?" Advisor
- **Scope**: Contextual beginner advisor evaluating player level, inventory, and location to offer intelligent suggestions.
- **Definition of Done**: Interactive advisor button on Home dashboard generating tailored recommendations.

### M9 — MarketWatch
- **Scope**: Marketplace price overview and player shop notes (if ethically and technically supported).
- **Definition of Done**: Offline price catalog and trade notes without automated auction bidding.

### M10 — Hardening & Release
- **Scope**: Comprehensive QA, performance profiling, accessibility audits, and community beta release.
- **Definition of Done**: Zero memory leaks, sub-1ms tick overhead, full localization coverage.