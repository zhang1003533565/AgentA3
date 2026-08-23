param(
    [Parameter(Mandatory = $true)][string]$Pptx,
    [Parameter(Mandatory = $true)][string]$OutputDir
)

$ErrorActionPreference = "Stop"
$pptxPath = [System.IO.Path]::GetFullPath($Pptx)
$outputPath = [System.IO.Path]::GetFullPath($OutputDir)
if (-not (Test-Path -LiteralPath $pptxPath -PathType Leaf)) {
    throw "PPTX not found: $pptxPath"
}
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

$powerPoint = $null
$presentation = $null
try {
    $powerPoint = New-Object -ComObject PowerPoint.Application
    # Do not assign Visible here. Some Office installations reject changing
    # this COM property in a non-interactive session even when PowerPoint can
    # open and export presentations normally.
    $presentation = $powerPoint.Presentations.Open($pptxPath, $true, $false, $false)
    for ($index = 1; $index -le $presentation.Slides.Count; $index++) {
        $slide = $presentation.Slides.Item($index)
        $target = Join-Path $outputPath ("slide-{0}.png" -f $index)
        $slide.Export($target, "PNG")
    }
    [PSCustomObject]@{
        renderer = "Microsoft PowerPoint COM"
        slideCount = $presentation.Slides.Count
        outputDir = $outputPath
    } | ConvertTo-Json -Compress
}
finally {
    if ($presentation -ne $null) { $presentation.Close() }
    if ($powerPoint -ne $null) { $powerPoint.Quit() }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
