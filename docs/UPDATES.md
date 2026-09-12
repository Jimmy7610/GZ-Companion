# In-app updates

Status: **Implemented, automated tests passing, awaiting human QA** (see the bootstrap note below -
this document itself describes the target end-state once `0.1.0-alpha.2` is installed).

> **GZ Companion is an unofficial community project for GameZoneMC. It is not affiliated with or
> endorsed by GameZoneMC.**

## 1. What this is

Starting with `0.1.0-alpha.2`, GZ Companion can update itself: it quietly checks for a newer
release in the background, and - only when the player explicitly chooses to - downloads,
verifies, and applies it with a single click, restarting only the game (not necessarily the
Minecraft Launcher). No manual download, no visiting GitHub, no finding the mods folder, no
replacing jars by hand.

## 2. Bootstrap requirement - read this first

**Players currently on `0.1.0-alpha.1` do NOT have an updater yet.** `0.1.0-alpha.1` shipped before
this feature existed, so it has no code to check for or apply updates. Those players must install
`0.1.0-alpha.2` manually, exactly like today (download `GZ-Companion-Setup.exe`, run it) - this is
the **one, final** manual install. Every release after `0.1.0-alpha.2` can be delivered through the
in-app updater described below.

The manual download (e.g. via Google Drive) remains available indefinitely as a fallback/bootstrap
path for a brand new player or a reinstall. The in-app updater itself never uses that fallback
location - it only ever talks to GitHub Releases (see below).

## 3. Where updates come from

The public GitHub repository **`Jimmy7610/GZ-Companion`**, via its public Releases API
(`https://api.github.com/repos/Jimmy7610/GZ-Companion/releases`). No API key, no GitHub token, no
Jimmy-specific credentials of any kind - this is the same public endpoint anyone can query with a
web browser. HTTPS only.

Each release must contain exactly two assets:
- `GZ-Companion-Setup.exe` - the same installer players already know, extended with an
  `--apply-update` mode (see section 8).
- `update-manifest.json` - a small, strict, versioned manifest (see section 4) that authoritatively
  declares the release's version, target Minecraft/Fabric versions, the installer's exact SHA-256
  and size, and short Swedish release notes.

**Release selection is real Semantic Versioning, not string comparison** (see
`se.jimmyeliasson.gzcompanion.update.SemanticVersion`) - `0.1.0-alpha.9 < 0.1.0-alpha.10`, and a
version without a prerelease tag always outranks one with. Draft releases are always ignored.
GZ Companion's current channel is ALPHA (matches its own `0.1.0-alpha.N` version), so it currently
accepts alpha, beta, or stable releases - a future stable client will accept ONLY stable releases,
never silently receiving a prerelease (see `UpdateChannel`).

## 4. Update manifest schema

```json
{
  "schemaVersion": 1,
  "version": "0.1.0-alpha.3",
  "channel": "alpha",
  "minecraftVersion": "26.1.2",
  "fabricLoaderVersion": "0.19.5",
  "fabricApiVersion": "0.155.3+26.1.2",
  "installer": {
    "fileName": "GZ-Companion-Setup.exe",
    "sha256": "<64-hex-char lowercase SHA-256 of the exact installer bytes>",
    "sizeBytes": 12345678
  },
  "notes": [
    "Ny funktion: ...",
    "Förbättrad: ...",
    "Bugfix: ..."
  ]
}
```

An unsupported/unknown `schemaVersion` (or any malformed field - a bad SHA-256 shape, a zero/
negative size, a path-traversal-looking file name) makes the ENTIRE manifest fail to parse, and
that release is simply skipped as if it didn't exist - never guessed at, never partially trusted.

## 5. Security boundary - this feature downloads an executable

**Trust model:** GZ Companion trusts releases published from the official `Jimmy7610/GZ-Companion`
GitHub repository - the same trust a player already places in the manual download today.
**Integrity verification is real and independent of that trust**, not a rubber stamp:

1. HTTPS only, everywhere.
2. Only the public `Jimmy7610/GZ-Companion` releases endpoint is ever queried - a fixed, hardcoded
   URL, never derived from anything a release or a player could influence.
3. The manifest's declared installer file name must be a plain file name (no path separators, no
   `..`) - anything else fails the whole manifest, never sanitized-and-proceeded-with.
4. The download is written to a `.part` staging file first - it is never treated as the update
   until every check below passes.
5. The actual downloaded byte count is compared against the manifest's declared size.
6. A SHA-256 is computed locally from the downloaded bytes and compared against the manifest's
   declared hash.
7. If GitHub's own release-asset API additionally provides a digest for that asset, it is checked
   too - as defense in depth, never as a substitute for the check above.
8. Only after ALL of the above pass is the `.part` file renamed into place as the real installer.
9. A hash mismatch, a size mismatch, or an interrupted download all delete the `.part` file and
   leave the current installation completely untouched - GZ Companion never executes a
   partially-downloaded or hash-mismatched file, under any circumstance.
10. Immediately before actually launching the downloaded installer (when the player presses "Stäng
    och uppdatera"), its hash is verified ONE MORE TIME from the file on disk - in case anything
    happened to it between download-time verification and this moment.

See `se.jimmyeliasson.gzcompanion.update.UpdateDownloader` and `UpdateInstallerLauncher`.

## 6. What is checked, and what is never sent

The background check is infrastructure, not gameplay, and carries no gameplay data whatsoever - it
never sends the player's Minecraft username, GameZone username, server IP, settlement, coins,
online players, coordinates, any setting, or any telemetry. The only outgoing data is a plain HTTP
`User-Agent` header identifying this updater by name and version
(`GZCompanion-Updater/<version>`), exactly like any command-line tool identifies itself to GitHub's
API.

## 7. Check frequency

- The first automatic check happens ~20 seconds after GZ Companion initializes - never blocking
  startup, never blocking the render thread or the game's connection.
- After that, at most once every 45 minutes automatically.
- The Inställningar tab's "Sök efter uppdateringar" button (and Home's own retry actions) always
  bypasses that cooldown.
- A check only ever looks at metadata (the release list + the small manifest text) - **the
  installer itself is only ever downloaded when the player explicitly presses "Ladda ner."**
- If GitHub is unreachable, GZ Companion continues completely normally and shows nothing - a
  network hiccup is never surfaced as an error to the player.

## 8. Applying an update - fast path vs. full path

The downloaded `GZ-Companion-Setup.exe` becomes the update worker itself (no separate, second
installer executable) via a new invocation mode:

```
GZ-Companion-Setup.exe --apply-update --wait-pid <minecraftPid> --from-version <currentVersion>
```

When the player presses "Stäng och uppdatera": the mod re-verifies the downloaded installer's
hash, starts it in this mode, and **only if that process actually starts** does it request a
graceful Minecraft shutdown (`Minecraft.stop()` - verified via `javap` against the real Minecraft
26.1.2 jar to be a trivial, thread-safe flag flip the client's own main loop unwinds from normally;
never `Runtime.halt`/`System.exit`). This ordering means a failure to start the updater never
leaves the game already closed with nothing able to apply the update.

The update worker first waits for that specific Minecraft process to exit, then additionally
confirms no other Minecraft game process is still using the installation, before touching any
file. It then picks one of two paths:

- **Safe fast path** (the common case): if the previous installation already targets the exact
  same Minecraft version AND Fabric Loader version the update also targets, `launcher_profiles.json`
  is NEVER opened, and the Minecraft Launcher app may stay open the whole time. Only GZ Companion's
  own mod jar (and Fabric API, if its hash changed) are replaced, using the same staged-then-
  verified-then-atomically-renamed pattern as a fresh install, and the OLD jar is only removed
  after the NEW one is confirmed good.
- **Full path fallback**: if the Minecraft or Fabric Loader version is changing (or there's no
  readable previous install-state to safely confirm otherwise), the existing, already-proven full
  `InstallEngine` install path is reused - which DOES require the Minecraft Launcher app closed,
  exactly like a first-time install. If it's open, the player sees "Stäng Minecraft Launcher för
  att fortsätta" with a "Försök igen" button; GZ Companion never force-closes the Launcher itself.

Either way, updater failures never destroy a working installation: every new file is staged and
hash-verified before anything old is removed, and `installed.json` is written last, only once
success is certain.

## 9. What is always preserved

The updater may only ever replace files GZ Companion itself owns (its own mod jar, and Fabric API
when needed). It never touches: `config/`, `saves/`, `screenshots/`, `logs/`, `servers.dat`,
favorites, chest cache, settlement plans, guide progress, settings, or any other/unrelated mod.

## 10. Local storage

Downloaded updates live under `%LOCALAPPDATA%\GZ Companion\updates\<version>\` - never inside
`.minecraft`, `mods`, `saves`, `config`, the Desktop, or Downloads. Each version gets its own
subfolder; an abandoned `.part` staging file from a previous crashed/interrupted attempt is pruned
automatically the next time GZ Companion starts.

## 11. Human QA

See the two QA plans in the current updater task's final report: one for the `0.1.0-alpha.1` →
`0.1.0-alpha.2` bootstrap manual install, and one (once a real `0.1.0-alpha.3` GitHub Release
exists) for proving the one-click `alpha.2` → `alpha.3` update end to end on a real machine.
