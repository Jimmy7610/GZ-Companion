#Requires -Version 5.1
<#
.SYNOPSIS
    Builds GZ-Companion-Setup.exe: the single-file, self-contained Windows installer for GZ Companion.

.DESCRIPTION
    1. Builds the GZ Companion mod jar via the main Gradle project (.\gradlew.bat build).
    2. Copies that jar into the installer's Assets folder as an embedded resource.
    3. Publishes GZCompanion.Installer.App as a self-contained, single-file win-x64 executable -
       the end user needs no .NET runtime, no Java, nothing beyond Windows itself.
    4. Copies the published exe to distribution\dist\GZ-Companion-Setup.exe and prints its SHA-256.

    Run from anywhere; paths are resolved relative to this script's own location.
#>
param(
    [switch]$SkipModBuild
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..")
$distributionDir = Join-Path $repoRoot "distribution"
$installerDir = $scriptDir
$appProject = Join-Path $installerDir "GZCompanion.Installer.App\GZCompanion.Installer.App.csproj"
$assetsDir = Join-Path $installerDir "GZCompanion.Installer.App\Assets"
$distDir = Join-Path $distributionDir "dist"

Write-Host "== 1. Building GZ Companion mod jar ==" -ForegroundColor Cyan
if (-not $SkipModBuild) {
    Push-Location $repoRoot
    try {
        & ".\gradlew.bat" build --console=plain
        if ($LASTEXITCODE -ne 0) { throw "gradlew.bat build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Skipped (-SkipModBuild)." -ForegroundColor Yellow
}

$modJar = Get-ChildItem (Join-Path $repoRoot "build\libs") -Filter "gzcompanion-*.jar" |
    Where-Object { $_.Name -notlike "*-sources.jar" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $modJar) { throw "Could not find a built gzcompanion-*.jar under build\libs. Run .\gradlew.bat build first." }
Write-Host "Using mod jar: $($modJar.FullName)"

Write-Host "== 2. Embedding mod jar into installer Assets ==" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null
Copy-Item $modJar.FullName (Join-Path $assetsDir "gzcompanion.jar") -Force

$modJarHash = (Get-FileHash $modJar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Mod jar SHA-256: $modJarHash"
$manifestJar = (Get-Content (Join-Path $distributionDir "compatibility.json") -Raw | ConvertFrom-Json).supported[0].companionJar.sha256
if ($modJarHash -ne $manifestJar) {
    Write-Warning "The freshly-built mod jar's SHA-256 ($modJarHash) does not match distribution\compatibility.json ($manifestJar)."
    Write-Warning "If you intentionally changed the mod, update compatibility.json's companionJar.sha256/sizeBytes before publishing."
}

Write-Host "== 3. Publishing self-contained single-file installer ==" -ForegroundColor Cyan
$publishDir = Join-Path $installerDir "GZCompanion.Installer.App\bin\Release\net10.0-windows\win-x64\publish"
dotnet publish $appProject -c Release -r win-x64 --self-contained true `
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true
if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed with exit code $LASTEXITCODE" }

Write-Host "== 4. Copying final installer to distribution\dist ==" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$finalExe = Join-Path $distDir "GZ-Companion-Setup.exe"
Copy-Item (Join-Path $publishDir "GZ-Companion-Setup.exe") $finalExe -Force

$hash = (Get-FileHash $finalExe -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item $finalExe).Length
Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "  Path:   $finalExe"
Write-Host "  Size:   $size bytes"
Write-Host "  SHA256: $hash"
