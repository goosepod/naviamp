[CmdletBinding()]
param(
    [string]$ReleaseZip,
    [string]$OutputDirectory,
    [string]$MakeAppxPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$metadata = Join-Path $repoRoot 'apps/desktop/packaging/windows-store'
$release = Get-Content (Join-Path $metadata 'release.json') -Raw | ConvertFrom-Json
$python = (Get-Command python -ErrorAction Stop).Source
$tool = Join-Path $PSScriptRoot 'windows-store-package.py'

if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot 'build/windows-store'
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$package = Join-Path $OutputDirectory "Naviamp-$($release.tag)-windows-x64-store.msix"
if (Test-Path $package) {
    throw "Output already exists: $package. Choose a fresh -OutputDirectory."
}

if (-not $MakeAppxPath) {
    $sdkRoot = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits/10/bin'
    $MakeAppxPath = Get-ChildItem "$sdkRoot/*/x64/makeappx.exe" -ErrorAction SilentlyContinue |
        Sort-Object { [version]$_.Directory.Parent.Name } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $MakeAppxPath -or -not (Test-Path $MakeAppxPath -PathType Leaf)) {
    throw 'Install the Windows 10/11 SDK or supply -MakeAppxPath to its x64 makeappx.exe.'
}

if (-not $ReleaseZip) {
    $ReleaseZip = Join-Path $OutputDirectory $release.asset
    & $python $tool download $ReleaseZip
    if ($LASTEXITCODE -ne 0) { throw 'Release download/checksum validation failed.' }
}

# Never rebuild the application or run Gradle here. Only wrap the published ZIP.
$layout = Join-Path $OutputDirectory ('layout-' + [Guid]::NewGuid().ToString('N'))
& $python $tool prepare $ReleaseZip $layout
if ($LASTEXITCODE -ne 0) { throw 'Release layout preparation failed.' }

# Do not use /nv: schema and semantic validation must remain enabled.
& $MakeAppxPath pack /d $layout /p $package /h SHA256 /no |
    Tee-Object -FilePath (Join-Path $OutputDirectory 'makeappx.log')
if ($LASTEXITCODE -ne 0) { throw 'MakeAppx package validation failed.' }

& $python $tool verify $ReleaseZip $package (Join-Path $OutputDirectory 'provenance.json')
if ($LASTEXITCODE -ne 0) { throw 'MSIX payload verification failed.' }
$hash = (Get-FileHash -Algorithm SHA256 -Path $package).Hash.ToLowerInvariant()
"$hash  $([IO.Path]::GetFileName($package))" |
    Set-Content -Encoding ascii (Join-Path $OutputDirectory 'SHA256SUMS')
Write-Host "Unsigned Store submission package: $package"
Write-Host "Developer deployment layout: $layout"
Write-Host 'Interactive Windows acceptance and Store certification are separate required checks.'
