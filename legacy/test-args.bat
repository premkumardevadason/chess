@echo off
echo Testing Chess Application Command Line Arguments
echo.

echo Test 1: Default mode (no arguments)
echo Command: mvn spring-boot:run
echo Expected: Web-only mode
echo.

echo Test 2: Dual mode (recommended)
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--dual-mode"
echo Expected: Web interface + MCP WebSocket on port 8082
echo.

echo Test 3: Dual mode with custom port
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--dual-mode --port=8083"
echo Expected: Web interface + MCP WebSocket on port 8083
echo.

echo Test 4: MCP-only WebSocket
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--mcp --transport=websocket --port=8082"
echo Expected: MCP WebSocket server only (no web interface)
echo.

echo Test 5: MCP-only stdio
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--mcp --transport=stdio"
echo Expected: MCP stdio server only (no web interface)
echo.

echo Run any of these commands to test the different modes.
pause