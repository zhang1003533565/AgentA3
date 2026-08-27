$ErrorActionPreference = "Stop"

$ProjectName = "smart-campus-ai"
$AiServerHost = "127.0.0.1"
$PythonServerPort = 8081
$RootEnvFilePath = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"

for ($i = 0; $i -lt $args.Count; $i++) {
    switch ($args[$i]) {
        "--host" {
            $i++
            if ($i -ge $args.Count) { throw "--host requires a value" }
            $AiServerHost = $args[$i]
        }
        "--port" {
            $i++
            if ($i -ge $args.Count) { throw "--port requires a value" }
            $PythonServerPort = [int]$args[$i]
        }
        "-h" {
            Write-Host "Usage: .\start-ai-server.ps1 [--host 127.0.0.1] [--port 8081]"
            exit 0
        }
        "--help" {
            Write-Host "Usage: .\start-ai-server.ps1 [--host 127.0.0.1] [--port 8081]"
            exit 0
        }
        default {
            throw "Unknown option: $($args[$i])"
        }
    }
}

Set-Location $PSScriptRoot
if (-not $env:UV_LINK_MODE) {
    $env:UV_LINK_MODE = "copy"
}

function Write-Log {
    param([string]$Message)
    Write-Host "[$ProjectName] $Message"
}

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Log "No root .env found at '$Path'. Using existing process environment."
        return
    }

    $loadedKeys = New-Object System.Collections.Generic.List[string]
    foreach ($rawLine in Get-Content -Encoding UTF8 -LiteralPath $Path) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -le 0) {
            continue
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()
        if (-not ($name -match "^[A-Za-z_][A-Za-z0-9_]*$")) {
            continue
        }

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        # LLM_* 必须以项目 .env 为准，避免旧 shell 环境让兜底配置失效；
        # 其它变量保留现有进程环境优先级。
        $existing = [Environment]::GetEnvironmentVariable($name, "Process")
        if ($name.StartsWith("LLM_", [StringComparison]::OrdinalIgnoreCase) -or -not $existing) {
            Set-Item -Path "Env:$name" -Value $value
            $loadedKeys.Add($name) | Out-Null
        }
    }

    if ($loadedKeys.Count -gt 0) {
        Write-Log "Loaded root .env keys: $($loadedKeys -join ', ')"
    } else {
        Write-Log "Root .env found, but no environment keys were loaded."
    }
}

function Stop-WithError {
    param([string]$Message)
    Write-Error "[$ProjectName] ERROR: $Message"
    exit 1
}

function Test-Command {
    param([string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Ensure-Uv {
    if (-not (Test-Command "uv")) {
        Stop-WithError "uv is not available. Install uv first, for example: winget install astral-sh.uv"
    }
}

function Sync-Dependencies {
    Write-Log "Syncing Python dependencies with uv..."
    & uv sync
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to sync Python dependencies."
    }
}

function Start-AiServer {
    if (-not $env:CHROMIUM_PATH) {
        $playwrightRoot = "$env:LOCALAPPDATA\ms-playwright"
        $chromiumDirs = Get-ChildItem -Path $playwrightRoot -Directory -Filter "chromium-*" -ErrorAction SilentlyContinue `
            | Sort-Object Name -Descending
        foreach ($dir in $chromiumDirs) {
            # Playwright 旧版装到 chrome-win，新版(1.5x+)装到 chrome-win64，两者都探测
            $chromeExe = @(
                (Join-Path $dir.FullName "chrome-win64\chrome.exe"),
                (Join-Path $dir.FullName "chrome-win\chrome.exe")
            ) | Where-Object { Test-Path $_ } | Select-Object -First 1
            if ($chromeExe) {
                $env:CHROMIUM_PATH = $chromeExe
                Write-Log "Using Chromium: $chromeExe"
                break
            }
        }
        if (-not $env:CHROMIUM_PATH) {
            Write-Log "WARNING: No Playwright Chromium found, PPT rendering will fail"
        }
    }
    Write-Log "Starting AI Server at http://${AiServerHost}:$PythonServerPort ..."
    & uv run python -m uvicorn app.main:app --host $AiServerHost --port $PythonServerPort
    exit $LASTEXITCODE
}

Import-DotEnv $RootEnvFilePath
Ensure-Uv
Sync-Dependencies
Start-AiServer
