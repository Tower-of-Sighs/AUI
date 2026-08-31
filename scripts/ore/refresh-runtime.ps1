param(
    [Parameter(Mandatory = $true)]
    [string]$UpstreamRoot,
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$expectedCommit = 'ec87d29a9516a741e5bd4ac707dcabc704409cb2'
$actualCommit = (& git -C $UpstreamRoot rev-parse HEAD).Trim()
if ($actualCommit -ne $expectedCommit) {
    throw "Expected mcui-oreui $expectedCommit but found $actualCommit"
}

Push-Location $UpstreamRoot
try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw 'mcui-oreui build failed' }

    $runtimeRoot = Join-Path $ProjectRoot `
        'common\src\main\resources\assets\apricityui\apricity\apricityui\theme\ore\runtime'
    $tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('aui-ore-runtime-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    try {
        $config = Join-Path $tempRoot 'babel.config.cjs'
        [IO.File]::WriteAllText($config, @'
module.exports = {
  comments: false,
  compact: false,
  assumptions: {
    superIsCallableConstructor: true
  },
  presets: [["@babel/preset-env", {
    targets: { ie: "11" },
    bugfixes: true,
    modules: false,
    useBuiltIns: false
  }]],
  plugins: [process.env.AUI_RHINO_SEMANTICS_PLUGIN]
};
'@, [Text.UTF8Encoding]::new($false))

        $babelPackages = @(
            '@babel/core@7.28.4',
            '@babel/cli@7.28.3',
            '@babel/preset-env@7.28.3',
            '@babel/types@7.28.4'
        )
        & npm install --prefix $tempRoot --no-save --no-package-lock --ignore-scripts `
            --audit=false --fund=false @babelPackages
        if ($LASTEXITCODE -ne 0) { throw 'Babel toolchain install failed' }
        $babel = Join-Path $tempRoot 'node_modules\.bin\babel.cmd'
        $previousRhinoSemanticsPlugin = $env:AUI_RHINO_SEMANTICS_PLUGIN
        $previousNodePath = $env:NODE_PATH
        $env:AUI_RHINO_SEMANTICS_PLUGIN = (Resolve-Path (Join-Path $ProjectRoot 'scripts\ore\babel\rhino-semantics.cjs')).Path
        $env:NODE_PATH = Join-Path $tempRoot 'node_modules'
        $jobs = @(
            @{ Source = 'node_modules\vue\dist\vue.global.prod.js'; Target = 'vue.aui.js' },
            @{ Source = 'dist\mcui-oreui.umd.cjs'; Target = 'mcui-oreui.aui.js' }
        )
        try {
            foreach ($job in $jobs) {
                $source = Join-Path $UpstreamRoot $job.Source
                $target = Join-Path $runtimeRoot $job.Target
                & $babel $source --out-file $target --config-file $config
                if ($LASTEXITCODE -ne 0) { throw "Babel transform failed for $source" }
            }
        } finally {
            $env:AUI_RHINO_SEMANTICS_PLUGIN = $previousRhinoSemanticsPlugin
            $env:NODE_PATH = $previousNodePath
        }
    } finally {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
} finally {
    Pop-Location
}

& (Join-Path $PSScriptRoot 'refresh-integrity.ps1') -Mode Update -UpstreamRoot $UpstreamRoot -ProjectRoot $ProjectRoot
