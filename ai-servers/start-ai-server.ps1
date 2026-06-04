$ErrorActionPreference = "Stop"

$ProjectName = "smart-campus-ai"
$AiServerHost = if ($env:AI_SERVER_HOST) { $env:AI_SERVER_HOST } else { "127.0.0.1" }
$PythonServerPort = if ($env:PYTHON_SERVER_PORT) { [int]$env:PYTHON_SERVER_PORT } else { 8081 }
$RagVectorStoreBackend = if ($env:RAG_VECTOR_STORE_BACKEND) { $env:RAG_VECTOR_STORE_BACKEND } else { "local_jsonl" }
$RagDockerWaitSeconds = if ($env:RAG_DOCKER_WAIT_SECONDS) { [int]$env:RAG_DOCKER_WAIT_SECONDS } else { 90 }
$StartDocker = $true
$BuildKnowledgeBase = $false

for ($i = 0; $i -lt $args.Count; $i++) {
    switch ($args[$i]) {
        "--backend" {
            $i++
            if ($i -ge $args.Count) { throw "--backend requires a value" }
            $RagVectorStoreBackend = $args[$i]
        }
        "--build-kb" {
            $BuildKnowledgeBase = $true
        }
        "--host" {
            $i++
            if ($i -ge $args.Count) { throw "--host requires a value" }
            $AiServerHost = $args[$i]
        }
        "--no-docker" {
            $StartDocker = $false
        }
        "--port" {
            $i++
            if ($i -ge $args.Count) { throw "--port requires a value" }
            $PythonServerPort = [int]$args[$i]
        }
        "-h" {
            Write-Host "Usage: .\start-ai-server.ps1 [--backend local_jsonl|milvus] [--build-kb] [--host 127.0.0.1] [--no-docker] [--port 8081]"
            exit 0
        }
        "--help" {
            Write-Host "Usage: .\start-ai-server.ps1 [--backend local_jsonl|milvus] [--build-kb] [--host 127.0.0.1] [--no-docker] [--port 8081]"
            exit 0
        }
        default {
            throw "Unknown option: $($args[$i])"
        }
    }
}

Set-Location $PSScriptRoot
$RootDir = Split-Path -Parent $PSScriptRoot
$RagComposeFile = Join-Path $RootDir "docker-compose.rag.yml"

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

function Initialize-Compose {
    if (-not (Test-Command "docker")) {
        Stop-WithError "Docker is not installed. Install Docker Desktop first."
    }

    docker compose version *> $null
    if ($LASTEXITCODE -eq 0) {
        $script:UseDockerComposeV2 = $true
        return
    }

    if (Test-Command "docker-compose") {
        $script:UseDockerComposeV2 = $false
        return
    }

    Stop-WithError "Docker Compose is not available. Install Docker Desktop first."
}

function Invoke-Compose {
    if ($script:UseDockerComposeV2) {
        & docker compose @args
    } else {
        & docker-compose @args
    }
}

function Test-DockerReady {
    docker info *> $null
    return $LASTEXITCODE -eq 0
}

function Start-DockerDesktop {
    if (Test-DockerReady) {
        return
    }

    $candidates = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Docker\Docker Desktop.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            Write-Log "Docker is not running; opening Docker Desktop..."
            Start-Process $candidate | Out-Null
            break
        }
    }

    Write-Log "Waiting for Docker to become available..."
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-DockerReady) {
            return
        }
        Start-Sleep -Seconds 2
    }

    Stop-WithError "Docker is still unavailable. Start Docker Desktop and run this script again."
}

function Wait-ForMilvus {
    Write-Log "Waiting for Milvus at http://localhost:9091/healthz ..."
    for ($i = 0; $i -lt $RagDockerWaitSeconds; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:9091/healthz" -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }

    Stop-WithError "Milvus did not become ready within ${RagDockerWaitSeconds}s."
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

function Ensure-EnvFile {
    if ((-not (Test-Path ".env")) -and (Test-Path "example.env")) {
        Write-Log "Creating .env from example.env ..."
        Copy-Item "example.env" ".env"
    }
}

function Install-Requirements {
    Write-Log "Installing Python dependencies..."
    & python -m pip install -r requirements.txt
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to install Python dependencies."
    }
}

function Start-DockerServices {
    if (-not $StartDocker) {
        return
    }
    if ($RagVectorStoreBackend -ne "milvus") {
        return
    }
    if (-not (Test-Path $RagComposeFile)) {
        Stop-WithError "Missing compose file: $RagComposeFile"
    }
    Initialize-Compose
    Start-DockerDesktop
    Write-Log "Starting RAG Docker services..."
    Invoke-Compose -f $RagComposeFile up -d
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to start RAG Docker services."
    }
    Wait-ForMilvus
}

function Build-KnowledgeBase {
    if (-not $BuildKnowledgeBase) {
        return
    }
    Write-Log "Building knowledge base with backend=$RagVectorStoreBackend ..."
    & python scripts/build_knowledge_base.py --backend $RagVectorStoreBackend
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to build knowledge base."
    }
}

function Start-AiServer {
    $env:AI_SERVER_HOST = $AiServerHost
    $env:PYTHON_SERVER_PORT = [string]$PythonServerPort
    $env:RAG_VECTOR_STORE_BACKEND = $RagVectorStoreBackend
    Write-Log "Starting AI Server at http://${AiServerHost}:$PythonServerPort ..."
    & python -m uvicorn app.main:app --host $AiServerHost --port $PythonServerPort
    exit $LASTEXITCODE
}

Ensure-Python
Ensure-EnvFile
Ensure-Venv
Install-Requirements
Start-DockerServices
Build-KnowledgeBase
Start-AiServer
