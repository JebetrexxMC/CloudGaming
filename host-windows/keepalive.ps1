<#
.SYNOPSIS
  Keeps the gaming host awake and streaming. There is no session timer anywhere in this stack.

.PARAMETER Apply
  One-time system tweaks: high-performance power plan, no sleep/hibernate/monitor-off,
  no screensaver, no lock screen on idle, no RDP idle disconnect.

.PARAMETER Watch
  Loop forever: restart Apollo/Sunshine if the service stops, and nudge the system
  every minute so Windows never considers it idle.
#>
[CmdletBinding()]
param([switch]$Apply, [switch]$Watch, [int]$IntervalSeconds = 60)

$ErrorActionPreference = 'Continue'

if ($Apply) {
    powercfg /setactive SCHEME_MIN | Out-Null          # High performance
    foreach ($s in 'standby-timeout-ac', 'monitor-timeout-ac', 'hibernate-timeout-ac', 'disk-timeout-ac') {
        powercfg /change $s 0 | Out-Null
    }
    powercfg /hibernate off | Out-Null

    $desktop = 'HKCU:\Control Panel\Desktop'
    Set-ItemProperty -Path $desktop -Name ScreenSaveActive -Value '0' -ErrorAction SilentlyContinue

    $pol = 'HKLM:\SOFTWARE\Policies\Microsoft\Windows\Personalization'
    New-Item -Path $pol -Force | Out-Null
    Set-ItemProperty -Path $pol -Name NoLockScreen -Value 1 -Type DWord

    $ts = 'HKLM:\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services'
    New-Item -Path $ts -Force | Out-Null
    Set-ItemProperty -Path $ts -Name MaxIdleTime -Value 0 -Type DWord
    Set-ItemProperty -Path $ts -Name MaxDisconnectionTime -Value 0 -Type DWord

    Write-Host 'keepalive: system tweaks applied'
}

if ($Watch) {
    Add-Type -Namespace Win32 -Name Power -MemberDefinition @'
[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);
'@
    # ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED
    $flags = [uint32]'0x80000003'

    while ($true) {
        [Win32.Power]::SetThreadExecutionState($flags) | Out-Null

        Get-Service | Where-Object { $_.Name -match 'Apollo|Sunshine' } | ForEach-Object {
            if ($_.Status -ne 'Running') {
                Write-Host "keepalive: restarting $($_.Name)"
                Start-Service $_.Name -ErrorAction SilentlyContinue
            }
        }
        Start-Sleep -Seconds $IntervalSeconds
    }
}

if (-not $Apply -and -not $Watch) { Get-Help $MyInvocation.MyCommand.Path }
