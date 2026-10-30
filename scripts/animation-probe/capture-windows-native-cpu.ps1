param(
    [Parameter(Mandatory = $true)][int]$ApplicationProcessId,
    [ValidateRange(5, 30)][int]$Seconds = 12
)

# Run in an administrator PowerShell; the disposable app itself must remain unelevated.
# No elevation prompt, OS policy change or UI input is performed by this helper.
$ErrorActionPreference = 'Stop'
$principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Kernel CPU profiling requires an administrator PowerShell. Leave the test app running normally.'
}
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$target = Get-Process -Id $ApplicationProcessId
$nativeProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $ApplicationProcessId"
$executable = $nativeProcess.ExecutablePath
if ($nativeProcess.Name -ne 'Naviamp.exe' -or [string]::IsNullOrEmpty($executable) -or
    -not ($executable.StartsWith((Join-Path $repository 'build/'), [StringComparison]::OrdinalIgnoreCase) -or
        $executable.StartsWith((Join-Path $repository 'apps/desktop/build/'), [StringComparison]::OrdinalIgnoreCase))) {
    throw 'Target must be a disposable Naviamp build in this checkout, not the installed app.'
}
$systemDirectory = if ([Environment]::Is64BitProcess) { 'System32' } else { 'Sysnative' }
$recorder = Join-Path $env:WINDIR "$systemDirectory/wpr.exe"
if (-not (Test-Path -LiteralPath $recorder)) { throw 'Native Windows Performance Recorder is unavailable.' }
$instance = 'Naviamp-' + [guid]::NewGuid().ToString('N')
$directory = Join-Path $repository "build/windows-native-cpu/$instance"
New-Item -ItemType Directory -Path $directory | Out-Null
[pscustomobject]@{
    ApplicationProcessId = $ApplicationProcessId
    ApplicationStartTime = $target.StartTime.ToString('o')
    Executable = $executable
    RequestedSeconds = $Seconds
    Instance = $instance
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'target.json')
$trace = Join-Path $directory 'cpu.etl'
& $recorder -start CPU -filemode -recordtempto $directory -instancename $instance
if ($LASTEXITCODE -ne 0) { throw "WPR could not start (exit $LASTEXITCODE). No existing session is canceled." }
# Bracket the requested interval separately from WPR setup and merge/rundown.
$recordingStarted = [DateTimeOffset]::Now
try {
    Start-Sleep -Seconds $Seconds
} finally {
    $recordingStopped = [DateTimeOffset]::Now
    & $recorder -stop $trace -instancename $instance
    if ($LASTEXITCODE -ne 0) {
        & $recorder -cancel -instancename $instance
        throw 'WPR could not save the trace; cancellation was limited to this helper session.'
    }
    [pscustomobject]@{
        RecordingStarted = $recordingStarted.ToString('o')
        RecordingStopped = $recordingStopped.ToString('o')
        Note = 'Requested sleep interval; ETL also includes WPR setup and rundown.'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'recording-window.json')
}
$target.Refresh()
if ($target.HasExited) { throw "Target exited during recording; retain the rejected trace at $trace" }
Write-Output "Local CPU trace: $trace"
