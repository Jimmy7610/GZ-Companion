# GameZone Rule Pack Specification

## 1. Overview
The **GZ Companion GameZone Rule Pack** is a versioned, declarative collection of JSON files that define server-specific schemas, commands, guides, and feature flags.

The editable top-level directory `gamezone-pack/` in the repository is the canonical source of truth and is packaged directly into `/gamezone-pack/` inside the mod JAR during build.

> [!IMPORTANT]
> **Unofficial Project**: GZ Companion is an unofficial, independent community project. The Rule Pack is not created, endorsed, or maintained by GameZoneMC administration.

---

## 2. Verification Policy & Status Lifecycle

Verification status strictly separates **technical compatibility** from **live server confirmation**:

- `COMPATIBLE`: Schema and file structures are syntactically valid and loadable by GZ Companion.
- `VERIFIED`: Confirmed against documented or observed live GameZoneMC behavior. Server-specific data is NOT marked VERIFIED unless explicitly proven.
- `UNVERIFIED`: Schema structure is present and loadable, but server-specific behavior/commands have not yet been live-verified.
- `UNAVAILABLE`: Module is not yet implemented in the current milestone.
- `INCOMPATIBLE`: Known mismatch with Minecraft version or corrupted JSON structure.

---

## 3. Manifest Schema (`manifest.json`)

```json
{
  "schemaVersion": "1.0.0",
  "profile": "gamezone",
  "packVersion": "2026.09.09.1",
  "name": "GZ Companion GameZone Rule Pack",
  "author": "Jimmy Eliasson",
  "compatibleCompanionVersions": ">=0.1.0-alpha.1",
  "testedMinecraftVersions": ["26.1.2"],
  "targetHost": "play.gamezonemc.se",
  "verification": {
    "source": "GZ Companion Initial Alpha Schema",
    "lastVerified": "2026-09-09",
    "status": "UNVERIFIED"
  },
  "modules": {
    "featureFlags": { "file": "feature-flags.json", "status": "COMPATIBLE" },
    "commands": { "file": "commands.json", "status": "UNVERIFIED" },
    "buildings": { "file": "buildings.json", "status": "UNVERIFIED" },
    "settlements": { "file": "settlements.json", "status": "UNVERIFIED" },
    "settlementLevels": { "file": "settlement-levels.json", "status": "UNVERIFIED" },
    "economy": { "file": "economy.json", "status": "UNVERIFIED" },
    "worldRules": { "file": "world-rules.json", "status": "UNVERIFIED" },
    "guides": { "file": "guides.json", "status": "UNVERIFIED" },
    "itemOverrides": { "file": "item-overrides.json", "status": "UNVERIFIED" },
    "craftingOverrides": { "file": "crafting-overrides.json", "status": "UNVERIFIED" },
    "parsers": { "file": "parsers.json", "status": "UNVERIFIED" }
  }
}
```

---

## 4. Single Source of Truth
All GameZone-specific knowledge lives in the declarative JSON files within `gamezone-pack/`. The Java runtime does not hardcode server assumptions.

---

## 5. M4 Knowledge Modules (`commands.json`, `crafting-overrides.json`, `item-overrides.json`)

Starting with Milestone 4, these three files are loaded by dedicated readers in `se.jimmyeliasson.gzcompanion.knowledge.*`
(`CommandKnowledgeLoader`, `CraftingKnowledgeLoader`, `ItemKnowledgeLoader`) — **not** by `RulePackLoader` and **not**
gated by `manifest.json`'s per-module `status` field. Each file declares its own numeric `schemaVersion` (currently `1`)
understood only by its own loader. The `manifest.json` entries for these three files are legacy bookkeeping left over
from the M1 schema and carry no effect on the M4 modules; `RulePackLoader.parseCommands()` still exists for backward
compatibility but expects the older nested `categories[].commands[]` shape and returns nothing for the M4 files' flat
`categories[]` + `commands[]` shape — the two paths do not collide.

Every fact-bearing entry in these three files carries a `verification` block:

```json
"verification": {
  "status": "VERIFIED",
  "sourceName": "GameZone Wiki — Alla 50 reliker",
  "sourceReference": "https://www.gamezonemc.se/wiki/relics/relikregister",
  "lastVerified": "2026-09-10"
}
```

`status` is one of `VERIFIED`, `UNVERIFIED`, `STALE`, or `UNKNOWN` (`se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus`)
— deliberately distinct from the `CompatibilityStatus` enum used elsewhere in this document, which answers a different
question ("is this file/module schema-valid") rather than "has this specific fact been confirmed against an official
source." `VERIFIED` is only honored when `sourceName` and `lastVerified` are both present — see
[Knowledge Base](KNOWLEDGE-BASE.md) for the full sourcing policy and schema reference.

---

## 6. M6/M7/M9 Modules (`settlement-levels.json`, `settlements.json`, `buildings.json`, `marketwatch.json`)

These four files follow the exact same pattern as the M4 modules above: each is loaded by its own
dedicated reader (`SettlementKnowledgeLoader`, `BuildingKnowledgeLoader`, `MarketWatchKnowledgeLoader`
in `se.jimmyeliasson.gzcompanion.knowledge.*`), never by `RulePackLoader`, and never gated by
`manifest.json`'s per-module `status` field — those entries (including the `marketwatch` entry added
for this pass) remain legacy bookkeeping only. Each file declares its own numeric `schemaVersion`
(currently `1`) and carries the same `VerificationMetadata` shape shown above.

`settlement-levels.json` and `settlements.json` are loaded independently of each other by
`SettlementKnowledgeLoader` even though both feed one `SettlementCatalog` — a malformed
`settlements.json` never hides a valid level progression, and vice versa. `marketwatch.json`
intentionally does NOT duplicate the 7 production categories — `MarketWatchTabComponent` reads
those from the already-loaded `SettlementCatalog.productionCategories()` instead, so the two lists
can never drift apart. See [Settlement Companion](SETTLEMENT-COMPANION.md),
[Building Planner](BUILDING-PLANNER.md), and [MarketWatch](MARKETWATCH.md) for the full sourcing
policy, schemas, and current verified counts.