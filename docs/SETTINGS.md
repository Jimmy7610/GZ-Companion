# Settings (Inställningar)

Status: **Implemented, pending human gameplay QA.**

## What this is

A complete local settings/privacy/data-management screen. Every default was chosen to preserve
the exact behavior that was already shipping and human-QA-approved before this milestone existed
— installing it never silently changes what a returning player sees.

## Architecture

- `settings.CompanionSettings` — a flat, global (never world/server-bound) record of 5 booleans.
  `CompanionSettings.defaults()` is the single source of truth for conservative defaults.
- `settings.SettingsStore` / `SettingsLoadResult` / `JsonSettingsStore` — persistence at
  `config/gzcompanion/settings.json`, schema v1, mirroring every other Companion store's atomic
  write, corrupt-file backup and recovery, and fail-closed-on-future-schema guarantees exactly.
  Unlike Settlement/Building/MarketWatch state, this is a single flat object, never a per-context
  map — settings are deliberately global.
- `settings.SettingsManager` — the runtime coordinator; every setter persists immediately.
- `settings.DiagnosticsTextBuilder` — builds the exact safe, redacted diagnostics text (see
  below).
- `ui.tabs.SettingsTabComponent` / `ui.layout.SettingsLayout` — a single scrollable tab with five
  sections in order: General toggles, Privacy & Fair Play, Local Data Management, Diagnostics,
  and the current keybind.

## General toggles and what they actually do

| Toggle | Default | Real effect |
|---|---|---|
| Companion-notiser | På | Gates `GameZoneToastManager`'s general notification supplier. |
| GameZone-händelsenotiser | På | Gates `GameZoneToastManager`'s GameZone-specific toast supplier, independent of the above. |
| Visa tekniska Minecraft-ID | På | Shows/hides the raw `minecraft:...` id line in Crafting's recipe detail panes (the only place M1-M4 already displayed a raw id). |
| Visa overifierad kunskap | På | Filters non-VERIFIED entries out of Settlement's Progression list and Byggplaner's building list when off. Crafting/Kommandon (M4, already human-QA-approved) are intentionally left untouched — their verification badge already communicates trust per-entry, and this milestone's instructions were explicit about not touching M1-M4 behavior without a genuine need. |
| Använd senast kända kistodata i planerare | På | Shows/hides the "Beräkna från sparade kistor" button in Settlement's Material view. |

Every toggle above defaults to **on**, because that is exactly the behavior each of those tabs
already had before this setting existed. Turning a toggle off is the only way to see different
behavior — a fresh install matches a pre-Settings install exactly.

**Confirmed during the cross-server safety pass**: turning "Använd senast kända kistodata i
planerare" on only makes the "Beräkna från sparade kistor" button visible - it never
automatically reads or sums chest data by itself. Reaching an actual estimate still always
requires the player to (1) click the button to open the container picker, (2) manually check
which specific already-indexed containers to include (none are pre-selected), and (3) click
"Beräkna" to apply the sum. No cached chest content is ever read without that explicit sequence.

A "Remember planner/search selections across restarts" toggle was deliberately **not** shipped:
no tab's search text or list selection is currently persisted across closing and reopening the
Companion (each tab component is a fresh in-memory object every time `GZCompanionMainScreen` is
constructed), so a toggle controlling that would have no real effect. Per this project's "no
meaningless toggles" rule, it was left out rather than shipped as a no-op.

## Privacy & Fair Play

A static information block, not a settings control: Local-only, No telemetry, No cloud sync, No
automatic commands, No container scanning, No hidden server data - restating the guarantees
already true of every milestone in this mod.

## Local Data Management

Seven actions, each requiring a confirming second click within a 4-second window (a stray click
elsewhere on the tab cancels any pending confirmation): reset Guide progress, clear the Chest
Manager index, clear the Settlement planner, clear Building plans, clear MarketWatch notes, and
reset Settings itself — each isolated to the player's current GameZone-server-vs-singleplayer
context via the existing `clearContext(contextKey)` method newly added to `ChestManager`,
`SettlementPlannerManager`, `BuildingPlanManager`, and `MarketWatchNotesManager` for this
milestone.

**"Rensa ALL lokal GZ Companion-data"** requires a STRONGER confirmation than every other action
here: three total clicks (two confirming clicks, not one), and clicking anywhere else on the tab
resets the confirmation stage back to zero rather than merely expiring on a timer. It never
touches any file outside `config/gzcompanion/`.

## Diagnostics

`DiagnosticsTextBuilder.build(session)` produces exactly: Companion version, Minecraft version,
Rule Pack version, active profile, and a status line per module (Guide, Kistor, Kommandon,
Crafting, Settlement, Byggplaner, MarketWatch, event engine + its verified-parser count). This is
copy-only via "Kopiera diagnostik". It deliberately contains **no** chat text, chest coordinates,
chest contents, MarketWatch note text, or any other player-authored content — every field is a
status enum, a count, or a version string.

## Keybind

Shown dynamically via `KeybindHandler.getOpenMenuKey().getTranslatedKeyMessage()` - the mod's
current actual bound key, whatever the player configured it to in Minecraft's own Controls
screen, never a hardcoded "G".
