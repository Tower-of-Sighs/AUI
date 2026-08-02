param(
    [int]$MaxAttempts = 5,
    [int]$AutoExitSeconds = 5,
    [int]$ViewportWidth = 427,
    [int]$ViewportHeight = 249
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$referencePath = Join-Path $root "run\apricity\apricityui\example.png"
$screenshotsDir = Join-Path $root "run\screenshots\aui"
$compareScript = Join-Path $root "tools\aui_compare.py"

function Get-LatestScreenshot {
    Get-ChildItem -LiteralPath $screenshotsDir -Filter *.png -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
}

if (!(Test-Path -LiteralPath $referencePath)) {
    throw "Reference image not found: $referencePath"
}
if (!(Test-Path -LiteralPath $screenshotsDir)) {
    New-Item -ItemType Directory -Path $screenshotsDir | Out-Null
}
if (!(Test-Path -LiteralPath $compareScript)) {
    throw "Compare script not found: $compareScript"
}

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    $before = Get-LatestScreenshot
    Write-Host ("Attempt {0}/{1}: starting client" -f $attempt, $MaxAttempts)

    & .\gradlew.bat runClient "-PauiAutoExitSeconds=$AutoExitSeconds" "-PauiViewportWidth=$ViewportWidth" "-PauiViewportHeight=$ViewportHeight" "-PauiLogStyles=true" "--console=plain"
    if ($LASTEXITCODE -ne 0) {
        throw "runClient failed with exit code $LASTEXITCODE"
    }

    $after = Get-LatestScreenshot
    if ($null -eq $after) {
        throw "No screenshot found after client exit."
    }
    if ($before -and $after.FullName -eq $before.FullName -and $after.LastWriteTimeUtc -le $before.LastWriteTimeUtc) {
        throw "Latest screenshot did not update after client exit."
    }

    Write-Host ("Latest screenshot: {0}" -f $after.FullName)

    $resultJson = & python $compareScript $referencePath $after.FullName
    if ($LASTEXITCODE -eq 0) {
        Write-Host $resultJson
        Write-Host "Result: page content matches reference closely enough."
        exit 0
    }

    Write-Host $resultJson
    Write-Host "Result: screenshot does not match reference yet."
}

throw "Preview loop did not produce a matching screenshot after $MaxAttempts attempts."
