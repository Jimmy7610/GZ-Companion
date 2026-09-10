# GZ Companion

[![Minecraft 26.1.2](https://img.shields.io/badge/Minecraft-26.1.2-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Modloader-Fabric-blue.svg)](https://fabricmc.net/)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Status: Alpha](https://img.shields.io/badge/Status-Alpha-purple.svg)]()

> **GZ Companion is an unofficial community project for GameZoneMC (`play.gamezonemc.se`). It is not affiliated with, endorsed by, or operated by GameZoneMC.**

GZ Companion is a client-side Fabric companion mod engineered to assist new and existing players on the GameZoneMC Swedish Minecraft community server.

---

## 🍃 Core Philosophy & Fair Play

GZ Companion is designed strictly within ethical fair-play boundaries. 

### What GZ Companion is:
- A contextual guide and knowledge companion for beginners.
- A local-first, safe assistant for recipe overviews and command catalogs.
- A personal organizer for player-opened containers (indexing only data legitimately received by the client when opening a chest).
- An accessible in-game dashboard with Swedish localization.

### What GZ Companion is NOT (Strict Fair Play Policy):
- **NO X-Ray or ESP**: Never reveals hidden ores or entities.
- **NO Chest / Container Scanning**: Only tracks containers the player manually and legitimately opens.
- **NO Automation**: No botting, auto-mining, auto-combat, auto-farming, or automated movement.
- **NO Packet Exploits**: Uses standard client interactions only.
- **NO Telemetry / Cloud Accounts**: Zero tracking, zero remote analytics, zero external API keys.

---

## 🛠 Features

### Milestone 1 — Foundation
- **Modern In-Game UI**: Dark translucent glass styling with emerald accents and readable Swedish typography.
- **Keybind G**: Open the companion anytime in-game; press `ESC` or `G` to close.
- **Dynamic Player & Server Detection**: Real-time identification of your Minecraft username and connection status to `play.gamezonemc.se`.
- **Versioned GameZone Rule Pack**: "Java understands Minecraft. Data understands GameZone." All server rules, commands, and guides live in declarative JSON rule packs rather than hardcoded Java constants.
- **Graceful Degradation**: If an individual server module or rule is unverified or modified, unrelated features continue running seamlessly.
- **Local-First Storage**: User configurations are saved safely in `config/gzcompanion/config.json` with corruption recovery.

### Milestone 2 — Guide Engine
- **Data-Driven Progression**: Complete vanilla beginner guide (5 chapters, 22 steps) from first tree to iron armor.
- **Live Inventory Condition Evaluation**: Automatically detects items, tags (`#minecraft:logs`, `#minecraft:planks`, `#minecraft:beds`, `#minecraft:coals`), and food in player inventory.
- **Progression Inference (`supersededBy`)**: Smart backfilling (e.g. obtaining an iron pickaxe automatically satisfies wood and stone tool steps).
- **Dynamic Keybinding Tokens**: Dynamically resolves `{key.inventory}`, `{key.forward}`, etc., to the user's custom keybinds.
- **Interactive 2-Pane Guide UI**: Left chapter/step navigator and right detail pane with live condition evidence, tip callouts, manual toggles, and reset confirmation dialog.
- **Home Dashboard Objective Integration**: "Nästa uppgift" automatically syncs with the active guide step.

### Milestone 3 — Chest Manager (Kistor)
- **"Senast känt innehåll" (Last Known Contents)**: Indexes ONLY storage the player has personally and legitimately opened — never live, never scanned. See [Chest Manager](docs/CHEST-MANAGER.md) for the full fair-play boundary.
- **Real Opened-Storage Capture**: A physical block right-click plus a correlated, matching menu open together start a capture session; the visible snapshot updates while the screen is open and is finalized once on close.
- **Explicit Storage Allow-List**: Chest, Trapped Chest, Barrel, placed Shulker Box, Hopper, Dispenser, and Dropper — verified against actual Minecraft 26.1.2 block/menu classes. Everything else (furnaces, anvils, Ender Chests, plugin GUIs) is explicitly excluded.
- **Searchable, Filterable, Sortable Local Index**: Search by item, label, storage type, dimension, or coordinates; filter by storage type; sort by recency, name, or type — across your own already-opened storage, never a world search.
- **Local Custom Labels & Copy Coordinates**: Name a chest "Gruvbas" locally, and copy its coordinates to the clipboard — both pure local conveniences, never written to a sign, chat, or a server command.
- **Explicit `StorageShape`**: Single, double, or unknown-shape chests are shown honestly and distinctly — never guessed.
- **Context & Dimension Isolated**: Reuses the Guide Engine's world/server identity rules; the same coordinates in a different world, server, or dimension are always separate entries.

### Milestone 4 — GameZone Knowledge Base *(Completed — confirmed via human gameplay QA)*
- **Kommandon Tab**: A searchable, category-filtered catalog of GameZone commands sourced from the official GameZone Wiki, each with a verification badge and a "Kopiera kommando" clipboard action — never auto-sent, never auto-typed into chat.
- **Crafting Tab**: Legitimately-unlocked client/server-synced crafting-table recipes read from your own Minecraft recipe book, shown alongside any documented GameZone crafting overrides (none exist on the wiki today) and the 50-relic GameZone item registry — switchable via an Alla/Recept/GameZone-föremål filter.
- **Explicit Verification Model**: Every GameZone fact carries a `VerificationStatus` (Verifierad/Overifierad/Inaktuell/Okänd) plus its exact source page and the date it was checked — see [Knowledge Base](docs/KNOWLEDGE-BASE.md) for the full sourcing policy.
- **Never "Vanilla"**: Recipes read from your own recipe book are labeled "Tillgängligt Minecraft-recept" (client/server-synced), kept structurally and visually distinct from hand-verified GameZone Rule Pack facts.
- **Zero Automation, Zero Runtime Network Access**: The GameZone Wiki is a build-time authoring source only — the shipped mod never fetches it, never crafts automatically, and never modifies or intercepts any recipe.

---

## 🚀 Building from Source

### Prerequisites
- **Java / JDK**: 25 (e.g. Eclipse Adoptium Temurin 25)
- **Git**

### Build Commands (Windows PowerShell)
```powershell
# Run automated tests
.\gradlew.bat test

# Build production mod JAR
.\gradlew.bat build

# Launch development Minecraft client
.\gradlew.bat runClient
```

The compiled mod JAR will be located at:
`build/libs/gzcompanion-0.1.0-alpha.1.jar`

---

## 📜 Documentation

- [Product Bible](docs/PRODUCT-BIBLE.md): Vision, user personas, and design boundaries.
- [Architecture](docs/ARCHITECTURE.md): Structural layout and layer isolation.
- [Chest Manager](docs/CHEST-MANAGER.md): Fair-play boundary, capture lifecycle, and persistence for the Kistor module.
- [Design System](docs/design/DESIGN-SYSTEM.md): Authoritative UI palette, components, and styling.
- [GameZone Rule Pack](docs/GAMEZONE-RULE-PACK.md): Data schema, versioning, and verification specification.
- [Roadmap](docs/ROADMAP.md): Milestones M1 through M10 with Definitions of Done.
- [Changelog](CHANGELOG.md): Version history and release notes.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — Copyright (c) 2026 Jimmy Eliasson.