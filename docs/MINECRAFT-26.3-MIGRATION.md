# Minecraft 26.3 migration

**Status: ported and verified locally on branch `feature/minecraft-26.3`. Not released.**
Everything that can be verified without GameZoneMC itself has been verified (see section 4).
What remains is the final check on the real GameZoneMC server once it runs 26.3 (section 5).
Up to and including `v0.1.0-alpha.12`, GZ Companion targeted Minecraft 26.1.2; that history is unchanged.

## 1. Toolchain

| | 26.1.2 (alpha.12) | 26.3 |
|---|---|---|
| Minecraft | 26.1.2 | **26.3** (stable release) |
| Fabric Loader | 0.19.5 | 0.19.5 (unchanged - current stable loader; Fabric API requires >= 0.19.3) |
| Fabric API | 0.155.3+26.1.2 | **0.164.0+26.3** |
| Fabric Loom | 1.18.0-alpha.21 | **1.18.4** (the 1.18 line used by Fabric's 26.3 example mod) |
| Gradle / Java | 9.7.1 / 25 | 9.7.1 / 25 (unchanged) |

Sources: Fabric Meta (`meta.fabricmc.net`), Fabric Maven, the official
[Fabric for Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html) post, and the 26.3 branch of
Fabric's example mod. API details were checked with `javap` against the real 26.3 client.

## 2. Breaking changes that affected GZ Companion

| Change in 26.3 | Effect on GZ Companion | Fix |
|---|---|---|
| GLFW removed, SDL used for window and input | `org.lwjgl.glfw.GLFW` key constants no longer exist (compile error in 9 files) | `InputConstants.KEY_*` (SDL scancodes, e.g. G = 10, Esc = 41); keybind type `KEYBOARD` |
| Mouse buttons numbered from 1 (left = 1, right = 3) | **Silent**: every tab ignored clicks with `button != 0` - all left clicks would have done nothing | `UiMouseButton` translates once at the screen boundary to the tabs' own left = 0 |
| Characters only arrive while text input is started (`TextInputManager`) | **Silent**: typing into Companion search, note and coordinate fields would have stopped | `TextInputFocusTracker` reports focus to `Minecraft#onTextInputFocusChange` and releases it on close |
| Screen moved to `Gui`: `Minecraft.screen` / `setScreen` removed | compile errors | `client.gui.screen()` / `client.gui.setScreen(...)` |
| HUD parts moved to `Gui.hud` | `gui.getTabList()` / `getBossOverlay()` removed | `client.gui.hud.getTabList()` / `getBossOverlay()` |

Unchanged in 26.3 (verified): all three mixin targets (`PlayerTabOverlay.header`,
`BossHealthOverlay.events`, `ToastManager.addToast`, `SystemToastId.UNSECURE_SERVER_WARNING`),
`GuiGraphicsExtractor` drawing, fonts and `fakeItem`, the recipe-book display API, block/menu
classes for storage capture, registries/tags/`DataComponents.FOOD`, every Fabric event used,
`Minecraft.stop()` (still a plain flag flip, used by the updater).

Version-coupled data (no compile error, but would have broken features):
- `guide-content/manifest.json` must list the running version, otherwise the **Guide is disabled**
  (`GuideLoadStatus.INCOMPATIBLE`) - now `["26.3"]`. `gamezone-pack/manifest.json` likewise (warning only).
- The Minecraft version shown on Home / in diagnostics and used by the compatibility checks is now
  read from Fabric Loader at runtime (`CompanionConstants.runningMinecraftVersion()`);
  `TARGET_MINECRAFT_VERSION = "26.3"` is the build target and fallback.
- The installer no longer hard-codes a Minecraft version: it installs the single VERIFIED entry of
  `distribution/compatibility.json` (`CompatibilityManifest.FindInstallTarget()`).

Also fixed on the way: the counter is inactive in development runs (`runClient`, game tests), so
test runs can never send heartbeats to the production statistics; the bundled DejaVu license now
has a valid lower-case resource path (was logged as an error on every launch).

## 3. Fair play and privacy

Unchanged. No new data is read from the world or the server. Storage capture still only reads a
storage the player opens. The usage heartbeat is still exactly `{installationId, version}`, at most
once a day.

## 4. Verification

- **Unit tests**: `gradlew test` - 1365 tests, 0 failures (1354 from alpha.12, none removed or
  skipped, plus 26.3 regression tests: mouse-button translation, text-input focus tracking,
  version consistency, no GLFW / raw button literals, dev-environment counter guard).
- **Runtime (real Minecraft 26.3 client)**: `gradlew runClientGameTest`, run **twice**
  (`src/gametest`). Run 1 - 56 checks with real simulated input in a fresh singleplayer world:
  versions; Guide incl. item/tag registry validation; rule pack compatibility; counter inactive;
  G opens/closes, Esc closes; all 13 tabs in both themes (screenshots); real mouse clicks; SDL text
  input start/stop and typed characters; capture of chest, trapped chest, barrel, shulker box,
  hopper, dispenser and dropper opened by a real use-key press, with exact slot contents; furnace
  never indexed; double chest with partner and both halves; search; Hitta start, HUD and arrival
  stop; boss bar, TAB header and toast mixins; online list; Guide inventory/tag/food detection;
  recipe book; disconnect clears transient state; rejoin keeps data. Run 2 (new JVM = game
  restart) - theme, 8 captured storages and the Guide load again. Screenshots:
  `build/run/clientGameTest/screenshots`. Only the integrated server is used (no EULA accepted).
- **Installer**: 235 installer tests, 0 failures. Upgrade simulation with `--test-root` (isolated
  sandbox, real downloads): the published alpha.12 installer installed the 26.1.2 stack, then the
  26.3 development installer upgraded it - same launcher profile and dedicated game directory,
  profile on `fabric-loader-0.19.5-26.3`, new Fabric API and Companion jar (hashes as pinned), the
  old owned Fabric API removed, user config / Kistor data / `servers.dat` / saves byte-identical.

## 5. Still to verify on GameZoneMC 26.3 (cannot be done locally)

Status: **READY FOR FINAL GAMEZONE 26.3 VALIDATION.**
- Connect to `play.gamezonemc.se` on 26.3: server detection, profile "GameZone", Online tab with the
  real TAB list, settlement/live status from the real TAB header.
- GameZone chat/event observation and toasts with real server messages.
- Storage capture on the server's real containers (plugins may wrap menus).
- The official Minecraft Launcher installing and starting the 26.3 profile on a player machine.
- Leaderboards / Bounties / Events use GameZone's public web APIs and are not tied to the Minecraft
  version; they were rendered in the runtime test but their data comes from the live endpoints.

## 6. Before releasing a 26.3 build

- **Timing**: the in-game updater offers any newer release regardless of its Minecraft version, and
  the installer moves the player's profile to 26.3. Publish a 26.3 build only once GameZoneMC accepts
  26.3 clients, otherwise updated players could not join.
- **Version**: the branch still says `0.1.0-alpha.12`; bump `mod_version` (and `compatibility.json`)
  in the release step - see `distribution/RELEASING.md`.
- Known, pre-existing (also in alpha.12, not a migration regression): in singleplayer every world
  resolves to the same storage/guide context (`singleplayer:.`), because the world folder name is
  read from `getWorldPath(LevelResource.ROOT)`, whose file name is ".". GameZone (multiplayer) is
  unaffected.
