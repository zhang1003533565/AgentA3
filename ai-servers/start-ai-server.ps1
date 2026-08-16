$ErrorActionPreference = "Stop"

$ProjectName = "smart-campus-ai"
$AiServerHost = "127.0.0.1"
$PythonServerPort = 8081

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
    Write-Log "Starting AI Server at http://${AiServerHost}:$PythonServerPort ..."
    & uv run python -m uvicorn app.main:app --host $AiServerHost --port $PythonServerPort
    exit $LASTEXITCODE
}

Ensure-Uv
Sync-Dependencies
Start-AiServer
