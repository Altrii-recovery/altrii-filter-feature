#!/usr/bin/env pwsh

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ScriptArgs
)

$ErrorActionPreference = 'Stop'

$AppHome = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
$propertiesPath = Join-Path $AppHome 'gradle/wrapper/gradle-wrapper.properties'

if (-not (Test-Path -Path $propertiesPath)) {
    Write-Error "Unable to locate $propertiesPath"
    exit 1
}

$props = @{}
Get-Content -Path $propertiesPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line -split '=', 2
        if ($parts.Length -eq 2) {
            $props[$parts[0]] = $parts[1]
        }
    }
}

if (-not $props.ContainsKey('distributionUrl')) {
    Write-Error 'distributionUrl is not defined in gradle-wrapper.properties'
    exit 1
}

$distUrl = $props['distributionUrl'].Replace('\', '')
$distBase = if ($props.ContainsKey('distributionBase')) { $props['distributionBase'] } else { 'GRADLE_USER_HOME' }
$distPath = if ($props.ContainsKey('distributionPath')) { $props['distributionPath'] } else { 'wrapper/dists' }

function Resolve-BaseDir {
    param([string]$Base)
    switch ($Base) {
        'GRADLE_USER_HOME' {
            if ($env:GRADLE_USER_HOME) { return $env:GRADLE_USER_HOME }
            else { return (Join-Path $env:USERPROFILE '.gradle') }
        }
        'PROJECT' { return $AppHome }
        'USER_HOME' { return $env:USERPROFILE }
        default {
            if ($env:GRADLE_USER_HOME) { return $env:GRADLE_USER_HOME }
            else { return (Join-Path $env:USERPROFILE '.gradle') }
        }
    }
}

$baseDir = Resolve-BaseDir $distBase
$installRoot = Join-Path $baseDir ($distPath -replace '/', [IO.Path]::DirectorySeparatorChar)
$distName = [IO.Path]::GetFileNameWithoutExtension($distUrl)
$distDir = Join-Path $installRoot $distName

if (-not (Test-Path -Path $distDir)) {
    New-Item -ItemType Directory -Force -Path $installRoot | Out-Null
    $tmpDir = New-Item -ItemType Directory -Path (Join-Path $installRoot ('.tmp-' + [guid]::NewGuid().ToString()))
    try {
        $zipPath = Join-Path $tmpDir 'dist.zip'
        if (Get-Command -Name 'Invoke-WebRequest' -ErrorAction SilentlyContinue) {
            Invoke-WebRequest -UseBasicParsing -Uri $distUrl -OutFile $zipPath | Out-Null
        } else {
            (New-Object System.Net.WebClient).DownloadFile($distUrl, $zipPath)
        }
        Expand-Archive -LiteralPath $zipPath -DestinationPath $tmpDir -Force
        $extracted = Get-ChildItem -Directory -Path $tmpDir | Select-Object -First 1
        if (-not $extracted) {
            throw 'Gradle distribution archive did not contain the expected directory.'
        }
        if (Test-Path -Path $distDir) {
            Remove-Item -Recurse -Force $distDir
        }
        Move-Item -Path $extracted.FullName -Destination $distDir
    }
    finally {
        if (Test-Path -Path $tmpDir) {
            Remove-Item -Recurse -Force $tmpDir
        }
    }
}

$gradleBat = Join-Path $distDir 'bin/gradle.bat'
if (-not (Test-Path -Path $gradleBat)) {
    Write-Error "Gradle executable not found at $gradleBat"
    exit 1
}

& $gradleBat @ScriptArgs
exit $LASTEXITCODE
