@echo off
REM MCP Wireshark Plugin Installation Script for Windows
REM This script installs the Lua-based MCP dissector for Wireshark

echo Installing MCP Wireshark Plugin...

REM Check if Wireshark is installed
where wireshark >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Wireshark is not installed or not in PATH
    echo Please install Wireshark from https://www.wireshark.org/
    pause
    exit /b 1
)

REM Get Wireshark version
for /f "tokens=*" %%i in ('wireshark -v 2^>^&1 ^| findstr "Wireshark"') do set WIRESHARK_VERSION=%%i
echo Found: %WIRESHARK_VERSION%

REM Create plugins directory if it doesn't exist
set PLUGINS_DIR=%APPDATA%\Wireshark\plugins
if not exist "%PLUGINS_DIR%" (
    echo Creating plugins directory: %PLUGINS_DIR%
    mkdir "%PLUGINS_DIR%"
)

REM Copy the Lua plugin
echo Copying mcp_dissector.lua to %PLUGINS_DIR%...
copy mcp_dissector.lua "%PLUGINS_DIR%\" >nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to copy plugin file
    pause
    exit /b 1
)

REM Verify installation
if exist "%PLUGINS_DIR%\mcp_dissector.lua" (
    echo SUCCESS: MCP Wireshark plugin installed successfully!
    echo.
    echo Installation location: %PLUGINS_DIR%\mcp_dissector.lua
    echo.
    echo To use the plugin:
    echo 1. Restart Wireshark
    echo 2. Capture traffic on port 8082 (or your MCP server port)
    echo 3. Use display filter: mcp
    echo.
    echo For more information, see README.md
) else (
    echo ERROR: Plugin installation failed
    pause
    exit /b 1
)

echo.
echo Press any key to exit...
pause >nul
