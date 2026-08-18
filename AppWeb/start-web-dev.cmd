@echo off
setlocal
title AgentA3 Web Development Server
cd /d "%~dp0"

set "NODEJS_DIR=D:\DevTools\NodeJS"
if exist "%NODEJS_DIR%\npm.cmd" (
  set "PATH=%NODEJS_DIR%;%PATH%"
  set "NPM_CMD=%NODEJS_DIR%\npm.cmd"
) else (
  set "NPM_CMD=npm.cmd"
)

echo Starting AgentA3 web development server...
call "%NPM_CMD%" run dev

echo.
echo The web development server has stopped.
echo Press any key to close this window.
pause >nul
