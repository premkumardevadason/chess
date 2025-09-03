@echo off
echo ========================================
echo Chess Application - Dual Mode Startup
echo ========================================
echo.
echo Starting Chess application in dual mode...
echo - Web UI: http://localhost:8080
echo - React Frontend: http://localhost:8080/react
echo - MCP WebSocket: ws://localhost:8082
echo.
echo Press Ctrl+C to stop the application
echo.

REM Check if Maven is available
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Maven (mvn) is not found in PATH
    echo Please ensure Maven is installed and added to your PATH
    pause
    exit /b 1
)

REM Check if Java is available
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Java is not found in PATH
    echo Please ensure Java is installed and added to your PATH
    pause
    exit /b 1
)

REM Build the application first
echo Building application...
mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)

REM Start the application
echo Starting Spring Boot application...
java -jar target/chess-0.0.1-SNAPSHOT.jar --dual-mode

echo.
echo Application stopped.
pause
