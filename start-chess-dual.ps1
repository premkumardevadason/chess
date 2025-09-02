# Chess Application - Dual Mode Startup (PowerShell)
# Starts the Chess application in dual mode with enhanced features

param(
    [switch]$Quiet,
    [switch]$Background,
    [int]$Port = 8080,
    [int]$MCPPort = 8082
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Chess Application - Dual Mode Startup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not $Quiet) {
    Write-Host "Configuration:" -ForegroundColor Yellow
    Write-Host "- Web UI: http://localhost:$Port" -ForegroundColor Green
    Write-Host "- React Frontend: http://localhost:$Port/react" -ForegroundColor Green
    Write-Host "- MCP WebSocket: ws://localhost:$MCPPort" -ForegroundColor Green
    Write-Host ""
}

# Check if Maven is available
try {
    $mvnVersion = & mvn --version 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Maven not found"
    }
} catch {
    Write-Host "ERROR: Maven (mvn) is not found in PATH" -ForegroundColor Red
    Write-Host "Please ensure Maven is installed and added to your PATH" -ForegroundColor Red
    if (-not $Quiet) { Read-Host "Press Enter to exit" }
    exit 1
}

# Check if Java is available
try {
    $javaVersion = & java -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Java not found"
    }
} catch {
    Write-Host "ERROR: Java is not found in PATH" -ForegroundColor Red
    Write-Host "Please ensure Java is installed and added to your PATH" -ForegroundColor Red
    if (-not $Quiet) { Read-Host "Press Enter to exit" }
    exit 1
}

# Build arguments
$arguments = "--dual-mode"
if ($Port -ne 8080) {
    $arguments += " --port=$Port"
}
if ($MCPPort -ne 8082) {
    $arguments += " --mcp-port=$MCPPort"
}

if (-not $Quiet) {
    Write-Host "Starting Spring Boot application..." -ForegroundColor Yellow
    Write-Host "Arguments: $arguments" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Press Ctrl+C to stop the application" -ForegroundColor Yellow
    Write-Host ""
}

# Start the application
if ($Background) {
    # Start in background
    $job = Start-Job -ScriptBlock {
        param($args)
        & mvn spring-boot:run -Dspring-boot.run.arguments=$args
    } -ArgumentList $arguments
    
    Write-Host "Application started in background (Job ID: $($job.Id))" -ForegroundColor Green
    Write-Host "To stop: Stop-Job $($job.Id)" -ForegroundColor Gray
    Write-Host "To view output: Receive-Job $($job.Id)" -ForegroundColor Gray
} else {
    # Start in foreground
    try {
        & mvn spring-boot:run -Dspring-boot.run.arguments=$arguments
    } catch {
        Write-Host "Application stopped with error: $_" -ForegroundColor Red
    }
}

if (-not $Quiet) {
    Write-Host ""
    Write-Host "Application stopped." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
}
