param(
    [Parameter(Mandatory = $true)][int]$ApplicationProcessId,
    [Parameter(Mandatory = $true)][string]$ObserverDirectory,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Phase,
    [Parameter(Mandatory = $true)][string]$JdkDirectory
)

# Read-only JVM diagnostics around the observer's interval, never during it. No UI input.
# HotSpot perf counters remain available when the packaged runtime omits java.management.
$ErrorActionPreference = 'Stop'
$jcmd = Join-Path $JdkDirectory 'bin/jcmd.exe'
$directory = (Resolve-Path -LiteralPath $ObserverDirectory).Path
$resultPath = Join-Path $directory "$Phase.txt"
if (Test-Path -LiteralPath $resultPath) { throw "Refusing to reuse existing phase $Phase" }
$application = Get-Process -Id $ApplicationProcessId

function Read-CompilerCounters([string]$Name) {
    $lines = & $jcmd $ApplicationProcessId PerfCounter.print
    if ($LASTEXITCODE -ne 0) { throw 'HotSpot compiler counters are unavailable.' }
    $lines | Set-Content -LiteralPath (Join-Path $directory "$Phase-$Name-perf.txt")
    $counters = @{}
    foreach ($line in $lines) {
        if ($line -match '^(sun\.ci\.(standardTime|osrTime|totalCompiles)|sun\.os\.hrt\.frequency)=(\d+)$') {
            $counters[$Matches[1]] = [long]$Matches[3]
        }
    }
    if ($counters.Count -ne 4 -or $counters['sun.os.hrt.frequency'] -le 0) {
        throw 'Required HotSpot compiler counters are missing.'
    }
    return $counters
}

$before = Read-CompilerCounters 'before'
$Phase | Set-Content -LiteralPath (Join-Path $directory 'phase')
$timer = [Diagnostics.Stopwatch]::StartNew()
while (-not (Test-Path -LiteralPath $resultPath)) {
    $application.Refresh()
    if ($application.HasExited) { throw 'Application exited before completing the phase.' }
    if ($timer.Elapsed.TotalSeconds -gt 30) { throw "Observer timed out for phase $Phase" }
    Start-Sleep -Milliseconds 200
}
$result = Get-Content -LiteralPath $resultPath -Raw
if ($result -notmatch "\bpid=$ApplicationProcessId\b") { throw 'Observer result belongs to a different application.' }
$after = Read-CompilerCounters 'after'
if ($before['sun.os.hrt.frequency'] -ne $after['sun.os.hrt.frequency']) { throw 'HotSpot clock frequency changed.' }
$deltaTicks = ($after['sun.ci.standardTime'] + $after['sun.ci.osrTime']) -
    ($before['sun.ci.standardTime'] + $before['sun.ci.osrTime'])
$deltaCompiles = $after['sun.ci.totalCompiles'] - $before['sun.ci.totalCompiles']
if ($deltaTicks -lt 0 -or $deltaCompiles -lt 0) { throw 'HotSpot counters moved backwards.' }
$summary = [pscustomobject]@{
    Phase = $Phase
    ApplicationProcessId = $ApplicationProcessId
    CompilerElapsedMilliseconds = 1000.0 * $deltaTicks / $after['sun.os.hrt.frequency']
    Compilations = $deltaCompiles
    Note = 'Compiler counters bracket the CPU interval; this is aggregate compiler elapsed time, not process CPU.'
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory "$Phase-jit.json")
$result.Trim()
$summary | ConvertTo-Json
