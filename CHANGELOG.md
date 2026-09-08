# Changelog

All notable changes to GZ Companion will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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