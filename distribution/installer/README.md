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
GZCompanion.Installer.Tests/   - xUnit tests against Core (68 tests as of this writing).
build-installer.ps1            - builds the mod jar, embeds it, publishes the single-file exe,
                                  and copies the result to distribution/dist/GZ-Companion-Setup.exe.
```

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

| File | Source | Verification |
|---|---|---|
| GZ Companion jar | **Embedded** in the exe (copied in by `build-installer.ps1` from this repo's own Gradle build) | SHA-256 checked against `compatibility.json` immediately after extracting it, before it's moved into place |
| `compatibility.json` | **Embedded** in the exe | n/a (it IS the trust root) |
| Fabric Loader version profile JSON | Downloaded live from `https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json` at install time | The response's own `id` field is checked against `compatibility.json` before anything is written |
| Fabric Loader's library jars (asm, sponge-mixin, fabric-loader itself, etc.) | Downloaded from `https://maven.fabricmc.net/` at install time, using the exact relative paths and hashes the Meta API response itself provides | SHA-256 from that same API response (Fabric's own authoritative source); the one jar Fabric's API doesn't hash - `fabric-loader-{version}.jar` - falls back to an independently-verified hash pinned in `compatibility.json` |
| Fabric API mod jar | Downloaded from Modrinth's CDN (`https://cdn.modrinth.com/...`), URL pinned in `compatibility.json` | SHA-256 pinned in `compatibility.json`, independently computed from a real download whose SHA-512 was cross-checked against Modrinth's own published version metadata |
| Vanilla Minecraft 26.1.2 itself (client jar, assets, libraries) | **Not touched by this installer at all.** The Fabric profile JSON has `"inheritsFrom": "26.1.2"`; the official Minecraft Launcher downloads the vanilla version natively (from Mojang's own servers) the first time the "GZ Companion - GameZone" profile is played. This is the same mechanism Fabric's own installer relies on. | Mojang's own launcher handles this end-to-end; we never fetch or verify it ourselves |

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
4. Rebuild: `.\build-installer.ps1` (it will warn if the freshly-built mod jar's hash doesn't
   match what you put in the manifest - update the manifest, not the warning).

The installer will **never** install a Minecraft version whose manifest entry isn't exactly
`VERIFIED` - a missing entry and an `UNSUPPORTED`/`COMPATIBLE` one are both refused the same way.

## Safety properties worth knowing about before touching this code

- `LauncherProfilesEditor` works on a generic `JsonNode` tree, never a strongly-typed model of the
  whole file - every key this editor doesn't own (other profiles, unknown future fields, the
  launcher's own `clientToken`/`settings`) is round-tripped untouched. It aborts with
  `UnsupportedLauncherProfileSchemaException` rather than guessing if the file isn't shaped the
  way every known launcher version shapes it.
- Every write to an existing file goes through `AtomicFileWriter` (write to a temp file, then
  rename over the destination) - a crash mid-write can never leave a half-written
  `launcher_profiles.json` or mod jar.
- `launcher_profiles.json` is backed up (timestamped sibling file) before every write.
- `InstallEngine` never deletes GZ Companion's own `config/gzcompanion` directory - reinstalling
  or updating never wipes Guide progress, Settlement plans, chest data, notes, or settings.
  Uninstalling asks first (`keepUserData`, defaulting to true in the UI).
