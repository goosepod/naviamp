$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../windows-process-family.ps1')
function Assert-Ids($Actual, $Expected) {
    if (($Actual -join ',') -ne ($Expected -join ',')) { throw "Unexpected process IDs: $($Actual -join ',')" }
}
function Record([int]$Id, [int]$Parent, [int]$Seconds) {
    [pscustomobject]@{ProcessId=$Id; ParentProcessId=$Parent; CreationDate=$epoch.AddSeconds($Seconds)}
}
$epoch = [datetime]'2026-01-01T00:00:00Z'
# Out-of-order descendants are followed, but older processes with recycled parent PIDs are not.
$records = @(
    (Record 40 20 30), (Record 30 10 1), (Record 31 30 40),
    (Record 20 10 20), (Record 41 20 15), (Record 10 1 10),
    [pscustomobject]@{ProcessId=50; ParentProcessId=10; CreationDate=$null}
)
$selected = @(Get-OwnedProcessRecords 10 $epoch.AddSeconds(10) $records)
Assert-Ids @($selected.ProcessId) @(20,40)
Assert-Ids @(Get-OwnedProcessRecords 10 $epoch.AddSeconds(50) $records) @()
$rejected = $false
try { Get-OwnedProcessRecords 0 $epoch $records } catch { $rejected = $true }
if (-not $rejected) { throw 'Missing application PID was accepted.' }
# A PID reused between snapshot and handle acquisition cannot enter the owned family.
function Get-CimInstance { @(Record 20 10 20) }
function Get-Process {
    param($Id, $ErrorAction)
    [pscustomobject]@{Id=$Id; Handle=1; StartTime=$epoch.AddSeconds(50)}
}
$root = [pscustomobject]@{Id=10; StartTime=$epoch.AddSeconds(10)}
$rejected = $false
try { Get-ApplicationProcessFamily $root } catch { $rejected = $true }
if (-not $rejected) { throw 'A recycled descendant PID was accepted.' }
# Cleanup receives the retained object rather than issuing a kill by numeric PID.
$script:stopped = $null
function Stop-Process { param($InputObject, [switch]$Force) $script:stopped = $InputObject }
$owned = [pscustomobject]@{Id=20; HasExited=$false}
$owned | Add-Member ScriptMethod CloseMainWindow { $false }
$owned | Add-Member ScriptMethod WaitForExit { param($Milliseconds) $false }
Stop-OwnedApplicationProcess $owned
if (-not [object]::ReferenceEquals($owned, $script:stopped)) { throw 'Cleanup lost the owned process identity.' }
$script:stopped = $null
$owned.HasExited = $true
Stop-OwnedApplicationProcess $owned
if ($null -ne $script:stopped) { throw 'Cleanup attempted to stop an exited process.' }
Write-Host 'Windows process ownership regressions passed.'
