param(
    [Parameter(Mandatory = $true)][string]$Pptx,
    [string]$OutputDir = "",
    [switch]$RenderOffice
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRoot = Split-Path -Parent $scriptRoot
$pptxPath = [System.IO.Path]::GetFullPath($Pptx)
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $serverRoot "data\ppt-audits\$stamp"
}
$outputPath = [System.IO.Path]::GetFullPath($OutputDir)
$auditArgs = @($pptxPath, "--out", $outputPath)
if ($RenderOffice) { $auditArgs += "--render-office" }
$environmentScript = Join-Path $scriptRoot "ppt_audit\probe_environment.ps1"
$environmentPath = Join-Path $outputPath "environment.json"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $environmentScript -OutputPath $environmentPath | Out-Host
$venvPython = Join-Path $serverRoot ".venv\Scripts\python.exe"
Push-Location $serverRoot
try {
    if (Test-Path -LiteralPath $venvPython -PathType Leaf) {
        & $venvPython "scripts/ppt_audit/audit_pptx.py" @auditArgs
    }
    else {
        $cacheDir = Join-Path $serverRoot ".uv-cache-ppt-audit"
        New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
        $env:UV_CACHE_DIR = $cacheDir
        & uv run python "scripts/ppt_audit/audit_pptx.py" @auditArgs
    }
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}
Write-Host "Audit output: $outputPath"
exit $exitCode
