$ErrorActionPreference = "Stop"

$ProjectName = "smart-campus-ai"
$AiServerHost = "127.0.0.1"
$PythonServerPort = 8081
$VectorStoreBackend = "milvus"
$DockerWaitSeconds = 90
$DockerPullRetries = 3
$StartDocker = $true
$BuildKnowledgeBase = $false

for ($i = 0; $i -lt $args.Count; $i++) {
    switch ($args[$i]) {
        "--backend" {
            $i++
            if ($i -ge $args.Count) { throw "--backend requires a value" }
            $VectorStoreBackend = $args[$i]
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
            Write-Host "Usage: .\start-ai-server.ps1 [--backend milvus|local_jsonl] [--build-kb] [--host 127.0.0.1] [--no-docker] [--port 8081]"
            exit 0
        }
        "--help" {
            Write-Host "Usage: .\start-ai-server.ps1 [--backend milvus|local_jsonl] [--build-kb] [--host 127.0.0.1] [--no-docker] [--port 8081]"
            exit 0
        }
        default {
            throw "Unknown option: $($args[$i])"
        }
    }
}

Set-Location $PSScriptRoot
$RagComposeFile = Join-Path $PSScriptRoot "docker-compose.yml"

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
    $ErrorActionPreference = "SilentlyContinue"
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
    for ($i = 0; $i -lt $DockerWaitSeconds; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:9091/healthz" -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }

    Stop-WithError "Milvus did not become ready within ${DockerWaitSeconds}s."
}

function Pull-DockerImages {
    Write-Log "Checking if Docker images exist locally..."
    
    # 获取 compose 文件中定义的所有镜像
    $composeConfig = Invoke-Compose -f $RagComposeFile config | Out-String
    $images = ($composeConfig | Select-String 'image:' | ForEach-Object { $_.Line.Trim() -replace '^\s*image:\s*', '' }) | Sort-Object -Unique
    
    # 检查所有镜像是否已存在
    $allImagesExist = $true
    foreach ($image in $images) {
        if (-not $image) { continue }
        $ErrorActionPreference = "SilentlyContinue"
        docker image inspect $image *> $null
        $ErrorActionPreference = "Stop"
        if ($LASTEXITCODE -ne 0) {
            Write-Log "Image not found locally: $image"
            $allImagesExist = $false
            break
        }
    }
    
    # 如果所有镜像都存在，跳过拉取
    if ($allImagesExist) {
        Write-Log "All Docker images found locally, skipping pull."
        return
    }
    
    # 否则尝试拉取缺失的镜像
    for ($i = 1; $i -le $DockerPullRetries; $i++) {
        Write-Log "Pulling missing RAG Docker images (${i}/${DockerPullRetries})..."
        Invoke-Compose -f $RagComposeFile pull
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Write-Log "Docker image pull failed; retrying in 5 seconds..."
        Start-Sleep -Seconds 5
    }

    Stop-WithError "Failed to pull RAG Docker images. Check Docker network access and rerun this script."
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

function Start-DockerServices {
    if (-not $StartDocker) {
        return
    }
    if ($VectorStoreBackend -ne "milvus") {
        return
    }
    if (-not (Test-Path $RagComposeFile)) {
        Stop-WithError "Missing compose file: $RagComposeFile"
    }
    Initialize-Compose
    Start-DockerDesktop
    Pull-DockerImages
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
    Write-Log "Building knowledge base with backend=$VectorStoreBackend ..."
    & python scripts/build_knowledge_base.py --backend $VectorStoreBackend
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to build knowledge base."
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
Start-DockerServices
Build-KnowledgeBase
Start-AiServer
