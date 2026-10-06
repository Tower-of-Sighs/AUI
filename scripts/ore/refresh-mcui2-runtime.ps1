param(
    [Parameter(Mandatory = $true)][string]$UpstreamRoot,
    [Parameter(Mandatory = $true)][string]$TempRoot,
    [Parameter(Mandatory = $true)][string]$CacheRoot,
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$upstream = (Resolve-Path -LiteralPath $UpstreamRoot).Path
$temp = (Resolve-Path -LiteralPath $TempRoot).Path
$cache = (Resolve-Path -LiteralPath $CacheRoot).Path
$project = (Resolve-Path -LiteralPath $ProjectRoot).Path
$expectedCommit = 'd3344a6cec68ce97eb990c125ed3e050c69d7d4c'
$actualCommit = (& git -C $upstream rev-parse HEAD).Trim()
if ($actualCommit -ne $expectedCommit) {
    throw "Expected mcui-oreui $expectedCommit, found $actualCommit"
}

$previousTemp = $env:TEMP
$previousTmp = $env:TMP
$previousTmpDir = $env:TMPDIR
$previousNpmCache = $env:npm_config_cache
$previousNodePath = $env:NODE_PATH
$previousRhinoPlugin = $env:AUI_RHINO_SEMANTICS_PLUGIN
$env:TEMP = $temp
$env:TMP = $temp
$env:TMPDIR = $temp
$env:npm_config_cache = $cache

try {
    $bundle = Join-Path $temp 'mcui2-build'
    if (Test-Path -LiteralPath $bundle) {
        throw "Build directory must not exist: $bundle"
    }

    Push-Location $upstream
    try {
        if (-not (Test-Path -LiteralPath (Join-Path $upstream 'node_modules\vite\dist\node\index.js'))) {
            & npm ci --ignore-scripts --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { throw 'Upstream npm ci failed' }
        }
        & npm run generate
        if ($LASTEXITCODE -ne 0) { throw 'Upstream generated registry failed' }
    } finally {
        Pop-Location
    }

    & node (Join-Path $project 'scripts\ore\build-mcui2-runtime.mjs') $upstream $bundle
    if ($LASTEXITCODE -ne 0) { throw 'AUI IIFE build failed' }

    $babelRoot = Join-Path $temp 'aui-babel-tools'
    $babel = Join-Path $babelRoot 'node_modules\@babel\cli\bin\babel.js'
    if (-not (Test-Path -LiteralPath $babel)) {
        & npm install --prefix $babelRoot --no-save --no-package-lock --ignore-scripts `
            --audit=false --fund=false `
            '@babel/core@7.28.4' '@babel/cli@7.28.3' `
            '@babel/preset-env@7.28.3' '@babel/types@7.28.4'
        if ($LASTEXITCODE -ne 0) { throw 'Babel toolchain install failed' }
    }

    $config = Join-Path $temp 'mcui2-babel.config.cjs'
    [IO.File]::WriteAllText($config, @'
module.exports = {
  comments: false,
  compact: false,
  assumptions: { superIsCallableConstructor: true },
  presets: [['@babel/preset-env', {
    targets: { ie: '11' }, bugfixes: true, modules: false, useBuiltIns: false
  }]],
  plugins: [process.env.AUI_RHINO_SEMANTICS_PLUGIN]
};
'@, [Text.UTF8Encoding]::new($false))
    $env:NODE_PATH = Join-Path $babelRoot 'node_modules'
    $env:AUI_RHINO_SEMANTICS_PLUGIN = Join-Path $project 'scripts\runtime\rhino-semantics.cjs'

    $scripts = @('mcui-oreui', 'mcui-icons-normal', 'mcui-icons-key',
        'mcui-icons-x', 'mcui-sounds-default')
    foreach ($name in $scripts) {
        $source = Join-Path $bundle "$name.aui.iife.js"
        $target = Join-Path $bundle "$name.aui.js"
        & node $babel $source --out-file $target --config-file $config
        if ($LASTEXITCODE -ne 0) { throw "Rhino transform failed: $name" }
        & node --check $target
        if ($LASTEXITCODE -ne 0) { throw "JavaScript syntax check failed: $name" }
    }
    $gallerySource = Join-Path $bundle 'gallery\gallery.aui.iife.js'
    $galleryTarget = Join-Path $bundle 'gallery\gallery.aui.js'
    & node $babel $gallerySource --out-file $galleryTarget --config-file $config
    if ($LASTEXITCODE -ne 0) { throw 'Rhino transform failed: gallery' }
    & node --check $galleryTarget
    if ($LASTEXITCODE -ne 0) { throw 'JavaScript syntax check failed: gallery' }

    $runtime = Join-Path $project `
        'common\src\main\resources\assets\apricityui\apricity\apricityui\runtime\mcui'
    if (-not $runtime.StartsWith($project + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Runtime target escaped project: $runtime"
    }
    $fontTarget = Join-Path $runtime 'fonts'
    New-Item -ItemType Directory -Path $fontTarget -Force | Out-Null
    foreach ($name in $scripts) {
        Copy-Item -LiteralPath (Join-Path $bundle "$name.aui.js") `
            -Destination (Join-Path $runtime "$name.aui.js") -Force
    }
    Copy-Item -LiteralPath $galleryTarget -Destination (Join-Path $runtime 'gallery.aui.js') -Force
    Copy-Item -LiteralPath (Join-Path $bundle 'gallery\style.css') `
        -Destination (Join-Path $runtime 'gallery.css') -Force
    Copy-Item -LiteralPath (Join-Path $bundle 'style.css') `
        -Destination (Join-Path $runtime 'components.css') -Force
    Copy-Item -LiteralPath (Join-Path $bundle 'fonts.css') `
        -Destination (Join-Path $runtime 'fonts.css') -Force
    foreach ($name in @('Minecraft-Ten.otf', 'Minecraft-Seven.otf',
            'Minecraft-Five.otf', 'Minecraft-Five-Bold.otf')) {
        Copy-Item -LiteralPath (Join-Path $bundle "fonts\$name") `
            -Destination (Join-Path $fontTarget $name) -Force
    }
    Copy-Item -LiteralPath (Join-Path $upstream 'LICENSE') `
        -Destination (Join-Path $runtime 'LICENSE.txt') -Force

    $manifest = Join-Path $runtime 'provenance.sha256'
    $files = Get-ChildItem -LiteralPath $runtime -Recurse -File |
        Where-Object { $_.FullName -ne $manifest } |
        Sort-Object { $_.FullName.Substring($runtime.Length + 1).Replace('\', '/') }
    $lines = foreach ($file in $files) {
        $relative = $file.FullName.Substring($runtime.Length + 1).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relative"
    }
    [IO.File]::WriteAllText($manifest, ($lines -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
    Write-Host "Refreshed mcui-oreui $actualCommit with $($files.Count) verified resources"
} finally {
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
    $env:TMPDIR = $previousTmpDir
    $env:npm_config_cache = $previousNpmCache
    $env:NODE_PATH = $previousNodePath
    $env:AUI_RHINO_SEMANTICS_PLUGIN = $previousRhinoPlugin
}
