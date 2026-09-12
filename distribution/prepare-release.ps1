#Requires -Version 5.1
<#
.SYNOPSIS
    Builds and packages one GZ Companion release into distribution\release\v<version>\ -
    GZ-Companion-Setup.exe + update-manifest.json. NEVER publishes anything - see
    distribution\RELEASING.md and (optionally) publish-release.ps1 for the actual publish step.

.DESCRIPTION
    1. Reads the canonical version from gradle.properties.
    2. Runs the mod test suite, builds the mod jar.
    3. Runs the installer test suite, builds the installer (which itself re-verifies the embedded
       jar's hash against distribution\compatibility.json and hard-fails on any version mismatch).
    4. Computes the just-built installer's real SHA-256/size.
    5. Prompts for short Swedish release notes and generates update-manifest.json from real,
       just-built values - never hand-typed hashes/sizes.
    6. Copies both files into distribution\release\v<version>\.
#>
param(
    [switch]$SkipTests,
    # Optional: supply release notes non-interactively (e.g. from CI or a script), one line each.
    # Omit to be prompted interactively instead.
    [string[]]$Notes
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")
$installerDir = Join-Path $scriptDir "installer"

Write-Host "== 1. Reading canonical version ==" -ForegroundColor Cyan
$gradlePropsPath = Join-Path $repoRoot "gradle.properties"
$modVersionLine = Get-Content $gradlePropsPath | Where-Object { $_ -match '^\s*mod_version\s*=' } | Select-Object -First 1
if (-not $modVersionLine) { throw "Could not find mod_version= in $gradlePropsPath" }
$version = ($modVersionLine -split '=', 2)[1].Trim()
Write-Host "Version: $version"

$compat = Get-Content (Join-Path $scriptDir "compatibility.json") -Raw | ConvertFrom-Json
$entry = $compat.supported[0]
if ($entry.companionVersion -ne $version) {
    throw "compatibility.json companionVersion ($($entry.companionVersion)) does not match gradle.properties mod_version ($version). Bump both first - see distribution\RELEASING.md."
}

Write-Host "== 2. Running mod tests and building the mod jar ==" -ForegroundColor Cyan
Push-Location $repoRoot
try {
    if (-not $SkipTests) {
        & ".\gradlew.bat" clean test --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Mod tests failed." }
    }
    & ".\gradlew.bat" build --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Mod build failed." }
} finally {
    Pop-Location
}

Write-Host "== 3. Building the installer (hash-guarded) ==" -ForegroundColor Cyan
Push-Location $installerDir
try {
    if ($SkipTests) {
        .\build-installer.ps1 -SkipModBuild -SkipTests
    } else {
        .\build-installer.ps1 -SkipModBuild
    }
    if ($LASTEXITCODE -ne 0) { throw "Installer build failed." }
} finally {
    Pop-Location
}

$exePath = Join-Path $scriptDir "dist\GZ-Companion-Setup.exe"
if (-not (Test-Path $exePath)) { throw "Expected installer not found at $exePath" }

Write-Host "== 4. Computing installer hash/size ==" -ForegroundColor Cyan
$exeHash = (Get-FileHash $exePath -Algorithm SHA256).Hash.ToLowerInvariant()
$exeSize = (Get-Item $exePath).Length
Write-Host "sha256=$exeHash size=$exeSize"

Write-Host "== 5. Release notes ==" -ForegroundColor Cyan
if ($Notes -and $Notes.Count -gt 0) {
    $notes = $Notes
    Write-Host "Using -Notes: $($notes -join ' | ')"
} elseif ([Environment]::UserInteractive) {
    Write-Host "Enter short Swedish release-note lines, one per line. Empty line to finish:"
    $notes = @()
    while ($true) {
        $line = Read-Host "  Nytt"
        if ([string]::IsNullOrWhiteSpace($line)) { break }
        $notes += $line
    }
    if ($notes.Count -eq 0) { $notes = @("Buggfixar och förbättringar.") }
} else {
    Write-Host "Non-interactive session and no -Notes given - using a placeholder. Edit update-manifest.json's notes before publishing." -ForegroundColor Yellow
    $notes = @("Buggfixar och förbättringar.")
}

$channel = if ($version -match '-alpha\.') { "alpha" } elseif ($version -match '-beta\.') { "beta" } else { "stable" }

$manifest = [ordered]@{
    schemaVersion      = 1
    version            = $version
    channel            = $channel
    minecraftVersion   = $entry.minecraftVersion
    fabricLoaderVersion = $entry.fabricLoaderVersion
    fabricApiVersion   = $entry.fabricApiVersion
    installer          = [ordered]@{
        fileName  = "GZ-Companion-Setup.exe"
        sha256    = $exeHash
        sizeBytes = $exeSize
    }
    notes = $notes
}

Write-Host "== 6. Writing release bundle ==" -ForegroundColor Cyan
$releaseDir = Join-Path $scriptDir "release\v$version"
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
Copy-Item $exePath (Join-Path $releaseDir "GZ-Companion-Setup.exe") -Force
$manifest | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $releaseDir "update-manifest.json") -Encoding utf8

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "  $releaseDir\GZ-Companion-Setup.exe"
Write-Host "  $releaseDir\update-manifest.json"
Write-Host ""
Write-Host "Nothing has been published. See distribution\RELEASING.md for the next step." -ForegroundColor Yellow
