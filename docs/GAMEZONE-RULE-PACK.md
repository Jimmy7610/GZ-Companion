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