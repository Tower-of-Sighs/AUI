param(
    [string]$BrowserMetricsLog = "run/resource-browser-font-source-raster-browser-last.log",
    [string]$AuiMaskDir = "run/font-raster-masks",
    [string]$OutLog = "run/resource-browser-font-source-alpha-histogram.log"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function Read-BrowserSourceMetrics {
    param([string]$Path)
    $line = Get-Content -Path $Path | Where-Object { $_ -like "BROWSER_FONT_SOURCE_RASTER_METRICS *" } | Select-Object -First 1
    if (-not $line) {
        throw "BROWSER_FONT_SOURCE_RASTER_METRICS payload not found in $Path"
    }
    return ($line -replace "^BROWSER_FONT_SOURCE_RASTER_METRICS\s+", "") | ConvertFrom-Json
}

function Read-Metadata {
    param([string]$Path)
    $meta = @{}
    foreach ($line in Get-Content -Path $Path) {
        $index = $line.IndexOf("=")
        if ($index -lt 0) {
            continue
        }
        $key = $line.Substring(0, $index)
        $value = $line.Substring($index + 1)
        $meta[$key] = $value
    }
    $meta["metadataPath"] = $Path
    return $meta
}

function Get-BinLabel {
    param([int]$Alpha)
    if ($Alpha -le 31) { return "1-31" }
    if ($Alpha -le 63) { return "32-63" }
    if ($Alpha -le 95) { return "64-95" }
    if ($Alpha -le 127) { return "96-127" }
    if ($Alpha -le 159) { return "128-159" }
    if ($Alpha -le 191) { return "160-191" }
    if ($Alpha -le 223) { return "192-223" }
    return "224-255"
}

function Measure-AlphaMask {
    param([string]$ImagePath)
    $bitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $ImagePath))
    try {
        $bins = [ordered]@{
            "1-31" = 0
            "32-63" = 0
            "64-95" = 0
            "96-127" = 0
            "128-159" = 0
            "160-191" = 0
            "192-223" = 0
            "224-255" = 0
        }
        $cdfCounts = @{
            le32 = 0
            le64 = 0
            le96 = 0
            le128 = 0
            le160 = 0
            le192 = 0
            le224 = 0
            le240 = 0
        }
        $ink = 0
        $sum = 0.0
        $minX = $bitmap.Width
        $minY = $bitmap.Height
        $maxX = -1
        $maxY = -1
        for ($y = 0; $y -lt $bitmap.Height; $y++) {
            for ($x = 0; $x -lt $bitmap.Width; $x++) {
                $color = $bitmap.GetPixel($x, $y)
                $alpha = [int]$color.R
                if ($alpha -le 0) {
                    continue
                }
                $ink++
                $sum += $alpha
                $bins[(Get-BinLabel $alpha)]++
                if ($alpha -le 32) { $cdfCounts.le32++ }
                if ($alpha -le 64) { $cdfCounts.le64++ }
                if ($alpha -le 96) { $cdfCounts.le96++ }
                if ($alpha -le 128) { $cdfCounts.le128++ }
                if ($alpha -le 160) { $cdfCounts.le160++ }
                if ($alpha -le 192) { $cdfCounts.le192++ }
                if ($alpha -le 224) { $cdfCounts.le224++ }
                if ($alpha -le 240) { $cdfCounts.le240++ }
                $minX = [Math]::Min($minX, $x)
                $minY = [Math]::Min($minY, $y)
                $maxX = [Math]::Max($maxX, $x)
                $maxY = [Math]::Max($maxY, $y)
            }
        }
        $area = [Math]::Max(1, $bitmap.Width * $bitmap.Height)
        return [pscustomobject]@{
            width = $bitmap.Width
            height = $bitmap.Height
            area = $area
            ink = $ink
            coverage = if ($area -eq 0) { 0.0 } else { $ink / $area }
            avgAlpha = if ($ink -eq 0) { 0.0 } else { $sum / $ink }
            bounds = if ($ink -eq 0) { "none" } else { "$minX,$minY,$($maxX - $minX + 1),$($maxY - $minY + 1)" }
            bins = $bins
            cdf = [pscustomobject]@{
                le32 = if ($ink -eq 0) { 0.0 } else { $cdfCounts.le32 / $ink }
                le64 = if ($ink -eq 0) { 0.0 } else { $cdfCounts.le64 / $ink }
                le96 = if ($ink -eq 0) { 0.0 } else { $cdfCounts.le96 / $ink }
                le128 = if ($ink -eq 0) { 0.0 } else { $cdfCounts.le128 / $ink }
                le160 = if ($ink -eq 0) { 0.0 } else { $cdfCounts.le160 / $ink }
                le192 = if ($ink -eq 0) { 0.0 } else { $cdfCounts.le192 / $ink }
                le224 = if ($ink -eq 0) { 0.0 } else { $cdfCounts.le224 / $ink }
                le240 = if ($ink -eq 0) { 0.0 } else { $cdfCounts.le240 / $ink }
            }
        }
    } finally {
        $bitmap.Dispose()
    }
}

function Format-Bins {
    param([object]$Bins)
    $labels = @("1-31", "32-63", "64-95", "96-127", "128-159", "160-191", "192-223", "224-255")
    $parts = foreach ($label in $labels) {
        if ($Bins -is [hashtable] -or $Bins -is [System.Collections.Specialized.OrderedDictionary]) {
            "$label=$($Bins[$label])"
        } else {
            "$label=$($Bins.$label)"
        }
    }
    return ($parts -join ",")
}

function Format-Cdf {
    param([object]$Cdf)
    $keys = @("le32", "le64", "le96", "le128", "le160", "le192", "le224", "le240")
    $parts = foreach ($key in $keys) {
        "$key=$("{0:F6}" -f [double]$Cdf.$key)"
    }
    return ($parts -join ",")
}

function Find-AuiMask {
    param(
        [array]$Metadata,
        [string]$Text,
        [string]$FontFamily,
        [string]$FontSize,
        [string]$LetterSpacing,
        [string]$BackgroundColor
    )
    $matches = $Metadata | Where-Object {
        $_.text -eq $Text -and
        $_.fontFamily -eq $FontFamily -and
        $_.fontSize -eq $FontSize -and
        $_.letterSpacing -eq $LetterSpacing -and
        $_.raster -like "physical:*" -and
        $_.source -eq "draw-string" -and
        $_.composite -eq "transparent" -and
        $_.cacheKey -like "*$BackgroundColor*" -and
        -not $_.ContainsKey("alphaScale") -and
        -not $_.ContainsKey("alphaCap")
    }
    if (-not $matches) {
        return $null
    }
    return $matches | Sort-Object { (Get-Item $_.metadataPath).LastWriteTime } -Descending | Select-Object -First 1
}

$browser = Read-BrowserSourceMetrics $BrowserMetricsLog
$metadata = Get-ChildItem -Path $AuiMaskDir -Filter *.txt | ForEach-Object { Read-Metadata $_.FullName }
$sampleIds = @("arial13White", "arial13Fafafa", "arial12White", "arial12Fafafa")
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("browserLog=$BrowserMetricsLog auiMaskDir=$AuiMaskDir")

foreach ($id in $sampleIds) {
    $sample = $browser.samples | Where-Object { $_.id -eq $id } | Select-Object -First 1
    if (-not $sample) {
        $lines.Add("sample=$id missing browser")
        continue
    }
    $browserStats = $sample.physicalDpr.sourceAlpha
    $background = $sample.backgroundColor
    $auiMeta = Find-AuiMask $metadata $sample.text $sample.fontFamily ([string]([double]$sample.fontSize) + ".0") ([string]([double]$sample.letterSpacing) + ".0") $background
    if (-not $auiMeta) {
        $lines.Add("sample=$id missing auiMask text='$($sample.text)' fontFamily=$($sample.fontFamily) fontSize=$($sample.fontSize) letterSpacing=$($sample.letterSpacing) background=$background")
        continue
    }
    $maskPath = $auiMeta.mask
    if ($maskPath.StartsWith("./")) {
        $maskPath = Join-Path "run" $maskPath.Substring(2)
    }
    $auiStats = Measure-AlphaMask $maskPath
    $browserBounds = "$($browserStats.bounds.x),$($browserStats.bounds.y),$($browserStats.bounds.width),$($browserStats.bounds.height)"
    $lines.Add("sample=$id text='$($sample.text)' background=$background")
    $lines.Add("browser source image=$($sample.physicalDpr.pixelWidth)x$($sample.physicalDpr.pixelHeight) ink=$($browserStats.ink) coverage=$("{0:F6}" -f [double]$browserStats.coverage) avgAlpha=$("{0:F6}" -f [double]$browserStats.avgA) bounds=$browserBounds bins=$(Format-Bins $browserStats.alphaBins) cdf=$(Format-Cdf $browserStats.alphaCdf)")
    $lines.Add("aui source image=$($auiStats.width)x$($auiStats.height) ink=$($auiStats.ink) coverage=$("{0:F6}" -f [double]$auiStats.coverage) avgAlpha=$("{0:F6}" -f [double]$auiStats.avgAlpha) bounds=$($auiStats.bounds) mask=$maskPath metadata=$($auiMeta.metadataPath) bins=$(Format-Bins $auiStats.bins) cdf=$(Format-Cdf $auiStats.cdf)")
}

$outDir = Split-Path -Parent $OutLog
if ($outDir) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}
$lines | Set-Content -Path $OutLog -Encoding UTF8
$lines
