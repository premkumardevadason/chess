@echo off
echo Chess Application - Different Modes Demo
echo.

echo Available modes:
echo 1. Web-only mode (default)
echo 2. Dual mode (Web + MCP WebSocket on port 8082) - RECOMMENDED
echo 3. Dual mode with custom port
echo 4. MCP-only WebSocket mode
echo 5. MCP-only stdio mode
echo.

set /p choice="Select mode (1-5): "

if "%choice%"=="1" (
    echo Starting Web-only mode...
    mvn spring-boot:run
) else if "%choice%"=="2" (
    echo Starting Dual mode (Web + MCP WebSocket on port 8082)...
    mvn spring-boot:run -Dspring-boot.run.arguments="--dual-mode"
) else if "%choice%"=="3" (
    set /p port="Enter port number (default 8082): "
    if "%port%"=="" set port=8082
    echo Starting Dual mode on port %port%...
    mvn spring-boot:run -Dspring-boot.run.arguments="--dual-mode --port=%port%"
) else if "%choice%"=="4" (
    set /p port="Enter port number (default 8082): "
    if "%port%"=="" set port=8082
    echo Starting MCP-only WebSocket mode on port %port%...
    mvn spring-boot:run -Dspring-boot.run.arguments="--mcp --transport=websocket --port=%port%"
) else if "%choice%"=="5" (
    echo Starting MCP-only stdio mode...
    mvn spring-boot:run -Dspring-boot.run.arguments="--mcp --transport=stdio"
) else (
    echo Invalid choice. Exiting.
    pause
    exit /b 1
)