# Releasing GZ Companion

This is the exact developer workflow for cutting a new release once `0.1.0-alpha.2` (the last
manual-bootstrap version - see `docs/UPDATES.md`) is out. Publishing is always a deliberate,
manual action - nothing here runs automatically as part of a normal build.

## 1. Bump the canonical version

`gradle.properties`' `mod_version` is the ONE canonical source for the product version - every
other place (Fabric metadata, the installer, `compatibility.json`) either reads it directly or is
regenerated/verified against it at build time.

1. Edit `gradle.properties`: bump `mod_version` (e.g. `0.1.0-alpha.2` → `0.1.0-alpha.3`).
2. Edit `distribution/compatibility.json`'s `supported[0].companionVersion` to the SAME value, and
   update `companionJar.fileName` to `gzcompanion-<version>.jar`. Leave `sha256`/`sizeBytes` as
   placeholders for now - step 3 fills them in for real.
3. `distribution/installer/build-installer.ps1` HARD-FAILS if these two don't match - this is
   intentional, not a bug to work around.

## 2. Build and verify

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

Compute the new jar's real hash/size and paste them into `compatibility.json`:

```powershell
Get-FileHash .\build\libs\gzcompanion-<version>.jar -Algorithm SHA256
(Get-Item .\build\libs\gzcompanion-<version>.jar).Length
```

Then build the installer (this also regenerates the installer's own version file, re-verifies the
embedded jar against `compatibility.json`, and runs the installer's own test suite):

```powershell
cd distribution\installer
.\build-installer.ps1
```

This fails loudly (no exe produced) if anything disagrees. Manually smoke-test
`distribution\dist\GZ-Companion-Setup.exe` (run it once - confirm branding/footer/version look
right, then close it) before continuing.

## 3. Generate the release bundle

From the repo root:

```powershell
.\distribution\prepare-release.ps1
```

This re-runs the full pipeline above (mod tests → mod build → installer tests → installer build →
embedded-jar hash re-verification) and produces:

```
distribution\release\v<version>\
    GZ-Companion-Setup.exe
    update-manifest.json
```

`update-manifest.json` is generated FROM the real, just-built exe's own SHA-256/size - never typed
by hand - and from `compatibility.json`'s Minecraft/Fabric versions. You'll be prompted for the
Swedish release notes (short bullet lines) to embed in it.

This step does NOT publish anything - it only writes local files for you to review.

## 4. Create the GitHub Release

Tag format: **`v<version>`** (e.g. `v0.1.0-alpha.3`) - the in-app updater's `SemanticVersion`
parser expects this exact `v`-prefixed form.

### With the GitHub CLI (`gh`), if installed and authenticated

```powershell
.\distribution\publish-release.ps1 -Publish
```

This is a SEPARATE, explicit script/flag from `prepare-release.ps1` - it is never run as part of a
normal build, never embeds a token, and never prints one. It creates the tag, creates the release
(marked as a prerelease for any `-alpha.`/`-beta.` version, matching `UpdateChannel`), and uploads
BOTH `GZ-Companion-Setup.exe` and `update-manifest.json` from
`distribution\release\v<version>\`.

### Without `gh` - the GitHub web UI

1. Go to `https://github.com/Jimmy7610/GZ-Companion/releases/new`.
2. Tag: `v<version>` (create the tag from this screen, targeting `main`).
3. Title: `v<version>`.
4. Description: paste the same release notes you gave `prepare-release.ps1`.
5. Check **"Set as a pre-release"** for any `-alpha.`/`-beta.` version. Leave it unchecked only for
   a real stable release.
6. **Do not check "Set as the latest release" for a prerelease** if a more recent stable release
   already exists (GitHub's own "latest" flag is independent of this project's own SemVer
   selection, but keeping it accurate avoids confusing a human browsing the repo).
7. Attach both files from `distribution\release\v<version>\`: `GZ-Companion-Setup.exe` and
   `update-manifest.json`.
8. Publish.

## 5. Verify the release

- Confirm the release page shows exactly two assets, both downloadable.
- Download `update-manifest.json` fresh from the release page and confirm its `sha256` matches
  `Get-FileHash` on the downloaded `GZ-Companion-Setup.exe` from the SAME release page (not your
  local copy) - this is the actual end-to-end integrity chain a real client will follow.
- On a real (or test-root) machine already on the previous version, confirm the in-app updater
  finds it (Inställningar → "Sök efter uppdateringar", or wait for the automatic check) and that
  the full download → verify → apply flow completes. See `docs/UPDATES.md` section 11.

## 6. Rollback / yank a bad release

GitHub Releases has no true "unpublish that still lets old clients see it was pulled" - instead:

1. Immediately edit the bad release on GitHub and check **"Set as a pre-release"** if not already,
   and add a `**YANKED - do not install**` note at the top of its description.
2. Publish a NEW, higher-versioned release with the fix as soon as possible - GZ Companion's
   updater always selects the highest valid version, so a client that already saw the bad release
   will move straight to the fix once it's live, and a client that hasn't checked yet will simply
   never offer the bad one once the fix is the newest.
3. Only delete the bad release/asset outright if it's actively harmful (e.g. a real hash/security
   problem) - a client that already fully downloaded and verified it before deletion is unaffected
   either way, since verification is local and doesn't re-check GitHub at apply-time.

## 7. Files this workflow touches

| File | Role |
|---|---|
| `gradle.properties` | Canonical version (`mod_version`) - edit this first. |
| `distribution/compatibility.json` | Must match the canonical version; carries the mod jar's hash/size. |
| `distribution/installer/GZCompanion.Installer.Core/GeneratedProductVersion.cs` | Auto-regenerated by `build-installer.ps1` - never edit by hand. |
| `distribution/prepare-release.ps1` | Builds + generates `distribution/release/v<version>/`. Never publishes. |
| `distribution/publish-release.ps1` | Optional, explicit, `gh`-based publish step. Never run automatically. |
