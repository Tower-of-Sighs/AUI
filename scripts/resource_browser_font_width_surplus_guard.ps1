param(
    [string]$BaselineColumnsLog = "run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1.log",
    [string]$CutoffColumnsLog = "run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1.log",
    [string]$AuiMetricsLog = "run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log",
    [string]$BaselineProjectionLog = "run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log",
    [string]$OutLog = "run/resource-browser-font-width-surplus-guard.log",
    [string]$SampleIds = "arial12White,arial13"
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
        $letterSpacing = [double]$Matches[5]
        $cssX = [double]$Matches[6]
        $cssY = [double]$Matches[7]
        $textureW = [int]$Matches[8]
        $textureH = [int]$Matches[9]
        $quadMode = $Matches[12]
        $physicalW = [double]$Matches[21]
        $quadScaleX = [double]$Matches[23]
        $sourceBounds = $Matches[25]
        $physicalInkWidth = [double]$Matches[28]
        $sourceW = $null
        if ($sourceBounds -match "^[-0-9]+,[-0-9]+,([-0-9]+),[-0-9]+$") {
            $sourceW = [int]$Matches[1]
        }
        $items.Add([pscustomobject]@{
            text = $text
            fontFamily = $fontFamily
            fontSize = $fontSize
            letterSpacing = $letterSpacing
            cssX = $cssX
            cssY = $cssY
            textureW = $textureW
            textureH = $textureH
            quadMode = $quadMode
            physicalW = $physicalW
            quadScaleX = $quadScaleX
            sourceInkBounds = $sourceBounds
            sourceInkWidth = $sourceW
            physicalInkWidth = $physicalInkWidth
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

$baselineColumns = Read-ColumnWidths $BaselineColumnsLog
$cutoffColumns = Read-ColumnWidths $CutoffColumnsLog
$samples = Read-AuiSamples $AuiMetricsLog
$projections = @(Read-Projections $BaselineProjectionLog)
$lines = New-Object System.Collections.Generic.List[string]

$lines.Add("baselineColumnsLog=$BaselineColumnsLog")
$lines.Add("cutoffColumnsLog=$CutoffColumnsLog")
$lines.Add("auiMetricsLog=$AuiMetricsLog")
$lines.Add("baselineProjectionLog=$BaselineProjectionLog")
$lines.Add("decisionRule=applyCutoffOnlyWhenBaselineFinalWidthSurplusGt0")

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

    $browser = $baselineColumns[$sampleId]["browser"]
    $baseline = $baselineColumns[$sampleId]["aui"]
    $cutoff = $cutoffColumns[$sampleId]["aui"]
    $projection = if ($samples.ContainsKey($sampleId)) {
        Select-ProjectionForSample $projections $samples[$sampleId]
    } else {
        $null
    }
    $surplus = [int]$baseline.width - [int]$browser.width
    $cutoffDelta = [int]$cutoff.width - [int]$baseline.width
    $cutoffError = [int]$cutoff.width - [int]$browser.width
    $guardWouldApply = $surplus -gt 0
    $guardedWidth = if ($guardWouldApply) { [int]$cutoff.width } else { [int]$baseline.width }
    $guardedError = $guardedWidth - [int]$browser.width
    $decision = if ($guardWouldApply) { "apply" } else { "skip" }

    $projectionText = if ($projection) {
        "sourceInkWidth=$($projection.sourceInkWidth) physicalInkWidth=$([Math]::Round($projection.physicalInkWidth, 6)) quadScaleX=$([Math]::Round($projection.quadScaleX, 6)) textureW=$($projection.textureW) sourceInkBounds=$($projection.sourceInkBounds)"
    } else {
        "sourceProjection=<missing>"
    }

    $lines.Add("sample=$sampleId browserWidth=$($browser.width) baselineAuiWidth=$($baseline.width) baselineSurplus=$surplus cutoffAuiWidth=$($cutoff.width) cutoffDelta=$cutoffDelta cutoffError=$cutoffError guardDecision=$decision guardedWidth=$guardedWidth guardedError=$guardedError $projectionText")
}

$outDir = Split-Path -Parent $OutLog
if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$lines | Set-Content -Path $OutLog -Encoding UTF8
$lines
