# ---------------------------------------------------------------------------
# One-shot setup, build and run for the CS 4361 maze game.
#
#   powershell -ExecutionPolicy Bypass -File .\setup-and-run.ps1
#
# What it does:
#   1. checks you have a JDK (javac, not just java)
#   2. downloads JOGL into lib\ if it is not already there
#      - first tries the jogamp.org fat jar (one file, natives included)
#      - falls back to the four Maven Central jars if that fails
#   3. compiles src\*.java into bin\
#   4. runs MazeGame
#
# Safe to re-run. Once the jars are in lib\ it skips straight to build+run.
#
# Flags:
#   -SkipRun      build only, do not launch the game
#   -Clean        wipe bin\ before compiling
#   -ForceDownload   re-download the libraries even if lib\ looks populated
# ---------------------------------------------------------------------------

param(
    [switch]$SkipRun,
    [switch]$Clean,
    [switch]$ForceDownload
)

$ErrorActionPreference = 'Stop'

# Invoke-WebRequest is dramatically slower with the progress bar on - this is
# the difference between a 20 second download and a 5 minute one.
$ProgressPreference = 'SilentlyContinue'

# Force TLS 1.2 for Windows PowerShell 5.1, which still defaults to older
# protocols that these hosts refuse.
try {
    [Net.ServicePointManager]::SecurityProtocol =
        [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch { }

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$libDir = Join-Path $root 'lib'
$srcDir = Join-Path $root 'src'
$binDir = Join-Path $root 'bin'

function Write-Step($msg)  { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "    $msg" -ForegroundColor Green }
function Write-Warn2($msg) { Write-Host "    $msg" -ForegroundColor Yellow }

# ---------------------------------------------------------------------------
# 1. JDK check
# ---------------------------------------------------------------------------

Write-Step 'Checking for a JDK'

$javac = Get-Command javac -ErrorAction SilentlyContinue
if (-not $javac) {
    Write-Host @"

javac was not found on your PATH.

You have a Java runtime but apparently not a JDK, or the JDK's bin folder is
not on PATH. You cannot compile without one.

Fix it one of these ways:
  - install Temurin 17 or 21 from adoptium.net (tick "Add to PATH"), or
  - point at a JDK you already have, e.g. the one IntelliJ uses:
      `$env:PATH = "C:\Program Files\Java\jdk-17\bin;`$env:PATH"
    then re-run this script in the same terminal.

"@ -ForegroundColor Red
    exit 1
}

$javacVersion = (& javac -version 2>&1) -join ' '
Write-Ok "found $javacVersion"

# ---------------------------------------------------------------------------
# 2. Libraries
# ---------------------------------------------------------------------------

if (-not (Test-Path $libDir)) {
    New-Item -ItemType Directory -Path $libDir | Out-Null
}

# A real jar is a zip, so it starts with the bytes "PK". A 404 or a captive
# portal page saved to disk will not - that is the failure this catches, and
# it is the one that otherwise wastes an hour because the file "downloaded
# fine" but javac still cannot see the packages.
function Test-IsJar($path) {
    if (-not (Test-Path $path)) { return $false }
    $info = Get-Item $path
    if ($info.Length -lt 100KB) { return $false }
    try {
        $stream = [IO.File]::OpenRead($path)
        $bytes = New-Object byte[] 2
        $null = $stream.Read($bytes, 0, 2)
        $stream.Close()
        return ($bytes[0] -eq 0x50 -and $bytes[1] -eq 0x4B)   # 'P','K'
    } catch {
        return $false
    }
}

function Get-Jar($url, $outFile) {
    Write-Host "    downloading $(Split-Path -Leaf $outFile) ..." -NoNewline
    try {
        Invoke-WebRequest -Uri $url -OutFile $outFile -UseBasicParsing -TimeoutSec 300
    } catch {
        Write-Host " failed" -ForegroundColor Red
        if (Test-Path $outFile) { Remove-Item $outFile -Force }
        return $false
    }
    if (-not (Test-IsJar $outFile)) {
        Write-Host " got a non-jar response" -ForegroundColor Red
        Remove-Item $outFile -Force -ErrorAction SilentlyContinue
        return $false
    }
    $size = [math]::Round((Get-Item $outFile).Length / 1MB, 1)
    Write-Host " ok ($size MB)" -ForegroundColor Green
    return $true
}

$existingJars = @(Get-ChildItem -Path $libDir -Filter *.jar -ErrorAction SilentlyContinue)

if ($existingJars.Count -gt 0 -and -not $ForceDownload) {
    Write-Step 'JOGL already present'
    foreach ($j in $existingJars) { Write-Ok $j.Name }
} else {
    Write-Step 'Downloading JOGL'

    if ($ForceDownload -and $existingJars.Count -gt 0) {
        Remove-Item (Join-Path $libDir '*.jar') -Force
    }

    $ok = $false

    # --- Attempt 1: the jogamp.org fat jar. One file, natives bundled. ---
    $fatPath = Join-Path $libDir 'jogamp-fat.jar'
    $fatUrls = @(
        'https://jogamp.org/deployment/jogamp-current/fat/jogamp-fat.jar',
        'https://jogamp.org/deployment/v2.5.0/fat/jogamp-fat.jar',
        'https://jogamp.org/deployment/v2.4.0/fat/jogamp-fat.jar'
    )
    foreach ($url in $fatUrls) {
        if (Get-Jar $url $fatPath) { $ok = $true; break }
    }

    # --- Attempt 2: Maven Central. Needs the platform natives jars too, or
    #     you get UnsatisfiedLinkError at runtime instead of a clean failure.
    if (-not $ok) {
        Write-Warn2 'jogamp.org did not work, falling back to Maven Central'

        $arch = if ([Environment]::Is64BitOperatingSystem) { 'amd64' } else { 'i586' }
        $classifier = "natives-windows-$arch"

        foreach ($version in @('2.5.0', '2.4.0')) {
            $base = 'https://repo1.maven.org/maven2/org/jogamp'
            $set = @(
                @{ url = "$base/jogl/jogl-all/$version/jogl-all-$version.jar";
                   file = "jogl-all-$version.jar" },
                @{ url = "$base/gluegen/gluegen-rt/$version/gluegen-rt-$version.jar";
                   file = "gluegen-rt-$version.jar" },
                @{ url = "$base/jogl/jogl-all/$version/jogl-all-$version-$classifier.jar";
                   file = "jogl-all-$version-$classifier.jar" },
                @{ url = "$base/gluegen/gluegen-rt/$version/gluegen-rt-$version-$classifier.jar";
                   file = "gluegen-rt-$version-$classifier.jar" }
            )

            $allGood = $true
            foreach ($item in $set) {
                if (-not (Get-Jar $item.url (Join-Path $libDir $item.file))) {
                    $allGood = $false
                    break
                }
            }

            if ($allGood) { $ok = $true; break }
            Remove-Item (Join-Path $libDir '*.jar') -Force -ErrorAction SilentlyContinue
            Write-Warn2 "version $version unavailable, trying older"
        }
    }

    if (-not $ok) {
        Write-Host @"

Could not download JOGL from any source.

That usually means the network is blocking it - campus wifi and VPNs often do.
Try a different network, or download the fat jar manually in a browser from
jogamp.org (Deployment -> current -> fat -> jogamp-fat.jar) and drop it in:

  $libDir

Then re-run this script.

"@ -ForegroundColor Red
        exit 1
    }
}

# ---------------------------------------------------------------------------
# 3. Compile
# ---------------------------------------------------------------------------

Write-Step 'Compiling'

if ($Clean -and (Test-Path $binDir)) {
    Remove-Item $binDir -Recurse -Force
}
if (-not (Test-Path $binDir)) {
    New-Item -ItemType Directory -Path $binDir | Out-Null
}

# Windows classpath separator is ';'. Quoting matters everywhere this is used.
$jars = (Get-ChildItem -Path $libDir -Filter *.jar | ForEach-Object { $_.FullName }) -join ';'
$sources = (Get-ChildItem -Path $srcDir -Filter *.java | ForEach-Object { $_.FullName })

if ($sources.Count -eq 0) {
    Write-Host "No .java files found in $srcDir" -ForegroundColor Red
    exit 1
}

& javac -cp $jars -d $binDir $sources
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nCompile failed. The errors above are the real ones - the missing-package cascade should be gone now." -ForegroundColor Red
    exit 1
}
Write-Ok "compiled $($sources.Count) files into bin\"

# ---------------------------------------------------------------------------
# 4. Run
# ---------------------------------------------------------------------------

if ($SkipRun) {
    Write-Step 'Build complete (-SkipRun given, not launching)'
    Write-Host "`nTo run it yourself:`n  java -cp `"bin;lib\*`" MazeGame`n"
    exit 0
}

Write-Step 'Launching MazeGame'
Write-Host '    (a window should open - close it to return here)'

& java -cp "$binDir;$jars" MazeGame
$exit = $LASTEXITCODE

if ($exit -ne 0) {
    Write-Host "`nMazeGame exited with code $exit. If the error mentions natives or UnsatisfiedLinkError, re-run with -ForceDownload to pull a clean set of jars." -ForegroundColor Yellow
}
exit $exit
