param(
    [int]$Port = 9333,
    [int]$Width = 1463,
    [int]$Height = 843
)

$ErrorActionPreference = 'Stop'
$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$comparisonDir = (Resolve-Path (Join-Path $PSScriptRoot '..\run\ore-comparison')).Path
$profile = Join-Path $comparisonDir 'hover-chrome-profile'
$pagePath = (Resolve-Path (Join-Path $PSScriptRoot '..\src\main\resources\assets\apricityui\apricity\apricityui\theme\ore\example.html')).Path
$output = Join-Path $comparisonDir 'browser-ore-brand-hover.png'

New-Item -ItemType Directory -Force -Path $profile | Out-Null
$arguments = @(
    '--headless=new',
    '--disable-gpu',
    '--disable-extensions',
    "--remote-debugging-port=$Port",
    "--user-data-dir=$profile",
    "--window-size=$Width,$Height",
    '--force-device-scale-factor=1',
    'about:blank'
)
$process = Start-Process -FilePath $chrome -ArgumentList $arguments -WindowStyle Hidden -PassThru

try {
    $targets = $null
    for ($attempt = 0; $attempt -lt 50; $attempt++) {
        try {
            $targets = Invoke-RestMethod "http://127.0.0.1:$Port/json"
            if ($targets) { break }
        } catch {
            Start-Sleep -Milliseconds 100
        }
    }
    if (-not $targets) { throw 'Chrome CDP did not start.' }

    $target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
    $nextId = 0

    function Invoke-Cdp([string]$Method, [hashtable]$Parameters = @{}) {
        $script:nextId++
        $currentId = $script:nextId
        $json = @{ id = $currentId; method = $Method; params = $Parameters } | ConvertTo-Json -Depth 12 -Compress
        $bytes = [Text.Encoding]::UTF8.GetBytes($json)
        $segment = [ArraySegment[byte]]::new($bytes)
        $socket.SendAsync($segment, [Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).GetAwaiter().GetResult()

        while ($true) {
            $buffer = New-Object byte[] 1048576
            $offset = 0
            do {
                $part = [ArraySegment[byte]]::new($buffer, $offset, $buffer.Length - $offset)
                $result = $socket.ReceiveAsync($part, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
                $offset += $result.Count
            } while (-not $result.EndOfMessage)
            $message = [Text.Encoding]::UTF8.GetString($buffer, 0, $offset) | ConvertFrom-Json
            if ($message.id -eq $currentId) { return $message }
        }
    }

    Invoke-Cdp 'Page.enable' | Out-Null
    Invoke-Cdp 'Runtime.enable' | Out-Null
    Invoke-Cdp 'Emulation.setDeviceMetricsOverride' @{
        width = $Width
        height = $Height
        deviceScaleFactor = 1
        mobile = $false
    } | Out-Null

    $url = 'file:///' + ($pagePath -replace '\\', '/')
    Invoke-Cdp 'Page.navigate' @{ url = $url } | Out-Null
    Invoke-Cdp 'Runtime.evaluate' @{
        expression = 'new Promise(async r => { await document.fonts.ready; requestAnimationFrame(() => requestAnimationFrame(r)); })'
        awaitPromise = $true
        returnByValue = $true
    } | Out-Null

    $metrics = (Invoke-Cdp 'Runtime.evaluate' @{
        expression = '(() => { const e=document.querySelector(".navbar-brand"); const r=e.getBoundingClientRect(); const s=getComputedStyle(e); return {x:r.x,y:r.y,width:r.width,height:r.height,font:s.font,lineHeight:s.lineHeight,color:s.color,viewport:[innerWidth,innerHeight]}; })()'
        returnByValue = $true
    }).result.result.value

    Invoke-Cdp 'Input.dispatchMouseEvent' @{
        type = 'mouseMoved'
        x = [double]$metrics.x + [double]$metrics.width / 2
        y = [double]$metrics.y + [double]$metrics.height / 2
        button = 'none'
        buttons = 0
    } | Out-Null
    Invoke-Cdp 'Runtime.evaluate' @{
        expression = 'new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))'
        awaitPromise = $true
        returnByValue = $true
    } | Out-Null

    $hover = (Invoke-Cdp 'Runtime.evaluate' @{
        expression = '(() => { const e=document.querySelector(".navbar-brand"); const r=e.getBoundingClientRect(); const s=getComputedStyle(e); return {hover:e.matches(":hover"),x:r.x,y:r.y,width:r.width,height:r.height,textDecoration:s.textDecorationLine,textDecorationThickness:s.textDecorationThickness,textUnderlineOffset:s.textUnderlineOffset}; })()'
        returnByValue = $true
    }).result.result.value
    $screenshot = (Invoke-Cdp 'Page.captureScreenshot' @{
        format = 'png'
        fromSurface = $true
        captureBeyondViewport = $false
    }).result.data
    [IO.File]::WriteAllBytes($output, [Convert]::FromBase64String($screenshot))

    [pscustomobject]@{
        output = $output
        metrics = $metrics | ConvertTo-Json -Compress
        hover = $hover | ConvertTo-Json -Compress
    }
} finally {
    if ($socket -and $socket.State -eq [Net.WebSockets.WebSocketState]::Open) {
        try { Invoke-Cdp 'Browser.close' | Out-Null } catch {}
        $socket.Dispose()
    }
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
    }
}
