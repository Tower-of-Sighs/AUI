param(
    [string]$OracleGuardLog = "run/resource-browser-font-width-surplus-guard-source-cutoff-1.log",
    [string]$AuiMetricsLog = "run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log",
    [string]$BaselineProjectionLog = "run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log",
    [string]$OutLog = "run/resource-browser-font-runtime-edge-proxy.log",
    [string]$SampleIds = "arial12White,arial13",
    [double]$RightFracThreshold = 0.25
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

function Read-OracleGuard {
    param([string]$Path)
    $items = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "^sample=([^ ]+) .* guardDecision=([^ ]+) guardedWidth=([-0-9]+) guardedError=([-0-9]+)") {
            $items[$Matches[1]] = [pscustomobject]@{
                decision = $Matches[2]
                guardedWidth = [int]$Matches[3]
                guardedError = [int]$Matches[4]
                raw = $line
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
        $items.Add([pscustomobject]@{
            text = $Matches[1]
            fontFamily = $Matches[2]
            fontSize = [double]$Matches[3]
            letterSpacing = [double]$Matches[5]
            cssX = [double]$Matches[6]
            cssY = [double]$Matches[7]
            textureW = [int]$Matches[8]
            textureH = [int]$Matches[9]
            quadMode = $Matches[12]
            physicalInkX = [double]$Matches[26]
            physicalInkW = [double]$Matches[28]
            sourceInkBounds = $Matches[25]
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

function Get-Fraction {
    param([double]$Value)
    $floor = [Math]::Floor($Value)
    $fraction = $Value - $floor
    if ($fraction -lt 0) { $fraction += 1.0 }
    return $fraction
}

$oracle = Read-OracleGuard $OracleGuardLog
$samples = Read-AuiSamples $AuiMetricsLog
$projections = @(Read-Projections $BaselineProjectionLog)
$lines = New-Object System.Collections.Generic.List[string]

$lines.Add("oracleGuardLog=$OracleGuardLog")
$lines.Add("auiMetricsLog=$AuiMetricsLog")
$lines.Add("baselineProjectionLog=$BaselineProjectionLog")
$lines.Add("proxyRule=applyWhenPhysicalInkRightFracLe$RightFracThreshold")

foreach ($sampleId in (Convert-SampleList $SampleIds)) {
    if (-not $oracle.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId missing oracle guard")
        continue
    }
    if (-not $samples.ContainsKey($sampleId)) {
        $lines.Add("sample=$sampleId missing AUI sample metadata")
        continue
    }
    $projection = Select-ProjectionForSample $projections $samples[$sampleId]
    if (-not $projection) {
        $lines.Add("sample=$sampleId missing projection")
        continue
    }
    $right = [double]$projection.physicalInkX + [double]$projection.physicalInkW
    $fraction = Get-Fraction $right
    $proxyDecision = if ($fraction -le $RightFracThreshold) { "apply" } else { "skip" }
    $matchesOracle = $proxyDecision -eq $oracle[$sampleId].decision
    $lines.Add("sample=$sampleId physicalInkX=$([Math]::Round([double]$projection.physicalInkX, 6)) physicalInkW=$([Math]::Round([double]$projection.physicalInkW, 6)) physicalInkRight=$([Math]::Round($right, 6)) rightFrac=$([Math]::Round($fraction, 6)) sourceInkBounds=$($projection.sourceInkBounds) proxyDecision=$proxyDecision oracleDecision=$($oracle[$sampleId].decision) matchesOracle=$matchesOracle guardedWidth=$($oracle[$sampleId].guardedWidth) guardedError=$($oracle[$sampleId].guardedError)")
}

$outDir = Split-Path -Parent $OutLog
if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$lines | Set-Content -Path $OutLog -Encoding UTF8
$lines
