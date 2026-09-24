<#
.SYNOPSIS
    Builds HelloApplication for Windows so it runs without an IDE or a Java install.

.DESCRIPTION
    1. mvnw javafx:jlink  -> target\app, a Java runtime with only the modules the app needs
    2. jpackage           -> target\installer\...

    -Type app-image  A folder with HelloApplication.exe. Zip it and share it; no installer needed.
    -Type exe        A setup .exe that adds Start-menu and desktop shortcuts. Needs the WiX Toolset.
    -Type msi        Same, as an .msi.

    Installed, the app keeps its data in %LOCALAPPDATA%\HelloApplication\data (see AppPaths.java).

.EXAMPLE
    .\package-windows.ps1                  # exe if WiX is installed, otherwise app-image
    .\package-windows.ps1 -Type app-image
    .\package-windows.ps1 -Type exe -SkipTests
#>
param(
    [ValidateSet('auto', 'app-image', 'exe', 'msi')]
    [string]$Type = 'auto',
    [string]$Version = '1.0.0',
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$AppName = 'HelloApplication'
$MainModule = 'com.example.helloapplication/com.example.helloapplication.Launcher'
# Keep this fixed: it's how Windows knows a new installer upgrades the old install.
$UpgradeUuid = '5f3f83b6-bf6a-45b1-b4a7-dd26b595974c'

# --- Find a JDK with jpackage -------------------------------------------------
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\jpackage.exe'))) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\jpackage.exe') } |
            Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdk) {
        throw 'No JDK with jpackage found. Set JAVA_HOME to a JDK 21 or newer.'
    }
    $env:JAVA_HOME = $jdk.FullName
}
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
Write-Host "Using JDK: $env:JAVA_HOME"

# --- Pick the package type ----------------------------------------------------
$hasWix = [bool](Get-Command wix, candle -ErrorAction SilentlyContinue)
if ($Type -eq 'auto') {
    $Type = if ($hasWix) { 'exe' } else { 'app-image' }
}
if ($Type -ne 'app-image' -and -not $hasWix) {
    throw "Building a .$Type installer needs the WiX Toolset (https://wixtoolset.org). Install it, or run with -Type app-image."
}

# --- 1. Runtime image ---------------------------------------------------------
$goals = if ($SkipTests) { @('-DskipTests', 'clean', 'javafx:jlink') } else { @('clean', 'test', 'javafx:jlink') }
& .\mvnw.cmd -q @goals
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

# --- 2. Package ---------------------------------------------------------------
$dest = 'target\installer'
$jpackageArgs = @(
    '--type', $Type,
    '--name', $AppName,
    '--app-version', $Version,
    '--vendor', 'Curtis Foster',
    '--description', 'Import linked data files and explore them without SQL.',
    '--runtime-image', 'target\app',
    '--module', $MainModule,
    '--dest', $dest,
    # Same JVM options as javafx:run in pom.xml.
    '--java-options', '--enable-native-access=javafx.graphics',
    '--java-options', '--enable-native-access=org.xerial.sqlitejdbc',
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--java-options', '--sun-misc-unsafe-memory-access=allow'
)
if ($Type -ne 'app-image') {
    $jpackageArgs += @(
        '--win-per-user-install',  # installs under %LOCALAPPDATA%, no admin rights needed
        '--win-menu', '--win-menu-group', $AppName,
        '--win-shortcut',
        '--win-upgrade-uuid', $UpgradeUuid
    )
}

& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

Write-Host ''
if ($Type -eq 'app-image') {
    $zip = "target\$AppName-$Version-windows.zip"
    Compress-Archive -Path "$dest\$AppName" -DestinationPath $zip -Force
    Write-Host "Done: $dest\$AppName\$AppName.exe"
    Write-Host "      $zip  (unzip anywhere and run $AppName.exe)"
} else {
    Get-ChildItem $dest -Filter "*.$Type" | ForEach-Object { Write-Host "Done: $($_.FullName)" }
}
