# No-Docker / no-MySQL local demo start for AgentA3 (Windows)
# Uses embedded H2 + existing local Redis (if running).
$ErrorActionPreference = "Stop"

function Stop-WithError {
  param([string]$Message)
  Write-Error $Message
  exit 1
}

$Backend = $PSScriptRoot
if (-not (Test-Path (Join-Path $Backend "pom.xml"))) {
  Stop-WithError "Run this script from AppBackend"
}

$env:JAVA_HOME = if (Test-Path "C:\Program Files\Java\jdk-21.0.10") {
  "C:\Program Files\Java\jdk-21.0.10"
} else { $env:JAVA_HOME }

$MvnHome = "C:\Users\25876\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5"
if (Test-Path "$MvnHome\bin\mvn.cmd") {
  $env:Path = "$env:JAVA_HOME\bin;$MvnHome\bin;$env:Path"
}

# Clear MySQL overrides so the demo H2 datasource can take effect.
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_DRIVER_CLASS_NAME -ErrorAction SilentlyContinue

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "Starting demo backend (H2, no Docker/MySQL) at http://localhost:8080 ..."
Write-Host "App login: zzs / admin123"
Write-Host "Web admin: admin / 123456"
Set-Location $Backend
& mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
exit $LASTEXITCODE
