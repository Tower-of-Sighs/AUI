param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
)

$ErrorActionPreference = 'Stop'
$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$tempRoot = Join-Path $tempBase ('aui-vue-runtime-' + [Guid]::NewGuid().ToString('N'))
$previousPlugin = $env:AUI_RHINO_SEMANTICS_PLUGIN
$previousNodePath = $env:NODE_PATH
$previousNpmCache = $env:npm_config_cache

New-Item -ItemType Directory -Path $tempRoot | Out-Null
try {
    $env:npm_config_cache = Join-Path $tempRoot 'npm-cache'
    $packages = @(
        'vue@3.5.34',
        '@babel/core@7.28.4',
        '@babel/cli@7.28.3',
        '@babel/preset-env@7.28.3',
        '@babel/types@7.28.4'
    )
    & npm install --prefix $tempRoot --no-save --no-package-lock --ignore-scripts --audit=false --fund=false @packages
    if ($LASTEXITCODE -ne 0) { throw 'Pinned Vue and Babel package install failed' }

    $config = Join-Path $tempRoot 'babel.config.cjs'
    [IO.File]::WriteAllText($config, @'
module.exports = {
  comments: false,
  compact: false,
  assumptions: { superIsCallableConstructor: true },
  presets: [["@babel/preset-env", {
    targets: { ie: "11" },
    bugfixes: true,
    modules: false,
    useBuiltIns: false
  }]],
  plugins: [process.env.AUI_RHINO_SEMANTICS_PLUGIN]
};
'@, [Text.UTF8Encoding]::new($false))

    $env:AUI_RHINO_SEMANTICS_PLUGIN = (Resolve-Path (
        Join-Path $PSScriptRoot 'rhino-semantics.cjs')).Path
    $env:NODE_PATH = Join-Path $tempRoot 'node_modules'
    $source = Join-Path $tempRoot 'node_modules/vue/dist/vue.global.prod.js'
    $generated = Join-Path $tempRoot 'vue.aui.js'
    $babel = Join-Path $tempRoot 'node_modules/.bin/babel.cmd'
    & $babel $source --out-file $generated --config-file $config
    if ($LASTEXITCODE -ne 0) { throw 'Vue Babel transform failed' }

    $target = Join-Path $ProjectRoot (
        'common/src/main/resources/assets/apricityui/apricity/apricityui/runtime/vue.aui.js')
    Copy-Item -LiteralPath $generated -Destination $target -Force
    Write-Host "Refreshed $target"
} finally {
    $env:AUI_RHINO_SEMANTICS_PLUGIN = $previousPlugin
    $env:NODE_PATH = $previousNodePath
    $env:npm_config_cache = $previousNpmCache
    $resolved = [IO.Path]::GetFullPath($tempRoot)
    if (-not $resolved.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Temporary directory escaped its parent: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
