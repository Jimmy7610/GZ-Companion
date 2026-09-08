# GameZone Rule Pack Specification

## 1. Overview
The **GameZone Rule Pack** is a versioned, declarative collection of JSON files that define all server-specific metadata, commands, guides, and feature flags.

The editable top-level directory `gamezone-pack/` in the repository is the canonical source of truth and is packaged directly into `/gamezone-pack/` inside the mod JAR during build.

---

## 2. Manifest Schema (`manifest.json`)

```json
{
  "schemaVersion": "1.0.0",
  "profile": "gamezone",
  "packVersion": "2026.09.09.1",
  "name": "GameZoneMC Official Rule Pack",
  "author": "Jimmy Eliasson",
  "compatibleCompanionVersions": ">=0.1.0-alpha",
  "testedMinecraftVersions": ["26.1.2"],
  "targetHost": "play.gamezonemc.se",
  "verification": {
    "source": "Official Community Specification",
    "lastVerified": "2026-09-09",
    "status": "VERIFIED"
  },
  "modules": {
    "featureFlags": { "file": "feature-flags.json", "status": "VERIFIED" },
    "commands": { "file": "commands.json", "status": "VERIFIED" },
    "buildings": { "file": "buildings.json", "status": "UNVERIFIED" },
    "settlements": { "file": "settlements.json", "status": "UNVERIFIED" },
    "settlementLevels": { "file": "settlement-levels.json", "status": "UNVERIFIED" },
    "economy": { "file": "economy.json", "status": "UNVERIFIED" },
    "worldRules": { "file": "world-rules.json", "status": "UNVERIFIED" },
    "guides": { "file": "guides.json", "status": "VERIFIED" },
    "itemOverrides": { "file": "item-overrides.json", "status": "UNVERIFIED" },
    "craftingOverrides": { "file": "crafting-overrides.json", "status": "UNVERIFIED" },
    "parsers": { "file": "parsers.json", "status": "VERIFIED" }
  }
}
```

---

## 3. Verification Metadata & Status Values

Every module and data entry can carry verification metadata:
- `VERIFIED`: Confirmed against active GameZoneMC gameplay/plugins.
- `UNVERIFIED`: Schema structure defined; awaiting live server verification.
- `STALE`: Previously verified, but may have changed in recent server updates.
- `INCOMPATIBLE`: Known mismatch with current server behavior.
- `UNKNOWN`: Status unverified or unmonitored.

---

## 4. Future Remote Updates (Data Only)
In future milestones, the Rule Pack engine may support declarative remote updating.
- **Strict Rule**: Remote updates will **never execute arbitrary code**. They will only replace declarative JSON schemas under strict cryptographic hash verification.