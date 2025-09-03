# Chess Application Startup Automation

This document describes various ways to automate the startup of the Chess Spring Boot application with the `--dual-mode` parameter.

## Quick Start Options

### 1. Simple Batch Script (Windows)
```bash
start-chess-dual.bat
```
- Interactive startup with console output
- Checks for Maven and Java availability
- Shows startup URLs
- Easy to use for development

### 2. Quiet Background Startup (Windows)
```bash
start-chess-dual-quiet.bat
```
- Starts application in background
- Minimal console output
- Good for quick testing

### 3. PowerShell Script (Windows)
```bash
# Basic startup
.\start-chess-dual.ps1

# Quiet mode
.\start-chess-dual.ps1 -Quiet

# Background mode
.\start-chess-dual.ps1 -Background

# Custom ports
.\start-chess-dual.ps1 -Port 8080 -MCPPort 8082
```

### 4. Linux/macOS Script
```bash
./start-chess-dual.sh
```

### 5. Windows Service (Advanced)
```bash
start-chess-dual-service.bat
```
- Installs as Windows service using NSSM
- Starts automatically on system boot
- Requires NSSM installation

## Manual Commands

### Direct Maven Command
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--dual-mode
```

### With Custom Ports
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--dual-mode --port=8080 --mcp-port=8082"
```

### Using JAR File (after building)
```bash
# Build first
mvn clean package

# Run with JAR
java -jar target/chess-0.0.1-SNAPSHOT.jar --dual-mode
```

## Application URLs

When running in dual mode, the application provides:

- **Web UI**: http://localhost:8080
- **React Frontend**: http://localhost:8080/react
- **MCP WebSocket**: ws://localhost:8082
- **API Endpoints**: http://localhost:8080/api/*

## Prerequisites

1. **Java**: JDK 8 or higher
2. **Maven**: 3.6 or higher
3. **Node.js**: 16 or higher (for React frontend development)

## Troubleshooting

### Maven Not Found
```
ERROR: Maven (mvn) is not found in PATH
```
**Solution**: Install Maven and add it to your system PATH.

### Java Not Found
```
ERROR: Java is not found in PATH
```
**Solution**: Install Java JDK and add it to your system PATH.

### Port Already in Use
```
Port 8080 was already in use
```
**Solution**: 
- Stop other applications using port 8080
- Use custom port: `mvn spring-boot:run -Dspring-boot.run.arguments="--dual-mode --port=8081"`

### Build Failures
```
BUILD FAILURE
```
**Solution**: 
- Clean and rebuild: `mvn clean package`
- Check for compilation errors
- Ensure all dependencies are available

## Advanced Configuration

### Environment Variables
You can set these environment variables to customize behavior:

```bash
# Windows
set CHESS_PORT=8080
set CHESS_MCP_PORT=8082
set CHESS_PROFILE=dev

# Linux/macOS
export CHESS_PORT=8080
export CHESS_MCP_PORT=8082
export CHESS_PROFILE=dev
```

### Application Properties
Create `src/main/resources/application-dual.properties`:

```properties
# Server configuration
server.port=8080
server.servlet.context-path=/

# MCP configuration
chess.mcp.port=8082
chess.mcp.transport=websocket

# Dual mode settings
chess.dual-mode.enabled=true
chess.dual-mode.web-ui.enabled=true
chess.dual-mode.react-ui.enabled=true
```

## Integration with IDEs

### IntelliJ IDEA
1. Create a new Run Configuration
2. Set Main class: `com.example.chess.ChessApplication`
3. Set Program arguments: `--dual-mode`
4. Set Working directory: Project root

### Eclipse
1. Right-click project → Run As → Run Configurations
2. Create new Java Application configuration
3. Set Main class: `com.example.chess.ChessApplication`
4. Set Program arguments: `--dual-mode`

### Visual Studio Code
Create `.vscode/launch.json`:
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Chess Dual Mode",
            "request": "launch",
            "mainClass": "com.example.chess.ChessApplication",
            "args": "--dual-mode",
            "cwd": "${workspaceFolder}"
        }
    ]
}
```

## Monitoring and Logs

### Log Files
- Application logs: `logs/application.log`
- Service logs: `logs/chess-service.log` (Windows service)
- Error logs: `logs/chess-service-error.log` (Windows service)

### Health Checks
- Application health: http://localhost:8080/actuator/health
- MCP status: Check WebSocket connection to ws://localhost:8082

## Performance Optimization

### JVM Options
For better performance, you can add JVM options:

```bash
# Windows
set MAVEN_OPTS=-Xmx2g -Xms1g -XX:+UseG1GC

# Linux/macOS
export MAVEN_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"
```

### Production Deployment
For production, consider:
1. Using a proper application server (Tomcat, Jetty)
2. Setting up reverse proxy (Nginx, Apache)
3. Using process managers (PM2, systemd)
4. Implementing proper logging and monitoring
