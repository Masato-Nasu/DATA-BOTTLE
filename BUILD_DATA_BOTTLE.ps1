$ErrorActionPreference = "Stop"
$ProjectDir = $PSScriptRoot
Set-Location $ProjectDir

Write-Host "DATA BOTTLE v0.1.7 - Windows debug build" -ForegroundColor Cyan

function Find-JavaHome {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += "C:\Program Files\Android\Android Studio\jbr"
    $candidates += "C:\Program Files\Android\Android Studio\jre"

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate "bin\java.exe"))) {
            return $candidate
        }
    }

    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) {
        return (Split-Path (Split-Path $java.Source -Parent) -Parent)
    }
    return $null
}

$javaHome = Find-JavaHome
if (-not $javaHome) {
    throw "Java was not found. Install Android Studio, then run this script again."
}
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
Write-Host "[DATA BOTTLE] JAVA_HOME = $javaHome"

# java -version writes its version text to STDERR by design.
# Windows PowerShell 5.1 can turn that into NativeCommandError when
# $ErrorActionPreference is Stop, so capture it through Process instead.
$javaExe = Join-Path $javaHome "bin\java.exe"
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $javaExe
$psi.Arguments = "-version"
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
[void]$proc.Start()
$javaStdout = $proc.StandardOutput.ReadToEnd()
$javaStderr = $proc.StandardError.ReadToEnd()
$proc.WaitForExit()
if ($proc.ExitCode -ne 0) {
    throw "Java could not be started: $javaExe"
}
$javaVersionText = (($javaStderr + "`n" + $javaStdout) -split "`r?`n" | Where-Object { $_ } | Select-Object -First 1)
Write-Host "[DATA BOTTLE] Java = $javaVersionText"
if ($javaVersionText -match 'version\s+"(?<major>\d+)') {
    $major = [int]$Matches['major']
    if ($major -lt 17) {
        throw "Java 17 or newer is required. Current Java: $javaVersionText"
    }
}

$sdkCandidates = @()
if ($env:ANDROID_SDK_ROOT) { $sdkCandidates += $env:ANDROID_SDK_ROOT }
if ($env:ANDROID_HOME) { $sdkCandidates += $env:ANDROID_HOME }
if ($env:LOCALAPPDATA) { $sdkCandidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk") }

$sdk = $sdkCandidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $sdk) {
    throw "Android SDK was not found. Open Android Studio > SDK Manager and install Android SDK 36."
}
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$sdkForProperties = $sdk.Replace('\','/')
Set-Content -Path (Join-Path $ProjectDir "local.properties") -Value "sdk.dir=$sdkForProperties" -Encoding ASCII
Write-Host "[DATA BOTTLE] Android SDK = $sdk"

$GradleVersion = "8.13"
$CacheDir = Join-Path $env:USERPROFILE ".gradle\data-bottle-bootstrap"
$GradleHome = Join-Path $CacheDir "gradle-$GradleVersion"
$ZipFile = Join-Path $CacheDir "gradle-$GradleVersion-bin.zip"
$GradleBat = Join-Path $GradleHome "bin\gradle.bat"
$GradleUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

if (-not (Test-Path $GradleBat)) {
    New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null
    Write-Host "[DATA BOTTLE] Gradle $GradleVersion is not installed in the local bootstrap cache."

    if (-not (Test-Path $ZipFile)) {
        Write-Host "[DATA BOTTLE] Downloading Gradle $GradleVersion..."
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $GradleUrl -OutFile $ZipFile
        } catch {
            Write-Host "[DATA BOTTLE] Invoke-WebRequest failed. Trying .NET downloader..." -ForegroundColor Yellow
            $client = New-Object System.Net.WebClient
            $client.DownloadFile($GradleUrl, $ZipFile)
        }
    }

    Write-Host "[DATA BOTTLE] Extracting Gradle..."
    if (Test-Path $GradleHome) { Remove-Item $GradleHome -Recurse -Force }
    Expand-Archive -Path $ZipFile -DestinationPath $CacheDir -Force
}

if (-not (Test-Path $GradleBat)) {
    throw "Gradle could not be prepared: $GradleBat"
}

Write-Host "[DATA BOTTLE] Building APK..."
& $GradleBat --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE"
}

$source = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
$dest = Join-Path $ProjectDir "DATA-BOTTLE-v0.1.7-debug.apk"
if (-not (Test-Path $source)) {
    throw "APK was not found after a successful build: $source"
}
Copy-Item $source $dest -Force

Write-Host ""
Write-Host "DONE" -ForegroundColor Green
Write-Host $dest
