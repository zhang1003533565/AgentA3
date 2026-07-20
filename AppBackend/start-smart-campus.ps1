$ErrorActionPreference = "Stop"

$ProjectName = "smart-campus"
$MysqlService = "mysql"
$MysqlRootPassword = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "123456" }
$MysqlDatabase = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "smart-campus" }
$MysqlCharset = if ($env:MYSQL_CHARSET) { $env:MYSQL_CHARSET } else { "utf8mb4" }
$MysqlCollation = if ($env:MYSQL_COLLATION) { $env:MYSQL_COLLATION } else { "utf8mb4_unicode_ci" }
$MysqlWaitSeconds = if ($env:MYSQL_WAIT_SECONDS) { [int]$env:MYSQL_WAIT_SECONDS } else { 90 }
$AdminerPort = if ($env:ADMINER_PORT) { [int]$env:ADMINER_PORT } else { 0 }
$BackendPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
$DataSqlPath = Join-Path $PSScriptRoot "src\main\resources\data.sql"
$ImportDataSql = $false
if ($env:IMPORT_DATA_SQL) {
    $ImportDataSql = @("1", "true", "yes", "on") -contains $env:IMPORT_DATA_SQL.ToLowerInvariant()
}

Set-Location $PSScriptRoot

function Initialize-ConsoleEncoding {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [Console]::InputEncoding = $utf8
    [Console]::OutputEncoding = $utf8
    $script:OutputEncoding = $utf8
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
        $ErrorActionPreference = "SilentlyContinue"
        Invoke-Compose exec -T -e "MYSQL_PWD=$MysqlRootPassword" $MysqlService mysqladmin ping -uroot --silent *> $null
        $ErrorActionPreference = "Stop"
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
    
    Invoke-Compose exec -T -e "MYSQL_PWD=$MysqlRootPassword" $MysqlService mysql -uroot -e $query
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to create or verify database '$MysqlDatabase'."
    }
}

function Import-DataSqlIfRequested {
    if (-not $ImportDataSql) {
        Write-Log "Skipping data.sql import. Set IMPORT_DATA_SQL=1 only when you need to reset/seed local data."
        return
    }

    if (-not (Test-Path $DataSqlPath)) {
        Stop-WithError "data.sql was not found at '$DataSqlPath'."
    }

    Write-Log "IMPORT_DATA_SQL=1 detected. Importing data.sql into '$MysqlDatabase'."
    Write-Log "Warning: data.sql contains TRUNCATE statements and may reset local seed data."

    Get-Content -Raw -Encoding UTF8 $DataSqlPath |
        Invoke-Compose exec -T -e "MYSQL_PWD=$MysqlRootPassword" $MysqlService mysql --default-character-set=utf8mb4 -uroot $MysqlDatabase

    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to import data.sql."
    }

    Write-Log "data.sql import completed."
}

function Wait-ForRedis {
    Write-Log "Waiting for Redis container..."
    for ($i = 0; $i -lt $MysqlWaitSeconds; $i++) {
        $ErrorActionPreference = "SilentlyContinue"
        Invoke-Compose exec -T redis redis-cli ping *> $null
        $ErrorActionPreference = "Stop"
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    }

    Stop-WithError "Redis did not become ready within ${MysqlWaitSeconds}s."
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
    Write-Log "Backend API: http://localhost:$BackendPort"
    Write-Log "Swagger UI: http://localhost:$BackendPort/swagger-ui.html"
    Write-Log "Adminer: http://localhost:$AdminerPort"
    Write-Log "Starting Spring Boot backend..."
    & mvn spring-boot:run
    exit $LASTEXITCODE
}

Initialize-ConsoleEncoding
Initialize-Compose
Start-DockerDesktop
Set-AdminerPort

Write-Log "Starting Docker services..."
Invoke-Compose up -d
if ($LASTEXITCODE -ne 0) {
    Stop-WithError "Failed to start Docker services."
}

Wait-ForMysql
Wait-ForRedis
Ensure-Database
Import-DataSqlIfRequested
Ensure-BackendTools
Start-Backend
