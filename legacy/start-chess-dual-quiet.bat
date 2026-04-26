@echo off
REM Chess Application - Dual Mode Startup (Quiet)
REM Starts the application without console output

REM Check if Maven is available
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Maven (mvn) is not found in PATH
    exit /b 1
)

REM Start the application in background
start /B mvn spring-boot:run -Dspring-boot.run.arguments=--dual-mode >nul 2>&1

REM Wait a moment for startup
timeout /t 3 /nobreak >nul

echo Chess application started in dual mode
echo Web UI: http://localhost:8080
echo React Frontend: http://localhost:8080/react
echo MCP WebSocket: ws://localhost:8082
