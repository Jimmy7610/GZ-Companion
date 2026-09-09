# GZ Companion Knowledge Base (Milestone 4)

## 1. Scope

Milestone 4 adds three independent GameZone knowledge modules, each with its own Rule Pack file,
loader, in-memory index, and UI surface:

| Module | Rule Pack file | Loader | In-memory index | UI |
|---|---|---|---|---|
| Commands | `commands.json` | `knowledge.commands.CommandKnowledgeLoader` | `CommandCatalog` | Kommandon tab |
| Crafting overrides | `crafting-overrides.json` | `knowledge.crafting.CraftingKnowledgeLoader` | `CraftingKnowledgeBase` | Crafting tab |
| Custom items (relics) | `item-overrides.json` | `knowledge.items.ItemKnowledgeLoader` | `ItemKnowledgeBase` | Crafting tab |

Each module loads exactly once at startup, in its own try/catch, in `CompanionSession.loadKnowledgeModules()`.
A failure in one (missing file, malformed JSON, unsupported schema) never affects the other two, and
never affects Guide or Kistor (M2/M3), which remain on their own, separately-approved
`GuideLoadStatus`/`ChestManagerStatus` types.

## 2. Verification model

`se.jimmyeliasson.gzcompanion.knowledge.common`:

- **`VerificationStatus`** — VERIFIED / UNVERIFIED / STALE / UNKNOWN. Answers "has this specific
  fact been confirmed against a live/official GameZone source?" Deliberately separate from
  `se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus`, which answers a different
  question ("is this file/module schema-valid and loadable?"). A schema can be perfectly
  compatible while every fact inside it is still UNVERIFIED — the two axes never collapse into one.
- **`VerificationMetadata`** — `(status, sourceName, sourceReference, lastVerified)`. Its canonical
  constructor enforces a hard trust rule: `VERIFIED` is automatically downgraded to `UNVERIFIED`
  unless `sourceName`, `sourceReference`, AND `lastVerified` are ALL present (hardened in the M4
  post-implementation pass — M4's policy requires an exact canonical source page, not just a
  named source). A missing or unparseable raw status string always defaults to `UNVERIFIED`,
  never `VERIFIED`.
- **`KnowledgeModuleStatus`** — LOADED / UNAVAILABLE / ERROR / INCOMPATIBLE. New and scoped to the
  three `knowledge.*` modules only; does **not** replace `GuideLoadStatus` or `ChestManagerStatus`
  (that would have been a regression-risk refactor with no M4 value).
- **`KnowledgeLoadResult<T>`** — the shared `Outcome` (LOADED / INCOMPATIBLE_SCHEMA / ERROR) + data
  shape returned by all three loaders.

## 3. Source trust policy

A GameZone fact in `commands.json` or `item-overrides.json` is marked `VERIFIED` only when:

1. it is stated explicitly on the current official GameZone Wiki (`gamezonemc.se/wiki/...`),
2. the exact source page is recorded in `sourceReference`,
3. the page identifies the information as current/authoritative (the two pages used here both
   carry an explicit "Verifierad GameZone-data" marker), and
4. no detail beyond what the source actually states was added.

Nothing here is marked VERIFIED from model memory, general Minecraft conventions, another
server, unchecked search snippets, or forum/Reddit speculation. Facts fetched via an
AI-summarizing tool were re-verified by reading the live rendered page directly before being
written to the Rule Pack, since a summarizing intermediary can paraphrase or drop detail.

Every GameZone crafting-override and item/relic detail pane in the Crafting tab renders this trail
directly: the status badge (Verifierad/Overifierad/Inaktuell/Okänd), then "Källa: `<sourceName>`"
and "Senast kontrollerad: `<lastVerified>`" when a source is present, or an honest "Den här
informationen har ännu inte bekräftats." line when it isn't. The raw `sourceReference` URL is kept
in the data for traceability but is deliberately never dumped into the normal UI.

Bundled dataset, as of the `2026-09-10` verification pass:

- **Commands** (`commands.json`): 107 commands across 8 categories (allmant, settlement, ekonomi,
  foretag, byggnader, territorium, socialt, admin), **all 107 VERIFIED** against
  `GameZone Wiki — Kommandon` (`https://www.gamezonemc.se/wiki/commands/kommandon`).
- **Crafting overrides** (`crafting-overrides.json`): **0 recipes.** The official GameZone Wiki
  (checked 2026-09-10, including its "Produktion" article) does not document any GameZone-specific
  crafting-table recipe additions, replacements, or disables. The file intentionally ships empty
  rather than inventing one — the Crafting tab shows "Inget verifierat craftingrecept finns i detta
  Rule Pack" for this half of its data.
- **Custom items** (`item-overrides.json`): 50 relics (GZR-0001–GZR-0050), **all 50 VERIFIED**
  against `GameZone Wiki — Alla 50 reliker` (`https://www.gamezonemc.se/wiki/relics/relikregister`).
  `releaseStatus` is `null` for every relic — the wiki only states a general disclaimer that "not
  all 50 may exist in the world yet" with no per-relic status, so none was invented.

## 4. Schemas

All three files use a flat, numeric `schemaVersion` (currently `1`) understood only by their own
loader — see [GameZone Rule Pack §5](GAMEZONE-RULE-PACK.md#5-m4-knowledge-modules-commandsjson-crafting-overridesjson-item-overridesjson)
for how this relates to (and stays independent of) `manifest.json`'s legacy per-module status.

### `commands.json`
```json
{
  "schemaVersion": 1,
  "categories": [{ "id": "settlement", "displayName": "Settlement", "sortOrder": 20 }],
  "commands": [{
    "id": "settlement_info", "primaryCommand": "/settlement info", "aliases": [],
    "syntax": "/settlement info", "description": "...", "categoryId": "settlement",
    "keywords": ["settlement", "info"], "examples": [], "requirements": null,
    "verification": { "status": "VERIFIED", "sourceName": "GameZone Wiki — Kommandon",
      "sourceReference": "https://www.gamezonemc.se/wiki/commands/kommandon", "lastVerified": "2026-09-10" }
  }]
}
```
Validation: blank/duplicate `id` rejected; blank `primaryCommand`/`syntax` rejected; a duplicate
`primaryCommand` is rejected; every primary command and alias is tracked in one global claimed-set
so an alias can never silently collide with (shadow) another command's primary command or alias —
first occurrence in the file always wins. An unknown `categoryId` does not fail the command; it
resolves to a shared `CommandCategory.FALLBACK` ("Okategoriserad") at read time.

### `crafting-overrides.json`
```json
{
  "schemaVersion": 1,
  "recipes": [{
    "id": "...", "outputItemId": "minecraft:...", "outputCount": 1,
    "kind": "SHAPED", "width": 3, "height": 3,
    "grid": [{ "itemIds": ["minecraft:..."] }, ...],
    "ingredients": [],
    "source": "GAMEZONE_ADDITION", "notes": "...",
    "verification": { "...": "..." }
  }]
}
```
`kind` is `SHAPED` (grid, row-major, exactly `width*height` entries, 1-3 per side — a real
crafting table) or `SHAPELESS` (`ingredients`, non-empty). `source` is one of
`GAMEZONE_ADDITION` / `GAMEZONE_REPLACEMENT` / `GAMEZONE_DISABLED` — there is **no `VANILLA`
value**; nothing in this enum is ever inferred from the Minecraft client. An ingredient is either
an explicit item-id list (`itemIds`, shown as alternatives) or a tag reference (`tag`, rendered as
`#minecraft:planks`). A recipe with an invalid grid size (doesn't match `width*height`, or exceeds
3×3) or a shapeless recipe with zero ingredients is skipped with a warning, not partially loaded.

### `item-overrides.json`
```json
{
  "schemaVersion": 1,
  "items": [{
    "id": "gzr_0048", "displayName": "The Hammer of Creation", "baseMinecraftItemId": "minecraft:netherite_pickaxe",
    "description": "...", "lore": [], "category": "relic", "tier": "MYTHIC", "culture": "Andvari",
    "serial": "GZR-0048", "enchants": ["Efficiency V", "Fortune III", "Silk Touch", "Sharpness V"],
    "specialEffect": "...", "acquisitionNotes": null, "craftingReferenceId": null, "releaseStatus": null,
    "verification": { "...": "..." }
  }]
}
```
Only `id` and `displayName` are required — every other field is optional by design, since an
official source publishing only some facts about an item must not force the rest to be invented
"to complete the entry." `byBaseItemId()` deliberately returns a `List`, never assuming a single
relic owns a given base Minecraft item (several relics legitimately share one, e.g.
`minecraft:iron_pickaxe`).

## 5. Client/server-synced recipes vs. GameZone Rule Pack facts

Three structurally separate identities exist and are never merged:

- **`ClientRecipeSnapshot`** (`knowledge.crafting`) — read live from
  `LocalPlayer.getRecipeBook().getCollections()` by `MinecraftRecipeDisplayAdapter`, the only file
  in the module that touches Minecraft recipe/item classes. This is the exact same
  legitimately-unlocked recipe collection vanilla's own Recipe Book UI uses — never a dump of every
  possible recipe, and nothing is scanned or inferred beyond what the client already knows. It
  carries **no** `VerificationMetadata` (it isn't a Rule Pack fact) and is always labeled
  **"Tillgängligt Minecraft-recept"** in the UI — never "Vanilla," since the data is
  client/server-synced (recipe unlocks arrive from whichever server the player is connected to),
  not a static vanilla constant. Each snapshot carries both `outputItemId` (raw) and
  `outputDisplayName` (the player's actual translated item name, e.g. "Oak Planks", resolved once
  by the adapter when the snapshot is built — never per render frame), and each ingredient slot's
  alternatives are `IngredientOption(itemId, displayName)` pairs for the same reason.
- **`GameZoneCraftingEntry`** (`knowledge.crafting`) — a hand-authored, verified-or-not Rule Pack
  fact from `crafting-overrides.json`, always carrying `RecipeKnowledgeSource` +
  `VerificationMetadata`. Currently empty (see §3). Its ingredient ids have no separately-resolved
  display name (there's no live registry lookup for hand-authored Rule Pack text) - the id string
  itself is the label.
- **`CustomItemKnowledge`** (`knowledge.items`) — a Rule Pack fact about a GameZone-specific item
  (the 50 relics), independent of both recipe types above.

The Crafting tab's mode filter (Alla / Recept / GameZone-föremål) reads all three sources but keeps
them visually and structurally distinct — a recipe or item row's badge/label always states which of
the three it is. Each mode's "no data" empty state is evaluated independently: RECEPT only looks at
the crafting side (GameZone overrides + client recipes), GAMEZONE_FOREMAL only at the item side,
and ALLA only when *both* sides have nothing - a search producing zero results is a separate,
later check from this true-empty-data check.

### Recipe book caching

Reading the client's recipe book is real work (iterates every unlocked `RecipeCollection` and
resolves every ingredient's `ItemStack`), so `CraftingTabComponent` never calls
`MinecraftRecipeDisplayAdapter.readClientRecipeBook()` on every render frame. Instead it caches the
result and calls `refreshClientRecipesIfNeeded(nowMs, contextKey)` each render, which only actually
re-reads the recipe book when `knowledge.crafting.ClientRecipeCachePolicy.shouldRefresh(...)` says
to: the very first call, a player/world/server context change (reusing the same storage-context key
Kistor/Guide already use), or the throttle interval (`ClientRecipeCachePolicy.MIN_REFRESH_INTERVAL_MS`,
1500ms) having elapsed. The precomputed client-recipe search index (display name + raw id + every
ingredient's id/display name, each also indexed with underscores normalized to spaces) is rebuilt
exactly once per actual refresh, never per keystroke.

Recipe list-selection identity is a `stableKey()` derived purely from a snapshot's own already-
visible content (output id, kind, dimensions, every slot's ingredient ids in order) — never a list
index and never an invented server-side identifier - so the currently-selected recipe survives the
recipe book being re-read and returned in a different order.

## 6. `TextInputHandler`

`se.jimmyeliasson.gzcompanion.ui.TextInputHandler` generalizes the exact
`isTextInputFocused()`/`keyPressed()`/`charTyped()` contract `KistorTabComponent` already proved
out for the M3 "G shouldn't close Companion while typing in a search field" fix.
`CommandsTabComponent` and `CraftingTabComponent` both implement it, and
`GZCompanionMainScreen` now dispatches G/key/char events through a small
`activeTextInputHandler()` lookup instead of a `TabType.KISTOR`-only check — Kistor's own behavior
is unchanged (verified by the existing `KistorTabInputTest` suite still passing unmodified).

## 7. Fair-play and privacy guarantees carried over from M3

- **No runtime network access.** The GameZone Wiki is a build-time authoring source only — the
  shipped mod never fetches it, and Kommandon/Crafting work fully offline once the JAR is built.
- **No automation.** "Kopiera kommando" only writes to the OS clipboard
  (`Minecraft.getInstance().keyboardHandler.setClipboard(...)`) — it never sends chat, opens chat,
  simulates Enter, or executes anything. The Crafting tab never crafts, moves inventory, modifies
  a recipe, intercepts a recipe, or sends a packet to test one.
- **No hidden data.** The relic registry is public wiki content; no relic signature, hidden
  location, or unreleased-but-obtainable claim is reproduced here.

## 8. Known limitations

- Item-identity resolution is intentionally shallow: `byBaseItemId()` matches on the raw
  `baseMinecraftItemId` string only. It does not inspect a held/inventory `ItemStack`'s NBT/data
  components to determine which specific relic (if any) a given real item actually is — that would
  require deeper item-identity work explicitly out of scope for this milestone.
- Shaped crafting grid cells render real Minecraft item icons via
  `GuiGraphicsExtractor.fakeItem(ItemStack, x, y)` (confirmed by decompiling the merged Minecraft
  26.1.2 jar - `fakeItem` is the correct call for a detached, no-owner/no-slot `ItemStack`, exactly
  this scenario). A cell falls back to a short text label only when an id can't be resolved to a
  concrete item (an unresolvable tag reference, or a malformed Rule Pack entry) - the fallback
  always prefers a real display name over a raw id where one exists. A cell with more than one
  legitimate ingredient alternative shows the first alternative's icon plus a small "+", never
  implying it is the only valid choice. Shapeless ingredient lists remain text (display name,
  not raw id) rather than icon rows, since that list is unbounded in length unlike the fixed 3×3
  grid.
