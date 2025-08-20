@echo off
echo Testing NIO.2 Implementation with DeepLearning4J Stream Bridge
echo.

REM Enable async I/O for all AI systems
set JAVA_OPTS=-Dchess.async.qlearning=true -Dchess.async.genetic=true -Dchess.async.alphafold3=true -Dchess.async.dqn=true -Dchess.async.deeplearning=true -Dchess.async.cnn=true -Dchess.async.alphazero=true -Dchess.async.leelazerochess=true

echo Starting Chess application with NIO.2 enabled for all AI systems...
echo JAVA_OPTS: %JAVA_OPTS%
echo.

REM Run the application (will need proper Maven/Java setup)
echo Run: java %JAVA_OPTS% -jar target/chess-0.0.1-SNAPSHOT.jar
echo.
echo Expected output:
echo - "DeepLearning4J ModelSerializer API Investigation"
echo - "Stream methods found - NIO.2 compatible"
echo - "ASYNC I/O: [AI] using NIO.2 async LOAD/SAVE path"
echo - "DeepLearning4J model loaded/saved using NIO.2 stream bridge"
echo.
pause