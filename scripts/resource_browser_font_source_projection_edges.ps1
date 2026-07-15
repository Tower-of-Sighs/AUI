param(
    [string]$AuiMetricsLog = "run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-last.log",
    [string]$AuiImage = "",
    [string]$AuiMaskDir = "run/font-raster-masks",
    [string]$OutLog = "run/resource-browser-font-source-projection-edges.log",
    [string]$SampleIds = "arial12White,arial13",
    [string]$AuiSource = "outline-coverage-4x-row-clamp",
    [string]$QuadMode = "snap-physical-y-texture-gutter-1",
    [double]$GlyphDarknessThreshold = 20.0,
    [int]$RightColumns = 4
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

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
                id = $Matches[1]
                text = $Matches[2]
                rect = [pscustomobject]@{
                    x = [double]$Matches[3]
                    y = [double]$Matches[4]
                    width = [double]$Matches[5]
                    height = [double]$Matches[6]
                }
                backgroundColor = $Matches[10]
                textFontFamily = $Matches[16]
                textFontSize = [double]$Matches[17]
                textLetterSpacing = [double]$Matches[20]
            }
        }
    }
    if (-not $metrics.viewport) {
        throw "AUI font raster viewport not found in $Path"
    }
    return $metrics
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
            pixelScale = [double]$Matches[11]
            quadMode = $Matches[12]
            physicalX = [double]$Matches[19]
            physicalY = [double]$Matches[20]
            physicalW = [double]$Matches[21]
            physicalH = [double]$Matches[22]
            quadScaleX = [double]$Matches[23]
            quadScaleY = [double]$Matches[24]
            sourceInkBounds = $Matches[25]
        })
    }
    return $items
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
    return [pscustomobject]$meta
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

function Measure-GlyphPixels {
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
                $columns[$x] = [pscustomobject]@{ pixels = New-Object System.Collections.Generic.List[int]; darkness = 0.0 }
            }
            $columns[$x].pixels.Add($y)
            $columns[$x].darkness = [double]$columns[$x].darkness + $darkness
            $ink++
            $minX = [Math]::Min($minX, $x)
            $maxInkX = [Math]::Max($maxInkX, $x)
            $minY = [Math]::Min($minY, $y)
            $maxInkY = [Math]::Max($maxInkY, $y)
        }
    }
    return [pscustomobject]@{
        ink = $ink
        minX = $minX
        maxX = $maxInkX
        minY = $minY
        maxY = $maxInkY
        width = if ($ink -eq 0) { 0 } else { $maxInkX - $minX + 1 }
        height = if ($ink -eq 0) { 0 } else { $maxInkY - $minY + 1 }
        columns = $columns
    }
}

function Resolve-MaskPath {
    param([string]$MaskPath)
    if ($MaskPath.StartsWith("./")) { return Join-Path "run" $MaskPath.Substring(2) }
    return $MaskPath
}

function Find-MaskMetadata {
    param([array]$Metadata, [object]$Sample, [string]$Source, [string]$Mode)
    $fontSize = [string]$Sample.textFontSize
    if ($fontSize -notmatch "\.") { $fontSize = "$fontSize.0" }
    $letterSpacing = [string]$Sample.textLetterSpacing
    if ($letterSpacing -notmatch "\.") { $letterSpacing = "$letterSpacing.0" }
    $matches = $Metadata | Where-Object {
        $_.text -eq $Sample.text -and
        $_.fontFamily -eq $Sample.textFontFamily -and
        $_.fontSize -eq $fontSize -and
        $_.letterSpacing -eq $letterSpacing -and
        $_.source -eq $Source -and
        $_.raster -like "physical:*" -and
        $_.composite -eq "transparent" -and
        $_.filter -eq "linear" -and
        $_.cacheKey -like "*$($Sample.backgroundColor)*" -and
        $_.cacheKey -like "*quadTexture=$Mode*"
    }
    if (-not $matches) { return $null }
    return $matches | Sort-Object { (Get-Item $_.metadataPath).LastWriteTime } -Descending | Select-Object -First 1
}

function Find-Projection {
    param([array]$Projections, [object]$Sample, [string]$Mode)
    $matches = $Projections | Where-Object {
        $_.text -eq $Sample.text -and
        $_.fontFamily -eq $Sample.textFontFamily -and
        [Math]::Abs($_.fontSize - $Sample.textFontSize) -lt 0.001 -and
        [Math]::Abs($_.letterSpacing - $Sample.textLetterSpacing) -lt 0.001 -and
        $_.quadMode -eq $Mode
    }
    if (-not $matches) { return $null }
    return $matches | Sort-Object { [Math]::Abs($_.cssX - $Sample.rect.x) + [Math]::Abs($_.cssY - $Sample.rect.y) } | Select-Object -First 1
}

function Measure-AlphaColumns {
    param([string]$ImagePath)
    $bitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $ImagePath))
    try {
        $columns = @{}
        for ($x = 0; $x -lt $bitmap.Width; $x++) {
            $ink = 0
            $sum = 0
            $max = 0
            for ($y = 0; $y -lt $bitmap.Height; $y++) {
                $alpha = [int]$bitmap.GetPixel($x, $y).R
                if ($alpha -le 0) { continue }
                $ink++
                $sum += $alpha
                $max = [Math]::Max($max, $alpha)
            }
            if ($ink -gt 0) {
                $columns[$x] = [pscustomobject]@{ ink = $ink; avg = [Math]::Round($sum / [double]$ink, 6); max = $max }
            }
        }
        return $columns
    } finally {
        $bitmap.Dispose()
    }
}

function Format-AlphaColumns {
    param([hashtable]$Columns, [int[]]$Indexes)
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($index in ($Indexes | Sort-Object -Unique)) {
        if ($Columns.ContainsKey($index)) {
            $col = $Columns[$index]
            $parts.Add("src=$index,ink=$($col.ink),avgAlpha=$($col.avg),maxAlpha=$($col.max)")
        } else {
            $parts.Add("src=$index,ink=0,avgAlpha=0,maxAlpha=0")
        }
    }
    return "[" + ($parts -join ";") + "]"
}

function Format-FinalColumnProjection {
    param([int]$X, [object]$Column, [object]$Projection, [hashtable]$AlphaColumns)
    $ink = $Column.pixels.Count
    $avgDarkness = if ($ink -eq 0) { 0.0 } else { [Math]::Round([double]$Column.darkness / $ink, 6) }
    $normalized = (($X + 0.5d - $Projection.physicalX) / $Projection.physicalW) * $Projection.textureW
    $sourceCenter = $normalized - 0.5d
    $nearestSource = [int][Math]::Floor($normalized)
    $left = [Math]::Max(0, [int][Math]::Floor($sourceCenter))
    $right = [Math]::Min($Projection.textureW - 1, $left + 1)
    $footprint = @($left, $right)
    return "finalX=$X,relative=$($X - $script:CurrentGlyphMinX),ink=$ink,avgDarkness=$avgDarkness,normalizedSource=$([Math]::Round($normalized,6)),texelCenter=$([Math]::Round($sourceCenter,6)),nearestSource=$nearestSource,bilinearFootprint=$left,$right,sourceAlpha=$(Format-AlphaColumns $AlphaColumns $footprint)"
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

if ([string]::IsNullOrWhiteSpace($AuiImage)) {
    $AuiImage = (Get-ChildItem -Path "run/screenshots/aui" -Filter "*.png" | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

$aui = Read-AuiMetrics $AuiMetricsLog
$projections = @(Read-Projections $AuiMetricsLog)
$metadata = @(Get-ChildItem -Path $AuiMaskDir -Filter *.txt | ForEach-Object { Read-Metadata $_.FullName })
$bitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $AuiImage))

try {
    $scaleX = $bitmap.Width / [double]$aui.viewport.width
    $scaleY = $bitmap.Height / [double]$aui.viewport.height
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("auiMetricsLog=$AuiMetricsLog auiImage=$AuiImage auiMaskDir=$AuiMaskDir source=$AuiSource quadMode=$QuadMode thresholdDarkness=$GlyphDarknessThreshold rightColumns=$RightColumns")
    $lines.Add("auiImageSize=$($bitmap.Width)x$($bitmap.Height) viewport=$($aui.viewport.width)x$($aui.viewport.height) scale=$([Math]::Round($scaleX,6)),$([Math]::Round($scaleY,6))")

    foreach ($sampleId in (Convert-SampleList $SampleIds)) {
        if (-not $aui.samples.ContainsKey($sampleId)) {
            $lines.Add("sample=$sampleId missing AUI sample")
            continue
        }
        $sample = $aui.samples[$sampleId]
        $projection = Find-Projection $projections $sample $QuadMode
        $maskMeta = Find-MaskMetadata $metadata $sample $AuiSource $QuadMode
        if (-not $projection) {
            $lines.Add("sample=$sampleId missing projection quadMode=$QuadMode")
            continue
        }
        if (-not $maskMeta) {
            $lines.Add("sample=$sampleId missing mask source=$AuiSource quadMode=$QuadMode")
            continue
        }
        $maskPath = Resolve-MaskPath $maskMeta.mask
        $alphaColumns = Measure-AlphaColumns $maskPath
        $background = Convert-CssColorToRgb $sample.backgroundColor
        $glyph = Measure-GlyphPixels $bitmap $sample.rect $scaleX $scaleY $background $GlyphDarknessThreshold

        $lines.Add("sample=$sampleId text='$($sample.text)' rect=$($sample.rect.x),$($sample.rect.y),$($sample.rect.width),$($sample.rect.height) background=$($sample.backgroundColor)")
        $lines.Add("projection texture=$($projection.textureW)x$($projection.textureH) physicalQuad=$($projection.physicalX),$($projection.physicalY),$($projection.physicalW),$($projection.physicalH) quadScale=$($projection.quadScaleX),$($projection.quadScaleY) sourceInkBounds=$($projection.sourceInkBounds) mask=$maskPath")
        $lines.Add("finalGlyph bounds=$($glyph.minX),$($glyph.minY),$($glyph.width),$($glyph.height) ink=$($glyph.ink)")
        if ($glyph.ink -le 0) { continue }
        $script:CurrentGlyphMinX = $glyph.minX
        $keys = @($glyph.columns.Keys | Sort-Object {[int]$_} | Select-Object -Last $RightColumns)
        foreach ($key in $keys) {
            $lines.Add("edgeProjection sample=$sampleId $(Format-FinalColumnProjection ([int]$key) $glyph.columns[$key] $projection $alphaColumns)")
        }
    }

    $outDir = Split-Path -Parent $OutLog
    if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $lines | Set-Content -Path $OutLog -Encoding UTF8
    $lines
} finally {
    $bitmap.Dispose()
}
