# Explicit local test lifecycle. Never called by release or Store packaging CI.
# The caller must have authorized temporary machine certificate trust.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Install', 'Cleanup')]
    [string]$Action,
    [Parameter(Mandatory = $true)]
    [string]$StateDirectory,
    [string]$UnsignedPackage,
    [string]$ExpectedUnsignedSha256,
    [string]$SignToolPath,
    [switch]$AllowTemporaryCertificateTrust
)

$ErrorActionPreference = 'Stop'
$statePath = Join-Path $StateDirectory 'test-install-state.json'

function Remove-TestInstallation {
    if (-not (Test-Path -LiteralPath $statePath)) { return }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $package = Get-AppxPackage -Name $state.identity
    if ($package) {
        if ($package.PackageFullName -ne $state.package_full_name) {
            throw 'Installed package differs from the recorded test; refusing to remove it.'
        }
        Get-Process | Where-Object {
            $_.Path -and $_.Path.StartsWith($package.InstallLocation + '\', [StringComparison]::OrdinalIgnoreCase)
        } | Stop-Process
        Remove-AppxPackage -Package $package.PackageFullName
    }
    $trusted = "Cert:\LocalMachine\TrustedPeople\$($state.thumbprint)"
    $personal = "Cert:\CurrentUser\My\$($state.thumbprint)"
    if (Test-Path -LiteralPath $trusted) { Remove-Item -LiteralPath $trusted }
    if (Test-Path -LiteralPath $personal) { Remove-Item -LiteralPath $personal -DeleteKey }
    if (Test-Path -LiteralPath $state.signed_copy) { Remove-Item -LiteralPath $state.signed_copy }
    $publicCertificate = Join-Path $StateDirectory 'test-signing.cer'
    if (Test-Path -LiteralPath $publicCertificate) { Remove-Item -LiteralPath $publicCertificate }
    if (Get-AppxPackage -Name $state.identity) { throw 'Test package remains installed.' }
    if ((Test-Path -LiteralPath $trusted) -or (Test-Path -LiteralPath $personal)) {
        throw 'Test certificate remains in the certificate store.'
    }
    $state | Add-Member -NotePropertyName cleaned_up_utc -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
    $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
    Write-Output 'Removed test package, signed copy, temporary trust and private signing key.'
}

if ($Action -eq 'Cleanup') {
    Remove-TestInstallation
    return
}

if (-not $AllowTemporaryCertificateTrust) { throw 'Explicit -AllowTemporaryCertificateTrust is required.' }
if (-not $ExpectedUnsignedSha256 -or -not $UnsignedPackage -or -not $SignToolPath) {
    throw 'Supply -UnsignedPackage, -ExpectedUnsignedSha256 and -SignToolPath.'
}
$config = Get-Content (Join-Path $PSScriptRoot '../apps/desktop/packaging/windows-store/release.json') -Raw | ConvertFrom-Json
if (Get-AppxPackage -Name $config.identity) { throw 'A Store package already exists; refusing to replace user data.' }
if (Test-Path -LiteralPath $StateDirectory) { throw 'Use a fresh -StateDirectory for each test lifecycle.' }
$actualHash = (Get-FileHash -LiteralPath $UnsignedPackage -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $ExpectedUnsignedSha256.ToLowerInvariant()) { throw 'Unsigned candidate checksum mismatch.' }
$toolSignature = Get-AuthenticodeSignature -LiteralPath $SignToolPath
if ($toolSignature.Status -ne 'Valid' -or $toolSignature.SignerCertificate.Subject -notmatch 'Microsoft Corporation') {
    throw 'Expected a valid Microsoft-signed SDK SignTool.'
}

New-Item -ItemType Directory -Path $StateDirectory | Out-Null
$signed = Join-Path $StateDirectory 'Naviamp-v2.9.0-windows-x64-LOCAL-TEST.msix'
Copy-Item -LiteralPath $UnsignedPackage -Destination $signed
$cert = New-SelfSignedCertificate -Type Custom -Subject $config.publisher `
    -FriendlyName 'Naviamp issue 323 temporary local test only' `
    -KeyUsage DigitalSignature -KeyAlgorithm RSA -KeyLength 2048 -HashAlgorithm SHA256 `
    -KeyExportPolicy NonExportable -CertStoreLocation 'Cert:\CurrentUser\My' `
    -NotAfter (Get-Date).AddDays(1) `
    -TextExtension @('2.5.29.37={text}1.3.6.1.5.5.7.3.3', '2.5.29.19={text}')
$state = [ordered]@{
    identity = $config.identity
    package_full_name = "$($config.identity)_$($config.msix_version)_x64__fc7t29wmhhnwe"
    thumbprint = $cert.Thumbprint
    signed_copy = $signed
    unsigned_sha256 = $actualHash
    created_utc = [DateTime]::UtcNow.ToString('o')
}
$state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8

try {
    & $SignToolPath sign /fd SHA256 /sha1 $cert.Thumbprint /s My $signed
    if ($LASTEXITCODE -ne 0) { throw 'Test package signing failed.' }
    $publicCertificate = Join-Path $StateDirectory 'test-signing.cer'
    Export-Certificate -Cert $cert -FilePath $publicCertificate | Out-Null
    Import-Certificate -FilePath $publicCertificate -CertStoreLocation 'Cert:\LocalMachine\TrustedPeople' | Out-Null
    & $SignToolPath verify /pa $signed
    if ($LASTEXITCODE -ne 0) { throw 'Test signature verification failed.' }
    Add-AppxPackage -Path $signed
    $package = Get-AppxPackage -Name $config.identity
    if (-not $package -or $package.PackageFullName -ne $state.package_full_name) {
        throw 'Installed package identity mismatch.'
    }
    if ((Get-FileHash -LiteralPath $UnsignedPackage -Algorithm SHA256).Hash.ToLowerInvariant() -ne $actualHash) {
        throw 'Unsigned upload candidate changed.'
    }
    $package | Select-Object Name, PackageFullName, InstallLocation, Status
    Write-Output "Temporary test certificate: $($cert.Thumbprint)"
    Write-Output "Run Cleanup with the same StateDirectory after acceptance testing."
} catch {
    Remove-TestInstallation
    throw
}
