param(
    [string]$BrowserImage = "run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png",
    [string]$AuiImage = "",
    [string]$BrowserMetricsLog = "run/resource-browser-font-raster-browser-last.log",
    [string]$AuiMetricsLog = "run/resource-browser-font-raster-aui-last.log",
    [string]$OutLog = "run/resource-browser-font-raster-samples.log",
    [string]$GlyphDarknessThresholds = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function Convert-ThresholdList {
    param([string]$Value)
    $thresholds = New-Object System.Collections.Generic.List[double]
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $thresholds
    }
    foreach ($part in ($Value -split ",")) {
        $trimmed = $part.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            continue
        }
        $thresholds.Add([double]$trimmed)
    }
    return $thresholds
}

function Read-BrowserMetrics {
    param([string]$Path)
    $line = Get-Content -Path $Path | Where-Object { $_ -like "BROWSER_FONT_RASTER_METRICS *" } | Select-Object -First 1
    if (-not $line) {
        throw "BROWSER_FONT_RASTER_METRICS payload not found in $Path"
    }
    return ($line -replace "^BROWSER_FONT_RASTER_METRICS\s+", "") | ConvertFrom-Json
}

function Read-AuiMetrics {
    param([string]$Path)
    $metrics = @{
        viewport = $null
        samples = @{}
    }
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "resource-browser-font-raster viewport viewport=([0-9.]+)x([0-9.]+)") {
            $metrics.viewport = [pscustomobject]@{
                width = [double]$Matches[1]
                height = [double]$Matches[2]
            }
            continue
        }
        if ($line -match "resource-browser-font-raster sample id=([^ ]+) text='([^']*)' x=([-0-9.]+) y=([-0-9.]+) width=([-0-9.]+) height=([-0-9.]+) right=([-0-9.]+) bottom=([-0-9.]+) color=([^ ]+) backgroundColor=([^ ]+) cssFontFamily=(.*?) cssFontSize=([^ ]+) cssFontWeight=([^ ]+) cssLineHeight=([^ ]+) cssLetterSpacing=([^ ]+) textFontFamily=(.*?) textFontSize=([-0-9.]+) textFontWeight=([-0-9]+) textLineHeight=([-0-9.]+) textLetterSpacing=([-0-9.]+)") {
            $id = $Matches[1]
            $metrics.samples[$id] = [pscustomobject]@{
                text = $Matches[2]
                rect = [pscustomobject]@{
                    x = [double]$Matches[3]
                    y = [double]$Matches[4]
                    width = [double]$Matches[5]
                    height = [double]$Matches[6]
                    right = [double]$Matches[7]
                    bottom = [double]$Matches[8]
                }
                color = $Matches[9]
                backgroundColor = $Matches[10]
                cssFontFamily = $Matches[11]
                cssFontSize = $Matches[12]
                cssFontWeight = $Matches[13]
                cssLineHeight = $Matches[14]
                cssLetterSpacing = $Matches[15]
                textFontFamily = $Matches[16]
                textFontSize = [double]$Matches[17]
                textFontWeight = [int]$Matches[18]
                textLineHeight = [double]$Matches[19]
                textLetterSpacing = [double]$Matches[20]
            }
        }
    }
    if (-not $metrics.viewport) {
        throw "AUI font raster viewport not found in $Path"
    }
    return $metrics
}

function Get-ScaledRect {
    param(
        [object]$Rect,
        [double]$ScaleX,
        [double]$ScaleY
    )
    $x = [Math]::Max(0, [int][Math]::Floor([double]$Rect.x * $ScaleX))
    $y = [Math]::Max(0, [int][Math]::Floor([double]$Rect.y * $ScaleY))
    $w = [Math]::Max(1, [int][Math]::Ceiling([double]$Rect.width * $ScaleX))
    $h = [Math]::Max(1, [int][Math]::Ceiling([double]$Rect.height * $ScaleY))
    return [pscustomobject]@{ x = $x; y = $y; width = $w; height = $h }
}

function Convert-CssColorToRgb {
    param(
        [string]$Color,
        [int]$FallbackR = 255,
        [int]$FallbackG = 255,
        [int]$FallbackB = 255
    )
    if ([string]::IsNullOrWhiteSpace($Color)) {
        return [pscustomobject]@{ R = $FallbackR; G = $FallbackG; B = $FallbackB }
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
        return [pscustomobject]@{
            R = [int]$Matches[1]
            G = [int]$Matches[2]
            B = [int]$Matches[3]
        }
    }
    if ($value -match "^rgba\(\s*([0-9]+)\s*,\s*([0-9]+)\s*,\s*([0-9]+)\s*,") {
        return [pscustomobject]@{
            R = [int]$Matches[1]
            G = [int]$Matches[2]
            B = [int]$Matches[3]
        }
    }
    return [pscustomobject]@{ R = $FallbackR; G = $FallbackG; B = $FallbackB }
}

function Measure-TextCrop {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [object]$Rect,
        [double]$ScaleX,
        [double]$ScaleY,
        [int]$BgR = 255,
        [int]$BgG = 255,
        [int]$BgB = 255,
        [double]$GlyphDarknessThreshold = 20.0
    )
    $scaled = Get-ScaledRect $Rect $ScaleX $ScaleY
    $maxX = [Math]::Min($Bitmap.Width, $scaled.x + $scaled.width)
    $maxY = [Math]::Min($Bitmap.Height, $scaled.y + $scaled.height)
    $area = [Math]::Max(0, ($maxX - $scaled.x) * ($maxY - $scaled.y))
    $ink = 0
    $sumR = 0.0
    $sumG = 0.0
    $sumB = 0.0
    $sumDarkness = 0.0
    $glyphInk = 0
    $glyphSumR = 0.0
    $glyphSumG = 0.0
    $glyphSumB = 0.0
    $glyphSumDarkness = 0.0
    $minX = $maxX
    $minY = $maxY
    $inkMaxX = $scaled.x
    $inkMaxY = $scaled.y
    $glyphMinX = $maxX
    $glyphMinY = $maxY
    $glyphMaxX = $scaled.x
    $glyphMaxY = $scaled.y
    $glyphRowInk = @{}
    $glyphRowDarkness = @{}

    for ($y = $scaled.y; $y -lt $maxY; $y++) {
        for ($x = $scaled.x; $x -lt $maxX; $x++) {
            $color = $Bitmap.GetPixel($x, $y)
            $distance = [Math]::Abs($color.R - $BgR) + [Math]::Abs($color.G - $BgG) + [Math]::Abs($color.B - $BgB)
            if ($distance -gt 8) {
                $darkness = ((($BgR - $color.R) + ($BgG - $color.G) + ($BgB - $color.B)) / 3.0)
                $ink++
                $sumR += $color.R
                $sumG += $color.G
                $sumB += $color.B
                $sumDarkness += $darkness
                $minX = [Math]::Min($minX, $x)
                $minY = [Math]::Min($minY, $y)
                $inkMaxX = [Math]::Max($inkMaxX, $x)
                $inkMaxY = [Math]::Max($inkMaxY, $y)
                if ($darkness -gt $GlyphDarknessThreshold) {
                    $glyphInk++
                    $glyphSumR += $color.R
                    $glyphSumG += $color.G
                    $glyphSumB += $color.B
                    $glyphSumDarkness += $darkness
                    if (-not $glyphRowInk.ContainsKey($y)) {
                        $glyphRowInk[$y] = 0
                        $glyphRowDarkness[$y] = 0.0
                    }
                    $glyphRowInk[$y] = [int]$glyphRowInk[$y] + 1
                    $glyphRowDarkness[$y] = [double]$glyphRowDarkness[$y] + $darkness
                    $glyphMinX = [Math]::Min($glyphMinX, $x)
                    $glyphMinY = [Math]::Min($glyphMinY, $y)
                    $glyphMaxX = [Math]::Max($glyphMaxX, $x)
                    $glyphMaxY = [Math]::Max($glyphMaxY, $y)
                }
            }
        }
    }

    if ($ink -eq 0) {
        $avgRgb = "none"
        $avgDarkness = "0"
        $inkBounds = "none"
    } else {
        $avgRgb = "{0},{1},{2}" -f ([Math]::Round($sumR / $ink, 2)), ([Math]::Round($sumG / $ink, 2)), ([Math]::Round($sumB / $ink, 2))
        $avgDarkness = [Math]::Round($sumDarkness / $ink, 2)
        $inkBounds = "($minX,$minY,$($inkMaxX - $minX + 1),$($inkMaxY - $minY + 1))"
    }

    if ($glyphInk -eq 0) {
        $glyphAvgRgb = "none"
        $glyphAvgDarkness = "0"
        $glyphBounds = "none"
    } else {
        $glyphAvgRgb = "{0},{1},{2}" -f ([Math]::Round($glyphSumR / $glyphInk, 2)), ([Math]::Round($glyphSumG / $glyphInk, 2)), ([Math]::Round($glyphSumB / $glyphInk, 2))
        $glyphAvgDarkness = [Math]::Round($glyphSumDarkness / $glyphInk, 2)
        $glyphBounds = "($glyphMinX,$glyphMinY,$($glyphMaxX - $glyphMinX + 1),$($glyphMaxY - $glyphMinY + 1))"
    }
    $glyphRows = "[]"
    if ($glyphRowInk.Count -gt 0) {
        $rowParts = New-Object System.Collections.Generic.List[string]
        foreach ($rowKey in ($glyphRowInk.Keys | Sort-Object {[int]$_})) {
            $rowCount = [int]$glyphRowInk[$rowKey]
            $rowAvgDarkness = if ($rowCount -eq 0) { 0.0 } else { [Math]::Round(([double]$glyphRowDarkness[$rowKey]) / $rowCount, 6) }
            $rowParts.Add("y=$rowKey,ink=$rowCount,avgDarkness=$rowAvgDarkness")
        }
        $glyphRows = "[" + ($rowParts -join ";") + "]"
    }
    $backgroundDeltaInk = [Math]::Max(0, $ink - $glyphInk)

    return [pscustomobject]@{
        css = "($($Rect.x),$($Rect.y),$($Rect.width),$($Rect.height))"
        px = "($($scaled.x),$($scaled.y),$($scaled.width),$($scaled.height))"
        area = $area
        ink = $ink
        coverage = if ($area -eq 0) { "0" } else { "{0:F6}" -f ($ink / $area) }
        avgInkRgb = $avgRgb
        avgInkDarkness = $avgDarkness
        inkBounds = $inkBounds
        glyphDarknessThreshold = $GlyphDarknessThreshold
        glyphInk = $glyphInk
        glyphCoverage = if ($area -eq 0) { "0" } else { "{0:F6}" -f ($glyphInk / $area) }
        glyphAvgInkRgb = $glyphAvgRgb
        glyphAvgInkDarkness = $glyphAvgDarkness
        glyphInkBounds = $glyphBounds
        glyphRows = $glyphRows
        backgroundDeltaInk = $backgroundDeltaInk
        backgroundDeltaCoverage = if ($area -eq 0) { "0" } else { "{0:F6}" -f ($backgroundDeltaInk / $area) }
    }
}

if ([string]::IsNullOrWhiteSpace($AuiImage)) {
    $latest = Get-ChildItem run/screenshots/aui/*.png | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) {
        throw "AUI image not provided and no screenshot found under run/screenshots/aui"
    }
    $AuiImage = $latest.FullName
}

$browser = Read-BrowserMetrics $BrowserMetricsLog
$aui = Read-AuiMetrics $AuiMetricsLog
$browserBitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $BrowserImage))
$auiBitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $AuiImage))
$glyphSweepThresholds = Convert-ThresholdList $GlyphDarknessThresholds

try {
    $browserDpr = 0.0
    if ($browser.viewport.PSObject.Properties.Name -contains "devicePixelRatio") {
        $browserDpr = [double]$browser.viewport.devicePixelRatio
    }
    $browserScaleX = if ($browserDpr -gt 0) { $browserDpr } else { $browserBitmap.Width / [double]$browser.viewport.innerWidth }
    $browserScaleY = if ($browserDpr -gt 0) { $browserDpr } else { $browserBitmap.Height / [double]$browser.viewport.innerHeight }
    $auiScaleX = $auiBitmap.Width / [double]$aui.viewport.width
    $auiScaleY = $auiBitmap.Height / [double]$aui.viewport.height

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("browser $BrowserImage dimensions=$($browserBitmap.Width)x$($browserBitmap.Height) cssViewport=$($browser.viewport.innerWidth)x$($browser.viewport.innerHeight) dpr=$($browser.viewport.devicePixelRatio) scale=$([Math]::Round($browserScaleX, 6)),$([Math]::Round($browserScaleY, 6))")
    $lines.Add("aui $AuiImage dimensions=$($auiBitmap.Width)x$($auiBitmap.Height) cssViewport=$($aui.viewport.width)x$($aui.viewport.height) scale=$([Math]::Round($auiScaleX, 6)),$([Math]::Round($auiScaleY, 6))")

    foreach ($property in $browser.samples.PSObject.Properties) {
        $id = $property.Name
        $browserSample = $property.Value
        if (-not $aui.samples.ContainsKey($id)) {
            $lines.Add("sample=$id missing in AUI")
            continue
        }
        $auiSample = $aui.samples[$id]
        $browserBg = Convert-CssColorToRgb $browserSample.style.backgroundColor
        $auiBg = Convert-CssColorToRgb $auiSample.backgroundColor
        $bStats = Measure-TextCrop $browserBitmap $browserSample.rect $browserScaleX $browserScaleY $browserBg.R $browserBg.G $browserBg.B
        $aStats = Measure-TextCrop $auiBitmap $auiSample.rect $auiScaleX $auiScaleY $auiBg.R $auiBg.G $auiBg.B
        $lines.Add("sample=$id text='$($browserSample.text)'")
        $lines.Add("browser style fontFamily=$($browserSample.style.fontFamily) fontSize=$($browserSample.style.fontSize) fontWeight=$($browserSample.style.fontWeight) lineHeight=$($browserSample.style.lineHeight) letterSpacing=$($browserSample.style.letterSpacing) backgroundColor=$($browserSample.style.backgroundColor)")
        $lines.Add("aui style cssFontFamily=$($auiSample.cssFontFamily) cssFontSize=$($auiSample.cssFontSize) cssFontWeight=$($auiSample.cssFontWeight) cssLineHeight=$($auiSample.cssLineHeight) cssLetterSpacing=$($auiSample.cssLetterSpacing) backgroundColor=$($auiSample.backgroundColor) textFontFamily=$($auiSample.textFontFamily) textFontSize=$($auiSample.textFontSize) textLetterSpacing=$($auiSample.textLetterSpacing)")
        $lines.Add("browser crop=$id css=$($bStats.css) px=$($bStats.px) area=$($bStats.area) ink=$($bStats.ink) coverage=$($bStats.coverage) avgInkRgb=$($bStats.avgInkRgb) avgInkDarkness=$($bStats.avgInkDarkness) inkBounds=$($bStats.inkBounds)")
        $lines.Add("browser glyphOnly=$id thresholdDarkness=$($bStats.glyphDarknessThreshold) ink=$($bStats.glyphInk) coverage=$($bStats.glyphCoverage) avgInkRgb=$($bStats.glyphAvgInkRgb) avgInkDarkness=$($bStats.glyphAvgInkDarkness) inkBounds=$($bStats.glyphInkBounds) rows=$($bStats.glyphRows) backgroundDeltaInk=$($bStats.backgroundDeltaInk) backgroundDeltaCoverage=$($bStats.backgroundDeltaCoverage)")
        $lines.Add("aui crop=$id css=$($aStats.css) px=$($aStats.px) area=$($aStats.area) ink=$($aStats.ink) coverage=$($aStats.coverage) avgInkRgb=$($aStats.avgInkRgb) avgInkDarkness=$($aStats.avgInkDarkness) inkBounds=$($aStats.inkBounds)")
        $lines.Add("aui glyphOnly=$id thresholdDarkness=$($aStats.glyphDarknessThreshold) ink=$($aStats.glyphInk) coverage=$($aStats.glyphCoverage) avgInkRgb=$($aStats.glyphAvgInkRgb) avgInkDarkness=$($aStats.glyphAvgInkDarkness) inkBounds=$($aStats.glyphInkBounds) rows=$($aStats.glyphRows) backgroundDeltaInk=$($aStats.backgroundDeltaInk) backgroundDeltaCoverage=$($aStats.backgroundDeltaCoverage)")
        foreach ($threshold in $glyphSweepThresholds) {
            $bSweep = Measure-TextCrop $browserBitmap $browserSample.rect $browserScaleX $browserScaleY $browserBg.R $browserBg.G $browserBg.B $threshold
            $aSweep = Measure-TextCrop $auiBitmap $auiSample.rect $auiScaleX $auiScaleY $auiBg.R $auiBg.G $auiBg.B $threshold
            $lines.Add("browser glyphSweep=$id thresholdDarkness=$($bSweep.glyphDarknessThreshold) ink=$($bSweep.glyphInk) coverage=$($bSweep.glyphCoverage) avgInkRgb=$($bSweep.glyphAvgInkRgb) avgInkDarkness=$($bSweep.glyphAvgInkDarkness) inkBounds=$($bSweep.glyphInkBounds) rows=$($bSweep.glyphRows) backgroundDeltaInk=$($bSweep.backgroundDeltaInk) backgroundDeltaCoverage=$($bSweep.backgroundDeltaCoverage)")
            $lines.Add("aui glyphSweep=$id thresholdDarkness=$($aSweep.glyphDarknessThreshold) ink=$($aSweep.glyphInk) coverage=$($aSweep.glyphCoverage) avgInkRgb=$($aSweep.glyphAvgInkRgb) avgInkDarkness=$($aSweep.glyphAvgInkDarkness) inkBounds=$($aSweep.glyphInkBounds) rows=$($aSweep.glyphRows) backgroundDeltaInk=$($aSweep.backgroundDeltaInk) backgroundDeltaCoverage=$($aSweep.backgroundDeltaCoverage)")
        }
    }

    $outDir = Split-Path -Parent $OutLog
    if ($outDir) {
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    }
    $lines | Set-Content -Path $OutLog -Encoding UTF8
    $lines
} finally {
    $browserBitmap.Dispose()
    $auiBitmap.Dispose()
}
