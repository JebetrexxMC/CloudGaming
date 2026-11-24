#Requires -RunAsAdministrator
<#
.SYNOPSIS
  Turns a Windows 10/11 or Windows Server 2022+ machine into a Genshin Impact cloud gaming host.

.DESCRIPTION
  - Installs the NVIDIA gaming driver on AWS g4dn/g5 instances (optional)
  - Installs Apollo (default) or Sunshine as the streaming host
  - Installs ViGEmBus so Moonlight controllers show up as Xbox/DS4 pads
  - Downloads the Genshin launcher (HoYoPlay, Collapse, or Starward)
  - Adds a "Genshin Impact" entry to the Apollo/Sunshine apps list
  - Registers the keepalive watchdog (no sleep / no idle disconnect)
  - Optionally joins a private network: Tailscale, Headscale, ZeroTier, EasyTier or Nebula
  - Extras: RustDesk, Virtual Display Driver, Playnite, Moonlight Internet Hosting Tool

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File install.ps1
  powershell -ExecutionPolicy Bypass -File install.ps1 -StreamHost Sunshine -Launcher Starward
#>
[CmdletBinding()]
param(
    [ValidateSet('Apollo', 'Sunshine')]
    [string]$StreamHost = 'Apollo',

    [ValidateSet('HoYoPlay', 'Collapse', 'Starward')]
    [string]$Launcher = 'HoYoPlay',

    [switch]$AwsNvidiaDriver,

    [string]$InstallRoot = 'C:\CloudGaming',

    # Private network so Moonlight ports never need to face the internet.
    [ValidateSet('None', 'Tailscale', 'Headscale', 'ZeroTier', 'EasyTier', 'Nebula')]
    [string]$Vpn = 'None',
    [string]$TailscaleAuthKey = $env:TAILSCALE_AUTHKEY,
    [string]$HeadscaleUrl = $env:HEADSCALE_URL,            # e.g. https://hs.example.duckdns.org
    [string]$ZeroTierNetwork = $env:ZEROTIER_NETWORK,
    [string]$EasyTierNetwork = $env:EASYTIER_NETWORK,      # network name
    [string]$EasyTierSecret = $env:EASYTIER_SECRET,        # network secret
    [string]$EasyTierPeer = $(if ($env:EASYTIER_PEER) { $env:EASYTIER_PEER } else { 'tcp://public.easytier.top:11010' }),
    [string]$NebulaConfigUrl = $env:NEBULA_CONFIG_URL,     # zip with config.yml, ca.crt, host.crt, host.key

    # Extras
    [switch]$RustDesk,             # remote admin without RDP (RDP breaks the stream's display)
    [switch]$VirtualDisplay,       # VirtualDrivers/Virtual-Display-Driver (auto with Sunshine; Apollo has SudoVDA)
    [switch]$Playnite,             # Playnite fullscreen as a Moonlight app (one library for all launchers)
    [switch]$InternetHostingTool,  # moonlight-stream/Internet-Hosting-Tool for a HOME PC behind NAT

    # Where Genshin will be installed by the launcher (used for the app entry).
    [string]$GenshinExe = 'C:\Program Files\HoYoPlay\games\Genshin Impact game\GenshinImpact.exe'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Downloads = Join-Path $InstallRoot 'installers'
New-Item -ItemType Directory -Force -Path $Downloads | Out-Null
Start-Transcript -Path (Join-Path $InstallRoot 'install.log') -Append | Out-Null

function Write-Step([string]$Message) { Write-Host "`n==> $Message" -ForegroundColor Cyan }

function Get-LatestReleaseAsset {
    param([string]$Repo, [string]$Pattern)
    $headers = @{ 'User-Agent' = 'CloudGaming-installer' }
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest" -Headers $headers
    $asset = $release.assets | Where-Object { $_.name -match $Pattern } | Select-Object -First 1
    if (-not $asset) { throw "No asset matching '$Pattern' in $Repo $($release.tag_name)" }
    $target = Join-Path $Downloads $asset.name
    if (-not (Test-Path $target)) {
        Write-Host "    downloading $($asset.name) ($Repo $($release.tag_name))"
        Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $target -UseBasicParsing
    }
    return $target
}

# ---------------------------------------------------------------- GPU driver
if ($AwsNvidiaDriver) {
    Write-Step 'Installing NVIDIA gaming driver from AWS (s3://nvidia-gaming)'
    if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
        $awsMsi = Join-Path $Downloads 'AWSCLIV2.msi'
        Invoke-WebRequest 'https://awscli.amazonaws.com/AWSCLIV2.msi' -OutFile $awsMsi -UseBasicParsing
        Start-Process msiexec.exe -ArgumentList "/i `"$awsMsi`" /qn" -Wait
        $env:Path += ';C:\Program Files\Amazon\AWSCLIV2'
    }
    $drvDir = Join-Path $Downloads 'nvidia'
    aws s3 cp --recursive s3://nvidia-gaming/windows/latest/ $drvDir --no-sign-request 2>$null
    if (-not (Get-ChildItem $drvDir -Filter *.zip -ErrorAction SilentlyContinue)) {
        aws s3 cp --recursive s3://nvidia-gaming/windows/latest/ $drvDir
    }
    $zip = Get-ChildItem $drvDir -Filter *.zip | Select-Object -First 1
    Expand-Archive $zip.FullName -DestinationPath $drvDir -Force
    $setup = Get-ChildItem $drvDir -Recurse -Filter '*.exe' | Where-Object Name -match 'win10|win11|server' | Select-Object -First 1
    Start-Process $setup.FullName -ArgumentList '-s -noreboot' -Wait
    # Gaming-driver licensing flag documented by AWS for g4dn/g5.
    New-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Services\nvlddmkm\Global' -Name 'vGamingMarketplace' -PropertyType DWord -Value 2 -Force | Out-Null
    Invoke-WebRequest 'https://nvidia-gaming.s3.amazonaws.com/GridSwCert-Archive/GridSwCertWindows_2024_02_22.cert' `
        -OutFile "$env:PUBLIC\Documents\GridSwCert.txt" -UseBasicParsing -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------- Streaming host
Write-Step "Installing $StreamHost"
if ($StreamHost -eq 'Apollo') {
    # Apollo bundles SudoVDA, a virtual display, so headless cloud VMs get a real monitor.
    $exe = Get-LatestReleaseAsset -Repo 'ClassicOldSong/Apollo' -Pattern '^Apollo-.*\.exe$'
    $HostDir = 'C:\Program Files\Apollo'
} else {
    $exe = Get-LatestReleaseAsset -Repo 'LizardByte/Sunshine' -Pattern 'windows-installer\.exe$|Sunshine.*\.exe$'
    $HostDir = 'C:\Program Files\Sunshine'
}
Start-Process $exe -ArgumentList '/S' -Wait

# ---------------------------------------------------------------- Virtual gamepad
Write-Step 'Installing ViGEmBus (virtual Xbox 360 / DualShock 4 controller)'
if (-not (Get-PnpDevice -FriendlyName '*Virtual Gamepad Emulation Bus*' -ErrorAction SilentlyContinue)) {
    $vigem = Get-LatestReleaseAsset -Repo 'nefarius/ViGEmBus' -Pattern '^ViGEmBus_.*\.exe$'
    Start-Process $vigem -ArgumentList '/qn /norestart' -Wait
}

# ---------------------------------------------------------------- Genshin launcher
Write-Step "Downloading launcher: $Launcher"
switch ($Launcher) {
    'HoYoPlay' {
        $hoyo = Join-Path $Downloads 'HoYoPlay_install.exe'
        Invoke-WebRequest 'https://sg-public-api.hoyoverse.com/event/download_porter/trace/hyp_global/hyphoyoverse/default' `
            -OutFile $hoyo -UseBasicParsing
        $LauncherPath = $hoyo
    }
    'Collapse' {
        $LauncherPath = Get-LatestReleaseAsset -Repo 'CollapseLauncher/Collapse' -Pattern 'stable-Setup\.exe$'
    }
    'Starward' {
        $LauncherPath = Get-LatestReleaseAsset -Repo 'Scighost/Starward' -Pattern '^Starward_Setup_.*_x64\.exe$'
    }
}
$shortcut = (New-Object -ComObject WScript.Shell).CreateShortcut("$env:PUBLIC\Desktop\Install $Launcher.lnk")
$shortcut.TargetPath = $LauncherPath
$shortcut.Save()
Write-Host "    Launcher installer: $LauncherPath (run it once over RDP or the Desktop stream, then log in)"

# ---------------------------------------------------------------- App entry
Write-Step "Adding Genshin Impact to $StreamHost apps"
$appsFile = Join-Path $HostDir 'config\apps.json'
$template = Get-Content (Join-Path $PSScriptRoot 'apps.genshin.json') -Raw | ConvertFrom-Json
$genshin = $template.apps[0]
$genshin.cmd = "`"$GenshinExe`""
$genshin.'working-dir' = Split-Path $GenshinExe

if (Test-Path $appsFile) {
    $apps = Get-Content $appsFile -Raw | ConvertFrom-Json
} else {
    New-Item -ItemType Directory -Force -Path (Split-Path $appsFile) | Out-Null
    $apps = [pscustomobject]@{ env = @{}; apps = @() }
}
$apps.apps = @($apps.apps | Where-Object { $_.name -ne $genshin.name }) + $genshin
if ($Playnite) {
    $pnExe = Join-Path $env:LOCALAPPDATA 'Playnite\Playnite.FullscreenApp.exe'
    $apps.apps = @($apps.apps | Where-Object { $_.name -ne 'Playnite' }) + [pscustomobject]@{
        name = 'Playnite'; cmd = "`"$pnExe`" --hidesplashscreen"; 'auto-detach' = 'true'; 'wait-all' = 'true'; 'exit-timeout' = '5'
    }
}
$apps | ConvertTo-Json -Depth 10 | Set-Content -Path $appsFile -Encoding UTF8

# ---------------------------------------------------------------- Firewall
Write-Step 'Opening Moonlight ports in Windows Firewall'
$rules = @(
    @{ Name = 'CloudGaming-TCP'; Protocol = 'TCP'; Port = '47984,47989,47990,48010' },
    @{ Name = 'CloudGaming-UDP'; Protocol = 'UDP'; Port = '47998-48000,48002,48010' }
)
foreach ($r in $rules) {
    Remove-NetFirewallRule -DisplayName $r.Name -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName $r.Name -Direction Inbound -Action Allow -Protocol $r.Protocol -LocalPort ($r.Port -split ',') | Out-Null
}

# ---------------------------------------------------------------- Private network
switch ($Vpn) {
    'Tailscale' {
        Write-Step 'Installing Tailscale'
        $msi = Join-Path $Downloads 'tailscale-setup-latest-amd64.msi'
        Invoke-WebRequest 'https://pkgs.tailscale.com/stable/tailscale-setup-latest-amd64.msi' -OutFile $msi -UseBasicParsing
        Start-Process msiexec.exe -ArgumentList "/i `"$msi`" /qn TS_UNATTENDEDMODE=always" -Wait
        $ts = 'C:\Program Files\Tailscale\tailscale.exe'
        if ($TailscaleAuthKey) {
            & $ts up --authkey=$TailscaleAuthKey --hostname=genshin-host --unattended --accept-routes
            Write-Host "    Tailscale IP: $(& $ts ip -4)"
        } else {
            Write-Host '    No auth key given. Run "tailscale up" over RDP to log in.'
        }
    }
    'ZeroTier' {
        Write-Step 'Installing ZeroTier One'
        $msi = Join-Path $Downloads 'ZeroTierOne.msi'
        Invoke-WebRequest 'https://download.zerotier.com/dist/ZeroTier%20One.msi' -OutFile $msi -UseBasicParsing
        Start-Process msiexec.exe -ArgumentList "/i `"$msi`" /qn /norestart" -Wait
        if ($ZeroTierNetwork) {
            Start-Sleep -Seconds 10
            & 'C:\ProgramData\ZeroTier\One\zerotier-one_x64.exe' -q join $ZeroTierNetwork
            Write-Host "    Joined $ZeroTierNetwork. Authorize 'genshin-host' in my.zerotier.com, then use its ZeroTier IP in Moonlight."
        }
    }
    'Headscale' {
        Write-Step 'Installing Tailscale client for your own Headscale server'
        if (-not $HeadscaleUrl) { throw '-HeadscaleUrl is required with -Vpn Headscale' }
        $msi = Join-Path $Downloads 'tailscale-setup-latest-amd64.msi'
        Invoke-WebRequest 'https://pkgs.tailscale.com/stable/tailscale-setup-latest-amd64.msi' -OutFile $msi -UseBasicParsing
        Start-Process msiexec.exe -ArgumentList "/i `"$msi`" /qn TS_UNATTENDEDMODE=always" -Wait
        $ts = 'C:\Program Files\Tailscale\tailscale.exe'
        $upArgs = @('up', "--login-server=$HeadscaleUrl", '--hostname=genshin-host', '--unattended')
        if ($TailscaleAuthKey) { $upArgs += "--authkey=$TailscaleAuthKey" }
        & $ts @upArgs
    }
    'EasyTier' {
        Write-Step 'Installing EasyTier (P2P mesh VPN)'
        if (-not $EasyTierNetwork -or -not $EasyTierSecret) { throw '-EasyTierNetwork and -EasyTierSecret are required' }
        $zip = Get-LatestReleaseAsset -Repo 'EasyTier/EasyTier' -Pattern '^easytier-windows-x86_64-.*\.zip$'
        $etDir = Join-Path $InstallRoot 'easytier'
        Expand-Archive $zip -DestinationPath $etDir -Force
        $core = Get-ChildItem $etDir -Recurse -Filter 'easytier-core.exe' | Select-Object -First 1
        $etArgs = "--network-name $EasyTierNetwork --network-secret $EasyTierSecret -p $EasyTierPeer --hostname genshin-host -d"
        $action = New-ScheduledTaskAction -Execute $core.FullName -Argument $etArgs -WorkingDirectory $core.DirectoryName
        $trigger = New-ScheduledTaskTrigger -AtStartup
        $set = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
        Register-ScheduledTask -TaskName 'CloudGaming-EasyTier' -Action $action -Trigger $trigger -Settings $set -User 'SYSTEM' -RunLevel Highest -Force | Out-Null
        Start-ScheduledTask -TaskName 'CloudGaming-EasyTier'
        Write-Host "    Joined EasyTier network '$EasyTierNetwork' via $EasyTierPeer (virtual IP via DHCP, see easytier-cli peer)"
    }
    'Nebula' {
        Write-Step 'Installing Nebula (Slack overlay network)'
        if (-not $NebulaConfigUrl) { throw '-NebulaConfigUrl is required (zip with config.yml, ca.crt, host.crt, host.key)' }
        $zip = Get-LatestReleaseAsset -Repo 'slackhq/nebula' -Pattern '^nebula-windows-amd64\.zip$'
        $nbDir = Join-Path $InstallRoot 'nebula'
        Expand-Archive $zip -DestinationPath $nbDir -Force
        $cfgZip = Join-Path $Downloads 'nebula-config.zip'
        Invoke-WebRequest $NebulaConfigUrl -OutFile $cfgZip -UseBasicParsing
        Expand-Archive $cfgZip -DestinationPath $nbDir -Force
        $nb = Get-ChildItem $nbDir -Recurse -Filter 'nebula.exe' | Select-Object -First 1
        & $nb.FullName -service install -config (Join-Path $nbDir 'config.yml')
        Start-Service nebula -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------- Extras
if ($StreamHost -eq 'Sunshine' -or $VirtualDisplay) {
    Write-Step 'Virtual Display Driver (headless monitor for Sunshine)'
    $vdd = Get-LatestReleaseAsset -Repo 'VirtualDrivers/Virtual-Display-Driver' -Pattern '^VirtualDisplayDriver-x86\.Driver\.Only\.zip$'
    $vddDir = Join-Path $InstallRoot 'vdd'
    Expand-Archive $vdd -DestinationPath $vddDir -Force
    Get-ChildItem $vddDir -Recurse -Filter *.inf | ForEach-Object { pnputil /add-driver $_.FullName /install | Out-Null }
    $ctl = Get-LatestReleaseAsset -Repo 'VirtualDrivers/Virtual-Display-Driver' -Pattern '^VDD\.Control\..*\.zip$'
    Expand-Archive $ctl -DestinationPath (Join-Path $vddDir 'control') -Force
    Write-Host "    If no virtual monitor shows up, run VDD Control in $vddDir\control and click Install."
}

if ($RustDesk) {
    Write-Step 'Installing RustDesk (remote admin; use it instead of RDP while streaming)'
    $rd = Get-LatestReleaseAsset -Repo 'rustdesk/rustdesk' -Pattern '^rustdesk-.*-x86_64\.msi$'
    Start-Process msiexec.exe -ArgumentList "/i `"$rd`" /qn" -Wait
}

if ($Playnite) {
    Write-Step 'Installing Playnite (game library frontend)'
    $pn = Get-LatestReleaseAsset -Repo 'JosefNemec/Playnite' -Pattern '^Playnite.*\.exe$'
    Start-Process $pn -ArgumentList '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART' -Wait
}

if ($InternetHostingTool) {
    Write-Step 'Installing Moonlight Internet Hosting Tool (home PC behind NAT/CGNAT)'
    $iht = Get-LatestReleaseAsset -Repo 'moonlight-stream/Internet-Hosting-Tool' -Pattern '^InternetHostingToolSetup-.*\.exe$'
    Start-Process $iht -ArgumentList '/quiet /norestart' -Wait
}

# ---------------------------------------------------------------- Keepalive
Write-Step 'Registering keepalive watchdog'
$keepalive = Join-Path $InstallRoot 'keepalive.ps1'
Copy-Item (Join-Path $PSScriptRoot 'keepalive.ps1') $keepalive -Force
& $keepalive -Apply
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$keepalive`" -Watch"
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName 'CloudGaming-Keepalive' -Action $action -Trigger $trigger -Settings $settings -User 'SYSTEM' -RunLevel Highest -Force | Out-Null
Start-ScheduledTask -TaskName 'CloudGaming-Keepalive'

Write-Step 'Done'
Write-Host @"
Next steps:
  1. Open https://localhost:47990 (or https://<public-ip>:47990) and set the $StreamHost admin login.
  2. Run "Install $Launcher" on the desktop, log in, and install Genshin Impact.
     If the game lands somewhere other than:
       $GenshinExe
     re-run:  install.ps1 -GenshinExe '<path to GenshinImpact.exe>'
  3. Pair Moonlight (PIN tab in the web UI) and start "Genshin Impact".
"@
Stop-Transcript | Out-Null
