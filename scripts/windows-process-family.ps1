# Pure ancestry selection is separate from the Windows process/handle boundary for regression tests.
function Get-OwnedProcessRecords([int]$RootProcessId, [datetime]$RootStartTime, $NativeProcesses) {
    if ($RootProcessId -le 0) { throw 'An application root PID is required.' }
    $births = @{}
    $births[$RootProcessId] = $RootStartTime.ToUniversalTime()
    $owned = @()
    do {
        $added = $false
        foreach ($record in $NativeProcesses) {
            $childId = [int]$record.ProcessId
            $parentId = [int]$record.ParentProcessId
            if ($childId -le 0 -or $births.ContainsKey($childId) -or -not $births.ContainsKey($parentId)) { continue }
            if ($null -eq $record.CreationDate) { continue }
            $created = ([datetime]$record.CreationDate).ToUniversalTime()
            # A reused PID can be recorded as the parent of an older, unrelated process.
            if ($created -lt $births[$parentId]) { continue }
            $births[$childId] = $created
            $owned += $record
            $added = $true
        }
    } while ($added)
    $owned
}

function Get-ApplicationProcessFamily($RootProcess) {
    $rootStart = $RootProcess.StartTime.ToUniversalTime()
    $records = @(Get-OwnedProcessRecords $RootProcess.Id $rootStart @(Get-CimInstance Win32_Process))
    $family = @($RootProcess)
    foreach ($record in $records) {
        $candidate = Get-Process -Id ([int]$record.ProcessId) -ErrorAction Stop
        # Retain the native process handle before checking its identity. Cleanup uses this object,
        # never a bare PID that might be recycled after the application exits.
        $null = $candidate.Handle
        $difference = ($candidate.StartTime.ToUniversalTime() - ([datetime]$record.CreationDate).ToUniversalTime()).TotalMilliseconds
        if ([math]::Abs($difference) -gt 1) { throw 'Application process identity changed during discovery.' }
        $family += $candidate
    }
    $family
}

function Stop-OwnedApplicationProcess($OwnedProcess) {
    if ($OwnedProcess.HasExited) { return }
    $OwnedProcess.CloseMainWindow() | Out-Null
    if (-not $OwnedProcess.WaitForExit(5000)) {
        # Get-ApplicationProcessFamily retained the original native handle, including for children.
        Stop-Process -InputObject $OwnedProcess -Force
    }
}
