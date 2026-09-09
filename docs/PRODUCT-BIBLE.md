# GZ Companion — Product Bible

## 1. Vision & Executive Summary
**GZ Companion** is a client-side companion mod tailored specifically for the Swedish Minecraft multiplayer community server **GameZoneMC** (`play.gamezonemc.se`).

Minecraft servers with custom economies, claims, rules, and commands can be overwhelming for beginners. GZ Companion bridges this gap by acting as a friendly, intelligent in-game advisor that teaches mechanics, organizes knowledge, and keeps track of legitimate player progress without ever compromising fair play.

---

## 2. Target Audience & Personas

### Persona A: "The Newcomer" (Nybörjaren)
- **Profile**: A player joining GameZoneMC for the first time.
- **Pain Points**: Unfamiliar with custom `/commands`, claims, currency, or where to start building safely.
- **How GZ Companion Helps**: The "Nästa uppgift" advisor and beginner guides provide clear, bite-sized next steps in Swedish.

### Persona B: "The Builder & Organizer" (Byggaren)
- **Profile**: An established player managing base chests, resources, and claims.
- **Pain Points**: Forgetting in which chest specific materials were stored, calculating block requirements.
- **How GZ Companion Helps**: The Kistor tab indexes "senast känt innehåll" (last known contents) for every chest, barrel, shulker box, hopper, dispenser, and dropper the player has personally opened — searchable by item, ID, or coordinates — plus future building planning tools. See [Chest Manager](CHEST-MANAGER.md).

---

## 3. Core Principles

1. **"Java understands Minecraft. Data understands GameZone."**
   - Server specifics belong in declarative data packs, not scattered constants across Java code.
2. **Fair Play Above All**
   - Never provide an unfair competitive advantage. No X-ray, no ESP, no botting, no packet exploits.
3. **Local-First & Private**
   - No telemetry, no external accounts, no cloud sync. All data belongs strictly to the player.
4. **Swedish Accessibility**
   - All player-facing content uses natural, clear, welcoming Swedish.
5. **Graceful Degradation**
   - If server rules change or a module is unverified, the mod remains functional and displays clear status notices.

---

## 4. Product Boundaries

| Feature Category | In Scope | Strictly Out of Scope |
| :--- | :--- | :--- |
| **Guides** | Step-by-step beginner guides, command tips | Server cheat guides, exploit explanations |
| **Chests** | Indexing "senast känt innehåll" for chests/barrels/shulker boxes/hoppers/dispensers/droppers the player physically opened | Remote scanning, wall-penetrating container radar, Ender Chests, unopened container reads, claiming cached data is live |
| **Economy** | Offline price guides, balance display | Market manipulation bots, automated auction sniping |
| **Settlements** | Claim size calculators, requirement checklists | Automated claiming bots, griefing radars |
| **Combat/Movement** | Death risk advice, safe zone reminders | Auto-totem, killaura, fly, speed, auto-sprint |