@echo off
setlocal

cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-smart-campus.ps1"
if %ERRORLEVEL% neq 0 (
    echo.
    echo [smart-campus] Script failed with error code %ERRORLEVEL%
)
echo.
echo [smart-campus] Script completed. Press any key to close this window...
pause >nul
exit /b %ERRORLEVEL%
