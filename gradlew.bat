@echo off
where gradle >nul 2>nul
if %errorlevel% neq 0 (
  echo Gradle is required to build this project.
  exit /b 1
)
setlocal
set CMD=gradle %*
%CMD%
