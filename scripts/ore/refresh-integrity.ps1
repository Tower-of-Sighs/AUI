param(
    [ValidateSet('Verify', 'Update')]
    [string]$Mode = 'Verify',
    [string]$UpstreamRoot,
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$expectedCommit = 'ec87d29a9516a741e5bd4ac707dcabc704409cb2'

if ($UpstreamRoot) {
    $upstreamCommit = (& git -C $UpstreamRoot rev-parse HEAD).Trim()
    if ($upstreamCommit -ne $expectedCommit) {
        throw "Expected mcui-oreui $expectedCommit but found $upstreamCommit"
    }
    $componentCount = (Get-ChildItem -LiteralPath (Join-Path $UpstreamRoot 'src\components') `
            -Filter 'Mc*.vue' -File).Count
    if ($componentCount -ne 33) {
        throw "Expected 33 upstream Mc components but found $componentCount"
    }
}

$themeRoot = Join-Path $ProjectRoot `
    'common\src\main\resources\assets\apricityui\apricity\apricityui\theme\ore'
$manifest = Join-Path $themeRoot 'provenance.sha256'
$files = Get-ChildItem -LiteralPath $themeRoot -File -Recurse |
    Where-Object { $_.FullName -ne $manifest } |
    Sort-Object { $_.FullName.Substring($themeRoot.Length + 1).Replace('\', '/') }

$lines = foreach ($file in $files) {
    $relativePath = $file.FullName.Substring($themeRoot.Length + 1).Replace('\', '/')
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $relativePath"
}
$expected = ($lines -join "`n") + "`n"

if ($Mode -eq 'Update') {
    [IO.File]::WriteAllText($manifest, $expected, [Text.UTF8Encoding]::new($false))
    Write-Host "Updated $manifest with $($files.Count) Ore resources"
    exit 0
}

if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) {
    throw "Missing Ore integrity manifest: $manifest"
}
$actual = [IO.File]::ReadAllText($manifest).Replace("`r`n", "`n")
if ($actual -ne $expected) {
    throw "Ore integrity manifest is stale. Run scripts/ore/refresh-integrity.ps1 -Mode Update after reviewing resource changes."
}
Write-Host "Verified $($files.Count) Ore resources against $manifest"
