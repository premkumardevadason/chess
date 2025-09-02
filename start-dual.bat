@echo off
echo Building application...
mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)
echo Starting Chess application in dual mode...
java -jar target/chess-0.0.1-SNAPSHOT.jar --dual-mode
