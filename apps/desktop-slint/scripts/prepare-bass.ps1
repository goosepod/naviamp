param(
    [Parameter(Mandatory = $false)]
    [string] $BassDownloadsDir = "$env:USERPROFILE\Downloads\BASS",

    [Parameter(Mandatory = $false)]
    [string] $TargetDir = "$PSScriptRoot\..\vendor\bass\windows-x64"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $BassDownloadsDir)) {
    throw "BASS downloads folder not found: $BassDownloadsDir"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

$packages = @(
    "bass24.zip",
    "bass_ssl.zip",
    "bass_aac24.zip",
    "bassflac24.zip",
    "bassopus24.zip",
    "bassalac24.zip",
    "bassape24.zip",
    "bassdsd24.zip",
    "bass_mpc24.zip",
    "basshls24.zip",
    "basswebm24.zip",
    "bassmidi24.zip",
    "bassmix24.zip",
    "bass_fx24.zip",
    "basswm24.zip"
)

foreach ($package in $packages) {
    $zipPath = Join-Path $BassDownloadsDir $package
    if (-not (Test-Path -LiteralPath $zipPath)) {
        Write-Warning "Skipping missing package: $package"
        continue
    }

    $zip = [IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $entry = $zip.Entries |
            Where-Object { $_.FullName -match '^x64/[^/]+\.dll$' } |
            Select-Object -First 1

        if (-not $entry) {
            $entry = $zip.Entries |
                Where-Object { $_.FullName -match '^[^/]+\.dll$' } |
                Select-Object -First 1
        }

        if (-not $entry) {
            Write-Warning "No DLL found in package: $package"
            continue
        }

        $targetPath = Join-Path $TargetDir ([IO.Path]::GetFileName($entry.FullName))
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $targetPath, $true)
        Write-Host "Copied $($entry.FullName) from $package"
    }
    finally {
        $zip.Dispose()
    }
}

Write-Host "BASS runtime ready: $TargetDir"
