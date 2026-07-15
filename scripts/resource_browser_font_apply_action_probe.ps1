param(
    [string]$ClassifierProbeLog = "run/resource-browser-font-runtime-classifier-probe-with-sweep.log",
    [string]$BaselineColumnsLog = "run/resource-browser-font-raster-classifier-sweep-columns-baseline-texture-gutter.log",
    [string]$Cutoff1ColumnsLog = "run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-1-rerun.log",
    [string]$Cutoff2ColumnsLog = "run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-2.log",
    [string]$OutLog = "run/resource-browser-font-apply-action-probe.log",
    [string]$SampleIds = "long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500,short13X114625"
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
        if ($line -match "^(browser|aui) columns=([^ ]+) .* width=([-0-9]+) ink=([-0-9]+)") {
            $kind = $Matches[1]
            $sample = $Matches[2]
            if (-not $items.ContainsKey($sample)) { $items[$sample] = @{} }
            $items[$sample][$kind] = [pscustomobject]@{
                width = [int]$Matches[3]
                ink = [int]$Matches[4]
            }
        }
    }
    return $items
}

function Read-StrictRuntimeDecisions {
    param([string]$Path)
    $items = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ($line -notmatch "^sample=([^ ]+).* group=classifierSweep .* fontSize=([-0-9.]+) .* cssPosition=([-0-9.]+),([-0-9.]+) .* sourceInkBounds=([^ ]+) physicalInkRight=([-0-9.]+) rightFrac=([-0-9.]+) text='([^']*)' decisions=.*strictRuntimeV1=([^/]+)") {
            continue
        }
        $sampleId = $Matches[1]
        $fontSize = [double]$Matches[2]
        $cssX = [double]$Matches[3]
        $cssY = [double]$Matches[4]
        $sourceBounds = $Matches[5]
        $physicalInkRight = [double]$Matches[6]
        $rightFrac = [double]$Matches[7]
        $text = $Matches[8]
        $decision = $Matches[9]
        $sourceWidth = $null
        if ($sourceBounds -match "^([-0-9]+),([-0-9]+),([-0-9]+),([-0-9]+)$") {
            $sourceWidth = [int]$Matches[3]
        }
        $items[$sampleId] = [pscustomobject]@{
            sampleId = $sampleId
            fontSize = $fontSize
            cssX = $cssX
            cssY = $cssY
            sourceInkBounds = $sourceBounds
            sourceInkWidth = $sourceWidth
            physicalInkRight = $physicalInkRight
            rightFrac = $rightFrac
            text = $text
            strictRuntimeV1 = $decision
        }
    }
    return $items
}

function Get-ActionWidth {
    param(
        [string]$Action,
        [object]$Row
    )
    switch ($Action) {
        "skip" { return $Row.baselineWidth }
        "cutoff1" { return $Row.cutoff1Width }
        "cutoff2" { return $Row.cutoff2Width }
        default { throw "Unknown action: $Action" }
    }
}

function Get-CandidateAction {
    param(
        [string]$Rule,
        [object]$Row
    )
    switch ($Rule) {
        "strictRuntimeV1Cutoff1" {
            if ($Row.strictRuntimeV1 -eq "apply") { return "cutoff1" }
            return "skip"
        }
        "strictRuntimeV1Cutoff2" {
            if ($Row.strictRuntimeV1 -eq "apply") { return "cutoff2" }
            return "skip"
        }
        "runtime12pxFracBandV1" {
            if ($Row.strictRuntimeV1 -eq "skip") { return "skip" }
            if ($Row.fontSize -le 12.0 -and $Row.sourceInkWidth -ge 340) {
                if ($Row.rightFrac -gt 0.18 -and $Row.rightFrac -lt 0.82) { return "cutoff2" }
            }
            return "cutoff1"
        }
        "runtime12pxPhysicalPhaseV1" {
            if ($Row.strictRuntimeV1 -eq "skip") { return "skip" }
            if ($Row.fontSize -le 12.0 -and $Row.sourceInkWidth -ge 340) {
                $physicalFloor = [Math]::Floor($Row.physicalInkRight)
                $evenFloor = ([int]$physicalFloor % 2) -eq 0
                if ($Row.rightFrac -gt 0.18 -and $Row.rightFrac -lt 0.82 -and ($evenFloor -or $Row.rightFrac -lt 0.25)) {
                    return "cutoff2"
                }
            }
            return "cutoff1"
        }
        "bestAvailableAction" {
            $actions = @("skip", "cutoff1", "cutoff2")
            return $actions | Sort-Object @{ Expression = { [Math]::Abs((Get-ActionWidth -Action $_ -Row $Row) - $Row.browserWidth) } }, @{ Expression = { $_ } } | Select-Object -First 1
        }
        default {
            throw "Unknown rule: $Rule"
        }
    }
}

$baseline = Read-ColumnWidths $BaselineColumnsLog
$cutoff1 = Read-ColumnWidths $Cutoff1ColumnsLog
$cutoff2 = Read-ColumnWidths $Cutoff2ColumnsLog
$decisions = Read-StrictRuntimeDecisions $ClassifierProbeLog
$rows = New-Object System.Collections.Generic.List[object]
$lines = New-Object System.Collections.Generic.List[string]

$lines.Add("classifierProbeLog=$ClassifierProbeLog")
$lines.Add("baselineColumnsLog=$BaselineColumnsLog")
$lines.Add("cutoff1ColumnsLog=$Cutoff1ColumnsLog")
$lines.Add("cutoff2ColumnsLog=$Cutoff2ColumnsLog")
$lines.Add("oracle=Chromium final text-column width at thresholdDarkness=20")
$lines.Add("candidateActions=skip,cutoff1,cutoff2")
$lines.Add("candidateRules=strictRuntimeV1Cutoff1,strictRuntimeV1Cutoff2,runtime12pxFracBandV1,runtime12pxPhysicalPhaseV1,bestAvailableAction")

foreach ($sampleId in (Convert-SampleList $SampleIds)) {
    if (-not $baseline.ContainsKey($sampleId) -or -not $baseline[$sampleId].ContainsKey("browser") -or -not $baseline[$sampleId].ContainsKey("aui")) {
        $lines.Add("sample=$sampleId missing baseline/browser widths")
        continue
    }
    if (-not $cutoff1.ContainsKey($sampleId) -or -not $cutoff1[$sampleId].ContainsKey("aui")) {
        $lines.Add("sample=$sampleId missing cutoff1 width")
        continue
    }
    if (-not $cutoff2.ContainsKey($sampleId) -or -not $cutoff2[$sampleId].ContainsKey("aui")) {
        $lines.Add("sample=$sampleId missing cutoff2 width")
        continue
    }
    if (-not $decisions.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId missing strictRuntimeV1 decision")
        continue
    }
    $rows.Add([pscustomobject]@{
        sampleId = $sampleId
        browserWidth = $baseline[$sampleId]["browser"].width
        baselineWidth = $baseline[$sampleId]["aui"].width
        cutoff1Width = $cutoff1[$sampleId]["aui"].width
        cutoff2Width = $cutoff2[$sampleId]["aui"].width
        fontSize = $decisions[$sampleId].fontSize
        cssX = $decisions[$sampleId].cssX
        cssY = $decisions[$sampleId].cssY
        sourceInkBounds = $decisions[$sampleId].sourceInkBounds
        sourceInkWidth = $decisions[$sampleId].sourceInkWidth
        physicalInkRight = $decisions[$sampleId].physicalInkRight
        rightFrac = $decisions[$sampleId].rightFrac
        text = $decisions[$sampleId].text
        strictRuntimeV1 = $decisions[$sampleId].strictRuntimeV1
    })
}

$rules = @("strictRuntimeV1Cutoff1", "strictRuntimeV1Cutoff2", "runtime12pxFracBandV1", "runtime12pxPhysicalPhaseV1", "bestAvailableAction")
foreach ($rule in $rules) {
    $exact = 0
    $errorSum = 0
    $maxError = 0
    foreach ($row in $rows) {
        $action = Get-CandidateAction -Rule $rule -Row $row
        $width = Get-ActionWidth -Action $action -Row $row
        $error = [Math]::Abs($width - $row.browserWidth)
        $errorSum += $error
        if ($error -gt $maxError) { $maxError = $error }
        if ($error -eq 0) { $exact++ }
    }
    $lines.Add("summary rule=$rule exact=$exact total=$($rows.Count) errorSum=$errorSum maxError=$maxError accepted=$($exact -eq $rows.Count)")
}

foreach ($row in $rows) {
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($rule in $rules) {
        $action = Get-CandidateAction -Rule $rule -Row $row
        $width = Get-ActionWidth -Action $action -Row $row
        $parts.Add("$rule=$action/width=$width/error=$($width - $row.browserWidth)")
    }
    $lines.Add("sample=$($row.sampleId) browserWidth=$($row.browserWidth) baseline=$($row.baselineWidth) baselineError=$($row.baselineWidth - $row.browserWidth) cutoff1=$($row.cutoff1Width) cutoff1Error=$($row.cutoff1Width - $row.browserWidth) cutoff2=$($row.cutoff2Width) cutoff2Error=$($row.cutoff2Width - $row.browserWidth) strictRuntimeV1=$($row.strictRuntimeV1) fontSize=$($row.fontSize) cssPosition=$([Math]::Round($row.cssX, 6)),$([Math]::Round($row.cssY, 6)) sourceInkBounds=$($row.sourceInkBounds) physicalInkRight=$([Math]::Round($row.physicalInkRight, 6)) rightFrac=$([Math]::Round($row.rightFrac, 6)) text='$($row.text)' choices=$($parts -join ';')")
}

$outDir = Split-Path -Parent $OutLog
if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$lines | Set-Content -Path $OutLog -Encoding UTF8
$lines
