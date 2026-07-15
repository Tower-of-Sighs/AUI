param(
    [string]$OriginalOracleGuardLog = "run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log",
    [string]$OriginalAuiMetricsLog = "run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log",
    [string]$OriginalProjectionLog = "run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log",
    [string]$CacheAuiMetricsLog = "run/resource-browser-font-raster-cache-safety-aui-runtime-right-frac-cutoff-0p75-last.log",
    [string]$CacheProjectionLog = "run/resource-browser-font-raster-cache-safety-aui-runtime-right-frac-cutoff-0p75-last.log",
    [string]$CacheColumnsLog = "run/resource-browser-font-raster-cache-safety-columns-runtime-right-frac-cutoff-0p75.log",
    [string]$SweepAuiMetricsLog = "run/resource-browser-font-raster-classifier-sweep-aui-baseline-texture-gutter-last.log",
    [string]$SweepProjectionLog = "run/resource-browser-font-raster-classifier-sweep-aui-baseline-texture-gutter-last.log",
    [string]$SweepBaselineColumnsLog = "run/resource-browser-font-raster-classifier-sweep-columns-baseline-texture-gutter.log",
    [string]$SweepCutoffColumnsLog = "run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-1-rerun.log",
    [string]$OutLog = "run/resource-browser-font-runtime-classifier-probe.log",
    [string]$OriginalSampleIds = "arial13,arial13Plain,sans13,chakra13,arial10,arial12,chakra12,arial13White,arial13Fafafa,arial12White,arial12Fafafa,chakra12White,chakra12Fafafa",
    [string]$CacheSampleIds = "cacheApply,cacheSkip",
    [string]$SweepSampleIds = "long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500,short13X114625"
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

function Get-Fraction {
    param([double]$Value)
    $floor = [Math]::Floor($Value)
    $fraction = $Value - $floor
    if ($fraction -lt 0) { $fraction += 1.0 }
    return $fraction
}

function Read-OracleGuard {
    param([string]$Path)
    $items = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "^sample=([^ ]+).* browserWidth=([-0-9]+) baselineAuiWidth=([-0-9]+) baselineSurplus=([-0-9]+) cutoffAuiWidth=([-0-9]+).* guardDecision=([^ ]+) guardedWidth=([-0-9]+) guardedError=([-0-9]+)") {
            $items[$Matches[1]] = [pscustomobject]@{
                sampleId = $Matches[1]
                browserWidth = [int]$Matches[2]
                baselineAuiWidth = [int]$Matches[3]
                baselineSurplus = [int]$Matches[4]
                cutoffAuiWidth = [int]$Matches[5]
                oracleDecision = $Matches[6]
                guardedWidth = [int]$Matches[7]
                guardedError = [int]$Matches[8]
            }
        }
    }
    return $items
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

function Read-AuiSamples {
    param([string]$Path)
    $items = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "resource-browser-font-raster sample id=([^ ]+) text='([^']*)' x=([-0-9.]+) y=([-0-9.]+) width=([-0-9.]+) height=([-0-9.]+) right=([-0-9.]+) bottom=([-0-9.]+) color=([^ ]+) backgroundColor=([^ ]+) cssFontFamily=(.*?) cssFontSize=([^ ]+) cssFontWeight=([^ ]+) cssLineHeight=([^ ]+) cssLetterSpacing=([^ ]+) textFontFamily=(.*?) textFontSize=([-0-9.]+) textFontWeight=([-0-9]+) textLineHeight=([-0-9.]+) textLetterSpacing=([-0-9.]+)") {
            $items[$Matches[1]] = [pscustomobject]@{
                id = $Matches[1]
                text = $Matches[2]
                x = [double]$Matches[3]
                y = [double]$Matches[4]
                width = [double]$Matches[5]
                height = [double]$Matches[6]
                right = [double]$Matches[7]
                bottom = [double]$Matches[8]
                textFontFamily = $Matches[16]
                textFontSize = [double]$Matches[17]
                textLetterSpacing = [double]$Matches[20]
            }
        }
    }
    return $items
}

function Read-Projections {
    param([string]$Path)
    $items = New-Object System.Collections.Generic.List[object]
    foreach ($line in Get-Content -Path $Path) {
        if ($line -notmatch "\[AUI FontRaster\] projection text='([^']*)' fontFamily=(.*?) fontSize=([-0-9.]+) renderedFontSize=([-0-9.]+) letterSpacing=([-0-9.]+) cssPosition=([-0-9.]+),([-0-9.]+) texture=([0-9]+)x([0-9]+) drawScale=([-0-9.]+) pixelScale=([-0-9.]+) quadMode=([^ ]+) uvInset=([-0-9.]+),([-0-9.]+) cssQuad=([-0-9.]+),([-0-9.]+),([-0-9.]+),([-0-9.]+) physicalQuad=([-0-9.]+),([-0-9.]+),([-0-9.]+),([-0-9.]+) quadScale=([-0-9.]+),([-0-9.]+) sourceInkBounds=([^ ]+) physicalInkBounds=([^,]+),([^,]+),([^,]+),([^ ]+)") {
            continue
        }
        $text = $Matches[1]
        $fontFamily = $Matches[2]
        $fontSize = [double]$Matches[3]
        $renderedFontSize = [double]$Matches[4]
        $letterSpacing = [double]$Matches[5]
        $cssX = [double]$Matches[6]
        $cssY = [double]$Matches[7]
        $textureW = [int]$Matches[8]
        $textureH = [int]$Matches[9]
        $drawScale = [double]$Matches[10]
        $pixelScale = [double]$Matches[11]
        $quadMode = $Matches[12]
        $physicalQuadX = [double]$Matches[19]
        $physicalQuadY = [double]$Matches[20]
        $physicalQuadW = [double]$Matches[21]
        $physicalQuadH = [double]$Matches[22]
        $quadScaleX = [double]$Matches[23]
        $quadScaleY = [double]$Matches[24]
        $sourceBounds = $Matches[25]
        $physicalInkX = [double]$Matches[26]
        $physicalInkW = [double]$Matches[28]
        $sourceX = $null
        $sourceY = $null
        $sourceW = $null
        $sourceH = $null
        if ($sourceBounds -match "^([-0-9]+),([-0-9]+),([-0-9]+),([-0-9]+)$") {
            $sourceX = [int]$Matches[1]
            $sourceY = [int]$Matches[2]
            $sourceW = [int]$Matches[3]
            $sourceH = [int]$Matches[4]
        }
        $physicalInkRight = $physicalInkX + $physicalInkW
        $items.Add([pscustomobject]@{
            text = $text
            fontFamily = $fontFamily
            fontSize = $fontSize
            renderedFontSize = $renderedFontSize
            letterSpacing = $letterSpacing
            cssX = $cssX
            cssY = $cssY
            textureW = $textureW
            textureH = $textureH
            drawScale = $drawScale
            pixelScale = $pixelScale
            quadMode = $quadMode
            physicalQuadX = $physicalQuadX
            physicalQuadY = $physicalQuadY
            physicalQuadW = $physicalQuadW
            physicalQuadH = $physicalQuadH
            quadScaleX = $quadScaleX
            quadScaleY = $quadScaleY
            sourceInkBounds = $sourceBounds
            sourceInkX = $sourceX
            sourceInkY = $sourceY
            sourceInkWidth = $sourceW
            sourceInkHeight = $sourceH
            physicalInkX = $physicalInkX
            physicalInkW = $physicalInkW
            physicalInkRight = $physicalInkRight
            physicalInkRightFrac = Get-Fraction $physicalInkRight
        })
    }
    return $items
}

function Select-ProjectionForSample {
    param([array]$Projections, [object]$Sample)
    if (-not $Sample) { return $null }
    $matches = $Projections | Where-Object {
        $_.text -eq $Sample.text -and
        $_.fontFamily -eq $Sample.textFontFamily -and
        [Math]::Abs($_.fontSize - [double]$Sample.textFontSize) -lt 0.001 -and
        [Math]::Abs($_.letterSpacing - [double]$Sample.textLetterSpacing) -lt 0.001 -and
        $_.cssX -ge ($Sample.x - 8.0) -and
        $_.cssX -le ($Sample.right + 8.0) -and
        $_.cssY -ge ($Sample.y - 8.0) -and
        $_.cssY -le ($Sample.bottom + 8.0)
    }
    if (-not $matches) {
        $matches = $Projections | Where-Object {
            $_.text -eq $Sample.text -and
            [Math]::Abs($_.fontSize - [double]$Sample.textFontSize) -lt 0.001 -and
            [Math]::Abs($_.letterSpacing - [double]$Sample.textLetterSpacing) -lt 0.001
        }
    }
    return $matches | Sort-Object @{ Expression = { [Math]::Abs($_.cssY - $Sample.y) } }, @{ Expression = { [Math]::Abs($_.cssX - $Sample.x) } } | Select-Object -First 1
}

function New-ClassifierRow {
    param(
        [string]$SampleId,
        [string]$Group,
        [object]$Oracle,
        [object]$Projection
    )
    [pscustomobject]@{
        sampleId = $SampleId
        group = $Group
        oracleDecision = $Oracle.oracleDecision
        oracleDecisionKind = $Oracle.oracleDecisionKind
        browserWidth = $Oracle.browserWidth
        observedAuiWidth = $Oracle.observedAuiWidth
        baselineAuiWidth = $Oracle.baselineAuiWidth
        cutoffAuiWidth = $Oracle.cutoffAuiWidth
        baselineError = $Oracle.baselineError
        cutoffError = $Oracle.cutoffError
        fontFamily = $Projection.fontFamily
        fontSize = $Projection.fontSize
        renderedFontSize = $Projection.renderedFontSize
        letterSpacing = $Projection.letterSpacing
        cssX = $Projection.cssX
        cssY = $Projection.cssY
        textureW = $Projection.textureW
        textureH = $Projection.textureH
        sourceInkBounds = $Projection.sourceInkBounds
        sourceInkY = $Projection.sourceInkY
        sourceInkWidth = $Projection.sourceInkWidth
        sourceInkHeight = $Projection.sourceInkHeight
        physicalInkRight = $Projection.physicalInkRight
        physicalInkRightFrac = $Projection.physicalInkRightFrac
        text = $Projection.text
    }
}

function Get-CandidateDecision {
    param([string]$Rule, [object]$Row)
    if ($Rule -eq "rightFracLe0p75") {
        return $(if ($Row.physicalInkRightFrac -le 0.75) { "apply" } else { "skip" })
    }
    if ($Rule -eq "rightFracLe0p75OrLong12pxSource") {
        $long12pxSource = $Row.fontSize -le 12.0 -and $Row.sourceInkWidth -ge 300
        return $(if ($Row.physicalInkRightFrac -le 0.75 -or $long12pxSource) { "apply" } else { "skip" })
    }
    if ($Rule -eq "strictRuntimeV1") {
        $is12px = $Row.fontSize -le 12.0
        $long12pxSource = $is12px -and $Row.sourceInkWidth -ge 360
        $fractional12pxEdge = $is12px -and $Row.physicalInkRightFrac -le 0.75
        $narrow13pxBrowserLikeApply = $Row.fontSize -eq 13.0 -and $Row.sourceInkWidth -le 95 -and $Row.physicalInkRightFrac -le 0.75
        return $(if ($long12pxSource -or $fractional12pxEdge -or $narrow13pxBrowserLikeApply) { "apply" } else { "skip" })
    }
    throw "Unknown rule: $Rule"
}

$originalOracle = Read-OracleGuard $OriginalOracleGuardLog
$originalSamples = Read-AuiSamples $OriginalAuiMetricsLog
$originalProjections = @(Read-Projections $OriginalProjectionLog)
$cacheSamples = Read-AuiSamples $CacheAuiMetricsLog
$cacheProjections = @(Read-Projections $CacheProjectionLog)
$cacheColumns = Read-ColumnWidths $CacheColumnsLog
$sweepSamples = Read-AuiSamples $SweepAuiMetricsLog
$sweepProjections = @(Read-Projections $SweepProjectionLog)
$sweepBaselineColumns = Read-ColumnWidths $SweepBaselineColumnsLog
$sweepCutoffColumns = Read-ColumnWidths $SweepCutoffColumnsLog
$rows = New-Object System.Collections.Generic.List[object]
$lines = New-Object System.Collections.Generic.List[string]

$lines.Add("originalOracleGuardLog=$OriginalOracleGuardLog")
$lines.Add("originalAuiMetricsLog=$OriginalAuiMetricsLog")
$lines.Add("originalProjectionLog=$OriginalProjectionLog")
$lines.Add("cacheAuiMetricsLog=$CacheAuiMetricsLog")
$lines.Add("cacheProjectionLog=$CacheProjectionLog")
$lines.Add("cacheColumnsLog=$CacheColumnsLog")
$lines.Add("sweepAuiMetricsLog=$SweepAuiMetricsLog")
$lines.Add("sweepProjectionLog=$SweepProjectionLog")
$lines.Add("sweepBaselineColumnsLog=$SweepBaselineColumnsLog")
$lines.Add("sweepCutoffColumnsLog=$SweepCutoffColumnsLog")
$lines.Add("oracle=original13UsesTargetPhysicalWidthSurplusGuard;cacheSafetyBothSamplesRequireApplyToMatchChromiumWidth;sweepUsesCloserBaselineOrCutoffWidthAgainstChromiumAtThreshold20")
$lines.Add("candidateRules=rightFracLe0p75,rightFracLe0p75OrLong12pxSource,strictRuntimeV1")

foreach ($sampleId in (Convert-SampleList $OriginalSampleIds)) {
    if (-not $originalOracle.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId group=original13 missing original oracle")
        continue
    }
    if (-not $originalSamples.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId group=original13 missing original AUI sample")
        continue
    }
    $projection = Select-ProjectionForSample $originalProjections $originalSamples[$sampleId]
    if (-not $projection) {
        $lines.Add("sample=$sampleId group=original13 missing original projection")
        continue
    }
    $oracle = [pscustomobject]@{
        oracleDecision = $originalOracle[$sampleId].oracleDecision
        oracleDecisionKind = "decisive"
        browserWidth = $originalOracle[$sampleId].browserWidth
        observedAuiWidth = $originalOracle[$sampleId].baselineAuiWidth
        baselineAuiWidth = $originalOracle[$sampleId].baselineAuiWidth
        cutoffAuiWidth = $originalOracle[$sampleId].cutoffAuiWidth
        baselineError = [Math]::Abs($originalOracle[$sampleId].baselineAuiWidth - $originalOracle[$sampleId].browserWidth)
        cutoffError = [Math]::Abs($originalOracle[$sampleId].cutoffAuiWidth - $originalOracle[$sampleId].browserWidth)
    }
    $rows.Add((New-ClassifierRow -SampleId $sampleId -Group "original13" -Oracle $oracle -Projection $projection))
}

foreach ($sampleId in (Convert-SampleList $CacheSampleIds)) {
    if (-not $cacheSamples.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId group=cacheSafety missing cache AUI sample")
        continue
    }
    if (-not $cacheColumns.ContainsKey($sampleId) -or -not $cacheColumns[$sampleId].ContainsKey("browser") -or -not $cacheColumns[$sampleId].ContainsKey("aui")) {
        $lines.Add("sample=$sampleId group=cacheSafety missing cache column widths")
        continue
    }
    $projection = Select-ProjectionForSample $cacheProjections $cacheSamples[$sampleId]
    if (-not $projection) {
        $lines.Add("sample=$sampleId group=cacheSafety missing cache projection")
        continue
    }
    $oracle = [pscustomobject]@{
        oracleDecision = "apply"
        oracleDecisionKind = "decisive"
        browserWidth = $cacheColumns[$sampleId]["browser"].width
        observedAuiWidth = $cacheColumns[$sampleId]["aui"].width
        baselineAuiWidth = $null
        cutoffAuiWidth = $cacheColumns[$sampleId]["aui"].width
        baselineError = $null
        cutoffError = [Math]::Abs($cacheColumns[$sampleId]["aui"].width - $cacheColumns[$sampleId]["browser"].width)
    }
    $rows.Add((New-ClassifierRow -SampleId $sampleId -Group "cacheSafety" -Oracle $oracle -Projection $projection))
}

foreach ($sampleId in (Convert-SampleList $SweepSampleIds)) {
    if (-not $sweepSamples.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId group=classifierSweep missing sweep AUI sample")
        continue
    }
    if (-not $sweepBaselineColumns.ContainsKey($sampleId) -or -not $sweepBaselineColumns[$sampleId].ContainsKey("browser") -or -not $sweepBaselineColumns[$sampleId].ContainsKey("aui")) {
        $lines.Add("sample=$sampleId group=classifierSweep missing baseline column widths")
        continue
    }
    if (-not $sweepCutoffColumns.ContainsKey($sampleId) -or -not $sweepCutoffColumns[$sampleId].ContainsKey("aui")) {
        $lines.Add("sample=$sampleId group=classifierSweep missing cutoff column widths")
        continue
    }
    $projection = Select-ProjectionForSample $sweepProjections $sweepSamples[$sampleId]
    if (-not $projection) {
        $lines.Add("sample=$sampleId group=classifierSweep missing sweep projection")
        continue
    }

    $browserWidth = $sweepBaselineColumns[$sampleId]["browser"].width
    $baselineWidth = $sweepBaselineColumns[$sampleId]["aui"].width
    $cutoffWidth = $sweepCutoffColumns[$sampleId]["aui"].width
    $baselineError = [Math]::Abs($baselineWidth - $browserWidth)
    $cutoffError = [Math]::Abs($cutoffWidth - $browserWidth)
    $decision = "ambiguous"
    $decisionKind = "ambiguous"
    if ($cutoffError -lt $baselineError) {
        $decision = "apply"
        $decisionKind = "decisive"
    } elseif ($baselineError -lt $cutoffError) {
        $decision = "skip"
        $decisionKind = "decisive"
    } elseif ($baselineError -eq 0 -and $cutoffError -eq 0) {
        $decisionKind = "ambiguousExact"
    } else {
        $decisionKind = "ambiguousBad"
    }

    $oracle = [pscustomobject]@{
        oracleDecision = $decision
        oracleDecisionKind = $decisionKind
        browserWidth = $browserWidth
        observedAuiWidth = $baselineWidth
        baselineAuiWidth = $baselineWidth
        cutoffAuiWidth = $cutoffWidth
        baselineError = $baselineError
        cutoffError = $cutoffError
    }
    $rows.Add((New-ClassifierRow -SampleId $sampleId -Group "classifierSweep" -Oracle $oracle -Projection $projection))
}

$rules = @("rightFracLe0p75", "rightFracLe0p75OrLong12pxSource", "strictRuntimeV1")
foreach ($rule in $rules) {
    $mismatchCount = 0
    $decisiveCount = 0
    $ambiguousBadCount = 0
    $exactWidthCount = 0
    $widthErrorSum = 0
    foreach ($row in $rows) {
        $decision = Get-CandidateDecision -Rule $rule -Row $row
        if ($row.oracleDecisionKind -eq "decisive") {
            $decisiveCount++
            if ($decision -ne $row.oracleDecision) { $mismatchCount++ }
        } elseif ($row.oracleDecisionKind -eq "ambiguousBad") {
            $ambiguousBadCount++
        }
        $chosenWidth = $(if ($decision -eq "apply") { $row.cutoffAuiWidth } else { $row.baselineAuiWidth })
        if ($null -ne $chosenWidth) {
            $error = [Math]::Abs($chosenWidth - $row.browserWidth)
            $widthErrorSum += $error
            if ($error -eq 0) { $exactWidthCount++ }
        }
    }
    $matched = $decisiveCount - $mismatchCount
    $accepted = ($mismatchCount -eq 0 -and $ambiguousBadCount -eq 0)
    $lines.Add("summary rule=$rule decisiveMatched=$matched decisiveTotal=$decisiveCount mismatches=$mismatchCount ambiguousBad=$ambiguousBadCount exactChosenWidths=$exactWidthCount totalRows=$($rows.Count) widthErrorSum=$widthErrorSum accepted=$accepted")
}

foreach ($row in $rows) {
    $decisionParts = New-Object System.Collections.Generic.List[string]
    foreach ($rule in $rules) {
        $decision = Get-CandidateDecision -Rule $rule -Row $row
        $chosenWidth = $(if ($decision -eq "apply") { $row.cutoffAuiWidth } else { $row.baselineAuiWidth })
        $chosenError = $(if ($null -eq $chosenWidth) { "na" } else { [Math]::Abs($chosenWidth - $row.browserWidth) })
        $decisionMatch = $(if ($row.oracleDecisionKind -eq "decisive") { $decision -eq $row.oracleDecision } else { "ambiguous" })
        $decisionParts.Add("$rule=$decision/match=$decisionMatch/chosenWidth=$chosenWidth/chosenError=$chosenError")
    }
    $lines.Add("sample=$($row.sampleId) group=$($row.group) oracleDecision=$($row.oracleDecision) oracleDecisionKind=$($row.oracleDecisionKind) browserWidth=$($row.browserWidth) observedAuiWidth=$($row.observedAuiWidth) baselineAuiWidth=$($row.baselineAuiWidth) cutoffAuiWidth=$($row.cutoffAuiWidth) baselineError=$($row.baselineError) cutoffError=$($row.cutoffError) fontFamily=$($row.fontFamily) fontSize=$($row.fontSize) letterSpacing=$($row.letterSpacing) cssPosition=$([Math]::Round($row.cssX, 6)),$([Math]::Round($row.cssY, 6)) texture=$($row.textureW)x$($row.textureH) sourceInkBounds=$($row.sourceInkBounds) physicalInkRight=$([Math]::Round($row.physicalInkRight, 6)) rightFrac=$([Math]::Round($row.physicalInkRightFrac, 6)) text='$($row.text)' decisions=$($decisionParts -join ';')")
}

$outDir = Split-Path -Parent $OutLog
if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$lines | Set-Content -Path $OutLog -Encoding UTF8
$lines
