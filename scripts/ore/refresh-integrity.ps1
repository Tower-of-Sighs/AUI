param(
    [ValidateSet('Verify', 'Update')]
    [string]$Mode = 'Verify',
    [string]$UpstreamRoot,
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$expectedCommit = 'd3344a6cec68ce97eb990c125ed3e050c69d7d4c'

if ($UpstreamRoot) {
    $upstreamCommit = (& git -C $UpstreamRoot rev-parse HEAD).Trim()
    if ($upstreamCommit -ne $expectedCommit) {
        throw "Expected mcui-oreui $expectedCommit but found $upstreamCommit"
    }
    $componentCount = (Get-ChildItem -LiteralPath (Join-Path $UpstreamRoot 'src\components') `
            -Filter 'Mc*' -Directory).Count
    if ($componentCount -ne 69) {
        throw "Expected 69 upstream Mc components but found $componentCount"
    }
}

$roots = @(
    (Join-Path $ProjectRoot 'common\src\main\resources\assets\apricityui\apricity\apricityui\theme\ore'),
    (Join-Path $ProjectRoot 'common\src\main\resources\assets\apricityui\apricity\apricityui\runtime\mcui')
)
foreach ($root in $roots) {
    $manifest = Join-Path $root 'provenance.sha256'
    $files = @(Get-ChildItem -LiteralPath $root -File -Recurse |
        Where-Object { $_.FullName -ne $manifest } |
        Sort-Object { $_.FullName.Substring($root.Length + 1).Replace('\', '/') })
    $lines = foreach ($file in $files) {
        $relativePath = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relativePath"
    }
    $expected = ($lines -join "`n") + "`n"
    if ($Mode -eq 'Update') {
        [IO.File]::WriteAllText($manifest, $expected, [Text.UTF8Encoding]::new($false))
        Write-Host "Updated $manifest with $($files.Count) resources"
        continue
    }
    if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) {
        throw "Missing integrity manifest: $manifest"
    }
    $actual = [IO.File]::ReadAllText($manifest).Replace("`r`n", "`n")
    if ($actual -ne $expected) {
        throw "Stale integrity manifest: $manifest"
    }
    Write-Host "Verified $($files.Count) resources against $manifest"
}
