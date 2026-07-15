param(
    [string]$BaselineColumnsLog = "run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-all.log",
    [string]$CutoffColumnsLog = "run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-all.log",
    [string]$OracleGuardLog = "run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log",
    [string]$ProxyLog = "run/resource-browser-font-runtime-edge-proxy-right-frac-0p75-target-physical-all.log",
    [string]$OutLog = "run/resource-browser-font-runtime-guarded-width.log",
    [string]$SampleIds = "arial13,arial13Plain,sans13,chakra13,arial10,arial12,chakra12,arial13White,arial13Fafafa,arial12White,arial12Fafafa,chakra12White,chakra12Fafafa"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Convert-SampleList {
    param([string]$Value)
    $ids = New-Object System.Collections.Generic.List[string]
    foreach ($part in ($Value -split ",")) {
        $id = $part.Trim()
        if (-not [string]::IsNullOrWhiteSpace($id)) { $ids.Add($id) }
    }
    return $ids
}

function Read-ColumnWidths {
    param([string]$Path)
    $items = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "^(browser|aui) columns=([^ ]+) .* bounds=\(([-0-9]+),([-0-9]+),([-0-9]+),([-0-9]+)\) width=([-0-9]+) ink=([-0-9]+)") {
            $kind = $Matches[1]
            $sample = $Matches[2]
            if (-not $items.ContainsKey($sample)) { $items[$sample] = @{} }
            $items[$sample][$kind] = [pscustomobject]@{
                boundsX = [int]$Matches[3]
                boundsY = [int]$Matches[4]
                boundsW = [int]$Matches[5]
                boundsH = [int]$Matches[6]
                width = [int]$Matches[7]
                ink = [int]$Matches[8]
            }
        }
    }
    return $items
}

function Read-Oracle {
    param([string]$Path)
    $items = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "^sample=([^ ]+) .* guardDecision=([^ ]+) guardedWidth=([-0-9]+) guardedError=([-0-9]+)") {
            $items[$Matches[1]] = [pscustomobject]@{
                decision = $Matches[2]
                guardedWidth = [int]$Matches[3]
                guardedError = [int]$Matches[4]
            }
        }
    }
    return $items
}

function Read-Proxy {
    param([string]$Path)
    $items = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "^sample=([^ ]+) .* rightFrac=([-0-9.]+) .* proxyDecision=([^ ]+) oracleDecision=([^ ]+) matchesOracle=([^ ]+)") {
            $items[$Matches[1]] = [pscustomobject]@{
                rightFrac = [double]$Matches[2]
                decision = $Matches[3]
                oracleDecision = $Matches[4]
                matchesOracle = [bool]::Parse($Matches[5])
            }
        }
    }
    return $items
}

$baselineColumns = Read-ColumnWidths $BaselineColumnsLog
$cutoffColumns = Read-ColumnWidths $CutoffColumnsLog
$oracle = Read-Oracle $OracleGuardLog
$proxy = Read-Proxy $ProxyLog

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("baselineColumnsLog=$BaselineColumnsLog")
$lines.Add("cutoffColumnsLog=$CutoffColumnsLog")
$lines.Add("oracleGuardLog=$OracleGuardLog")
$lines.Add("proxyLog=$ProxyLog")
$lines.Add("decisionRule=useCutoffWhenProxyDecisionApply")

$total = 0
$decisionMatches = 0
$improvedOrEqual = 0
$exact = 0

foreach ($sampleId in (Convert-SampleList $SampleIds)) {
    if (-not $baselineColumns.ContainsKey($sampleId) -or
        -not $baselineColumns[$sampleId].ContainsKey("browser") -or
        -not $baselineColumns[$sampleId].ContainsKey("aui")) {
        $lines.Add("sample=$sampleId missing baseline columns")
        continue
    }
    if (-not $cutoffColumns.ContainsKey($sampleId) -or -not $cutoffColumns[$sampleId].ContainsKey("aui")) {
        $lines.Add("sample=$sampleId missing cutoff columns")
        continue
    }
    if (-not $proxy.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId missing proxy")
        continue
    }
    if (-not $oracle.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId missing oracle")
        continue
    }

    $browser = $baselineColumns[$sampleId]["browser"]
    $baseline = $baselineColumns[$sampleId]["aui"]
    $cutoff = $cutoffColumns[$sampleId]["aui"]
    $proxyItem = $proxy[$sampleId]
    $oracleItem = $oracle[$sampleId]

    $selected = if ($proxyItem.decision -eq "apply") { $cutoff } else { $baseline }
    $baselineError = [int]$baseline.width - [int]$browser.width
    $cutoffError = [int]$cutoff.width - [int]$browser.width
    $selectedError = [int]$selected.width - [int]$browser.width
    $selectedKind = if ($proxyItem.decision -eq "apply") { "cutoff" } else { "baseline" }
    $decisionMatch = $proxyItem.decision -eq $oracleItem.decision
    $absBaselineError = [Math]::Abs($baselineError)
    $absSelectedError = [Math]::Abs($selectedError)
    $isImprovedOrEqual = $absSelectedError -le $absBaselineError
    $isExact = $selectedError -eq 0

    $total++
    if ($decisionMatch) { $decisionMatches++ }
    if ($isImprovedOrEqual) { $improvedOrEqual++ }
    if ($isExact) { $exact++ }

    $lines.Add("sample=$sampleId browserWidth=$($browser.width) baselineWidth=$($baseline.width) baselineError=$baselineError cutoffWidth=$($cutoff.width) cutoffError=$cutoffError rightFrac=$([Math]::Round($proxyItem.rightFrac, 6)) proxyDecision=$($proxyItem.decision) oracleDecision=$($oracleItem.decision) decisionMatch=$decisionMatch selected=$selectedKind selectedWidth=$($selected.width) selectedError=$selectedError improvedOrEqual=$isImprovedOrEqual exact=$isExact")
}

$lines.Add("summary total=$total decisionMatches=$decisionMatches improvedOrEqual=$improvedOrEqual exact=$exact")

$outDir = Split-Path -Parent $OutLog
if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$lines | Set-Content -Path $OutLog -Encoding UTF8
$lines
