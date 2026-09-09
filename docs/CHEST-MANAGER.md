# GZ Companion — Chest Manager (Kistor)

> **GZ Companion is an unofficial community project for GameZoneMC. It is not affiliated with or
> endorsed by GameZoneMC.**

Milestone 3 status: **implemented, pending human gameplay QA.** Do not treat this milestone as
complete until the manual QA sequence in this document has been run in real Minecraft gameplay.

## 1. Fair-Play Boundary

GZ Companion knows ONLY what the Minecraft client legitimately showed the player.

**Allowed:**
- The player physically interacts with a supported storage block.
- Minecraft legitimately opens its storage screen.
- GZ Companion reads the slots visible in that opened menu.
- GZ Companion updates its local snapshot while that screen remains open.
- GZ Companion saves the final visible state when the screen closes.
- The user later browses/searches that local cached snapshot.
- The user sees coordinates/dimension of storage they personally opened.

**Forbidden — and not implemented anywhere in this module:**
- World scanning for containers, or searching block entities to discover unopened storage.
- Reading unopened container inventories.
- X-ray, packet tricks, or any server-internal data extraction.
- Hidden chest detection, container radar, or plugin data extraction.
- Claiming cached contents are current/live.
- Recursively opening/reading nested storage the player did not open themselves (shulker box
  contents inside an item stack, bundle contents, etc.).
- Any automation that violates GameZone rules.

Every UI surface uses **last-known** language, never "live" or "current" language:
"Senast känt innehåll", "Senast öppnad", and "Kan ha ändrats sedan du öppnade förvaringen."
("May have changed since you opened this storage.")

## 2. Capture Lifecycle

The Chest Manager never snapshots a chest once on open — it runs a full opened-storage capture
session:

1. **Physical interaction.** The player right-clicks a block. Fabric's `UseBlockCallback` fires
   client-side (this hooks the client's own interaction handling and works identically in
   singleplayer and on any remote server, including GameZoneMC — no server cooperation needed,
   and nothing about vanilla interaction behavior is altered; the listener always returns
   `InteractionResult.PASS`). If the block is on the explicit storage allow-list, a short-lived
   *pending interaction* is recorded (position, dimension, context, storage kind, timestamp).
2. **Menu open correlation.** When a screen opens (`ScreenEvents.AFTER_INIT`) and it is an
   `AbstractContainerScreen`, the Minecraft-specific adapter determines every storage kind whose
   block(s) legitimately open that exact menu Java class. `ChestManager.tryBeginCapture` then
   verifies the pending interaction is recent, in the same context and dimension, AND one of
   those compatible kinds. If any check fails, the pending interaction is discarded and **nothing
   is indexed.** On success, an immediate snapshot is taken right away — the screen is already
   legitimately open, so this is a legitimate read — instead of waiting for the first tick. This
   closes a race where a player who closes the screen before the first tick would otherwise leave
   no legitimate snapshot at all.
3. **Live capture while open.** `ScreenEvents.afterTick` re-reads only the storage portion of the
   menu once per client tick. A lightweight fingerprint of the visible slots is computed; if
   unchanged since the last read, nothing happens. If changed, the in-memory snapshot is updated.
   **No disk write happens while the screen is open.**
4. **Finalize on close.** `ScreenEvents.remove` fires when the screen closes. One true final read
   of the menu is taken while it is still valid and forwarded the same way, so the persisted
   contents reflect the very last state the player actually saw — never a state from a stale
   earlier tick. The finalized snapshot is then persisted **exactly once**, updating "senast
   öppnad" (last opened) even when the contents are unchanged from the existing record. If a
   capture session somehow never received a single legitimate snapshot (it should not, given the
   immediate open-time read above), GZ Companion fails closed: no empty record is created, and an
   existing non-empty record is never overwritten with empty data. Either way, the capture session
   is then cleared.

## 3. Physical Interaction Correlation

`UseBlockCallback` and a screen opening are not intrinsically linked events, so a defensive
correlation window is used. A storage screen is only attached to a pending physical interaction
if **all** of the following hold:

- The interaction happened within `ChestManager.PENDING_INTERACTION_WINDOW_MS` (2000ms) of the
  menu opening.
- The context (player/world/server) matches.
- The dimension matches.
- The block that was clicked is one whose storage kind is structurally compatible with the menu
  class that opened (e.g. a `ChestMenu` structurally supports Chest, Trapped Chest, and Barrel;
  it does NOT satisfy a pending Hopper interaction).

If correlation cannot be proven, GZ Companion does not guess a position and does not index
anything. This is also the concrete defense against **GameZone plugin/virtual GUIs**: a server or
plugin can present a vanilla-looking inventory GUI (reusing e.g. `ChestMenu`) without any real
block interaction behind it — such a GUI never has a matching pending interaction, so it is
never indexed as a physical chest.

## 4. Supported Storage — Milestone 3

Explicit allow-list, verified against Minecraft 26.1.2's actual block and menu classes:

| Storage kind | Block class | Menu class actually opened |
| :--- | :--- | :--- |
| Chest | `ChestBlock` | `ChestMenu` |
| Trapped Chest | `TrappedChestBlock` (extends `ChestBlock`) | `ChestMenu` |
| Barrel | `BarrelBlock` | `ChestMenu` (`ChestMenu.threeRows`) |
| Shulker Box (placed) | `ShulkerBoxBlock` | `ShulkerBoxMenu` |
| Hopper | `HopperBlock` | `HopperMenu` |
| Dispenser | `DispenserBlock` | `DispenserMenu` |
| Dropper | `DropperBlock` (extends `DispenserBlock`) | `DispenserMenu` |

Note several vanilla block kinds legitimately share the same menu class (Barrel opens the same
`ChestMenu` a Chest does). Correlation therefore checks "is the clicked block's kind one of the
kinds this menu class structurally supports," not a 1:1 class mapping.

**Explicitly excluded, and never treated as storage:**
- Crafting table, furnace, blast furnace, smoker, anvil, enchanting table, merchant/villager
  trading, beacon, loom, grindstone, stonecutter, brewing stand, cartography table, lectern.
- Ender Chest — contents are player-global, not tied to one physical block, and are out of scope
  for this milestone.
- Minecart-with-chest and other container *entities* — this milestone only handles block storage.
- Horse/mount inventories.
- Any plugin/virtual GUI without a proven matching physical block interaction (see §3).

These may become separate, deliberately-modeled extensions in a later milestone; none are
silently treated as generic storage today.

## 5. Player Inventory Exclusion

The capture adapter reads `menu.slots` (every slot in the opened menu, which vanilla always
mixes container slots and the player's own inventory/hotbar slots together) and explicitly skips
every slot whose backing `Container` is the player's own inventory object. Only the remaining
slots — the physical storage itself — are ever recorded. The player's personal inventory is never
part of a stored snapshot.

## 6. Double Chests

Double-chest identity is derived **only** from the already client-visible `BlockState` of the
block the player clicked — specifically the vanilla `ChestBlock.TYPE` property
(`SINGLE`/`LEFT`/`RIGHT`) and `ChestBlock.getConnectedBlockPos(...)`. No surrounding block
entities are ever scanned.

- A `SINGLE` chest has no partner; it is recorded as a normal single-position entry.
- A `LEFT`/`RIGHT` chest half has its partner position read directly from the block state. The
  anchor position is canonicalized (the numerically lower of the two X/Y/Z positions) so that
  clicking either half of the same double chest resolves to the **same** logical entry — it is
  never duplicated depending on which side was opened.
- If the partner cannot be determined this way (an unexpected state shape), GZ Companion does
  **not** guess. The clicked position is stored as the anchor and the entry is marked
  "partner unknown" — shown to the player as "kan vara dubbel" (may be a double chest) rather
  than silently asserting a wrong shape.

## 7. Local Persistence

Path: `config/gzcompanion/chest-index.json`.

```json
{
  "schemaVersion": 1,
  "contexts": {
    "<profileId>@@<contextKey>": {
      "containers": {
        "<stableContainerKey>": {
          "kind": "CHEST",
          "label": "Gruvbas",
          "dimension": "minecraft:overworld",
          "anchor": { "x": 120, "y": 64, "z": -32 },
          "partner": { "x": 121, "y": 64, "z": -32 },
          "partnerUnknown": false,
          "lastOpenedAtMs": 1234567890,
          "slots": [ { "slot": 0, "itemId": "minecraft:iron_ingot", "count": 32 } ]
        }
      }
    }
  }
}
```

- Atomic writes: data is written to a `.tmp` file and moved into place with
  `StandardCopyOption.ATOMIC_MOVE`, exactly like `JsonGuideProgressStore`.
- Corruption recovery: a file that fails to parse (or fails to load at all) is preserved as
  `chest-index.json.corrupt.<timestamp>`, the mod continues with an empty index, and
  `ChestManager` still reports `LOADED` — a malformed file is treated the same as a fresh start,
  never as a permanent error state.
- **Incompatible future schema fails closed, without any overwrite risk.** `JsonChestIndexStore`
  returns a typed `ChestIndexLoadResult` distinguishing `NOT_FOUND` / `LOADED` /
  `CORRUPT_RECOVERED` (all safe, all result in `ChestManagerStatus.LOADED`) from
  `INCOMPATIBLE_SCHEMA` (a `schemaVersion` higher than this build understands). On
  `INCOMPATIBLE_SCHEMA` the file on disk is **never moved, deleted, or overwritten**, and
  `ChestManager` reports `ChestManagerStatus.INCOMPATIBLE` instead of `LOADED`. Every capture and
  mutation entry point (`recordPendingInteraction`, `tryBeginCapture`, `updateCaptureSlots`,
  `endCapture`, `forgetContainer`, `setLabel`) requires `LOADED` status and is a safe no-op
  otherwise — so an older client can never silently replace or partially overwrite an index file
  written by a newer version. Only in-memory queries (`getContainers`, `search`, etc.) remain
  available while incompatible; the Kistor tab shows a controlled "kistindexet är sparat av en
  nyare version" message, and the rest of GZ Companion (Guide, Home, etc.) is unaffected.
- Unknown/future JSON fields are tolerated and ignored on read, so a newer version of the file
  written by a future GZ Companion release doesn't break an older one that reads it.
- Container identity is a **stable key** (context + dimension + canonical anchor position +
  storage kind), never an array index — re-opening the same physical storage updates the existing
  entry.
- 100% local. No telemetry, no cloud account, no external API calls.
- **Growth policy:** nothing is automatically deleted in Milestone 3. The index grows only as
  large as the set of storage locations the player has actually opened. See §9.

## 8. Context Isolation

Reuses the exact same profile/world/server identity semantics already established by the Guide
Engine (`GuideContext` / `GuideContextResolver`) rather than inventing new identity rules — no
duplicate world-detection logic exists. On top of that context key, dimension is also part of a
container's identity, so:

- The same X/Y/Z under a different world/server context is a different entry.
- The same X/Y/Z in a different dimension (Overworld vs. Nether vs. End) is a different entry.
- Restarting Minecraft or switching worlds and back preserves each world's own index unchanged.

## 9. Search

`ChestManager.search(contextKey, query)` operates purely on already-indexed local data — it never
triggers any world lookup. Matching (case-insensitive) is checked against:

- The raw Minecraft item ID (`minecraft:iron_ingot`).
- A resolved item display name (the real vanilla translated name when running in-game via
  `MinecraftChestCaptureAdapter.resolveItemDisplayName`, or a readable fallback transform of the
  ID — e.g. `iron_ingot` → `Iron Ingot` — when no live resolver is wired, such as in unit tests).
- Coordinate text (`"120 64 -32"`).
- Storage kind name/display name (`"barrel"`, `"Tunna"`).
- The player's local custom label, if set.

An empty query returns every indexed container for the current context. Results never include
anything from a different context. Tag-based search was intentionally left out of Milestone 3 per
the roadmap scope — it did not block this milestone.

## 10. Local Custom Labels

`ChestManager.setLabel(contextKey, id, label)` stores a purely local, player-chosen label (e.g.
"Gruvbas", "Mat") **only** inside `chest-index.json`. It never writes a sign, places or edits a
block, sends a chat message, or issues a server command.

## 11. Corruption Behavior

Identical philosophy to the rest of GZ Companion: a malformed or unreadable `chest-index.json`
never crashes the mod. It is backed up with a timestamp suffix and the Chest Manager continues
with an empty index; the rest of the mod (Guide, Home, etc.) is entirely unaffected. If the store
cannot initialize at all, `ChestManagerStatus.ERROR` is reported and the Kistor tab shows a
controlled error state instead of pretending to work.

## 12. Privacy

Chest Manager diagnostics intentionally expose only status, schema version, and an indexed-count
number for the current context — never coordinates, labels, or item contents — in any generic
diagnostic output. All chest data is local-first: it never leaves the player's machine.

## 13. Known Limitations

- **Client cannot distinguish a real chest's real contents from a real chest whose contents a
  server-side plugin has deliberately customized.** If GameZone (or any plugin) intercepts a
  right-click on a genuine, previously-placed Chest block and serves a modified inventory through
  the same vanilla `ChestMenu`, GZ Companion has no way to know that server-side substitution
  happened — the interaction and the menu are both completely legitimate from the client's point
  of view. This is an inherent client-side limitation, not a bug, and is not specific to GZ
  Companion.
- **Typing the letter "g" while the Kistor search field is focused closes the Companion.** The
  established M1 global keybind (`G` always closes the Companion) was kept unconditional per this
  milestone's explicit instructions, while only `ESC` was made conditional on search-field focus.
  This is a known, accepted trade-off, not an oversight.
- **Ender Chests, container entities (minecart with chest), and mount inventories are not
  indexed** in Milestone 3 (see §4). These are candidates for a future, separately-designed
  extension.
- **The double-chest partner detection relies on `ChestBlock.getConnectedBlockPos`.** If that
  ever returns an unexpected shape, GZ Companion deliberately falls back to "partner unknown"
  rather than guessing (see §6).
- **No automated retention/cleanup exists yet.** The index can only grow as large as the storage
  locations a player has actually opened; a future milestone may add player-facing retention
  controls (e.g. "forget storage not opened in 90 days").

## 14. Testing

Automated JUnit tests cover the storage model (stable identity, context/dimension isolation,
double-chest canonicalization), snapshot/fingerprint behavior, correlation rejection paths
(stale/mismatched/unsupported/virtual-GUI), persistence (round-trip, atomic write, corruption
recovery, schema rejection, unknown-field tolerance, context isolation), search, forget, and
layout geometry (`ChestManagerTest`, `JsonChestIndexStoreTest`, `KistorLayoutTest`).

Consistent with the Guide Engine's own testing tiers, the Minecraft-specific capture adapter and
controller (`MinecraftChestCaptureAdapter`, `ChestCaptureController`) are **not** unit tested —
they require live Minecraft block/menu/screen state that only exists in real gameplay. They are
verified instead through the manual human gameplay QA sequence below. Automated tests alone are
not sufficient sign-off for this milestone.

### Manual gameplay QA sequence

Run with `.\gradlew.bat runClient`:

1. Open a new single chest containing items, close it, open GZ Companion → Kistor. The chest
   should appear with the correct last-known contents.
2. Reopen the same chest, move items between the chest and your own inventory, close it. GZ
   Companion should update the **same** record, not create a duplicate.
3. Open another chest at a different position. Both should appear as separate entries.
4. Search for an item. Only already-saved, previously-opened storage should match.
5. Open a normal plugin/virtual GUI, if one is available on the server. It must **not** be
   indexed as a physical chest.
6. Restart Minecraft. The same world's indexed storage should still be there.
7. Create or join another world. World A's chest index must not appear in World B.
8. Return to World A. Its storage index should reappear unchanged.

Do not consider Milestone 3 complete until this sequence has been run and confirmed in real
gameplay.
