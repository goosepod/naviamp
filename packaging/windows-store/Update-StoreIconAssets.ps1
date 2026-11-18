[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $Layout,

    [string] $Icon = (Join-Path $PSScriptRoot '../../platforms/desktop/src/desktopMain/resources/icons/naviamp.png'),

    [string] $MakePri = 'makepri.exe'
)

$ErrorActionPreference = 'Stop'
$layoutPath = (Resolve-Path -LiteralPath $Layout).ProviderPath
$manifest = Join-Path $layoutPath 'AppxManifest.xml'
[xml] $xml = Get-Content -LiteralPath $manifest -Raw
$namespaces = [System.Xml.XmlNamespaceManager]::new($xml.NameTable)
$namespaces.AddNamespace('appx', 'http://schemas.microsoft.com/appx/manifest/foundation/windows10')
$namespaces.AddNamespace('uap', 'http://schemas.microsoft.com/appx/manifest/uap/windows10')
$visual = $xml.SelectSingleNode(
    "/appx:Package/appx:Applications/appx:Application[@Id='Naviamp']/uap:VisualElements",
    $namespaces
)
if (-not $visual -or $visual.BackgroundColor -ne 'transparent') {
    throw 'The Naviamp MSIX manifest must declare a transparent VisualElements background.'
}

$logo = Get-Item -LiteralPath (Join-Path $layoutPath $visual.Square44x44Logo)
$stem = [IO.Path]::GetFileNameWithoutExtension($logo.Name)
$assetDirectory = $logo.DirectoryName

Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Bitmap]::new((Resolve-Path -LiteralPath $Icon).ProviderPath)
try {
    foreach ($size in 16, 20, 24, 30, 32, 36, 40, 44, 48, 60, 64, 72, 80, 96, 256) {
        $image = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($image)
            try {
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.DrawImage($source, 0, 0, $size, $size)
            } finally {
                $graphics.Dispose()
            }

            foreach ($variation in '', '_altform-unplated', '_altform-lightunplated') {
                $path = Join-Path $assetDirectory "$stem.targetsize-$size$variation.png"
                $image.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
            }
        } finally {
            $image.Dispose()
        }
    }
} finally {
    $source.Dispose()
}

# Index the wrapper artwork. The app image contains dependency filenames that MakePri
# would otherwise parse as resource qualifiers.
$work = Join-Path ([IO.Path]::GetTempPath()) "Naviamp-PRI-$([Guid]::NewGuid())"
$project = Join-Path $work 'project'
$artwork = Join-Path $project ([IO.Path]::GetDirectoryName($visual.Square44x44Logo))
$config = Join-Path $work 'priconfig.xml'
$index = Join-Path $work 'resources.pri'
try {
    New-Item -ItemType Directory -Path $artwork -Force | Out-Null
    Get-ChildItem -LiteralPath $assetDirectory -File | Copy-Item -Destination $artwork
    Copy-Item -LiteralPath $manifest -Destination $project
    & $MakePri createconfig /cf $config /dq en-US
    if ($LASTEXITCODE -ne 0) { throw "MakePri createconfig failed ($LASTEXITCODE)." }
    & $MakePri new /pr $project /cf $config /mn (Join-Path $project 'AppxManifest.xml') /of $index
    if ($LASTEXITCODE -ne 0) { throw "MakePri new failed ($LASTEXITCODE)." }
    Move-Item -LiteralPath $index -Destination (Join-Path $layoutPath 'resources.pri') -Force
} finally {
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
}
