# GZ Companion installer - developer documentation

A Windows-only, self-contained, single-file .NET/WinForms installer that gets GZ Companion,
Fabric Loader, and Fabric API onto a friend's computer without them touching Fabric, `.minecraft`,
or a mods folder directly.

## Why .NET/WinForms

The build environment already has the .NET 10 SDK (with the Windows Desktop runtime) installed,
and no other single-exe installer toolchain (Inno Setup, NSIS, WiX) was present. .NET's
**self-contained single-file publish** (`-p:PublishSingleFile=true --self-contained true -r win-x64`)
produces exactly one `.exe` with the runtime embedded, so the end user needs nothing pre-installed
- no admin rights, no separate Java/.NET install, and it runs on plain Windows 10/11. WinForms was
chosen over WPF for the UI because it needs no extra NuGet packages to stay self-contained and
publish reliably as a single file. This is a maintained, first-party Microsoft toolchain, not an
"obscure abandoned packager."

## Project layout

```
GZCompanion.Installer.slnx
GZCompanion.Installer.Core/    - all logic: manifest parsing, path/environment detection,
                                  launcher_profiles.json editing, downloading + checksum
                                  verification, the install/uninstall orchestration engine.
                                  No WinForms dependency - fully unit-testable headlessly.
GZCompanion.Installer.App/     - the WinForms UI (Program.cs, MainForm.cs, Theme.cs) plus the
                                  embedded resources (compatibility.json and, once copied in by
                                  build-installer.ps1, the GZ Companion mod jar itself).
GZCompanion.Installer.Tests/   - xUnit tests against Core (110 tests as of this writing).
build-installer.ps1            - builds the mod jar, embeds it (HARD-FAILING if it doesn't match
                                  compatibility.json), runs the test suite, publishes the
                                  single-file exe, and copies the result to
                                  distribution/dist/GZ-Companion-Setup.exe.
```

## Filesystem layout: what's shared vs. isolated

Confirmed against the **official Fabric Installer's own source** (`FabricMC/fabric-installer`,
Apache-2.0 - `ClientInstaller.java` and `ProfileInstaller.java`): Fabric's own installer always
writes the version JSON to `<mcDir>/versions/<id>/<id>.json` and every library to
`<mcDir>/libraries/...`, where `<mcDir>` is the SAME root the launcher itself uses - and its
`ProfileInstaller` never sets a `gameDir` on the profile it creates at all. A profile's `gameDir`
only relocates the game's own working directory (mods/config/saves/logs); the launcher always
resolves `lastVersionId` and its libraries from its own root, regardless of `gameDir`. Earlier
revisions of this installer put Fabric's version JSON and libraries inside the isolated
GZ Companion directory - this was wrong and has been corrected:

```
%APPDATA%\.minecraft\                          <- the launcher's own root (SHARED, never wiped)
    launcher_profiles.json                     <- Win32/standalone launcher profile file
    launcher_profiles_microsoft_store.json     <- Microsoft Store/Xbox app launcher profile file
    versions\fabric-loader-0.19.5-26.1.2\...   <- Fabric's version JSON
    libraries\...                              <- Fabric's loader libraries (asm, sponge-mixin, etc.)

%LOCALAPPDATA%\GZ Companion\minecraft\          <- fully isolated GAME directory (gameDir)
    mods\fabric-api-*.jar
    mods\gzcompanion-*.jar
    config\gzcompanion\...                     <- local user data, never wiped
```

This means Fabric's version/library store is **shared launcher infrastructure** another Fabric
profile could reference - see the uninstall ownership rules below.

## Two official profile files: Win32 vs. Microsoft Store

Confirmed against `ProfileInstaller.LauncherType` in the official Fabric Installer source: the
official Minecraft Launcher actually supports **two independent** profile files, and which one(s)
exist depends entirely on which launcher channel the player has actually run at least once:

- `launcher_profiles.json` - the standalone/legacy ("Win32") launcher.
- `launcher_profiles_microsoft_store.json` - the Microsoft Store/Xbox app launcher.

`EnvironmentDetection.CheckLauncher` only ever considers the launcher "found" when **at least one**
of these files already exists - a bare `.minecraft` directory alone is not enough evidence the
launcher has ever actually been run (mirrors `ProfileInstaller.getInstalledLauncherTypes()`
returning zero types, which makes Fabric's own installer refuse to create a profile at all).

`InstallEngine` never invents either file. It only ever writes into files that already exist:

- **Neither exists** → install is blocked before any download, with
  *"Minecraft Launcher är inte färdigkonfigurerad. Starta den officiella Minecraft Launcher en
  gång och försök igen."*
- **Exactly one exists** → that one is updated. Nothing is written to the other.
- **Both exist** → **both are updated independently** (each backed up and merged on its own).
  Fabric's own GUI installer instead *asks the player to pick one* in this case
  (`ClientHandler.showLauncherTypeSelection`, an "Xbox or Win32?" dialog) - this installer
  deliberately diverges from that for a zero-knowledge friend installer: no technical corruption
  risk was found in updating both (they are fully independent files, each safely merged on its
  own), and doing so guarantees the profile shows up in whichever launcher the friend actually
  opens without them ever needing to know or guess which "type" they have.

Uninstall mirrors this: it removes the GZ Companion profile from **every** profile file that
currently exists, tolerates any of them being absent, and never creates one just to remove
something from it. When deciding whether the shared Fabric version directory is safe to delete,
it checks for another profile referencing it in **either** file - a reference in just one is
enough to keep the version directory.

## How to rebuild

```powershell
cd distribution\installer
.\build-installer.ps1
```

This runs `..\..\gradlew.bat build` (skip with `-SkipModBuild` if you already have a fresh jar),
copies the resulting `gzcompanion-*.jar` into `GZCompanion.Installer.App\Assets\gzcompanion.jar`
(embedded as a resource - never downloaded), publishes the self-contained single-file exe, and
copies it to `distribution\dist\GZ-Companion-Setup.exe`, printing its final SHA-256.

Run the test suite on its own with:

```powershell
cd distribution\installer\GZCompanion.Installer.Tests
dotnet test
```

## Command-line modes

- *(no args)* - normal GUI flow.
- `--dry-run` - runs entirely headless (no window, printed to the console): detects everything,
  computes the exact plan, and logs every step it *would* take. Verified to write nothing to disk
  in this mode.
- `--uninstall` - switches the GUI (or `--dry-run`) to the uninstall flow.
- `--test-root <path>` - developer/testing only. Overrides every path (the "AppData\Roaming" and
  "AppData\Local" roots) to live under `<path>` instead of the real user profile, and runs
  headless. Used to smoke-test the real download/verify/write pipeline (genuine HTTPS downloads,
  genuine file writes, genuine launcher_profiles.json creation) against a throwaway directory
  without ever touching a real Minecraft installation. Combine with `--uninstall` to test that
  path too.
- `--verbose` - reserved for more detailed logging (currently the log is always fairly verbose).

Example smoke test that never touches a real machine:

```powershell
$test = "$env:TEMP\gzc-smoke-test"
.\GZCompanion.Installer.App\bin\Release\net10.0-windows\win-x64\GZ-Companion-Setup.exe --test-root $test
.\GZCompanion.Installer.App\bin\Release\net10.0-windows\win-x64\GZ-Companion-Setup.exe --uninstall --test-root $test
Remove-Item -Recurse -Force $test
```

## Which files are embedded vs. downloaded

| File | Source | Written to | Verification |
|---|---|---|---|
| GZ Companion jar | **Embedded** in the exe (copied in by `build-installer.ps1` from this repo's own Gradle build) | Isolated `mods\` | SHA-256 checked against `compatibility.json` immediately after extracting it, before it's moved into place. `build-installer.ps1` refuses to even produce the exe if the freshly-built jar doesn't match `compatibility.json` (hard failure, not a warning) - `EmbeddedResourceIntegrityTests` re-checks the same thing independently as part of the test suite |
| `compatibility.json` | **Embedded** in the exe | n/a | n/a (it IS the trust root) |
| Fabric Loader version profile JSON | Downloaded live from `https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json` at install time | Shared `.minecraft\versions\` | The response's own `id` field is checked against `compatibility.json` before anything is written |
| Fabric Loader's library jars (asm, sponge-mixin, fabric-loader itself, etc.) | Downloaded from `https://maven.fabricmc.net/` at install time, using the exact relative paths and hashes the Meta API response itself provides | Shared `.minecraft\libraries\` | SHA-256 from that same API response (Fabric's own authoritative source); the one jar Fabric's API doesn't hash - `fabric-loader-{version}.jar` - falls back to an independently-verified hash pinned in `compatibility.json` |
| Fabric API mod jar | Downloaded from Modrinth's CDN (`https://cdn.modrinth.com/...`), URL pinned in `compatibility.json` | Isolated `mods\` | SHA-256 pinned in `compatibility.json`, independently computed from a real download whose SHA-512 was cross-checked against Modrinth's own published version metadata |
| Vanilla Minecraft 26.1.2 itself (client jar, assets, libraries) | **Not touched by this installer at all.** The Fabric profile JSON has `"inheritsFrom": "26.1.2"`; the official Minecraft Launcher downloads the vanilla version natively (from Mojang's own servers) the first time the "GZ Companion - GameZone" profile is played. This is the same mechanism Fabric's own installer relies on. | Launcher-managed | Mojang's own launcher handles this end-to-end; we never fetch or verify it ourselves |

Every download is HTTPS-only (`HttpsFileDownloader` throws if given a non-HTTPS URL). Nothing is
ever executed - only jars and JSON are written to disk, and no downloaded script is ever run.

## Changing the supported Minecraft/Fabric/GZ Companion versions

1. Update `distribution/compatibility.json`'s `supported` entry (or add a new one) with the new
   `minecraftVersion`/`fabricLoaderVersion`/`fabricApiVersion`/`companionVersion`, and the new
   Fabric API download URL + SHA-256 (compute it once from a real download - see
   `CompatibilityManifest`'s doc comments for the exact fields).
2. Confirm the new Fabric Loader version actually exists for that Minecraft version:
   `https://meta.fabricmc.net/v2/versions/loader/<mcVersion>` should list it.
3. Set `"status": "VERIFIED"` only once you've actually real-launcher QA tested that combination -
   until then leave it `"COMPATIBLE"` (believed to work, not yet verified) so the installer won't
   offer it. `"UNSUPPORTED"` marks a combination explicitly known NOT to work.
4. Rebuild: `.\build-installer.ps1` - it HARD-FAILS (no exe produced) if the freshly-built mod
   jar's SHA-256/size don't exactly match what you put in the manifest. Update the manifest, not
   the check.

The installer will **never** install a Minecraft version whose manifest entry isn't exactly
`VERIFIED` - a missing entry and an `UNSUPPORTED`/`COMPATIBLE` one are both refused the same way.

## Safety properties worth knowing about before touching this code

- `LauncherProfilesEditor` works on a generic `JsonNode` tree, never a strongly-typed model of the
  whole file - every key this editor doesn't own (other profiles, unknown future fields, the
  launcher's own `clientToken`/`settings`) keeps the same value and structure. (Re-serializing the
  whole document does not guarantee byte-identical whitespace/formatting - only that no value,
  key, or nesting is lost, added, or reordered.) It aborts with
  `UnsupportedLauncherProfileSchemaException` rather than guessing if the file isn't shaped the
  way every known launcher version shapes it.
- Every write to an existing file goes through `AtomicFileWriter` (write to a temp file, then
  rename over the destination) - a crash mid-write can never leave a half-written
  `launcher_profiles.json` or mod jar.
- `launcher_profiles.json` is backed up (timestamped sibling file) before every write.
- `InstallEngine` never deletes GZ Companion's own `config/gzcompanion` directory - reinstalling
  or updating never wipes Guide progress, Settlement plans, chest data, notes, or settings.
  Uninstalling asks first (`keepUserData`, defaulting to true in the UI).
- **The launcher app itself must be closed.** Official Fabric installation guidance requires this
  before editing `launcher_profiles.json`. `EnvironmentDetection.IsMinecraftLauncherRunning`
  checks for any process whose main window title contains "Minecraft Launcher" (confirmed
  empirically: the modern Microsoft Store launcher's main window has exactly that title, running
  under a process literally named "Minecraft" - window title is channel-independent, unlike
  process name). `InstallEngine.RunAsync`/`UninstallAsync` re-check this themselves as the very
  first thing, before any download or file write, closing the race where the player opens the
  launcher between the GUI's own pre-flight check and clicking the button. A `--test-root`
  developer smoke test wires this check to always report "not running", since it never touches a
  real `launcher_profiles.json` anyway.
- **Shared Fabric infrastructure is never bulk-deleted on uninstall.** Fabric's version JSON and
  libraries live under the launcher's own `.minecraft` root (see above) and may be used by another
  Fabric profile the player set up themselves. `InstallEngine.UninstallAsync` therefore:
  - **Never** deletes anything under `.minecraft\libraries` - leaving a cached jar behind is
    strictly safer than risking another installation that might still need it.
  - Only deletes our own specific Fabric version directory (e.g.
    `versions\fabric-loader-0.19.5-26.1.2\`) if it can positively confirm - by reading **every**
    existing profile file (Win32 and/or Microsoft Store) - that no OTHER profile, in EITHER file,
    still has that exact `lastVersionId`. A reference from just one of the two files is enough to
    keep it. If our own `installed.json` can't say which version we installed, the version
    directory is left alone rather than guessed at. **Zero existing profile files is NOT proof of
    safety either** - `Enumerable.Any()` over an empty collection is vacuously `false`, which
    briefly made `versionSafeToRemove` incorrectly `true` in that case; fixed by requiring at
    least one profile file to have actually been checked before removal is ever considered safe
    (`Uninstall_KeepsFabricVersionDirectoryWhenZeroProfileFilesExistEvenIfOwned` locks this in).
- **Both official profile files are handled, independently.** See "Two official profile files"
  above - install/uninstall touch every existing one (never inventing a missing one), each backed
  up and merged/removed-from on its own.
- **Every existing profile file is pre-validated before ANYTHING else happens.** With two
  independent files, one could be perfectly valid while the other is corrupt/unsupported. If that
  were only discovered while writing (after directories were created, Fabric/Fabric API/the mod
  jar already downloaded, and possibly the FIRST, valid profile file already updated), the
  install would be left half-done. `InstallEngine.RunAsync` now calls
  `LauncherProfilesEditor.ParseAndValidateFile` on every existing profile file immediately after
  confirming at least one exists - before creating a single directory or downloading a single
  byte - and keeps the validated documents in memory for the later write step rather than
  re-reading them. If any file fails, the whole install aborts with
  `"<filename> kunde inte läsas säkert. Ingen installation har gjorts."` and touches nothing at
  all. `UninstallAsync` does the same for its own removal step. The GUI's own pre-flight
  (`MainForm.DetectAsync`) surfaces the same condition, via the same
  `LauncherProfilesEditor.FindFirstUnreadableProfile` helper (not a separately re-implemented
  parser), before the Installera/Avinstallera button is even enabled.
