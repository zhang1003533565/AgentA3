# No-Docker local start helper for AgentA3 (Windows)
# Prerequisites: MySQL on 3306 (root/123456, db smart-campus), Redis optional on 6379
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $PSScriptRoot "pom.xml"))) {
  $Root = $PSScriptRoot
  $Backend = Join-Path $Root "AppBackend"
} else {
  $Backend = $PSScriptRoot
  $Root = Split-Path -Parent $Backend
}

$env:JAVA_HOME = if (Test-Path "C:\Program Files\Java\jdk-21.0.10") {
  "C:\Program Files\Java\jdk-21.0.10"
} else { $env:JAVA_HOME }

$MvnHome = "C:\Users\25876\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5"
if (Test-Path "$MvnHome\bin\mvn.cmd") {
  $env:Path = "$env:JAVA_HOME\bin;$MvnHome\bin;$env:Path"
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "Starting backend (no Docker) at http://localhost:8080 ..."
Set-Location $Backend
& mvn spring-boot:run
