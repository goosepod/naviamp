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
$measuredProcesses = @()

. (Join-Path $PSScriptRoot "windows-process-family.ps1")

function Get-ApplicationCpuSeconds($Processes) {
    $values = @($Processes | ForEach-Object {
        $_.Refresh()
        if ($_.HasExited -or $null -eq $_.CPU) { throw "Application CPU unavailable for PID $($_.Id)." }
        $_.CPU
    })
    ($values | Measure-Object -Sum).Sum
}

try {
    Set-Location $repositoryRoot

    if (-not $SkipBuild) {
        & .\gradlew.bat `
            '-Pkotlin.native.enableKlibsCrossCompilation=false' `
            '-Pnaviamp.bass.platform=windows-x64' `
            :apps:desktop:verifyDesktopDistributable
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
    # jpackage's launcher can be idle while its child JVM spins; include the complete family.
    $measuredProcesses = @(Get-ApplicationProcessFamily $applicationProcess)
    $cpuStartSeconds = Get-ApplicationCpuSeconds $measuredProcesses
    $timer = [Diagnostics.Stopwatch]::StartNew()
    Start-Sleep -Seconds $SampleSeconds
    $cpuEndSeconds = Get-ApplicationCpuSeconds $measuredProcesses
    $elapsedSeconds = $timer.Elapsed.TotalSeconds
    $afterIds = @(Get-ApplicationProcessFamily $applicationProcess | ForEach-Object Id)
    if (@(Compare-Object @($measuredProcesses.Id) $afterIds).Count) {
        throw 'Application process family changed during idle measurement.'
    }
    $cpuPercent = (($cpuEndSeconds - $cpuStartSeconds) / $elapsedSeconds) * 100.0
    $roundedCpuPercent = [math]::Round($cpuPercent, 1)

    Write-Host "Windows idle PIDs: $($measuredProcesses.Id -join ',')"
    Write-Host "Windows idle CPU: $roundedCpuPercent% of one logical core (limit: $MaximumCpuPercent%)."
    if ($cpuPercent -gt $MaximumCpuPercent) {
        throw "Windows idle CPU exceeded the $MaximumCpuPercent% regression limit."
    }
} finally {
    if ($null -ne $applicationProcess) { Stop-OwnedApplicationProcess $applicationProcess }
    foreach ($process in $measuredProcesses) { Stop-OwnedApplicationProcess $process }
    if (Test-Path -LiteralPath $profileRoot) {
        Remove-Item -LiteralPath $profileRoot -Recurse -Force
    }
}
