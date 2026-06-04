$ErrorActionPreference = "Stop"

$ProjectName = "smart-campus"
$MysqlService = "mysql"
$MysqlRootPassword = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "123456" }
$MysqlDatabase = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "smart-campus" }
$MysqlCharset = if ($env:MYSQL_CHARSET) { $env:MYSQL_CHARSET } else { "utf8mb4" }
$MysqlCollation = if ($env:MYSQL_COLLATION) { $env:MYSQL_COLLATION } else { "utf8mb4_unicode_ci" }
$MysqlWaitSeconds = if ($env:MYSQL_WAIT_SECONDS) { [int]$env:MYSQL_WAIT_SECONDS } else { 90 }
$AdminerPort = if ($env:ADMINER_PORT) { [int]$env:ADMINER_PORT } else { 0 }

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

function Test-PortAvailable {
    param([int]$Port)

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect("127.0.0.1", $Port)
        return $false
    } catch {
        return $true
    } finally {
        $client.Dispose()
    }
}

function Set-AdminerPort {
    if ($AdminerPort -gt 0) {
        $env:ADMINER_PORT = [string]$AdminerPort
        Write-Log "Using configured Adminer port: $AdminerPort"
        return
    }

    foreach ($port in 7070..7080) {
        if (Test-PortAvailable $port) {
            $script:AdminerPort = $port
            $env:ADMINER_PORT = [string]$port
            Write-Log "Using Adminer port: $port"
            return
        }
    }

    Stop-WithError "No available Adminer port found in range 7070-7080. Set ADMINER_PORT and run again."
}

function Wait-ForMysql {
    Write-Log "Waiting for MySQL container..."
    for ($i = 0; $i -lt $MysqlWaitSeconds; $i++) {
        # Use environment variable to pass password securely
        $env:MYSQL_PWD = $MysqlRootPassword
        Invoke-Compose exec -T $MysqlService mysqladmin ping -uroot --silent *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    }

    Stop-WithError "MySQL did not become ready within ${MysqlWaitSeconds}s."
}

function Ensure-Database {
    Write-Log "Ensuring database '$MysqlDatabase' exists..."
    $quotedDatabase = '`' + $MysqlDatabase + '`'
    $query = "CREATE DATABASE IF NOT EXISTS $quotedDatabase DEFAULT CHARACTER SET $MysqlCharset COLLATE $MysqlCollation;"
    
    # Use environment variable to pass password securely
    $env:MYSQL_PWD = $MysqlRootPassword
    Invoke-Compose exec -T $MysqlService mysql -uroot -e $query
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to create or verify database '$MysqlDatabase'."
    }
}

function Ensure-BackendTools {
    if (-not (Test-Command "java")) {
        Stop-WithError "Java is not available. This backend requires JDK 21. Install Temurin/OpenJDK 21 or run: winget install EclipseAdoptium.Temurin.21.JDK"
    }

    if (-not ((Test-Command "mvn") -or (Test-Command "mvn.cmd"))) {
        Stop-WithError "Maven is not available. This backend requires Maven 3.9+. Install Maven or run: winget install Apache.Maven"
    }
}

function Start-Backend {
    Write-Log "Starting Spring Boot backend at http://localhost:8080 ..."
    & mvn spring-boot:run
    exit $LASTEXITCODE
}

Initialize-Compose
Start-DockerDesktop
Set-AdminerPort

Write-Log "Starting Docker services..."
Invoke-Compose up -d
if ($LASTEXITCODE -ne 0) {
    Stop-WithError "Failed to start Docker services."
}

Wait-ForMysql
Ensure-Database
Ensure-BackendTools
Start-Backend
