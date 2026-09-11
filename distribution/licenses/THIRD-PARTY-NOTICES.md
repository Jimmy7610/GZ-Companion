# Third-party notices

GZ Companion and its Windows installer (`GZ-Companion-Setup.exe`) include or download the
following third-party software. This file exists so those requirements are met wherever the
installer is redistributed.

## GZ Companion

MIT License. Copyright (c) 2026 Jimmy Eliasson. See [`GZ-COMPANION-LICENSE.txt`](GZ-COMPANION-LICENSE.txt).
Source: https://github.com/Jimmy7610/GZ-Companion

## Fabric Loader

Apache License 2.0. Copyright FabricMC and contributors. See [`FABRIC-LOADER-LICENSE.txt`](FABRIC-LOADER-LICENSE.txt).
Source: https://github.com/FabricMC/fabric-loader
Downloaded by the installer at install time from the official Fabric Meta API
(`https://meta.fabricmc.net`) and Fabric's Maven repository (`https://maven.fabricmc.net`).
Never modified or redistributed by us directly - always fetched fresh from Fabric's own servers.

## Fabric API

Apache License 2.0. Copyright FabricMC and contributors. See [`FABRIC-API-LICENSE.txt`](FABRIC-API-LICENSE.txt).
Source: https://github.com/FabricMC/fabric
Downloaded by the installer at install time from Modrinth's CDN
(`https://cdn.modrinth.com`), the same file Modrinth serves to every other Fabric API user.
Never modified or redistributed by us directly - always fetched fresh from Modrinth.

## .NET runtime (self-contained deployment)

MIT License. Copyright (c) .NET Foundation and Contributors.
Source: https://github.com/dotnet/runtime
`GZ-Companion-Setup.exe` is published as a **self-contained** .NET application, meaning a private
copy of the .NET runtime is embedded inside the single exe file so the end user never needs to
install .NET themselves. Full license: https://github.com/dotnet/runtime/blob/main/LICENSE.TXT

## Windows Forms

MIT License. Copyright (c) .NET Foundation and Contributors.
Source: https://github.com/dotnet/winforms
Used for the installer's own user interface only - never bundled into the Minecraft mod itself.

## What is never bundled or redistributed

- Minecraft Java Edition itself, or any Mojang/Microsoft copyrighted game asset.
- Any account credentials, tokens, or session data.
- Any content from the player's existing Minecraft installation.

## Endorsement disclaimer

GZ Companion is an unofficial community project for GameZoneMC. It is not affiliated with or
endorsed by GameZoneMC, Mojang, Microsoft, or Fabric. Fabric Loader and Fabric API are separate
open-source projects by the FabricMC team; GZ Companion merely uses them as its mod-loading
platform, exactly as any other Fabric mod does.
