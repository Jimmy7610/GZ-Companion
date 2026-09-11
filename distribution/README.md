# GZ Companion distribution

This folder is the friend-test Windows installer for GZ Companion, kept entirely separate from
the Minecraft mod's own runtime code (`src/main/java/...`). Nothing here runs inside Minecraft;
it only exists to get GZ Companion, Fabric Loader, and Fabric API onto a friend's computer with
as close to zero Minecraft-modding knowledge as possible.

```
distribution/
  README.md                 - this file
  compatibility.json        - the ONE place that says which Minecraft/Fabric/Companion
                               combination this installer will actually install
  licenses/                 - GZ Companion, Fabric Loader, Fabric API license texts + notices
  installer/                - the .NET/WinForms installer source (see installer/README.md)
  dist/                     - build-installer.ps1's output (git-ignored, built locally)
```

## What this installer does, in one sentence

Downloads the exact official Fabric Loader + Fabric API for Minecraft 26.1.2 from Fabric's and
Modrinth's own servers, drops GZ Companion's own jar (embedded in the exe) alongside them in a
brand-new, fully isolated game directory, and adds one new profile - "GZ Companion - GameZone" -
to the player's existing Minecraft Launcher, without touching anything else already there.

See [`installer/README.md`](installer/README.md) for the full technical breakdown (architecture,
how to rebuild, exact download sources, checksum policy) and
[`END-USER-GUIDE.md`](END-USER-GUIDE.md) for the short guide meant for the friend actually
installing it.

## compatibility.json

The installer refuses to install anything not listed here with `"status": "VERIFIED"`. For this
release that's exactly one entry: Minecraft 26.1.2 / Fabric Loader 0.19.5 / Fabric API
0.155.3+26.1.2 / GZ Companion 0.1.0-alpha.1. Future releases add new entries here rather than the
installer guessing at compatibility.

## Building the installer

```powershell
cd distribution\installer
.\build-installer.ps1
```

Produces `distribution\dist\GZ-Companion-Setup.exe` - see `installer/README.md` for details,
prerequisites, and what to change when the supported Minecraft/Fabric/Companion versions change.

## Status

This is an **alpha friend-test build**, not a public release. It has not been published to
Modrinth, no GitHub Release has been cut, and it should only be shared hand-to-hand with people
you trust to give feedback, per the explicit scope of this pass.
