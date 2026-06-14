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

function Ensure-Python {
    if (-not (Test-Command "python")) {
        Stop-WithError "Python is not available. Install Python 3.11+ first."
    }
}

function Ensure-Venv {
    if (-not (Test-Path ".venv")) {
        Write-Log "Creating Python virtual environment..."
        & python -m venv .venv
    }
    $activate = Join-Path ".venv" "Scripts\Activate.ps1"
    if (-not (Test-Path $activate)) {
        Stop-WithError "Virtual environment activation script is missing: $activate"
    }
    . $activate
}

function Install-Requirements {
    Write-Log "Installing Python dependencies..."
    & python -m pip install -r requirements.txt
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to install Python dependencies."
    }
}

function Start-AiServer {
    Write-Log "Starting AI Server at http://${AiServerHost}:$PythonServerPort ..."
    & python -m uvicorn app.main:app --host $AiServerHost --port $PythonServerPort
    exit $LASTEXITCODE
}

Ensure-Python
Ensure-Venv
Install-Requirements
Start-AiServer
