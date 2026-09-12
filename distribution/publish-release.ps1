#Requires -Version 5.1
<#
.SYNOPSIS
    OPTIONAL, explicit publish step for a release already prepared by prepare-release.ps1.
    Requires the GitHub CLI (`gh`) installed and authenticated. NEVER run automatically as part of
    any other script - this is the one deliberate, manual action that actually makes a release
    public. Never embeds, prints, or logs a token; `gh` handles its own stored auth.

.PARAMETER Publish
    Required safety switch - running this script with no arguments does nothing but print what it
    WOULD do, so a stray invocation can never accidentally publish.
#>
param(
    [switch]$Publish
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$gradlePropsPath = Join-Path $scriptDir "..\gradle.properties"
$modVersionLine = Get-Content $gradlePropsPath | Where-Object { $_ -match '^\s*mod_version\s*=' } | Select-Object -First 1
$version = ($modVersionLine -split '=', 2)[1].Trim()
$tag = "v$version"
$releaseDir = Join-Path $scriptDir "release\$tag"

$exePath = Join-Path $releaseDir "GZ-Companion-Setup.exe"
$manifestPath = Join-Path $releaseDir "update-manifest.json"
if (-not (Test-Path $exePath) -or -not (Test-Path $manifestPath)) {
    throw "Release bundle not found at $releaseDir. Run .\prepare-release.ps1 first."
}

$isPrerelease = $version -match '-alpha\.|-beta\.'
Write-Host "Tag:        $tag"
Write-Host "Prerelease: $isPrerelease"
Write-Host "Assets:     $exePath, $manifestPath"

if (-not $Publish) {
    Write-Host ""
    Write-Host "DRY RUN (no -Publish flag given). Nothing was published." -ForegroundColor Yellow
    Write-Host "Re-run with -Publish to actually create the GitHub Release." -ForegroundColor Yellow
    exit 0
}

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    throw "GitHub CLI ('gh') was not found on PATH. Install it, or publish manually via the GitHub web UI - see distribution\RELEASING.md."
}

$authStatus = & gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "gh is not authenticated. Run 'gh auth login' first, or publish manually - see distribution\RELEASING.md."
}

$notes = (Get-Content $manifestPath -Raw | ConvertFrom-Json).notes -join "`n- "
$notesBody = "## Nytt`n- $notes"

Write-Host "Creating GitHub Release $tag..." -ForegroundColor Cyan
$releaseArgs = @("release", "create", $tag, $exePath, $manifestPath, "--title", $tag, "--notes", $notesBody)
if ($isPrerelease) { $releaseArgs += "--prerelease" }

& gh @releaseArgs
if ($LASTEXITCODE -ne 0) { throw "gh release create failed." }

Write-Host ""
Write-Host "Published $tag." -ForegroundColor Green
