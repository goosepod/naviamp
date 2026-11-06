param(
    [Parameter(Mandatory = $true)][string]$Executable,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(1, 10)][int]$Cycles = 3
)

# Run in the interactive desktop. Observe untouched startup before any resize/input/restore.
# Every cold/warm pair shares only its own disposable profile; never use the owner's profile.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class NaviampPresentationWindow {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct Point { public int X, Y; }
    [DllImport("kernel32.dll")] static extern IntPtr GetConsoleWindow();
    [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] static extern bool AttachThreadInput(uint first, uint second, bool attach);
    public static bool PrepareLauncher() {
        IntPtr console = GetConsoleWindow();
        if (console == IntPtr.Zero) return false;
        ShowWindow(console, 9);
        uint ignored;
        uint other = GetWindowThreadProcessId(GetForegroundWindow(), out ignored);
        uint current = GetCurrentThreadId();
        bool attached = current != other && AttachThreadInput(current, other, true);
        try { SetForegroundWindow(console); }
        finally { if (attached) AttachThreadInput(current, other, false); }
        return GetForegroundWindow() == console;
    }
    [DllImport("user32.dll")] static extern IntPtr GetWindow(IntPtr h, uint relation);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h, out Rect r);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("dwmapi.dll")] static extern int DwmGetWindowAttribute(IntPtr h, int attribute, out int value, int size);
    public static bool IsUnobscured(IntPtr h, int left, int top, int right, int bottom) {
        for (IntPtr above = GetWindow(h, 3); above != IntPtr.Zero; above = GetWindow(above, 3)) {
            int cloaked;
            if (!IsWindowVisible(above) || IsIconic(above)) continue;
            if (DwmGetWindowAttribute(above, 14, out cloaked, 4) == 0 && cloaked != 0) continue;
            Rect r;
            if (GetWindowRect(above, out r) && r.Left < right && r.Right > left && r.Top < bottom && r.Bottom > top) return false;
        }
        return true;
    }
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out Rect r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref Point p);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int command);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint message, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
'@
[void][NaviampPresentationWindow]::SetProcessDPIAware()
$exe = (Resolve-Path -LiteralPath $Executable).Path
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh output directory; previous evidence is preserved.' }
$output = New-Item -ItemType Directory -Path $OutputDirectory
$results = @()
$originalAppData = $env:APPDATA
$originalLocalData = $env:LOCALAPPDATA

function Save-Client([Diagnostics.Process]$Process, [string]$Name) {
    $Process.Refresh()
    $handle = $Process.MainWindowHandle
    if ($handle -eq [IntPtr]::Zero -or [NaviampPresentationWindow]::IsIconic($handle)) { throw 'No visible application window.' }
    [uint32]$foregroundOwner = 0
    [void][NaviampPresentationWindow]::GetWindowThreadProcessId([NaviampPresentationWindow]::GetForegroundWindow(), [ref]$foregroundOwner)
    $rect = New-Object NaviampPresentationWindow+Rect
    $point = New-Object NaviampPresentationWindow+Point
    if (-not [NaviampPresentationWindow]::GetClientRect($handle, [ref]$rect) -or
        -not [NaviampPresentationWindow]::ClientToScreen($handle, [ref]$point)) { throw 'Cannot locate client rectangle.' }
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    if ($width -lt 400 -or $height -lt 300) { throw 'Unexpected client geometry.' }
    # Windows may show a scheduled interactive app without granting focus. Do not activate it:
    # check native Z-order so no foreign surface can supply the captured client pixels.
    if (-not [NaviampPresentationWindow]::IsUnobscured($handle, $point.X, $point.Y, ($point.X + $width), ($point.Y + $height))) {
        throw 'Client is obscured by another native window; preserve evidence and rerun without moving the app.'
    }
    $bitmap = New-Object Drawing.Bitmap($width, $height)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($point.X, $point.Y, 0, 0, $bitmap.Size)
        $bitmap.Save((Join-Path $output.FullName "$Name.png"), [Drawing.Imaging.ImageFormat]::Png)
        # Exclude the client edges/decoration; a uniform blank surface must fail.
        $colors = New-Object 'Collections.Generic.HashSet[int]'
        for ($y = 30; $y -lt $height - 30; $y += 4) {
            for ($x = 30; $x -lt $width - 30; $x += 4) { [void]$colors.Add($bitmap.GetPixel($x, $y).ToArgb()) }
        }
        if ($colors.Count -lt 32) { throw "Blank client: only $($colors.Count) sampled colors. Inspect retained capture." }
        return @{ Width = $width; Height = $height; SampledColors = $colors.Count; Foreground = ($foregroundOwner -eq $Process.Id) }
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
}

try {
    for ($cycle = 1; $cycle -le $Cycles; $cycle++) {
        $profile = Join-Path $output.FullName "profile-$cycle"
        foreach ($launch in @('cold', 'warm')) {
            $name = "$cycle-$launch"
            $env:APPDATA = Join-Path $profile 'appdata'
            $env:LOCALAPPDATA = Join-Path $profile 'localappdata'
            $process = $null
            $launcher = $null
            $originalFailure = $null
            try {
                # Establish a normal foreground launcher BEFORE the application exists. Nothing
                # activates/resizes/repaints Naviamp between launch and its initial capture.
                if (-not [NaviampPresentationWindow]::PrepareLauncher()) { throw 'Cannot prepare owned foreground launcher.' }
                $clock = [Diagnostics.Stopwatch]::StartNew()
                $launcher = Start-Process -FilePath $exe -PassThru -RedirectStandardOutput (Join-Path $output.FullName "$name.stdout.log") -RedirectStandardError (Join-Path $output.FullName "$name.stderr.log")
                $process = $launcher
                do {
                    Start-Sleep -Milliseconds 100
                    $process.Refresh()
                    if ($process.MainWindowHandle -eq [IntPtr]::Zero) {
                        $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $($launcher.Id)"
                        foreach ($child in $children) {
                            $candidate = Get-Process -Id $child.ProcessId -ErrorAction SilentlyContinue
                            if ($candidate -and $candidate.MainWindowHandle -ne [IntPtr]::Zero) { $process = $candidate; break }
                        }
                    }
                    if ($process.HasExited) { throw "App exited during startup: $($process.ExitCode)" }
                    if ($clock.Elapsed.TotalSeconds -gt 15) { throw 'No native window within startup budget.' }
                } while ($process.MainWindowHandle -eq [IntPtr]::Zero)
                # Observation only. No foreground activation, pointer movement, or resize precedes capture.
                Start-Sleep -Seconds 3
                $initial = Save-Client $process "$name-initial"
                $initialMs = $clock.Elapsed.TotalMilliseconds
                $cpuBefore = $process.TotalProcessorTime.TotalMilliseconds
                $idleClock = [Diagnostics.Stopwatch]::StartNew()
                Start-Sleep -Seconds 5
                $process.Refresh()
                $cpu = 100 * ($process.TotalProcessorTime.TotalMilliseconds - $cpuBefore) / $idleClock.Elapsed.TotalMilliseconds
                [void][NaviampPresentationWindow]::ShowWindow($process.MainWindowHandle, 6)
                Start-Sleep -Milliseconds 500
                if (-not [NaviampPresentationWindow]::IsIconic($process.MainWindowHandle)) { throw 'Minimize did not take effect.' }
                [void][NaviampPresentationWindow]::ShowWindow($process.MainWindowHandle, 9)
                Start-Sleep -Seconds 2
                $restored = Save-Client $process "$name-restored"
                $results += @{ Launch = $name; ProcessId = $process.Id; InitialCaptureMs = $initialMs; Initial = $initial; Restored = $restored; IdleCpuOneCorePercent = $cpu }
                $results | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $output.FullName 'results.json')
            } catch {
                $originalFailure = $_
                throw
            } finally {
                if ($process -and -not $process.HasExited) {
                    [void][NaviampPresentationWindow]::PostMessage($process.MainWindowHandle, 0x10, [IntPtr]::Zero, [IntPtr]::Zero)
                    if (-not $process.WaitForExit(10000)) {
                        "Owned test process $($process.Id) did not close normally; inspect before cleanup." | Set-Content (Join-Path $output.FullName "$name-cleanup.txt")
                        if (-not $originalFailure) { throw "Owned test process $($process.Id) did not close normally." }
                    }
                }
            }
        }
    }
    'PASS: inspect saved client captures; automated color checks alone do not establish correct UI.' | Set-Content (Join-Path $output.FullName 'completed.txt')
} catch {
    $_ | Out-String | Set-Content (Join-Path $output.FullName 'failure.txt')
    throw
} finally {
    $env:APPDATA = $originalAppData
    $env:LOCALAPPDATA = $originalLocalData
}
