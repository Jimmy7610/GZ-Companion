# Guide Engine & Progression Architecture

GZ Companion includes a modular, data-driven, client-side **Guide Engine** for Minecraft 26.1.2 (Fabric, Java 25).

## 1. Core Principles

- **"Engine understands progression. Data understands the lesson."**
  Java contains zero hardcoded tutorial text or game instructions. All lesson definitions, conditions, tips, and chapters live as versioned JSON files in `guide-content/` (bundled into `assets/gzcompanion/guides/`).
- **Fair Play & Local-First**:
  Only legitimate client-visible state is observed:
  - Inventory items and quantities via vanilla registries (`BuiltInRegistries.ITEM`).
  - Item tags (`ItemTags.LOGS`, `ItemTags.PLANKS`, `ItemTags.BEDS`, `ItemTags.COALS`).
  - Edible items (`ItemStack.has(DataComponents.FOOD)`).
  - Configured keybindings (`Options.key*`).
  No server packet snooping, no hidden block scanning, and zero network telemetry.
- **Context Isolation & Resilience**:
  Progression data is saved locally to `config/gzcompanion/guide-progress.json`, isolated by player profile UUID and world/server context key (`<profileId>@@<contextKey>`).

---

## 2. Guide Content Schema

### `manifest.json`
```json
{
  "schemaVersion": 1,
  "contentVersion": "2026.09.09.1",
  "locale": "sv-SE",
  "testedMinecraftVersions": ["26.1.2"],
  "guides": [
    {
      "id": "minecraft-beginner",
      "file": "minecraft-beginner-sv.json",
      "title": "Nybörjarguiden",
      "description": "Lär dig grunderna i Minecraft steg för steg från första träblocket till järnrustning."
    }
  ]
}
```

### `minecraft-beginner-sv.json` Structure
Contains 5 chapters and 22 verified survival steps:
1. **Kom igång** (`ch1_kom_igang`):
   - `movement_controls`: Rörelse och blick (MANUAL)
   - `open_inventory`: Öppna ditt inventory (MANUAL)
   - `gather_wood`: Samla träblock (`HAS_ITEM_TAG` `minecraft:logs`, count: 4)
2. **Första verktygen** (`ch2_forsta_verktygen`):
   - `craft_planks`: Tillverka träplankor (`HAS_ITEM_TAG` `minecraft:planks`, count: 4)
   - `craft_crafting_table`: Tillverka en arbetsbänk (`HAS_ITEM` `minecraft:crafting_table`, count: 1)
   - `craft_sticks`: Gör träpinnar (`HAS_ITEM` `minecraft:stick`, count: 4)
   - `craft_wooden_pickaxe`: Gör en trähacka (`HAS_ITEM` `minecraft:wooden_pickaxe`, count: 1)
   - `mine_cobblestone`: Bryt kullersten (`HAS_ITEM` `minecraft:cobblestone`, count: 3)
   - `craft_stone_pickaxe`: Gör en stenhacka (`HAS_ITEM` `minecraft:stone_pickaxe`, count: 1)
3. **Överlev natten** (`ch3_overlev_natten`):
   - `craft_stone_sword`: Tillverka ett stensvärd (`HAS_ITEM` `minecraft:stone_sword`, count: 1)
   - `gather_food`: Skaffa mat (`HAS_EDIBLE_ITEM`, count: 1)
   - `gather_wool`: Samla ull (`HAS_ITEM_TAG` `minecraft:wool`, count: 3)
   - `craft_bed`: Tillverka en säng (`HAS_ITEM_TAG` `minecraft:beds`, count: 1)
4. **Trygg bas & förvaring** (`ch4_trygg_bas`):
   - `craft_chest`: Bygg en kista (`HAS_ITEM` `minecraft:chest`, count: 1)
   - `craft_furnace`: Tillverka en ugn (`HAS_ITEM` `minecraft:furnace`, count: 1)
   - `mine_coal`: Samla kol eller tillverka träkol (`HAS_ITEM_TAG` `minecraft:coals`, count: 4)
   - `craft_torches`: Tillverka facklor (`HAS_ITEM` `minecraft:torch`, count: 4)
5. **Järnåldern** (`ch5_jarnaldern`):
   - `find_raw_iron`: Hitta och bryt råjärn (`HAS_ITEM` `minecraft:raw_iron`, count: 3)
   - `smelt_iron_ingots`: Smält järntackor (`HAS_ITEM` `minecraft:iron_ingot`, count: 3)
   - `craft_iron_pickaxe`: Gör en järnhacka (`HAS_ITEM` `minecraft:iron_pickaxe`, count: 1)
   - `craft_shield`: Tillverka en sköld (`HAS_ITEM` `minecraft:shield`, count: 1)
   - `craft_iron_armor`: Gör din första järnrustning (`HAS_ANY_ITEM` `iron_chestplate`, `iron_leggings`, `iron_helmet`, `iron_boots`)

---

## 3. Condition Evaluator

Supported condition types:
- `MANUAL`: User confirmation required or completed when prerequisites met.
- `HAS_ITEM`: Verifies exact item ID and minimum quantity in inventory.
- `HAS_ANY_ITEM`: Matches if any of the specified item IDs reach the target count.
- `HAS_ITEM_TAG`: Matches if items in inventory belonging to the specified vanilla tag reach the target count.
- `HAS_EDIBLE_ITEM`: Evaluates if the player holds any item marked with vanilla food data component.
- `ALL_OF`: Logical conjunction of subconditions.
- `ANY_OF`: Logical disjunction of subconditions.

---

## 4. Progression State Machine

Each step is dynamically evaluated into one of 6 states:
1. `LOCKED`: One or more prerequisites are not yet completed.
2. `AVAILABLE`: Prerequisites are completed, but step is not yet active or completed.
3. `ACTIVE`: The current top recommended incomplete step.
4. `COMPLETED_AUTO`: Satisfied automatically via player inventory evidence.
5. `COMPLETED_MANUAL`: Explicitly marked completed by user action.
6. `SATISFIED_BY_LATER_PROGRESS`: Implicitly completed because a later step declared in `supersededBy` was completed (e.g. obtaining an iron pickaxe automatically satisfies gathering wood and crafting a wooden pickaxe).

---

## 5. UI Architecture

- **MainScreen Integration**:
  - `TabType.GUIDE` is marked `ModuleStatus.AVAILABLE`.
  - Responsive 2-pane layout for `NORMAL` and `LARGE` screens:
    - **Left Navigator**: Scrollable list of chapters and steps with status dots and chapter badges.
    - **Right Detail Pane**: Step header, state badge, why/rationale, dynamic token-resolved instructions (`{key.inventory}` -> `[E]`), live requirement box, tip box, and action buttons (`[Markera klar]`, `[Ångra markering]`, `[Återställ guide]`).
  - Single-pane responsive mode in `COMPACT` mode.
- **Home Tab Objective Integration**:
  - The "Nästa uppgift" card in the Home tab dynamically reflects the active step from `GuideEngine`.
  - `[Öppna Guide]` and `[Vad ska jag göra?]` route directly to the active guide step.
