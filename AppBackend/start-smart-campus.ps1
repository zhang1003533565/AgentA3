$ErrorActionPreference = "Stop"

$ProjectName = "smart-campus"
$EnvFilePath = Join-Path $PSScriptRoot ".env"
$RootEnvFilePath = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        Write-Host "[$ProjectName] No .env file found at '$Path'. Using existing process environment."
        return
    }

    $loadedKeys = New-Object System.Collections.Generic.List[string]
    foreach ($rawLine in Get-Content -Encoding UTF8 $Path) {
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

        if (-not [Environment]::GetEnvironmentVariable($name, "Process")) {
            Set-Item -Path "Env:$name" -Value $value
            $loadedKeys.Add($name) | Out-Null
        }
    }

    if ($loadedKeys.Count -gt 0) {
        Write-Host "[$ProjectName] Loaded .env keys: $($loadedKeys -join ', ')"
    } else {
        Write-Host "[$ProjectName] .env found, but no new process environment keys were loaded."
    }
}

Import-DotEnv $RootEnvFilePath
Import-DotEnv $EnvFilePath

$MysqlService = "mysql"
$MysqlRootPassword = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "123456" }
$MysqlDatabase = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "smart-campus" }
$MysqlCharset = if ($env:MYSQL_CHARSET) { $env:MYSQL_CHARSET } else { "utf8mb4" }
$MysqlCollation = if ($env:MYSQL_COLLATION) { $env:MYSQL_COLLATION } else { "utf8mb4_unicode_ci" }
$MysqlWaitSeconds = if ($env:MYSQL_WAIT_SECONDS) { [int]$env:MYSQL_WAIT_SECONDS } else { 90 }
$AdminerPort = if ($env:ADMINER_PORT) { [int]$env:ADMINER_PORT } else { 0 }
$BackendPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
# Host port mapped from container's 3306 (see docker-compose.yml). Defaults to 3306.
$MysqlHostPort = if ($env:MYSQL_HOST_PORT) { [int]$env:MYSQL_HOST_PORT } else { 3306 }
$Neo4jEnabled = if ($env:NEO4J_ENABLED) { @("1", "true", "yes", "on") -contains $env:NEO4J_ENABLED.ToLowerInvariant() } else { $false }
$Neo4jWaitSeconds = if ($env:NEO4J_WAIT_SECONDS) { [int]$env:NEO4J_WAIT_SECONDS } else { $MysqlWaitSeconds }
$RedisWaitSeconds = if ($env:REDIS_WAIT_SECONDS) { [int]$env:REDIS_WAIT_SECONDS } else { $MysqlWaitSeconds }
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

function Invoke-ComposeWithInput {
    param(
        [string]$InputText,
        [object[]]$ComposeArgs
    )

    if ($script:UseDockerComposeV2) {
        $InputText | & docker compose @ComposeArgs
    } else {
        $InputText | & docker-compose @ComposeArgs
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

    $sqlText = Get-Content -Raw -Encoding UTF8 $DataSqlPath
    $composeArgs = @(
        "exec",
        "-T",
        "-e",
        "MYSQL_PWD=$MysqlRootPassword",
        $MysqlService,
        "mysql",
        "--default-character-set=utf8mb4",
        "-uroot",
        $MysqlDatabase
    )
    Invoke-ComposeWithInput -InputText $sqlText -ComposeArgs $composeArgs

    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Failed to import data.sql."
    }

    Write-Log "data.sql import completed."
}

function Wait-ForRedis {
    Write-Log "Waiting for Redis container..."
    for ($i = 0; $i -lt $RedisWaitSeconds; $i++) {
        $ErrorActionPreference = "SilentlyContinue"
        Invoke-Compose exec -T redis redis-cli ping *> $null
        $ErrorActionPreference = "Stop"
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    }

    Stop-WithError "Redis did not become ready within ${RedisWaitSeconds}s."
}

function Wait-ForNeo4j {
    if (-not $Neo4jEnabled) {
        Write-Log "Skipping Neo4j readiness check. Set NEO4J_ENABLED=true to enable knowledge graph storage."
        return
    }

    Write-Log "Waiting for Neo4j container..."
    for ($i = 0; $i -lt $Neo4jWaitSeconds; $i++) {
        $ErrorActionPreference = "SilentlyContinue"
        $status = docker inspect -f "{{.State.Health.Status}}" smart-campus-neo4j 2>$null
        $ErrorActionPreference = "Stop"
        if ($LASTEXITCODE -eq 0 -and $status -eq "healthy") {
            return
        }
        Start-Sleep -Seconds 1
    }

    Stop-WithError "Neo4j did not become healthy within ${Neo4jWaitSeconds}s."
}

function Ensure-BackendTools {
    if (-not (Test-Command "java")) {
        Stop-WithError "Java is not available. This backend requires JDK 21. Install Temurin/OpenJDK 21 or run: winget install EclipseAdoptium.Temurin.21.JDK"
    }

    if (-not ((Test-Command "mvn") -or (Test-Command "mvn.cmd"))) {
        Stop-WithError "Maven is not available. This backend requires Maven 3.9+. Install Maven or run: winget install Apache.Maven"
    }
}

function Test-EnvValue {
    param([string]$Value)
    return -not [string]::IsNullOrWhiteSpace($Value)
}

function Show-CosConfigStatus {
    $requiredKeys = @(
        "TENCENT_COS_SECRET_ID",
        "TENCENT_COS_SECRET_KEY",
        "TENCENT_COS_REGION",
        "TENCENT_COS_BUCKET",
        "TENCENT_COS_DOMAIN"
    )
    $missingKeys = @()
    foreach ($key in $requiredKeys) {
        if (-not (Test-EnvValue ([Environment]::GetEnvironmentVariable($key, "Process")))) {
            $missingKeys += $key
        }
    }

    if ($missingKeys.Count -gt 0) {
        Write-Log "Warning: COS config is incomplete. Image upload may fail. Missing: $($missingKeys -join ', ')"
        Write-Log "Create '$EnvFilePath' or set these environment variables before starting the backend."
        return
    }

    Write-Log "COS config loaded. Bucket: $env:TENCENT_COS_BUCKET; Region: $env:TENCENT_COS_REGION; Domain: $env:TENCENT_COS_DOMAIN"
}

function Set-DataSourceUrl {
    # Host MySQL port is mapped from container's 3306 via docker-compose.yml.
    # characterEncoding must use the Java charset name "UTF-8" (NOT "utf8mb4" --
    # MySQL Connector/J rejects MySQL charset names here with UnsupportedEncodingException).
    # Connector/J 8.0.26+ automatically uses utf8mb4 on the server side when UTF-8 is given.
    # URL must NOT contain connectionCollation (triggers MySQL error 1059 "Identifier too long").
    $existing = [Environment]::GetEnvironmentVariable("SPRING_DATASOURCE_URL", "Process")
    if ($existing) {
        Write-Log "Using configured SPRING_DATASOURCE_URL: $existing"
    } else {
        $url = "jdbc:mysql://localhost:${MysqlHostPort}/${MysqlDatabase}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
        $env:SPRING_DATASOURCE_URL = $url
        Write-Log "Using default SPRING_DATASOURCE_URL: $url"
        Write-Log "Tip: Override by setting SPRING_DATASOURCE_URL in .env or your shell environment."
    }

    if (-not [Environment]::GetEnvironmentVariable("SPRING_DATASOURCE_USERNAME", "Process")) {
        $env:SPRING_DATASOURCE_USERNAME = "root"
        Write-Log "Using default SPRING_DATASOURCE_USERNAME: root"
    }

    if (-not [Environment]::GetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", "Process")) {
        $env:SPRING_DATASOURCE_PASSWORD = $MysqlRootPassword
        Write-Log "Using MYSQL_ROOT_PASSWORD as SPRING_DATASOURCE_PASSWORD."
    }
}

function Start-Backend {
    Write-Log "Backend API: http://localhost:$BackendPort"
    Write-Log "Swagger UI: http://localhost:$BackendPort/swagger-ui.html"
    Write-Log "Adminer: http://localhost:$AdminerPort"
    Write-Log "Starting Spring Boot backend..."
    & mvn spring-boot:run -DskipTests
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
Wait-ForNeo4j
Ensure-Database
Import-DataSqlIfRequested
Ensure-BackendTools
Show-CosConfigStatus
Set-DataSourceUrl
Start-Backend
