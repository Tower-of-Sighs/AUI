param(
    [string]$BrowserImage = "run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png",
    [string]$AuiImage = "",
    [string]$BrowserMetricsLog = "run/resource-browser-font-raster-browser-background-pairs-last.log",
    [string]$AuiMetricsLog = "run/resource-browser-font-raster-aui-last.log",
    [string]$OutLog = "run/resource-browser-font-raster-columns.log",
    [string]$SampleIds = "arial12White,arial13",
    [double]$GlyphDarknessThreshold = 20.0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

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
                backgroundColor = $Matches[10]
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

function Measure-GlyphColumns {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [object]$Rect,
        [double]$ScaleX,
        [double]$ScaleY,
        [int]$BgR,
        [int]$BgG,
        [int]$BgB,
        [double]$Threshold
    )
    $scaled = Get-ScaledRect $Rect $ScaleX $ScaleY
    $maxX = [Math]::Min($Bitmap.Width, $scaled.x + $scaled.width)
    $maxY = [Math]::Min($Bitmap.Height, $scaled.y + $scaled.height)
    $columns = @{}
    $totalInk = 0
    $minX = $maxX
    $maxInkX = $scaled.x
    $minY = $maxY
    $maxInkY = $scaled.y

    for ($y = $scaled.y; $y -lt $maxY; $y++) {
        for ($x = $scaled.x; $x -lt $maxX; $x++) {
            $color = $Bitmap.GetPixel($x, $y)
            $distance = [Math]::Abs($color.R - $BgR) + [Math]::Abs($color.G - $BgG) + [Math]::Abs($color.B - $BgB)
            if ($distance -le 8) {
                continue
            }
            $darkness = ((($BgR - $color.R) + ($BgG - $color.G) + ($BgB - $color.B)) / 3.0)
            if ($darkness -le $Threshold) {
                continue
            }
            if (-not $columns.ContainsKey($x)) {
                $columns[$x] = [pscustomobject]@{ ink = 0; darkness = 0.0 }
            }
            $columns[$x].ink = [int]$columns[$x].ink + 1
            $columns[$x].darkness = [double]$columns[$x].darkness + $darkness
            $totalInk++
            $minX = [Math]::Min($minX, $x)
            $maxInkX = [Math]::Max($maxInkX, $x)
            $minY = [Math]::Min($minY, $y)
            $maxInkY = [Math]::Max($maxInkY, $y)
        }
    }

    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($key in ($columns.Keys | Sort-Object {[int]$_})) {
        $col = $columns[$key]
        $avg = if ($col.ink -eq 0) { 0.0 } else { [Math]::Round($col.darkness / $col.ink, 6) }
        $parts.Add("x=$key,ink=$($col.ink),avgDarkness=$avg")
    }

    $bounds = "none"
    if ($totalInk -gt 0) {
        $bounds = "($minX,$minY,$($maxInkX - $minX + 1),$($maxInkY - $minY + 1))"
    }

    return [pscustomobject]@{
        scaled = $scaled
        totalInk = $totalInk
        bounds = $bounds
        minX = $minX
        maxX = $maxInkX
        width = if ($totalInk -eq 0) { 0 } else { $maxInkX - $minX + 1 }
        columns = $columns
        text = "[" + ($parts -join ";") + "]"
    }
}

function Format-EdgeColumns {
    param(
        [object]$Profile,
        [int]$Count = 4
    )
    if ($Profile.totalInk -eq 0) {
        return "[]"
    }
    $keys = @($Profile.columns.Keys | Sort-Object {[int]$_})
    $selected = @()
    $selected += $keys | Select-Object -First $Count
    $selected += $keys | Select-Object -Last $Count
    $selected = $selected | Sort-Object {[int]$_} -Unique
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($key in $selected) {
        $col = $Profile.columns[$key]
        $avg = if ($col.ink -eq 0) { 0.0 } else { [Math]::Round($col.darkness / $col.ink, 6) }
        $parts.Add("x=$key,ink=$($col.ink),avgDarkness=$avg")
    }
    return "[" + ($parts -join ";") + "]"
}

function Format-OnlyColumns {
    param(
        [object]$LeftProfile,
        [object]$RightProfile
    )
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($key in ($LeftProfile.columns.Keys | Sort-Object {[int]$_})) {
        if ($RightProfile.columns.ContainsKey($key)) {
            continue
        }
        $col = $LeftProfile.columns[$key]
        $avg = if ($col.ink -eq 0) { 0.0 } else { [Math]::Round($col.darkness / $col.ink, 6) }
        $parts.Add("x=$key,ink=$($col.ink),avgDarkness=$avg")
    }
    return "[" + ($parts -join ";") + "]"
}

function Convert-SampleList {
    param([string]$Value)
    $ids = New-Object System.Collections.Generic.List[string]
    foreach ($part in ($Value -split ",")) {
        $id = $part.Trim()
        if (-not [string]::IsNullOrWhiteSpace($id)) {
            $ids.Add($id)
        }
    }
    return $ids
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
    $lines.Add("thresholdDarkness=$GlyphDarknessThreshold")

    foreach ($id in (Convert-SampleList $SampleIds)) {
        if (-not ($browser.samples.PSObject.Properties.Name -contains $id)) {
            $lines.Add("sample=$id missing in browser")
            continue
        }
        if (-not $aui.samples.ContainsKey($id)) {
            $lines.Add("sample=$id missing in AUI")
            continue
        }
        $browserSample = $browser.samples.$id
        $auiSample = $aui.samples[$id]
        $browserBg = Convert-CssColorToRgb $browserSample.style.backgroundColor
        $auiBg = Convert-CssColorToRgb $auiSample.backgroundColor
        $b = Measure-GlyphColumns $browserBitmap $browserSample.rect $browserScaleX $browserScaleY $browserBg.R $browserBg.G $browserBg.B $GlyphDarknessThreshold
        $a = Measure-GlyphColumns $auiBitmap $auiSample.rect $auiScaleX $auiScaleY $auiBg.R $auiBg.G $auiBg.B $GlyphDarknessThreshold

        $lines.Add("sample=$id text='$($browserSample.text)'")
        $lines.Add("browser columns=$id px=($($b.scaled.x),$($b.scaled.y),$($b.scaled.width),$($b.scaled.height)) bounds=$($b.bounds) width=$($b.width) ink=$($b.totalInk) edges=$(Format-EdgeColumns $b)")
        $lines.Add("aui columns=$id px=($($a.scaled.x),$($a.scaled.y),$($a.scaled.width),$($a.scaled.height)) bounds=$($a.bounds) width=$($a.width) ink=$($a.totalInk) edges=$(Format-EdgeColumns $a)")
        $lines.Add("auiOnlyColumns=$id $(Format-OnlyColumns $a $b)")
        $lines.Add("browserOnlyColumns=$id $(Format-OnlyColumns $b $a)")
        $lines.Add("browserAllColumns=$id $($b.text)")
        $lines.Add("auiAllColumns=$id $($a.text)")
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
