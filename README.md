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

### Kistor 2.0
- **Saker & Förvaring**: "Where are my things?" — an aggregated last-known item index with real item icons, plus storage cards with favorites, groups, location notes, freshness and "Sedan förra öppningen".
- **Hitta**: select ONE storage you've already opened and a small HUD arrow guides you back to its saved coordinates — straight-line distance and height difference only; no scanning, radar, ESP or pathfinding.
- **Hitta material i kistor & Hämtningslista**: see where Settlement/Byggplaner materials were last known to be, grouped into a local pickup list — estimates from last-known snapshots, never live server inventory.

### Milestone 4 — GameZone Knowledge Base *(Completed — confirmed via human gameplay QA)*
- **Kommandon Tab**: A searchable, category-filtered catalog of GameZone commands sourced from the official GameZone Wiki, each with a verification badge and a "Kopiera kommando" clipboard action — never auto-sent, never auto-typed into chat.
- **Crafting Tab**: Legitimately-unlocked client/server-synced crafting-table recipes read from your own Minecraft recipe book, shown alongside any documented GameZone crafting overrides (none exist on the wiki today) and the 50-relic GameZone item registry — switchable via an Alla/Recept/GameZone-föremål filter.
- **Explicit Verification Model**: Every GameZone fact carries a `VerificationStatus` (Verifierad/Overifierad/Inaktuell/Okänd) plus its exact source page and the date it was checked — see [Knowledge Base](docs/KNOWLEDGE-BASE.md) for the full sourcing policy.
- **Never "Vanilla"**: Recipes read from your own recipe book are labeled "Tillgängligt Minecraft-recept" (client/server-synced), kept structurally and visually distinct from hand-verified GameZone Rule Pack facts.
- **Zero Automation, Zero Runtime Network Access**: The GameZone Wiki is a build-time authoring source only — the shipped mod never fetches it, never crafts automatically, and never modifies or intercepts any recipe.

### Milestone 5 — GameZone Adapter *(Implemented, pending human gameplay QA)*
- **Read-Only Event Engine**: Observes chat messages the client already legitimately receives via Fabric's non-cancellable `ClientReceiveMessageEvents.GAME`/`CHAT` — never cancels, rewrites, hides, or responds to any message.
- **Verified-Only Parser Activation**: A parser can only ever activate when it is both `enabled` AND carries a fully verified source — "better inactive than false-positive." No GameZone chat pattern is guessed; the bundled Rule Pack ships with zero active parsers today, honestly reported as "engine ready, pattern awaiting live verification."
- **Local Toast Notifications**: A small, dedupe-windowed, settings-gated toast queue for future verified events (settlement invites, system messages) — no sound spam, no persistent chat history, never logs raw message text.

### Milestone 6 — Settlement Companion *(Implemented, human gameplay QA complete — prepared for v0.1.0-alpha.6)*
- **Live Settlement Dashboard**: When connected to GameZoneMC and a settlement is safely recognized from the same vanilla-visible TAB data Home/Online already read, Settlement shows a real **LIVE** dashboard — settlement name, level, role, bonus, treasury, and who from your settlement is currently online — reusing the exact same shared trackers, never a new parser or network source. A raw live level is only ever trusted for automatic planning once it's confirmed aligned with the bundled Rule Pack; a mismatch is still shown honestly, never hidden. See [Settlement Companion](docs/SETTLEMENT-COMPANION.md).
- **Full Current Progression**: All 50 levels / 49 upgrades of the current "Settlement Levels 1.0" engine, sourced from the official GameZone Wiki — the obsolete 15-level model is never used.
- **Local Level Planner, With a Trusted Live Override**: Choose a current and target level locally ("Planerad nuvarande nivå"); when a trusted live level is available it automatically becomes the *effective* current level for Progression/Material planning — without ever overwriting your manually saved value — and see the aggregated Coins, materials, and building prerequisites between them.
- **Material Checklist with Optional Chest Estimate**: Track owned amounts manually, or opt in to summing specific already-indexed Chest Manager containers — always labeled "Lokalt estimat från senast känt innehåll," never presented as the server's real settlement inventory.
- **Local Member Organizer, With Live Online Context**: Purely local "Lokala anteckningar" about settlement members — never a full live server roster, never scraped beyond the same TAB data Online already reads, never chat history — now shown alongside (never merged into) the currently-online players from your own settlement.

### Milestone 7 — Building Planner *(Implemented, pending human gameplay QA)*
- **Full Current Building Catalog**: All 19 buildings currently in the active Building System 1.0 progression (Stadskärna through Myntverk), each with its level requirement, license cost, bonus, and special block/entity requirements. See [Building Planner](docs/BUILDING-PLANNER.md).
- **Structure Calculator ("Planeringsestimat")**: A local footprint helper calculating floor/roof/wall area and the published minimum coverage block counts — always labeled an estimate, never a guarantee of GameZone's real approval.
- **Local Plans with a Fixed Checklist**: Create, rename, and delete (with confirmation) local building plans, tracking License/Nivå/Storlek/Väggar/Tak/Specialkrav as local-only planning checkboxes.

### Milestone 8 — Contextual Advisor *(Implemented, pending human gameplay QA)*
- **"Vad ska jag göra?" Becomes Real**: Home's existing button now opens a ranked, explainable overlay generated from your actual Guide/Settlement/Building state — never a hardcoded fake tip. See [Advisor](docs/ADVISOR.md).
- **Deterministic, Explainable Suggestions**: Each suggestion shows Vad/Varför/Nästa steg, with an optional copy-only clipboard button for an already-verified command.
- **No New Tab**: The Advisor is reached from Home without adding another top-level navigation tab.

### Milestone 9 — MarketWatch *(Implemented, pending human gameplay QA)*
- **The Real GameZone Concept**: MarketWatch represents resource demand for settlement upgrades, compared against registered settlement inventory — never an auction price list. See [MarketWatch](docs/MARKETWATCH.md).
- **Always-Available Offline Reference**: The verified command, category count, purpose, and usage steps work fully offline, with "Kopiera /marketwatch" as a clipboard-only action.
- **Local Watchlist**: Track resources with a category, a free-text note, a favorite toggle, and a last-observed timestamp — always labeled "Mina anteckningar," never live server truth.
- **Visible-GUI Capture Honestly Deferred**: The optional in-game MarketWatch menu capture was not implemented, since its exact screen structure isn't published — deferred rather than guessed.

### Inställningar (Settings) *(Implemented, pending human gameplay QA)*
- **Real Toggles, Conservative Defaults**: Every toggle defaults to exactly the behavior already shipping before Settings existed, so installing it never changes what a returning player sees. See [Settings](docs/SETTINGS.md).
- **Local Data Management**: Per-category reset actions (Guide, Kistor, Settlement, Byggplaner, MarketWatch, Settings itself), each requiring confirmation — with a stronger, two-click confirmation for "Rensa ALLT".
- **Redacted Diagnostics**: A copy-only technical summary (versions, module statuses) that never includes chat text, coordinates, or personal notes.
- **Dynamic Keybind Display**: Shows the Companion's actual current key binding, not a hardcoded "G".


### Leaderboards
- **GameZone Topplistor**: Se topplistor för spelare, settlements och företag direkt i Companion när den publika GameZone-datan är tillgänglig.
- **Read-Only Live Data**: Leaderboards är en informationsvy och utför inga automatiska spelåtgärder, kommandon eller ändringar på servern.

### Milestone 11 — Bounty Board *(Completed — released in v0.1.0-alpha.5)*
- **Live GameZone Bounties**: See GameZone's own public active-bounty registry (unique-target PvE hunts, Coin reward, optional public clue, optional expiry) directly inside Companion, fetched on demand from the official `/api/bounties` endpoint — the exact same data GameZone's own wiki page shows. See [Bounty Board](docs/BOUNTY-BOARD.md).
- **Full Public Clue, Scrollable Detail**: The entire public clue GameZone publishes is always shown, never truncated at a line limit — a scrollable, independently-scissored detail pane safely contains long clues, names, or expiry text without ever drawing over its own footer.
- **Clipboard-Only Command**: An optional "Kopiera /bounty info \<namn>" button copies the documented command to the clipboard — never sent automatically.
- **Shared Live-Data Runtime**: Reuses the exact same lazily-created executor/`HttpClient` as Leaderboards — Bounty Board adds zero new threads or network clients.

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
`build/libs/gzcompanion-0.1.0-alpha.6.jar` (the exact file name always matches `gradle.properties`'
`mod_version` - the single canonical product version, see `distribution/RELEASING.md`)

---

## 📜 Documentation

- [Product Bible](docs/PRODUCT-BIBLE.md): Vision, user personas, and design boundaries.
- [Architecture](docs/ARCHITECTURE.md): Structural layout and layer isolation.
- [Chest Manager](docs/CHEST-MANAGER.md): Fair-play boundary, capture lifecycle, and persistence for the Kistor module.
- [Design System](docs/design/DESIGN-SYSTEM.md): Authoritative UI palette, components, and styling.
- [GameZone Rule Pack](docs/GAMEZONE-RULE-PACK.md): Data schema, versioning, and verification specification.
- [Roadmap](docs/ROADMAP.md): Milestones M1 through M11 with Definitions of Done.
- [Changelog](CHANGELOG.md): Version history and release notes.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — Copyright (c) 2026 Jimmy Eliasson.