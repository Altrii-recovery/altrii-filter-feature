@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%gradlew.ps1"

where powershell >NUL 2>&1
if errorlevel 1 (
    echo ERROR: PowerShell is required to bootstrap Gradle. 1>&2
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" %*
exit /b %errorlevel%
