# GameZone Adapter — Read-Only Event Engine (Milestone 5)

Status: **Implemented, pending human gameplay QA.**

## What this is

A generic, client-side, strictly READ-ONLY layer that can turn legitimately-visible information
(chat messages the player already receives) into optional local Companion notifications. It never
touches, modifies, delays, cancels, or responds to anything Minecraft or GameZone sends the
player.

## The one rule that governs this entire module

The listener may **only observe** messages the player legitimately receives. It may **never**:
cancel a message, rewrite a message, hide a message from the player, send a chat response, issue
a command, or trigger any automatic server action.

This is enforced structurally, not just by convention:

- `GameZoneChatObserver` registers against `ClientReceiveMessageEvents.GAME` and
  `ClientReceiveMessageEvents.CHAT` (from `fabric-message-api-v1`) — the non-cancellable observer
  variants. Their cancel-capable siblings, `ALLOW_GAME`/`ALLOW_CHAT`, are never used anywhere in
  this codebase.
- `GameZoneObservedEvent` structurally cannot carry the raw message text — its only fields are an
  event type, the id of the parser that matched, and that parser's own declared named capture
  groups. There is no field to leak a whisper's or a system message's full text into memory,
  diagnostics, or a toast.

## Architecture

- `gamezone.events` — `GameZoneEventType` (SETTLEMENT_INVITE, WHISPER, BALANCE_CHANGE,
  SYSTEM_MESSAGE, UNKNOWN, each with a Swedish display name), `GameZoneObservedEvent`.
- `gamezone.parsing` — `ParserMatchType` (EXACT/CONTAINS/REGEX), `GameZoneParserDefinition` (a
  Rule Pack-driven parser rule; `isActive() = enabled && verification.status() == VERIFIED` — a
  data-entry mistake alone can never activate a parser), `GameZoneParserCatalog` (precomputes the
  active-parser subset once at load time), `GameZoneParserLoader` (reads `parsers.json`, schema
  v1, mirrors the M4 `CommandKnowledgeLoader` pattern exactly), `GameZoneParserEngine` (pure,
  stateless, zero Minecraft types or I/O — `match(activeParsers, message, now)`).
- `gamezone.toast` — `ToastEntry`, `GameZoneToastManager` (a small dedupe-windowed local
  notification queue, gated by two independent boolean suppliers so Settings can disable "all
  Companion notifications" and "GameZone event toasts" separately).
- `gamezone.bridge` — `GameZoneChatObserver` (the only class touching Fabric's message events),
  `GameZoneToastHudElement` (renders the current toast via the modern `HudElement`/
  `HudElementRegistry` API — the classic `HudRenderCallback` does not exist in this Fabric API
  version).

## Why zero parsers are active today

`parsers.json` ships with `"parsers": []`. GameZone's exact incoming chat text for a settlement
invite, a whisper, or a balance change was not exposed on the canonical Wiki pages consulted in
this pass with enough precision to write a pattern without guessing. Per this project's
verification policy, a guessed regex is worse than no regex at all — it would either miss real
events or, worse, false-positive on unrelated chat. The engine, the loader, the toast pipeline,
and the HUD element are all fully implemented and tested; only the verified pattern data itself is
missing. Adding a real parser later is a pure Rule Pack data change (one new object in
`parsers.json` with a VERIFIED source) — no Java code changes required.

## Settings integration point

`GameZoneToastManager.setNotificationsEnabledSupplier(...)` and
`setGameZoneToastsEnabledSupplier(...)` are the two hooks the Settings module wires to the user's
actual saved preferences. Until Settings exists, both default to always-enabled.

## Diagnostics

`GameZoneChatObserver` exposes `getObservedMessageCount()`, `getMatchedEventCount()`, and
`getLastMatchedEventType()` for a future Settings/diagnostics screen. None of these ever expose
raw chat text.
