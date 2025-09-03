@echo off
echo Testing MCP Chess Server
echo.
echo Starting MCP server in stdio mode...
echo.
echo You can test with these JSON-RPC commands:
echo.
echo 1. Initialize:
echo {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test-client","version":"1.0.0"}}}
echo.
echo 2. List Tools:
echo {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
echo.
echo 3. Create Game:
echo {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"create_chess_game","arguments":{"aiOpponent":"Negamax","playerColor":"white","difficulty":5}}}
echo.
echo Starting server...
java -jar target\chess-0.0.1-SNAPSHOT.jar --mcp --transport=stdio