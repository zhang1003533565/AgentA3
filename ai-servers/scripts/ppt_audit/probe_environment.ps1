param(
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [string[]]$RequestedFont = @("Microsoft YaHei", "SimSun", "Arial", "Calibri")
)

$ErrorActionPreference = "Continue"
$windowsRoot = [Environment]::GetEnvironmentVariable("WINDIR")
if ([string]::IsNullOrWhiteSpace($windowsRoot)) { $windowsRoot = "C:\Windows" }
$result = [ordered]@{
    timestamp = (Get-Date).ToString("o")
    os = [ordered]@{
        computer = $env:COMPUTERNAME
        user = $env:USERNAME
        os = [Environment]::OSVersion.VersionString
        powershell = $PSVersionTable.PSVersion.ToString()
        culture = (Get-Culture).Name
        uiCulture = (Get-UICulture).Name
    }
    renderers = [ordered]@{
        office = [ordered]@{ available = $false; error = "" }
        libreOffice = [ordered]@{ available = $false; path = "" }
    }
    fonts = [ordered]@{
        windowsFontDir = Join-Path $windowsRoot "Fonts"
        fontFileCount = 0
        requested = $RequestedFont
        fcMatchAvailable = $false
        matches = [ordered]@{}
    }
}

$fontDir = Join-Path $windowsRoot "Fonts"
if (Test-Path -LiteralPath $fontDir) {
    $result.fonts.fontFileCount = @(Get-ChildItem -LiteralPath $fontDir -File -ErrorAction SilentlyContinue).Count
}

$fcMatch = Get-Command fc-match -ErrorAction SilentlyContinue
if ($fcMatch) {
    $result.fonts.fcMatchAvailable = $true
    foreach ($font in $RequestedFont) {
        $match = (& $fcMatch.Source $font 2>$null | Select-Object -First 1)
        $result.fonts.matches[$font] = [string]$match
    }
}

$soffice = Get-Command soffice -ErrorAction SilentlyContinue
if (-not $soffice) { $soffice = Get-Command libreoffice -ErrorAction SilentlyContinue }
if ($soffice) {
    $result.renderers.libreOffice.available = $true
    $result.renderers.libreOffice.path = $soffice.Source
}

try {
    $powerPoint = New-Object -ComObject PowerPoint.Application
    $result.renderers.office["available"] = $true
    $powerPoint.Quit()
} catch {
    $result.renderers.office["error"] = $_.Exception.Message
}

$parent = Split-Path -Parent ([System.IO.Path]::GetFullPath($OutputPath))
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Get-Content -Raw -LiteralPath $OutputPath
