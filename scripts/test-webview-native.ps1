<#
.SYNOPSIS
Runs the Minecraft-free issue 138 JNI create/destroy regression (Windows x64).
.DESCRIPTION
Requires a JDK and an installed WebView2 runtime. Uses only the bundled resource
DLL: rebuild it with build-webview-native.ps1 before running this test. No downloads.
All classes, extracted DLLs, profiles, crash dumps and logs go under build/.
The Java child is timeout-controlled; a native crash fails this script, not its caller.
.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts/test-webview-native.ps1
.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts/test-webview-native.ps1 -PreloadCrt C:\host\msvcp140.dll
.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts/test-webview-native.ps1 -CompileOnly
#>
[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PreloadCrt,
    [ValidateRange(2, 100)][int]$Iterations = 3,
    [ValidateRange(1, 3600)][int]$TimeoutSeconds = 120,
    [switch]$CompileOnly
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path $repoRoot "build\webview-native-test"
$classes = Join-Path $testRoot "classes"
$binding = Join-Path $repoRoot "common\src\main\java\com\sighs\apricityui\webview\WebViewNative.java"
$harness = Join-Path $repoRoot "native\webview\tests\WebViewNativeIssue138Smoke.java"
$resourcePath = "assets\apricityui\native\windows-x64\apricityui_webview.dll"
$bundledDll = Join-Path $repoRoot "common\src\main\resources\$resourcePath"

if ($JavaHome) {
    $javac = Join-Path $JavaHome "bin\javac.exe"
    $java = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path $javac) -or -not (Test-Path $java)) {
        throw "JavaHome must contain bin\javac.exe and bin\java.exe: $JavaHome"
    }
} else {
    $javac = (Get-Command javac.exe -ErrorAction Stop).Source
    $java = Join-Path (Split-Path -Parent $javac) "java.exe"
    if (-not (Test-Path $java)) { throw "No java.exe beside $javac" }
}
if ($PreloadCrt) {
    $PreloadCrt = (Resolve-Path -LiteralPath $PreloadCrt -ErrorAction Stop).Path
    if (-not (Test-Path -LiteralPath $PreloadCrt -PathType Leaf)) {
        throw "PreloadCrt must name an existing DLL file."
    }
}
New-Item -ItemType Directory -Force -Path $classes | Out-Null
Write-Host "Compiling Java 8-compatible smoke harness with $javac ..."
& $javac -encoding UTF-8 -source 8 -target 8 -implicit:none -d $classes $binding $harness
if ($LASTEXITCODE -ne 0) { throw "Smoke harness compilation failed ($LASTEXITCODE)." }
if ($CompileOnly) {
    Write-Host "Compile-only passed; no native DLL was loaded. Classes: $classes"
    return
}
if (-not (Test-Path -LiteralPath $bundledDll -PathType Leaf)) {
    throw "Bundled DLL missing: $bundledDll. Run scripts/build-webview-native.ps1 first."
}
$stagedDll = Join-Path $classes $resourcePath
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $stagedDll) | Out-Null
Copy-Item -LiteralPath $bundledDll -Destination $stagedDll -Force
Write-Host "Bundled DLL SHA256: $((Get-FileHash -LiteralPath $stagedDll -Algorithm SHA256).Hash)"

$runDir = Join-Path $testRoot ("run-" + (Get-Date -Format "yyyyMMdd-HHmmss") + "-" + [Guid]::NewGuid().ToString("N"))
$tmp = Join-Path $runDir "tmp"
$profiles = Join-Path $runDir "profiles"
New-Item -ItemType Directory -Force -Path $runDir, $tmp, $profiles | Out-Null
$stdout = Join-Path $runDir "stdout.log"
$stderr = Join-Path $runDir "stderr.log"
# Start-Process joins ArgumentList with spaces on Windows PowerShell; quote each argument.
$javaArgs = @(
    "-Djava.io.tmpdir=$tmp",
    "-XX:ErrorFile=$runDir\hs_err_pid%p.log",
    "-cp", $classes, "WebViewNativeIssue138Smoke", $profiles, "$Iterations"
)
if ($PreloadCrt) { $javaArgs += $PreloadCrt }
$quotedArgs = ($javaArgs | ForEach-Object { '"' + $_ + '"' }) -join ' '
Write-Host "Running smoke test (timeout ${TimeoutSeconds}s); logs: $runDir"
# WebView2 children inherit these as well as the JVM's explicit java.io.tmpdir.
$oldTemp = $env:TEMP
$oldTmp = $env:TMP
try {
    $env:TEMP = $tmp
    $env:TMP = $tmp
    $process = Start-Process -FilePath $java -ArgumentList $quotedArgs -WorkingDirectory $runDir -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    # Retain the native process handle before it exits. Windows PowerShell may
    # otherwise lose ExitCode when WaitForExit reopens an already-exited process.
    $processHandle = $process.Handle
} finally {
    $env:TEMP = $oldTemp
    $env:TMP = $oldTmp
}
if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    # Kill only the timed-out child and its browser descendants, never the caller.
    & taskkill.exe /PID $process.Id /T /F | Out-Host
    throw "Native smoke timed out after ${TimeoutSeconds}s. Logs: $runDir"
}
$process.WaitForExit()
$process.Refresh()
Get-Content -LiteralPath $stdout | Out-Host
Get-Content -LiteralPath $stderr | Out-Host
if ($process.ExitCode -ne 0) {
    throw "Native smoke failed (Java exit $($process.ExitCode)). Logs/crash reports: $runDir"
}
if (-not (Select-String -LiteralPath $stdout -Pattern '^PASS issue138:' -Quiet)) {
    throw "Java exited without the smoke PASS marker. Logs: $runDir"
}
Write-Host "Issue 138 regression passed. Logs: $runDir"
