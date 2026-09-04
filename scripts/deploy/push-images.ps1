# Local build + push to Aliyun ACR (no CI).
#
# Four public repos (Docker):
#   lukezhang/agentbackend   <- AppBackend
#   lukezhang/agentweb       <- AppWeb
#   lukezhang/agentfrontend  <- Frontend
#   lukezhang/agentai        <- ai-servers
# AppFrontend (移动端 APP) 不推镜像，用 HBuilderX 单独导出安装包。
#
# Usage:
#   .\scripts\deploy\push-images.ps1
#   .\scripts\deploy\push-images.ps1 -Tag 20260904 -AlsoLatest
#   .\scripts\deploy\push-images.ps1 -Services backend,web,frontend
#   .\scripts\deploy\push-images.ps1 -SkipLogin
#   .\scripts\deploy\push-images.ps1 -Username aliyun1551583868

[CmdletBinding()]
param(
    [string]$Registry = "crpi-awzm63dqn5ugddo8.cn-hangzhou.personal.cr.aliyuncs.com",
    [string]$Namespace = "lukezhang",
    [string]$Tag = "latest",
    [string]$Platform = "linux/amd64",
    [string]$Username = "aliyun1551583868",
    [string]$DockerHubMirror = "docker.1ms.run/library",
    [ValidateSet("all", "backend", "web", "frontend", "ai")]
    [string[]]$Services = @("all"),
    [switch]$AlsoLatest,
    [switch]$SkipLogin,
    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Set-Location $RepoRoot

$imageMap = [ordered]@{
    backend = @{
        Name    = "$Registry/$Namespace/agentbackend"
        Context = "AppBackend"
        File    = "AppBackend/Dockerfile"
    }
    web = @{
        Name    = "$Registry/$Namespace/agentweb"
        Context = "AppWeb"
        File    = "AppWeb/Dockerfile"
    }
    frontend = @{
        Name    = "$Registry/$Namespace/agentfrontend"
        Context = "Frontend"
        File    = "Frontend/Dockerfile"
    }
    ai = @{
        Name    = "$Registry/$Namespace/agentai"
        Context = "ai-servers"
        File    = "ai-servers/Dockerfile"
    }
}

if ($Services -contains "all") {
    $selected = @("backend", "web", "frontend", "ai")
} else {
    $selected = $Services
}

Write-Host "[push] Repo: $RepoRoot"
Write-Host "[push] Registry: $Registry/$Namespace"
Write-Host "[push] Tag: $Tag"
Write-Host "[push] Platform: $Platform"
Write-Host "[push] Services: $($selected -join ', ')"
Write-Host "[push] DockerHubMirror: $DockerHubMirror"

if (-not $SkipLogin) {
    Write-Host "[push] Logging in as $Username ..."
    docker login --username=$Username $Registry
    if ($LASTEXITCODE -ne 0) {
        throw "docker login failed"
    }
}

$pushed = @()
$skipped = @()

foreach ($key in $selected) {
    $item = $imageMap[$key]
    $image = $item.Name
    $tagged = "${image}:${Tag}"
    $dockerfile = Join-Path $RepoRoot $item.File
    $context = Join-Path $RepoRoot $item.Context

    if (-not (Test-Path $dockerfile)) {
        Write-Host ""
        Write-Host "[push] SKIP $key : missing $($item.File)"
        $skipped += $key
        continue
    }
    if (-not (Test-Path $context)) {
        Write-Host ""
        Write-Host "[push] SKIP $key : missing context $($item.Context)"
        $skipped += $key
        continue
    }

    Write-Host ""
    Write-Host "[push] Building $tagged ..."
    docker build --platform $Platform `
        --build-arg "DOCKER_HUB_MIRROR=$DockerHubMirror" `
        -f $item.File -t $tagged $item.Context
    if ($LASTEXITCODE -ne 0) {
        throw "docker build failed for $key"
    }

    if ($AlsoLatest -and $Tag -ne "latest") {
        docker tag $tagged "${image}:latest"
        if ($LASTEXITCODE -ne 0) {
            throw "docker tag failed for $key"
        }
    }

    if (-not $SkipPush) {
        Write-Host "[push] Pushing $tagged ..."
        docker push $tagged
        if ($LASTEXITCODE -ne 0) {
            throw "docker push failed for $key"
        }

        if ($AlsoLatest -and $Tag -ne "latest") {
            Write-Host "[push] Pushing ${image}:latest ..."
            docker push "${image}:latest"
            if ($LASTEXITCODE -ne 0) {
                throw "docker push latest failed for $key"
            }
        }
    }

    $pushed += $tagged
}

Write-Host ""
Write-Host "[push] Done."
if ($pushed.Count -gt 0) {
    Write-Host "[push] Images:"
    foreach ($name in $pushed) {
        Write-Host "  - $name"
    }
}
if ($skipped.Count -gt 0) {
    Write-Host "[push] Skipped: $($skipped -join ', ')"
}
Write-Host ""
Write-Host "[push] AppFrontend: export APP separately (not pushed)."
Write-Host "[push] Server deploy/.env example:"
Write-Host "  IMAGE_TAG=$Tag"
Write-Host "  BACKEND_IMAGE=$Registry/$Namespace/agentbackend"
Write-Host "  WEB_IMAGE=$Registry/$Namespace/agentweb"
Write-Host "  FRONTEND_IMAGE=$Registry/$Namespace/agentfrontend"
Write-Host "  AI_SERVER_IMAGE=$Registry/$Namespace/agentai"
