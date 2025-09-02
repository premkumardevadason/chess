@echo off
echo ========================================
echo Chess Application - Dual Mode Service
echo ========================================
echo.
echo This script will start the Chess application as a Windows service
echo using NSSM (Non-Sucking Service Manager)
echo.

REM Check if NSSM is available
where nssm >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: NSSM is not found in PATH
    echo Please download NSSM from https://nssm.cc/download
    echo and add it to your PATH
    pause
    exit /b 1
)

REM Check if Maven is available
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Maven (mvn) is not found in PATH
    echo Please ensure Maven is installed and added to your PATH
    pause
    exit /b 1
)

set SERVICE_NAME=ChessDualMode
set SERVICE_DISPLAY_NAME=Chess Application Dual Mode
set SERVICE_DESCRIPTION=Chess Application running in dual mode (Web + MCP)

echo Installing service: %SERVICE_NAME%
echo Display Name: %SERVICE_DISPLAY_NAME%
echo.

REM Install the service
nssm install %SERVICE_NAME% "mvn" "spring-boot:run -Dspring-boot.run.arguments=--dual-mode"
if %errorlevel% neq 0 (
    echo ERROR: Failed to install service
    pause
    exit /b 1
)

REM Configure service settings
nssm set %SERVICE_NAME% DisplayName "%SERVICE_DISPLAY_NAME%"
nssm set %SERVICE_NAME% Description "%SERVICE_DESCRIPTION%"
nssm set %SERVICE_NAME% Start SERVICE_AUTO_START
nssm set %SERVICE_NAME% AppDirectory "%CD%"
nssm set %SERVICE_NAME% AppStdout "%CD%\logs\chess-service.log"
nssm set %SERVICE_NAME% AppStderr "%CD%\logs\chess-service-error.log"

REM Create logs directory if it doesn't exist
if not exist "logs" mkdir logs

echo.
echo Service installed successfully!
echo.
echo To start the service: nssm start %SERVICE_NAME%
echo To stop the service: nssm stop %SERVICE_NAME%
echo To remove the service: nssm remove %SERVICE_NAME% confirm
echo.
echo Service will start automatically on system boot.
echo.

set /p start_now="Start the service now? (y/n): "
if /i "%start_now%"=="y" (
    echo Starting service...
    nssm start %SERVICE_NAME%
    if %errorlevel% equ 0 (
        echo Service started successfully!
        echo Web UI: http://localhost:8080
        echo React Frontend: http://localhost:8080/react
        echo MCP WebSocket: ws://localhost:8082
    ) else (
        echo ERROR: Failed to start service
    )
)

pause
