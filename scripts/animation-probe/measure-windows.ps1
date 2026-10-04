param(
    [Parameter(Mandatory = $true)][int[]]$ApplicationProcessIds,
    [ValidateRange(5, 60)][int]$SampleCount = 15
)

# Read-only sampling. Keep the app visible and the same size, scale, refresh rate, power plan,
# track and layout between runs. CPU 100% means one core. GPU values sum engine utilization;
# they are useful for matching runs, not a percentage of the whole GPU's capacity.
# For a jpackage launcher, include its child application PID as well as the wrapper PID.
$ErrorActionPreference = 'Stop'
$appProcesses = @(Get-Process -Id $ApplicationProcessIds)
if ($appProcesses.Count -ne $ApplicationProcessIds.Count) { throw 'An application process is missing.' }
$dwmIds = @(Get-Process dwm | Select-Object -ExpandProperty Id)
$beforeValues = @($appProcesses | ForEach-Object {
    if ($null -eq $_.CPU) { throw "Application CPU counter is unavailable for PID $($_.Id)." }
    $_.CPU
})
$beforeCpu = ($beforeValues | Measure-Object -Sum).Sum
$timer = [Diagnostics.Stopwatch]::StartNew()
$counters = Get-Counter @('\GPU Engine(*)\Utilization Percentage', '\Process(dwm)\% Processor Time') `
    -SampleInterval 1 -MaxSamples $SampleCount
$elapsed = $timer.Elapsed.TotalSeconds
$afterValues = @(Get-Process -Id $ApplicationProcessIds | ForEach-Object {
    if ($null -eq $_.CPU) { throw "Application CPU counter is unavailable for PID $($_.Id)." }
    $_.CPU
})
$afterCpu = ($afterValues | Measure-Object -Sum).Sum
$gpuSamples = @($counters | ForEach-Object {
    $appGpu = 0.0
    $dwmGpu = 0.0
    foreach ($sample in $_.CounterSamples) {
        if ($sample.Status -ne 0) { throw "Invalid performance counter: $($sample.Path)" }
        if ($sample.InstanceName -match '^pid_(\d+)_') {
            $counterProcess = [int]$Matches[1]
            if ($counterProcess -in $ApplicationProcessIds) { $appGpu += $sample.CookedValue }
            if ($counterProcess -in $dwmIds) { $dwmGpu += $sample.CookedValue }
        }
    }
    [pscustomobject]@{ App = $appGpu; Dwm = $dwmGpu }
})
$dwmCpu = @($counters.CounterSamples | Where-Object { $_.Path -like '*\process(dwm)\% processor time' })
if (!$dwmCpu.Count) { throw 'DWM CPU counters are unavailable.' }
[pscustomobject]@{
    Seconds = $elapsed
    AppCpuOneCorePercent = 100 * ($afterCpu - $beforeCpu) / $elapsed
    DwmCpuOneCorePercent = ($dwmCpu | Measure-Object CookedValue -Average).Average
    AppGpuEngineSumMean = ($gpuSamples | Measure-Object App -Average).Average
    DwmGpuEngineSumMean = ($gpuSamples | Measure-Object Dwm -Average).Average
    GpuSamples = $gpuSamples.Count
} | ConvertTo-Json
