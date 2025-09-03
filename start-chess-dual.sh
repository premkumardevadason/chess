#!/bin/bash

# Chess Application - Dual Mode Startup (Linux/macOS)
# Starts the Chess application in dual mode

set -e  # Exit on any error

echo "========================================"
echo "Chess Application - Dual Mode Startup"
echo "========================================"
echo ""
echo "Starting Chess application in dual mode..."
echo "- Web UI: http://localhost:8080"
echo "- React Frontend: http://localhost:8080/react"
echo "- MCP WebSocket: ws://localhost:8082"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven (mvn) is not found in PATH"
    echo "Please ensure Maven is installed and added to your PATH"
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not found in PATH"
    echo "Please ensure Java is installed and added to your PATH"
    exit 1
fi

# Function to handle cleanup on exit
cleanup() {
    echo ""
    echo "Stopping application..."
    # Kill any background processes
    jobs -p | xargs -r kill
    echo "Application stopped."
    exit 0
}

# Set up signal handlers
trap cleanup SIGINT SIGTERM

# Start the application
echo "Starting Spring Boot application..."
mvn spring-boot:run -Dspring-boot.run.arguments=--dual-mode

echo ""
echo "Application stopped."
