$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$imageName = if ($env:PPTX_EXPORT_DOCKER_IMAGE) {
    $env:PPTX_EXPORT_DOCKER_IMAGE
} else {
    "agent-a3-pptx-exporter:latest"
}

Write-Host "Building Linux Presenton PPTX exporter image: $imageName"
docker build --file (Join-Path $scriptRoot "Dockerfile.pptx-exporter") --tag $imageName $scriptRoot
Write-Host "PPTX exporter image is ready. Keep Docker Desktop running before generating PPTX."
