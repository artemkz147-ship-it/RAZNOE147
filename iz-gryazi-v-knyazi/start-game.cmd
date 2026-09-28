@echo off
cd /d "%~dp0"
start "" /b node server.mjs
timeout /t 2 >nul
start "" http://localhost:4173
pause
