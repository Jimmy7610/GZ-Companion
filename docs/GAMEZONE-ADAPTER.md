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
actual saved preferences (`companionNotificationsEnabled`/`gameZoneToastsEnabled`).

## Profile gating - GameZone parsing never runs on another server

GZ Companion is explicitly allowed to run on singleplayer, another Minecraft server, or
GameZoneMC. The chat observation hooks (`ClientReceiveMessageEvents.GAME`/`CHAT`) fire for every
message on every one of those - but GameZone parser logic must only ever evaluate a message while
the client is actually connected to a verified GameZone profile.

`GameZoneChatObserver`'s constructor takes a `Supplier<Boolean> gameZoneProfileActiveSupplier`,
wired in `CompanionSession` to `this::isConnectedToGameZone` (itself backed by the existing
`ServerDetection`/`ServerProfile` logic - no new ping probes or additional server scanning). This
gate is checked FIRST in `onMessage`, before anything else: when it returns `false`, the message
is not parsed, no toast can ever be offered, and - deliberately - the message is not even counted
in `getObservedMessageCount()`. That counter and `getMatchedEventCount()` represent messages that
were actually *eligible* for GameZone parsing, not every chat line the client happened to receive
on an unrelated server.

**The profile gate always wins over settings.** Even with `gameZoneToastsEnabled = true`, no
GameZone toast can ever appear outside a verified GameZone profile - a user preference can turn
GameZone toasts off, but it can never turn them on somewhere they don't belong. See
`GameZoneChatObserverTest` for the behavioral proof (GameZoneMC eligible; another server and
singleplayer both ineligible; enabled settings alone can never override the gate).

The gate is a pure read - it never cancels, mutates, or delays the message itself; on a
non-GameZone server the message still renders in chat exactly as it always would, GZ Companion
just never looks at it for GameZone-parsing purposes.

## Performance

Verified regex patterns are compiled once per distinct pattern string and cached
(`GameZoneParserEngine`'s `ConcurrentHashMap<String, Pattern>`), never recompiled per incoming
message. `GameZoneToastManager`'s dedupe map opportunistically prunes entries older than the
dedupe window on every `offer()` call, so it stays bounded by recent activity rather than growing
by one entry per distinct event key ever seen across a session. Both were found and fixed during
this pass' performance audit - see `docs/ARCHITECTURE.md` §5.

## Diagnostics

`GameZoneChatObserver` exposes `getObservedMessageCount()`, `getMatchedEventCount()`, and
`getLastMatchedEventType()` for the Settings diagnostics screen. None of these ever expose raw
chat text, and (per the profile gate above) they only ever reflect GameZone-eligible messages.
