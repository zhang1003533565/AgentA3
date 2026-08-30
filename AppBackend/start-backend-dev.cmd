@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-backend-dev.ps1" %*
exit /b %ERRORLEVEL%
