param(
    [string]$BrowserImage = "run/screenshots/browser/resource-browser-direct-static-1463x843.png",
    [string]$AuiImage = "run/screenshots/aui/2026-07-15_13.05.24.png",
    [string]$BrowserMetricsLog = "run/resource-browser-browser-static-last.log",
    [string]$AuiMetricsLog = "run/resource-browser-layer-colors-resource-aui-last.log",
    [string]$OutLog = "run/resource-browser-gray-text-border-resource-samples.log"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function Read-BrowserMetrics {
    param([string]$Path)
    $line = Get-Content -Path $Path | Where-Object { $_ -like "BROWSER_RESOURCE_METRICS *" } | Select-Object -First 1
    if (-not $line) {
        throw "BROWSER_RESOURCE_METRICS payload not found in $Path"
    }
    return ($line -replace "^BROWSER_RESOURCE_METRICS\s+", "") | ConvertFrom-Json
}

function Read-AuiMetrics {
    param([string]$Path)
    $metrics = @{
        viewport = $null
        rects = @{}
        fileMeta = $null
    }
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match "resource-browser viewport phase=baseline viewport=([0-9.]+)x([0-9.]+)") {
            $metrics.viewport = [pscustomobject]@{
                width = [double]$Matches[1]
                height = [double]$Matches[2]
            }
            continue
        }
        if ($line -match "resource-browser rect phase=baseline ([A-Za-z0-9]+) x=([-0-9.]+) y=([-0-9.]+) width=([-0-9.]+) height=([-0-9.]+) right=([-0-9.]+) bottom=([-0-9.]+)") {
            $metrics.rects[$Matches[1]] = [pscustomobject]@{
                x = [double]$Matches[2]
                y = [double]$Matches[3]
                width = [double]$Matches[4]
                height = [double]$Matches[5]
                right = [double]$Matches[6]
                bottom = [double]$Matches[7]
            }
            continue
        }
        if ($line -match "resource-browser normalFileCardChild phase=baseline index=0 label=fileMeta x=([-0-9.]+) y=([-0-9.]+) width=([-0-9.]+) height=([-0-9.]+) right=([-0-9.]+) bottom=([-0-9.]+)") {
            $metrics.fileMeta = [pscustomobject]@{
                x = [double]$Matches[1]
                y = [double]$Matches[2]
                width = [double]$Matches[3]
                height = [double]$Matches[4]
                right = [double]$Matches[5]
                bottom = [double]$Matches[6]
            }
        }
    }
    if (-not $metrics.viewport) {
        throw "AUI baseline viewport not found in $Path"
    }
    return $metrics
}

function Get-ScaledRect {
    param(
        [object]$Rect,
        [double]$ScaleX,
        [double]$ScaleY
    )
    $x = [Math]::Max(0, [int][Math]::Floor($Rect.x * $ScaleX))
    $y = [Math]::Max(0, [int][Math]::Floor($Rect.y * $ScaleY))
    $w = [Math]::Max(1, [int][Math]::Ceiling($Rect.width * $ScaleX))
    $h = [Math]::Max(1, [int][Math]::Ceiling($Rect.height * $ScaleY))
    return [pscustomobject]@{ x = $x; y = $y; width = $w; height = $h }
}

function Format-Rgba {
    param([System.Drawing.Color]$Color)
    return "$($Color.R),$($Color.G),$($Color.B),$($Color.A)"
}

function Sample-Pixel {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [double]$CssX,
        [double]$CssY,
        [double]$ScaleX,
        [double]$ScaleY
    )
    $px = [Math]::Min($Bitmap.Width - 1, [Math]::Max(0, [int][Math]::Round($CssX * $ScaleX)))
    $py = [Math]::Min($Bitmap.Height - 1, [Math]::Max(0, [int][Math]::Round($CssY * $ScaleY)))
    $color = $Bitmap.GetPixel($px, $py)
    return [pscustomobject]@{ px = $px; py = $py; rgba = (Format-Rgba $color) }
}

function Measure-TextCrop {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [object]$Rect,
        [double]$ScaleX,
        [double]$ScaleY,
        [int]$BgR,
        [int]$BgG,
        [int]$BgB
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

    for ($y = $scaled.y; $y -lt $maxY; $y++) {
        for ($x = $scaled.x; $x -lt $maxX; $x++) {
            $color = $Bitmap.GetPixel($x, $y)
            $distance = [Math]::Abs($color.R - $BgR) + [Math]::Abs($color.G - $BgG) + [Math]::Abs($color.B - $BgB)
            if ($distance -gt 8) {
                $ink++
                $sumR += $color.R
                $sumG += $color.G
                $sumB += $color.B
                $sumDarkness += ((($BgR - $color.R) + ($BgG - $color.G) + ($BgB - $color.B)) / 3.0)
            }
        }
    }

    if ($ink -eq 0) {
        $avgRgb = "none"
        $avgDarkness = "0"
    } else {
        $avgRgb = "{0},{1},{2}" -f ([Math]::Round($sumR / $ink, 2)), ([Math]::Round($sumG / $ink, 2)), ([Math]::Round($sumB / $ink, 2))
        $avgDarkness = [Math]::Round($sumDarkness / $ink, 2)
    }

    return [pscustomobject]@{
        css = "($($Rect.x),$($Rect.y),$($Rect.width),$($Rect.height))"
        px = "($($scaled.x),$($scaled.y),$($scaled.width),$($scaled.height))"
        area = $area
        ink = $ink
        coverage = if ($area -eq 0) { "0" } else { "{0:F6}" -f ($ink / $area) }
        avgInkRgb = $avgRgb
        avgInkDarkness = $avgDarkness
    }
}

$browser = Read-BrowserMetrics $BrowserMetricsLog
$aui = Read-AuiMetrics $AuiMetricsLog

$browserBitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $BrowserImage))
$auiBitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $AuiImage))

try {
    $browserScaleX = $browserBitmap.Width / [double]$browser.viewport.innerWidth
    $browserScaleY = $browserBitmap.Height / [double]$browser.viewport.innerHeight
    $auiScaleX = $auiBitmap.Width / [double]$aui.viewport.width
    $auiScaleY = $auiBitmap.Height / [double]$aui.viewport.height

    $browserTargets = @{
        contentCount = $browser.rects.contentCount
        fileMeta = $browser.rects.normalFileCards[0].children.fileMeta
        detailEmpty = $browser.rects.detailEmpty
    }
    $auiTargets = @{
        contentCount = $aui.rects.contentCount
        fileMeta = $aui.fileMeta
        detailEmpty = $aui.rects.detailEmpty
    }

    $samples = @(
        @{ label = "sidebarBorder"; x = 279; y = 150 },
        @{ label = "detailBorder"; x = 1164; y = 100 },
        @{ label = "cardBorder"; x = 313; y = 170 }
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("browser $BrowserImage dimensions=$($browserBitmap.Width)x$($browserBitmap.Height) cssViewport=$($browser.viewport.innerWidth)x$($browser.viewport.innerHeight) scale=$([Math]::Round($browserScaleX, 6)),$([Math]::Round($browserScaleY, 6))")
    $lines.Add("aui $AuiImage dimensions=$($auiBitmap.Width)x$($auiBitmap.Height) cssViewport=$($aui.viewport.width)x$($aui.viewport.height) scale=$([Math]::Round($auiScaleX, 6)),$([Math]::Round($auiScaleY, 6))")

    foreach ($sample in $samples) {
        $b = Sample-Pixel $browserBitmap $sample.x $sample.y $browserScaleX $browserScaleY
        $a = Sample-Pixel $auiBitmap $sample.x $sample.y $auiScaleX $auiScaleY
        $lines.Add("browser sample=$($sample.label) css=($($sample.x),$($sample.y)) px=($($b.px),$($b.py)) rgba=$($b.rgba)")
        $lines.Add("aui sample=$($sample.label) css=($($sample.x),$($sample.y)) px=($($a.px),$($a.py)) rgba=$($a.rgba)")
    }

    $backgrounds = @{
        contentCount = @(250, 250, 250)
        fileMeta = @(255, 255, 255)
        detailEmpty = @(255, 255, 255)
    }
    foreach ($target in @("contentCount", "fileMeta", "detailEmpty")) {
        $bg = $backgrounds[$target]
        $bStats = Measure-TextCrop $browserBitmap $browserTargets[$target] $browserScaleX $browserScaleY $bg[0] $bg[1] $bg[2]
        $aStats = Measure-TextCrop $auiBitmap $auiTargets[$target] $auiScaleX $auiScaleY $bg[0] $bg[1] $bg[2]
        $lines.Add("browser crop=$target css=$($bStats.css) px=$($bStats.px) area=$($bStats.area) ink=$($bStats.ink) coverage=$($bStats.coverage) avgInkRgb=$($bStats.avgInkRgb) avgInkDarkness=$($bStats.avgInkDarkness)")
        $lines.Add("aui crop=$target css=$($aStats.css) px=$($aStats.px) area=$($aStats.area) ink=$($aStats.ink) coverage=$($aStats.coverage) avgInkRgb=$($aStats.avgInkRgb) avgInkDarkness=$($aStats.avgInkDarkness)")
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
