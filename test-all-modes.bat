@echo off
echo Testing ALL documented command line combinations
echo.

echo ========================================
echo DOCUMENTED COMMANDS TO TEST:
echo ========================================
echo.

echo 1. Web Application only (default)
echo    mvn spring-boot:run
echo    java -jar chess-application.jar
echo.

echo 2. Dual mode (Web + MCP) - RECOMMENDED  
echo    java -jar chess-application.jar --dual-mode
echo    Default: WebSocket transport on port 8082
echo.

echo 3. Dual mode with custom port
echo    java -jar chess-application.jar --dual-mode --port=8083
echo.

echo 4. Dual mode (alternative syntax)
echo    java -jar chess-application.jar --mcp --dual-mode
echo.

echo 5. MCP Server only (no web interface)
echo    java -jar chess-application.jar --mcp --transport=websocket --port=8082
echo    java -jar chess-application.jar --mcp --transport=stdio
echo.

echo ========================================
echo TESTING EACH MODE:
echo ========================================
echo.

set /p test="Press Enter to start testing or Ctrl+C to exit..."

echo Testing Mode 1: Default (Web-only)
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="" -q
timeout /t 2 >nul
echo Expected: Web-only mode
echo.

echo Testing Mode 2: Dual mode
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--dual-mode" -q  
timeout /t 2 >nul
echo Expected: Web + MCP WebSocket on port 8082
echo.

echo Testing Mode 3: Dual mode with custom port
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--dual-mode --port=8083" -q
timeout /t 2 >nul
echo Expected: Web + MCP WebSocket on port 8083
echo.

echo Testing Mode 4: Alternative dual mode syntax
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--mcp --dual-mode" -q
timeout /t 2 >nul
echo Expected: Web + MCP WebSocket on port 8082
echo.

echo Testing Mode 5a: MCP-only WebSocket
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--mcp --transport=websocket --port=8082" -q
timeout /t 2 >nul
echo Expected: MCP WebSocket only (no web interface)
echo.

echo Testing Mode 5b: MCP-only stdio
echo Command: mvn spring-boot:run -Dspring-boot.run.arguments="--mcp --transport=stdio" -q
timeout /t 2 >nul
echo Expected: MCP stdio only (no web interface)
echo.

echo ========================================
echo ALL TESTS COMPLETED
echo ========================================
echo.
echo To run any specific test, use the commands shown above.
echo.
pause