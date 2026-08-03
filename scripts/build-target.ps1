param(
    [Parameter(Mandatory = $true)]
    [string] $Target,

    [ValidateRange(1, 5)]
    [int] $Attempts = 3
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetsRoot = Join-Path $repoRoot 'targets'

if ([string]::IsNullOrWhiteSpace($Target) -or
    [System.IO.Path]::IsPathRooted($Target) -or
    $Target.IndexOfAny([System.IO.Path]::GetInvalidFileNameChars()) -ge 0 -or
    $Target.Contains('/') -or
    $Target.Contains('\') -or
    $Target.Contains('..')) {
    throw "Target must be a direct directory name under targets/: $Target"
}

$targetDirectory = Join-Path $targetsRoot $Target

if (-not (Test-Path -LiteralPath $targetDirectory -PathType Container)) {
    throw "Target directory not found: $targetDirectory"
}

$wrapper = Join-Path $targetDirectory 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
    throw "Target wrapper not found: $wrapper"
}

Push-Location $targetDirectory
try {
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        Write-Host "Building $Target (attempt $attempt of $Attempts)"
        & .\gradlew.bat clean build --console plain --no-daemon
        $exitCode = $LASTEXITCODE

        if ($exitCode -eq 0) {
            exit 0
        }

        if ($attempt -lt $Attempts) {
            Write-Warning "Build failed with exit code $exitCode. Retrying after 30 seconds."
            Start-Sleep -Seconds 30
        }
    }

    exit $exitCode
} finally {
    Pop-Location
}
