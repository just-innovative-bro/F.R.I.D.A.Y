@echo off
setlocal

cd /d "%~dp0"
if not defined LOCALAPPDATA (
    echo LOCALAPPDATA is not available. Run this from a normal Windows user session.
    exit /b 1
)

set "FRIDAY_CONFIG_DIR=%LOCALAPPDATA%\F.R.I.D.A.Y"
if not exist "%FRIDAY_CONFIG_DIR%" mkdir "%FRIDAY_CONFIG_DIR%"
if not exist "%FRIDAY_CONFIG_DIR%\server.env" (
    copy "%~dp0.env.example" "%FRIDAY_CONFIG_DIR%\server.env" >nul
    echo Created "%FRIDAY_CONFIG_DIR%\server.env".
    echo Edit that file and configure the AI provider before sending chat messages.
    echo.
)

if not exist "%~dp0.venv\Scripts\python.exe" (
    echo Backend environment not found.
    echo Create it with: py -3.12 -m venv .venv
    echo Then install with: .venv\Scripts\python.exe -m pip install -r requirements-backend.txt
    exit /b 1
)

"%~dp0.venv\Scripts\python.exe" "%~dp0friday_server.py"
exit /b %ERRORLEVEL%
