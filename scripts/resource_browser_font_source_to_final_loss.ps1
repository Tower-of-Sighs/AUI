param(
    [string]$BrowserSourceLog = "run/resource-browser-font-source-raster-browser-column-profile-last.log",
    [string]$BrowserImage = "run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png",
    [string]$BrowserFinalMetricsLog = "run/resource-browser-font-raster-browser-background-pairs-last.log",
    [string]$AuiMaskDir = "run/font-raster-masks",
    [string]$AuiImage = "run/screenshots/aui/2026-07-15_18.03.59.png",
    [string]$AuiFinalMetricsLog = "run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-last.log",
    [string]$OutLog = "run/resource-browser-font-source-to-final-loss.log",
    [string]$SampleIds = "arial12White,arial13White",
    [string]$AuiSource = "outline-coverage-4x-row-clamp",
    [double]$GlyphDarknessThreshold = 20.0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function Read-JsonLine {
    param(
        [string]$Path,
        [string]$Prefix
    )
    $line = Get-Content -Path $Path | Where-Object { $_ -like "$Prefix *" } | Select-Object -First 1
    if (-not $line) {
        throw "$Prefix payload not found in $Path"
    }
    return ($line -replace "^$([regex]::Escape($Prefix))\s+", "") | ConvertFrom-Json
}

function Read-AuiMetrics {
    param([string]$Path)
    $metrics = @{
        viewport = $null
        samples = @{}
    }
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "resource-browser-font-raster viewport viewport=([0-9.]+)x([0-9.]+)") {
            $metrics.viewport = [pscustomobject]@{ width = [double]$Matches[1]; height = [double]$Matches[2] }
            continue
        }
        if ($line -match "resource-browser-font-raster sample id=([^ ]+) text='([^']*)' x=([-0-9.]+) y=([-0-9.]+) width=([-0-9.]+) height=([-0-9.]+) right=([-0-9.]+) bottom=([-0-9.]+) color=([^ ]+) backgroundColor=([^ ]+) cssFontFamily=(.*?) cssFontSize=([^ ]+) cssFontWeight=([^ ]+) cssLineHeight=([^ ]+) cssLetterSpacing=([^ ]+) textFontFamily=(.*?) textFontSize=([-0-9.]+) textFontWeight=([-0-9]+) textLineHeight=([-0-9.]+) textLetterSpacing=([-0-9.]+)") {
            $metrics.samples[$Matches[1]] = [pscustomobject]@{
                text = $Matches[2]
                rect = [pscustomobject]@{
                    x = [double]$Matches[3]
                    y = [double]$Matches[4]
                    width = [double]$Matches[5]
                    height = [double]$Matches[6]
                }
                backgroundColor = $Matches[10]
            }
        }
    }
    if (-not $metrics.viewport) {
        throw "AUI font raster viewport not found in $Path"
    }
    return $metrics
}

function Read-Metadata {
    param([string]$Path)
    $meta = @{}
    foreach ($line in Get-Content -Path $Path) {
        $index = $line.IndexOf("=")
        if ($index -lt 0) { continue }
        $meta[$line.Substring(0, $index)] = $line.Substring($index + 1)
    }
    $meta["metadataPath"] = $Path
    return $meta
}

function Convert-CssColorToRgb {
    param([string]$Color)
    if ([string]::IsNullOrWhiteSpace($Color)) {
        return [pscustomobject]@{ R = 255; G = 255; B = 255 }
    }
    $value = $Color.Trim()
    if ($value -match "^#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?$") {
        $hex = $Matches[1]
        return [pscustomobject]@{
            R = [Convert]::ToInt32($hex.Substring(0, 2), 16)
            G = [Convert]::ToInt32($hex.Substring(2, 2), 16)
            B = [Convert]::ToInt32($hex.Substring(4, 2), 16)
        }
    }
    if ($value -match "^rgb\(\s*([0-9]+)\s*,\s*([0-9]+)\s*,\s*([0-9]+)\s*\)$") {
        return [pscustomobject]@{ R = [int]$Matches[1]; G = [int]$Matches[2]; B = [int]$Matches[3] }
    }
    return [pscustomobject]@{ R = 255; G = 255; B = 255 }
}

function Get-ScaledRect {
    param([object]$Rect, [double]$ScaleX, [double]$ScaleY)
    return [pscustomobject]@{
        x = [Math]::Max(0, [int][Math]::Floor([double]$Rect.x * $ScaleX))
        y = [Math]::Max(0, [int][Math]::Floor([double]$Rect.y * $ScaleY))
        width = [Math]::Max(1, [int][Math]::Ceiling([double]$Rect.width * $ScaleX))
        height = [Math]::Max(1, [int][Math]::Ceiling([double]$Rect.height * $ScaleY))
    }
}

function Measure-FinalColumns {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [object]$Rect,
        [double]$ScaleX,
        [double]$ScaleY,
        [object]$Background,
        [double]$Threshold
    )
    $scaled = Get-ScaledRect $Rect $ScaleX $ScaleY
    $maxX = [Math]::Min($Bitmap.Width, $scaled.x + $scaled.width)
    $maxY = [Math]::Min($Bitmap.Height, $scaled.y + $scaled.height)
    $columns = @{}
    $ink = 0
    $minX = $maxX
    $maxInkX = $scaled.x
    $minY = $maxY
    $maxInkY = $scaled.y
    for ($y = $scaled.y; $y -lt $maxY; $y++) {
        for ($x = $scaled.x; $x -lt $maxX; $x++) {
            $color = $Bitmap.GetPixel($x, $y)
            $distance = [Math]::Abs($color.R - $Background.R) + [Math]::Abs($color.G - $Background.G) + [Math]::Abs($color.B - $Background.B)
            if ($distance -le 8) { continue }
            $darkness = ((($Background.R - $color.R) + ($Background.G - $color.G) + ($Background.B - $color.B)) / 3.0)
            if ($darkness -le $Threshold) { continue }
            if (-not $columns.ContainsKey($x)) {
                $columns[$x] = [pscustomobject]@{ ink = 0; value = 0.0 }
            }
            $columns[$x].ink = [int]$columns[$x].ink + 1
            $columns[$x].value = [double]$columns[$x].value + $darkness
            $ink++
            $minX = [Math]::Min($minX, $x)
            $maxInkX = [Math]::Max($maxInkX, $x)
            $minY = [Math]::Min($minY, $y)
            $maxInkY = [Math]::Max($maxInkY, $y)
        }
    }
    return [pscustomobject]@{
        ink = $ink
        bounds = if ($ink -eq 0) { "none" } else { "($minX,$minY,$($maxInkX - $minX + 1),$($maxInkY - $minY + 1))" }
        width = if ($ink -eq 0) { 0 } else { $maxInkX - $minX + 1 }
        minX = $minX
        maxX = $maxInkX
        columns = $columns
    }
}

function Measure-AlphaColumns {
    param([string]$ImagePath)
    $bitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $ImagePath))
    try {
        $columns = @{}
        $ink = 0
        $minX = $bitmap.Width
        $maxX = -1
        $minY = $bitmap.Height
        $maxY = -1
        for ($y = 0; $y -lt $bitmap.Height; $y++) {
            for ($x = 0; $x -lt $bitmap.Width; $x++) {
                $color = $bitmap.GetPixel($x, $y)
                $alpha = [int]$color.R
                if ($alpha -le 0) { continue }
                if (-not $columns.ContainsKey($x)) {
                    $columns[$x] = [pscustomobject]@{ ink = 0; value = 0.0 }
                }
                $columns[$x].ink = [int]$columns[$x].ink + 1
                $columns[$x].value = [double]$columns[$x].value + $alpha
                $ink++
                $minX = [Math]::Min($minX, $x)
                $maxX = [Math]::Max($maxX, $x)
                $minY = [Math]::Min($minY, $y)
                $maxY = [Math]::Max($maxY, $y)
            }
        }
        return [pscustomobject]@{
            ink = $ink
            bounds = if ($ink -eq 0) { "none" } else { "($minX,$minY,$($maxX - $minX + 1),$($maxY - $minY + 1))" }
            width = if ($ink -eq 0) { 0 } else { $maxX - $minX + 1 }
            minX = $minX
            maxX = $maxX
            columns = $columns
        }
    } finally {
        $bitmap.Dispose()
    }
}

function Convert-BrowserProfile {
    param([object[]]$ColumnProfile)
    $columns = @{}
    foreach ($column in $ColumnProfile) {
        $columns[[int]$column.x] = [pscustomobject]@{
            ink = [int]$column.ink
            value = [double]$column.avgA * [int]$column.ink
        }
    }
    return $columns
}

function Convert-RelativeColumns {
    param([hashtable]$Columns, [int]$OriginX)
    $relative = @{}
    foreach ($key in $Columns.Keys) {
        $relative[[int]$key - $OriginX] = $Columns[$key]
    }
    return $relative
}

function Format-Edges {
    param([hashtable]$Columns, [int]$Count = 4)
    if ($Columns.Count -eq 0) { return "[]" }
    $keys = @($Columns.Keys | Sort-Object {[int]$_})
    $selected = @()
    $selected += $keys | Select-Object -First $Count
    $selected += $keys | Select-Object -Last $Count
    $selected = $selected | Sort-Object {[int]$_} -Unique
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($key in $selected) {
        $col = $Columns[$key]
        $avg = if ($col.ink -eq 0) { 0.0 } else { [Math]::Round($col.value / $col.ink, 6) }
        $parts.Add("x=$key,ink=$($col.ink),avg=$avg")
    }
    return "[" + ($parts -join ";") + "]"
}

function Format-MissingEdges {
    param([hashtable]$SourceColumns, [hashtable]$FinalColumns)
    $missing = @{}
    foreach ($key in $SourceColumns.Keys) {
        if (-not $FinalColumns.ContainsKey($key)) {
            $missing[$key] = $SourceColumns[$key]
        }
    }
    return Format-Edges $missing
}

function Find-AuiMask {
    param([array]$Metadata, [object]$Sample, [string]$Source)
    $fontSize = [string]([double]$Sample.fontSize) + ".0"
    $letterSpacing = [string]([double]$Sample.letterSpacing) + ".0"
    $matches = $Metadata | Where-Object {
        $_.text -eq $Sample.text -and
        $_.fontFamily -eq $Sample.fontFamily -and
        $_.fontSize -eq $fontSize -and
        $_.letterSpacing -eq $letterSpacing -and
        $_.source -eq $Source -and
        $_.raster -like "physical:*" -and
        $_.composite -eq "transparent" -and
        $_.filter -eq "linear" -and
        $_.cacheKey -like "*$($Sample.backgroundColor)*"
    }
    if (-not $matches) { return $null }
    return $matches | Sort-Object { (Get-Item $_.metadataPath).LastWriteTime } -Descending | Select-Object -First 1
}

function Resolve-MaskPath {
    param([string]$MaskPath)
    if ($MaskPath.StartsWith("./")) { return Join-Path "run" $MaskPath.Substring(2) }
    return $MaskPath
}

function Convert-SampleList {
    param([string]$Value)
    $ids = New-Object System.Collections.Generic.List[string]
    foreach ($part in ($Value -split ",")) {
        $id = $part.Trim()
        if (-not [string]::IsNullOrWhiteSpace($id)) { $ids.Add($id) }
    }
    return $ids
}

function Convert-FinalSampleId {
    param([string]$SourceId)
    if ($SourceId -eq "arial13White") { return "arial13" }
    return $SourceId
}

$browserSource = Read-JsonLine $BrowserSourceLog "BROWSER_FONT_SOURCE_RASTER_METRICS"
$browserFinal = Read-JsonLine $BrowserFinalMetricsLog "BROWSER_FONT_RASTER_METRICS"
$auiFinal = Read-AuiMetrics $AuiFinalMetricsLog
$metadata = Get-ChildItem -Path $AuiMaskDir -Filter *.txt | ForEach-Object { Read-Metadata $_.FullName }
$browserBitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $BrowserImage))
$auiBitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $AuiImage))

try {
    $browserDpr = [double]$browserFinal.viewport.devicePixelRatio
    $browserScaleX = if ($browserDpr -gt 0) { $browserDpr } else { $browserBitmap.Width / [double]$browserFinal.viewport.innerWidth }
    $browserScaleY = if ($browserDpr -gt 0) { $browserDpr } else { $browserBitmap.Height / [double]$browserFinal.viewport.innerHeight }
    $auiScaleX = $auiBitmap.Width / [double]$auiFinal.viewport.width
    $auiScaleY = $auiBitmap.Height / [double]$auiFinal.viewport.height

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("browserSourceLog=$BrowserSourceLog browserFinalLog=$BrowserFinalMetricsLog browserImage=$BrowserImage")
    $lines.Add("auiFinalLog=$AuiFinalMetricsLog auiImage=$AuiImage auiMaskDir=$AuiMaskDir auiSource=$AuiSource thresholdDarkness=$GlyphDarknessThreshold")

    foreach ($sourceId in (Convert-SampleList $SampleIds)) {
        $finalId = Convert-FinalSampleId $sourceId
        $sourceSample = $browserSource.samples | Where-Object { $_.id -eq $sourceId } | Select-Object -First 1
        if (-not $sourceSample) {
            $lines.Add("sample=$sourceId missing browser source")
            continue
        }
        if (-not ($browserFinal.samples.PSObject.Properties.Name -contains $finalId)) {
            $lines.Add("sample=$sourceId finalId=$finalId missing browser final")
            continue
        }
        if (-not $auiFinal.samples.ContainsKey($finalId)) {
            $lines.Add("sample=$sourceId finalId=$finalId missing AUI final")
            continue
        }
        $auiMeta = Find-AuiMask $metadata $sourceSample $AuiSource
        if (-not $auiMeta) {
            $lines.Add("sample=$sourceId missing AUI mask source=$AuiSource")
            continue
        }

        $bSourceStats = $sourceSample.physicalDpr.sourceAlpha
        $bSourceColumns = Convert-BrowserProfile $bSourceStats.columnProfile
        $bSourceRelative = Convert-RelativeColumns $bSourceColumns ([int]$bSourceStats.bounds.x)

        $bCompositedStats = $sourceSample.physicalDpr.composited
        $bCompositedColumns = Convert-BrowserProfile $bCompositedStats.columnProfile
        $bCompositedRelative = Convert-RelativeColumns $bCompositedColumns ([int]$bCompositedStats.bounds.x)

        $browserFinalSample = $browserFinal.samples.$finalId
        $browserBg = Convert-CssColorToRgb $browserFinalSample.style.backgroundColor
        $bFinalStats = Measure-FinalColumns $browserBitmap $browserFinalSample.rect $browserScaleX $browserScaleY $browserBg $GlyphDarknessThreshold
        $bFinalRelative = Convert-RelativeColumns $bFinalStats.columns ([int]$bFinalStats.minX)

        $maskPath = Resolve-MaskPath $auiMeta.mask
        $aSourceStats = Measure-AlphaColumns $maskPath
        $aSourceRelative = Convert-RelativeColumns $aSourceStats.columns ([int]$aSourceStats.minX)

        $auiFinalSample = $auiFinal.samples[$finalId]
        $auiBg = Convert-CssColorToRgb $auiFinalSample.backgroundColor
        $aFinalStats = Measure-FinalColumns $auiBitmap $auiFinalSample.rect $auiScaleX $auiScaleY $auiBg $GlyphDarknessThreshold
        $aFinalRelative = Convert-RelativeColumns $aFinalStats.columns ([int]$aFinalStats.minX)

        $lines.Add("sample=$sourceId finalId=$finalId text='$($sourceSample.text)'")
        $lines.Add("browser sourceAlpha width=$($bSourceStats.bounds.width) ink=$($bSourceStats.ink) relativeRight=$([int]$bSourceStats.bounds.width - 1) edges=$(Format-Edges $bSourceRelative)")
        $lines.Add("browser composited width=$($bCompositedStats.bounds.width) ink=$($bCompositedStats.ink) relativeRight=$([int]$bCompositedStats.bounds.width - 1) sourceToCompositedWidthLoss=$([int]$bSourceStats.bounds.width - [int]$bCompositedStats.bounds.width) missingSourceEdges=$(Format-MissingEdges $bSourceRelative $bCompositedRelative)")
        $lines.Add("browser finalGlyph width=$($bFinalStats.width) ink=$($bFinalStats.ink) relativeRight=$($bFinalStats.width - 1) sourceToFinalWidthLoss=$([int]$bSourceStats.bounds.width - [int]$bFinalStats.width) missingSourceFinalEdges=$(Format-MissingEdges $bSourceRelative $bFinalRelative)")
        $lines.Add("aui sourceAlpha width=$($aSourceStats.width) ink=$($aSourceStats.ink) relativeRight=$($aSourceStats.width - 1) mask=$maskPath edges=$(Format-Edges $aSourceRelative)")
        $lines.Add("aui finalGlyph width=$($aFinalStats.width) ink=$($aFinalStats.ink) relativeRight=$($aFinalStats.width - 1) sourceToFinalWidthLoss=$($aSourceStats.width - $aFinalStats.width) missingSourceFinalEdges=$(Format-MissingEdges $aSourceRelative $aFinalRelative)")
    }

    $outDir = Split-Path -Parent $OutLog
    if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $lines | Set-Content -Path $OutLog -Encoding UTF8
    $lines
} finally {
    $browserBitmap.Dispose()
    $auiBitmap.Dispose()
}
