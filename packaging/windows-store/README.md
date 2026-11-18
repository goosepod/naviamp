# Microsoft Store MSIX icons

The Store MSIX wraps the Windows release app image. Before packing the wrapper, generate the
Windows taskbar and Start icon variants from the same artwork as the Windows launcher. Windows
uses the target-size variants in its package resource index and the unplated variants for the
light and dark shell themes.

Run these commands against the staged MSIX layout from PowerShell with the Windows SDK tools on PATH.

```powershell
.\packaging\windows-store\Update-StoreIconAssets.ps1 -Layout <path-to-msix-layout>
makeappx pack /d <path-to-msix-layout> /p <output.msix> /o
```

Pass `-MakePri` with the SDK executable path when it is not on PATH. The layout must contain the
Naviamp `AppxManifest.xml` and the base PNG that its `Square44x44Logo` references. The generator
adds the qualified PNGs and regenerates `resources.pri`. Use MakeAppx semantic validation and
check the taskbar icon in a running, installed MSIX. Compare it with the standalone Windows icon.

[Microsoft app icon guidance](https://learn.microsoft.com/en-us/windows/apps/design/iconography/app-icon-construction) ·
[MSIX resource index instructions](https://learn.microsoft.com/en-us/windows/msix/desktop/desktop-to-uwp-manual-conversion)
