@echo off
setlocal
set "GRADLE_VERSION=8.13"
set "CACHE_DIR=%USERPROFILE%\.gradle\data-bottle-bootstrap"
set "GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%"
set "ZIP_FILE=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"
set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo [DATA BOTTLE] Gradle %GRADLE_VERSION% is not installed in the local bootstrap cache.
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  echo [DATA BOTTLE] Downloading Gradle %GRADLE_VERSION%...

  if exist "%PS_EXE%" (
    "%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%GRADLE_URL%' -OutFile '%ZIP_FILE%'"
  ) else (
    curl.exe -L "%GRADLE_URL%" -o "%ZIP_FILE%"
  )
  if errorlevel 1 exit /b 1

  echo [DATA BOTTLE] Extracting Gradle...
  if exist "%PS_EXE%" (
    "%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%CACHE_DIR%' -Force"
  ) else (
    tar.exe -xf "%ZIP_FILE%" -C "%CACHE_DIR%"
  )
  if errorlevel 1 exit /b 1
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
