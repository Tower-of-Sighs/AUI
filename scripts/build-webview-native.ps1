<#
.SYNOPSIS
Builds the ApricityUI offscreen WebView2 host (Windows x64) and drops the DLL into
common's resources so it ships inside every target jar.

.DESCRIPTION
The WebView2 SDK (Windows SDK + Microsoft.Web.WebView2 NuGet package) is not a
repository dependency: this script downloads the NuGet package into the git-ignored
build/ tree on first use, configures CMake against it, builds, and copies the result
to common/src/main/resources/assets/apricityui/native/windows-x64/.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts/build-webview-native.ps1
#>
[CmdletBinding()]
param(
    [string]$Configuration = "Release",
    [string]$SdkVersion = "1.0.4191.47",
    [switch]$SkipCopy
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$nativeDir = Join-Path $repoRoot "native\webview"
$sdkRoot = Join-Path $repoRoot "build\tmp\webview-sdk"
$packageDir = Join-Path $sdkRoot "sdk"
$buildDir = Join-Path $repoRoot "build\webview-native"
$outputDll = Join-Path $buildDir "out\apricityui_webview.dll"
$resourceDir = Join-Path $repoRoot "common\src\main\resources\assets\apricityui\native\windows-x64"

function Get-WebView2Sdk {
    if (Test-Path (Join-Path $packageDir "build\native\include\WebView2.h")) {
        Write-Host "WebView2 SDK already extracted at $packageDir"
        return
    }
    New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null
    $nupkg = Join-Path $sdkRoot "webview2-$SdkVersion.nupkg"
    if (-not (Test-Path $nupkg)) {
        $url = "https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/$SdkVersion/microsoft.web.webview2.$SdkVersion.nupkg"
        Write-Host "Downloading WebView2 SDK $SdkVersion ..."
        Invoke-WebRequest -Uri $url -OutFile $nupkg -UseBasicParsing
    }
    Write-Host "Extracting $nupkg ..."
    if (Test-Path $packageDir) { Remove-Item -Recurse -Force $packageDir }
    Expand-Archive -Path $nupkg -DestinationPath $packageDir -Force
}

function Get-VcVarsPath {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
    if (-not (Test-Path $vswhere)) {
        throw "vswhere.exe not found; install Visual Studio 2022 Build Tools with the C++ workload."
    }
    $install = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if (-not $install) {
        throw "No Visual Studio installation with the MSVC C++ toolset was found."
    }
    return Join-Path $install "VC\Auxiliary\Build\vcvars64.bat"
}

function Assert-JavaHome {
    if (-not $env:JAVA_HOME) {
        throw "JAVA_HOME must point at a JDK (jni.h is required to build the bridge)."
    }
    if (-not (Test-Path (Join-Path $env:JAVA_HOME "include\jni.h"))) {
        throw "JAVA_HOME ($env:JAVA_HOME) has no include\jni.h."
    }
}

Get-WebView2Sdk
Assert-JavaHome
$vcvars = Get-VcVarsPath

Write-Host "Configuring CMake ..."
cmd /c "`"$vcvars`" && cmake -S `"$nativeDir`" -B `"$buildDir`" -G `"Visual Studio 17 2022`" -A x64 -DWEBVIEW2_SDK_DIR=`"$packageDir`""
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed." }

Write-Host "Building ($Configuration) ..."
cmd /c "`"$vcvars`" && cmake --build `"$buildDir`" --config $Configuration"
if ($LASTEXITCODE -ne 0) { throw "CMake build failed." }

if (-not (Test-Path $outputDll)) { throw "Expected DLL not produced: $outputDll" }

if (-not $SkipCopy) {
    New-Item -ItemType Directory -Force -Path $resourceDir | Out-Null
    Copy-Item -Force $outputDll $resourceDir
    Write-Host "Copied -> $resourceDir\apricityui_webview.dll"
}

$info = Get-Item $outputDll
Write-Host ("Done: {0} ({1:N0} bytes)" -f $info.FullName, $info.Length)
