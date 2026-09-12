param(
    [double]$MaximumCpuPercent = 15.0,
    [int]$StartupSeconds = 15,
    [int]$SettleSeconds = 20,
    [int]$SampleSeconds = 15,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$profileRoot = Join-Path $repositoryRoot "build/performance/windows-idle-$([guid]::NewGuid().ToString('N'))"
$roamingProfile = Join-Path $profileRoot 'roaming'
$localProfile = Join-Path $profileRoot 'local'
$applicationProcess = $null

try {
    Set-Location $repositoryRoot

    if (-not $SkipBuild) {
        & .\gradlew.bat `
            '-Pkotlin.native.enableKlibsCrossCompilation=false' `
            '-Pnaviamp.bass.platform=windows-x64' `
            :apps:desktop:createDistributable
        if ($LASTEXITCODE -ne 0) {
            throw "Windows distributable build failed with exit code $LASTEXITCODE."
        }
    }

    New-Item -ItemType Directory -Force -Path $roamingProfile, $localProfile | Out-Null
    $env:APPDATA = $roamingProfile
    $env:LOCALAPPDATA = $localProfile
    Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue

    $executable = Resolve-Path 'apps/desktop/build/compose/binaries/main/app/Naviamp/Naviamp.exe'
    $applicationProcess = Start-Process `
        -FilePath $executable `
        -WorkingDirectory (Split-Path $executable) `
        -PassThru

    # MainWindowHandle is not reliably exposed by jpackage launchers on non-interactive CI
    # desktops. A stable launcher process plus the sampling window is the portable readiness gate.
    Start-Sleep -Seconds $StartupSeconds
    $applicationProcess.Refresh()
    if ($applicationProcess.HasExited) {
        throw "Naviamp exited during startup with code $($applicationProcess.ExitCode)."
    }

    Start-Sleep -Seconds $SettleSeconds
    $applicationProcess.Refresh()
    $cpuStartSeconds = $applicationProcess.CPU
    Start-Sleep -Seconds $SampleSeconds
    $applicationProcess.Refresh()
    $cpuPercent = (($applicationProcess.CPU - $cpuStartSeconds) / $SampleSeconds) * 100.0
    $roundedCpuPercent = [math]::Round($cpuPercent, 1)

    Write-Host "Windows idle CPU: $roundedCpuPercent% of one logical core (limit: $MaximumCpuPercent%)."
    if ($cpuPercent -gt $MaximumCpuPercent) {
        throw "Windows idle CPU exceeded the $MaximumCpuPercent% regression limit."
    }
} finally {
    if ($null -ne $applicationProcess -and -not $applicationProcess.HasExited) {
        $applicationProcess.CloseMainWindow() | Out-Null
        if (-not $applicationProcess.WaitForExit(5000)) {
            Stop-Process -Id $applicationProcess.Id -Force
        }
    }
    if (Test-Path -LiteralPath $profileRoot) {
        Remove-Item -LiteralPath $profileRoot -Recurse -Force
    }
}
