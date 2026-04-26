git stat# MCP Wireshark Plugin

This directory contains Wireshark plugins for analyzing Model Context Protocol (MCP) communication between AI agents and the Chess MCP Server.

## Overview

The MCP Wireshark plugins provide deep packet inspection capabilities for:
- **JSON-RPC 2.0** protocol analysis
- **WebSocket** frame dissection
- **MCP-specific** method and parameter parsing
- **Chess game** tool and resource identification
- **Encryption** field detection (Double Ratchet)
- **Agent identification** and session tracking

## Files

### Lua Plugin (Recommended)
- `mcp_dissector.lua` - Complete Lua-based dissector
- Easy to install and modify
- No compilation required
- Cross-platform compatible

### C Plugin (High Performance)
- `mcp_dissector.c` - C-based dissector for performance
- `CMakeLists.txt` - Build configuration
- Requires Wireshark development headers
- Better performance for high-traffic analysis

## Installation

### Lua Plugin Installation

1. **Copy the plugin file:**
   ```bash
   # Windows
   copy mcp_dissector.lua "%APPDATA%\Wireshark\plugins\"
   
   # Linux/macOS
   cp mcp_dissector.lua ~/.local/lib/wireshark/plugins/
   ```

2. **Restart Wireshark**

3. **Verify installation:**
   - Go to Help → About Wireshark → Plugins
   - Look for "mcp_dissector.lua" in the list

### C Plugin Installation

#### Prerequisites

1. **Install Wireshark development packages:**
   ```bash
   # Ubuntu/Debian
   sudo apt-get install libwireshark-dev libwireshark-data cmake build-essential
   
   # CentOS/RHEL/Fedora
   sudo yum install wireshark-devel cmake gcc gcc-c++ make
   # or for newer versions:
   sudo dnf install wireshark-devel cmake gcc gcc-c++ make
   
   # Arch Linux
   sudo pacman -S wireshark-cli cmake gcc make
   
   # Windows (with vcpkg)
   vcpkg install wireshark
   # Or install Wireshark with development headers from official installer
   ```

#### Building the Plugin

2. **Build the plugin:**
   ```bash
   # Create build directory
   mkdir build && cd build
   
   # Configure with CMake
   cmake ..
   
   # Build the plugin
   make
   # On Windows with Visual Studio:
   # cmake --build . --config Release
   ```

3. **Install the plugin:**
   ```bash
   # Linux/macOS
   sudo make install
   
   # Or manually copy the shared library:
   # cp libmcp_dissector.so /usr/lib/wireshark/plugins/
   # or
   # cp libmcp_dissector.so ~/.local/lib/wireshark/plugins/
   
   # Windows
   # Copy mcp_dissector.dll to your Wireshark plugins directory:
   # C:\Program Files\Wireshark\plugins\4.0\
   ```

#### Windows-Specific Build Instructions

For Windows users, you have several options:

**Option 1: Using Visual Studio**
```cmd
mkdir build
cd build
cmake .. -G "Visual Studio 17 2022"
cmake --build . --config Release
```

**Option 2: Using MinGW**
```cmd
mkdir build
cd build
cmake .. -G "MinGW Makefiles"
cmake --build .
```

**Option 3: Using vcpkg (Recommended)**
```cmd
# Install vcpkg and wireshark package
vcpkg install wireshark:x64-windows

# Build with vcpkg toolchain
mkdir build
cd build
cmake .. -DCMAKE_TOOLCHAIN_FILE=[path-to-vcpkg]/scripts/buildsystems/vcpkg.cmake
cmake --build . --config Release
```

## Usage

### Capturing MCP Traffic

1. **Start your Chess MCP Server:**
   ```bash
   java -jar chess-application.jar --dual-mode --port=8082
   ```

2. **Start Wireshark capture:**
   - Filter: `tcp.port == 8082`
   - Or use: `mcp` in the display filter

3. **Connect an MCP client** to analyze the communication

### Display Filters

Use these filters to analyze specific MCP traffic:

```
# All MCP traffic
mcp

# Specific methods
mcp.method == "initialize"
mcp.method == "tools/call"
mcp.method contains "chess"

# Encrypted messages
mcp.encrypted == true

# Specific agent
mcp.agent_id == "agent-12345"

# Error messages
mcp.error_code != 0

# WebSocket frames
mcp_ws

# Chess-specific tools
mcp.method == "create_chess_game"
mcp.method == "make_chess_move"
```

### Protocol Analysis

The plugin provides detailed analysis of:

#### JSON-RPC 2.0 Fields
- `mcp.version` - Protocol version (2.0)
- `mcp.method` - Method name with descriptions
- `mcp.id` - Request/response correlation ID
- `mcp.params` - Method parameters
- `mcp.result` - Method result
- `mcp.error_code` - Error code (-32700 to -32099)
- `mcp.error_message` - Error description

#### MCP-Specific Fields
- `mcp.agent_id` - Agent identifier
- `mcp.encrypted` - Encryption flag
- `mcp.ciphertext` - Encrypted content
- `mcp.iv` - Initialization vector
- `mcp.ratchet_header` - Double ratchet header

#### WebSocket Fields
- `mcp_ws.opcode` - Frame type (1 = text)
- `mcp_ws.fin` - Final frame flag
- `mcp_ws.payload_length` - Payload size
- `mcp_ws.payload` - Raw payload data

## Supported MCP Methods

### Core MCP Methods
- `initialize` - Connection handshake
- `tools/list` - List available tools
- `resources/list` - List available resources
- `tools/call` - Execute a tool
- `resources/read` - Read a resource

### Chess Tools
- `create_chess_game` - Create new game
- `make_chess_move` - Make a move
- `get_board_state` - Get current state
- `analyze_position` - Analyze position
- `get_legal_moves` - Get valid moves
- `get_move_hint` - Get AI suggestions
- `create_tournament` - Create tournament
- `get_tournament_status` - Get tournament info

### Chess Resources
- `chess://ai-systems` - AI system information
- `chess://opening-book` - Opening database
- `chess://game-history` - Game history
- `chess://training-data` - Training data
- `chess://performance-metrics` - Performance metrics

### Notifications
- `notifications/initialized` - Connection ready
- `notifications/chess/game_state` - Game state changes
- `notifications/chess/ai_move` - AI move notifications
- `notifications/chess/training_progress` - Training updates

## Troubleshooting

### Plugin Not Loading
1. Check file permissions
2. Verify Wireshark version compatibility
3. Check console for Lua errors (Help → About → Plugins)

### C Plugin Build Issues

#### CMake Configuration Errors
```bash
# If CMake can't find Wireshark:
export PKG_CONFIG_PATH=/usr/lib/x86_64-linux-gnu/pkgconfig:$PKG_CONFIG_PATH

# Or specify Wireshark path explicitly:
cmake .. -DWIRESHARK_ROOT=/usr/lib/w86_64-linux-gnu
```

#### Compilation Errors
- Ensure you have the correct Wireshark development headers
- Check that your compiler supports C99 standard
- Verify that all required libraries are linked

#### Windows Build Issues
- Ensure Visual Studio Build Tools are installed
- Check that vcpkg is properly configured
- Verify Wireshark development headers are available

#### Plugin Loading Issues
- Check that the plugin file has correct permissions
- Verify the plugin is in the correct Wireshark plugins directory
- Check Wireshark logs for loading errors

### No MCP Traffic Detected
1. Verify port 8082 is being captured
2. Check WebSocket upgrade is successful
3. Ensure JSON-RPC 2.0 format is correct

### Performance Issues
1. Use display filters to reduce packet processing
2. Consider using the C plugin for high-traffic scenarios
3. Disable other unnecessary dissectors

## Development

### Adding New Methods
Edit the method arrays in the dissector files:
- `MCP_METHODS` - Core MCP methods
- `CHESS_TOOLS` - Chess-specific tools
- `CHESS_RESOURCES` - Chess resources

### Customizing Display
Modify the tree structure and field definitions to add new analysis features.

### Debugging
Enable Wireshark's Lua console for debugging:
- View → Lua Console
- Check for error messages during packet processing

## License

This plugin is provided as part of the Chess MCP Server project and follows the same licensing terms.
