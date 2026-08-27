# Development backend entrypoint.
# Loads the repository-root .env before starting Spring Boot so the Java
# process receives the same LLM configuration as start-smart-campus.ps1.
$ErrorActionPreference = "Stop"

$Backend = $PSScriptRoot
$RootEnvFilePath = Join-Path (Split-Path $Backend -Parent) ".env"

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        Write-Host "[smart-campus] No root .env found at '$Path'. Using existing process environment."
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

        # LLM_* must come from the project .env. Otherwise a stale shell
        # variable can silently keep the backend on an old provider/model.
        # Other environment variables retain the existing shell-first behavior.
        $existing = [Environment]::GetEnvironmentVariable($name, "Process")
        if ($name.StartsWith("LLM_", [StringComparison]::OrdinalIgnoreCase) -or -not $existing) {
            Set-Item -Path "Env:$name" -Value $value
            $loadedKeys.Add($name) | Out-Null
        }
    }

    if ($loadedKeys.Count -gt 0) {
        Write-Host "[smart-campus] Loaded root .env keys: $($loadedKeys -join ', ')"
    } else {
        Write-Host "[smart-campus] Root .env found, but no new environment keys were loaded."
    }
}

Import-DotEnv $RootEnvFilePath

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "D:\DevTools\Java\temurin-21"
}
if (-not $env:MAVEN_HOME) {
    $env:MAVEN_HOME = "D:\DevTools\Maven\apache-maven-3.9.16\maven-mvnd-1.0.6-windows-amd64\mvn"
}
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;D:\DevTools\Docker\resources\bin;$env:Path"

if (-not $env:SPRING_DATASOURCE_URL) {
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3307/smart-campus?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
}
if (-not $env:SPRING_DATASOURCE_USERNAME) {
    $env:SPRING_DATASOURCE_USERNAME = "root"
}
if (-not $env:SPRING_DATASOURCE_PASSWORD) {
    $env:SPRING_DATASOURCE_PASSWORD = "123456"
}
if (-not $env:NEO4J_ENABLED) {
    $env:NEO4J_ENABLED = "false"
}
if (-not $env:SERVER_ADDRESS) {
    $env:SERVER_ADDRESS = "127.0.0.1"
}

Set-Location $Backend
& "$env:MAVEN_HOME\bin\mvn.cmd" spring-boot:run
exit $LASTEXITCODE
