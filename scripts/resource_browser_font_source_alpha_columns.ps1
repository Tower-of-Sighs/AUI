param(
    [string]$BrowserMetricsLog = "run/resource-browser-font-source-raster-browser-column-profile-last.log",
    [string]$AuiMaskDir = "run/font-raster-masks",
    [string]$OutLog = "run/resource-browser-font-source-alpha-columns.log",
    [string]$SampleIds = "arial12White,arial13White",
    [string]$AuiSource = "outline-coverage-4x-row-clamp"
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

function Resolve-MaskPath {
    param([string]$MaskPath)
    if ($MaskPath.StartsWith("./")) {
        return Join-Path "run" $MaskPath.Substring(2)
    }
    return $MaskPath
}

function Find-AuiMask {
    param(
        [array]$Metadata,
        [object]$Sample,
        [string]$Source
    )
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
    if (-not $matches) {
        return $null
    }
    return $matches | Sort-Object { (Get-Item $_.metadataPath).LastWriteTime } -Descending | Select-Object -First 1
}

function Measure-AlphaColumns {
    param([string]$ImagePath)
    $bitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $ImagePath))
    try {
        $columns = @{}
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
                if (-not $columns.ContainsKey($x)) {
                    $columns[$x] = [pscustomobject]@{ ink = 0; alpha = 0.0 }
                }
                $columns[$x].ink = [int]$columns[$x].ink + 1
                $columns[$x].alpha = [double]$columns[$x].alpha + $alpha
                $ink++
                $sum += $alpha
                $minX = [Math]::Min($minX, $x)
                $minY = [Math]::Min($minY, $y)
                $maxX = [Math]::Max($maxX, $x)
                $maxY = [Math]::Max($maxY, $y)
            }
        }
        return [pscustomobject]@{
            width = $bitmap.Width
            height = $bitmap.Height
            ink = $ink
            avgA = if ($ink -eq 0) { 0.0 } else { $sum / $ink }
            bounds = if ($ink -eq 0) { "none" } else { "($minX,$minY,$($maxX - $minX + 1),$($maxY - $minY + 1))" }
            minX = $minX
            maxX = $maxX
            columns = $columns
        }
    } finally {
        $bitmap.Dispose()
    }
}

function Convert-BrowserColumns {
    param([object[]]$ColumnProfile)
    $columns = @{}
    foreach ($column in $ColumnProfile) {
        $x = [int]$column.x
        $columns[$x] = [pscustomobject]@{
            ink = [int]$column.ink
            alpha = [double]$column.avgA * [int]$column.ink
        }
    }
    return $columns
}

function Convert-RelativeColumns {
    param(
        [hashtable]$Columns,
        [int]$OriginX
    )
    $relative = @{}
    foreach ($key in $Columns.Keys) {
        $relative[[int]$key - $OriginX] = $Columns[$key]
    }
    return $relative
}

function Format-Edges {
    param(
        [hashtable]$Columns,
        [int]$Count = 4
    )
    if ($Columns.Count -eq 0) {
        return "[]"
    }
    $keys = @($Columns.Keys | Sort-Object {[int]$_})
    $selected = @()
    $selected += $keys | Select-Object -First $Count
    $selected += $keys | Select-Object -Last $Count
    $selected = $selected | Sort-Object {[int]$_} -Unique
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($key in $selected) {
        $col = $Columns[$key]
        $avg = if ($col.ink -eq 0) { 0.0 } else { [Math]::Round($col.alpha / $col.ink, 6) }
        $parts.Add("x=$key,ink=$($col.ink),avgA=$avg")
    }
    return "[" + ($parts -join ";") + "]"
}

function Format-OnlyColumns {
    param(
        [hashtable]$LeftColumns,
        [hashtable]$RightColumns
    )
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($key in ($LeftColumns.Keys | Sort-Object {[int]$_})) {
        if ($RightColumns.ContainsKey($key)) {
            continue
        }
        $col = $LeftColumns[$key]
        $avg = if ($col.ink -eq 0) { 0.0 } else { [Math]::Round($col.alpha / $col.ink, 6) }
        $parts.Add("x=$key,ink=$($col.ink),avgA=$avg")
    }
    return "[" + ($parts -join ";") + "]"
}

$browser = Read-BrowserSourceMetrics $BrowserMetricsLog
$metadata = Get-ChildItem -Path $AuiMaskDir -Filter *.txt | ForEach-Object { Read-Metadata $_.FullName }
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("browserLog=$BrowserMetricsLog auiMaskDir=$AuiMaskDir auiSource=$AuiSource")

foreach ($id in (Convert-SampleList $SampleIds)) {
    $sample = $browser.samples | Where-Object { $_.id -eq $id } | Select-Object -First 1
    if (-not $sample) {
        $lines.Add("sample=$id missing browser")
        continue
    }
    $browserStats = $sample.physicalDpr.sourceAlpha
    if (-not ($browserStats.PSObject.Properties.Name -contains "columnProfile")) {
        $lines.Add("sample=$id missing browser columnProfile")
        continue
    }
    $browserColumns = Convert-BrowserColumns $browserStats.columnProfile
    $browserBounds = "($($browserStats.bounds.x),$($browserStats.bounds.y),$($browserStats.bounds.width),$($browserStats.bounds.height))"
    $auiMeta = Find-AuiMask $metadata $sample $AuiSource
    if (-not $auiMeta) {
        $lines.Add("sample=$id missing auiMask text='$($sample.text)' fontFamily=$($sample.fontFamily) fontSize=$($sample.fontSize) letterSpacing=$($sample.letterSpacing) background=$($sample.backgroundColor) source=$AuiSource")
        continue
    }
    $maskPath = Resolve-MaskPath $auiMeta.mask
    $auiStats = Measure-AlphaColumns $maskPath
    $browserRelativeColumns = Convert-RelativeColumns $browserColumns ([int]$browserStats.bounds.x)
    $auiRelativeColumns = Convert-RelativeColumns $auiStats.columns ([int]$auiStats.minX)
    $browserRelativeRight = [int]$browserStats.bounds.width - 1
    $auiRelativeRight = if ($auiStats.ink -eq 0) { -1 } else { [int]($auiStats.maxX - $auiStats.minX) }
    $lines.Add("sample=$id text='$($sample.text)' background=$($sample.backgroundColor)")
    $lines.Add("browser source=$id image=$($sample.physicalDpr.pixelWidth)x$($sample.physicalDpr.pixelHeight) bounds=$browserBounds width=$($browserStats.bounds.width) ink=$($browserStats.ink) avgA=$([Math]::Round([double]$browserStats.avgA, 6)) edges=$(Format-Edges $browserColumns)")
    $lines.Add("aui source=$id image=$($auiStats.width)x$($auiStats.height) bounds=$($auiStats.bounds) width=$(if ($auiStats.ink -eq 0) { 0 } else { $auiStats.maxX - $auiStats.minX + 1 }) ink=$($auiStats.ink) avgA=$([Math]::Round($auiStats.avgA, 6)) mask=$maskPath metadata=$($auiMeta.metadataPath) edges=$(Format-Edges $auiStats.columns)")
    $lines.Add("browser relativeColumns=$id originX=$($browserStats.bounds.x) right=$browserRelativeRight edges=$(Format-Edges $browserRelativeColumns)")
    $lines.Add("aui relativeColumns=$id originX=$($auiStats.minX) right=$auiRelativeRight edges=$(Format-Edges $auiRelativeColumns)")
    $lines.Add("auiOnlyRelativeColumns=$id $(Format-OnlyColumns $auiRelativeColumns $browserRelativeColumns)")
    $lines.Add("browserOnlyRelativeColumns=$id $(Format-OnlyColumns $browserRelativeColumns $auiRelativeColumns)")
}

$outDir = Split-Path -Parent $OutLog
if ($outDir) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}
$lines | Set-Content -Path $OutLog -Encoding UTF8
$lines
