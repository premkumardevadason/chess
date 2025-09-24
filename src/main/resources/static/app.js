let gameState = { board: [], whiteTurn: true, gameOver: false };
let selectedSquare = null;
let stompClient = null;
let binaryWebSocket = null;
let isConnected = false;
let isBinaryConnected = false;

// Eye-tracking variables
let eyeTrackingEnabled = false;
let webcamActive = false;
let calibrationActive = false;
let videoStream = null;
let videoElement = null;

function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(() => socket);
    
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        isConnected = true;
        
        // Subscribe to game state updates
        stompClient.subscribe('/topic/gameState', function (message) {
            const data = JSON.parse(message.body);
            gameState = data;
            renderBoard();
            updateTurnInfo();
        });
        
        // Subscribe to eye-tracking status
        stompClient.subscribe('/topic/eyeTrackingStatus', function (message) {
            const data = JSON.parse(message.body);
            if (data.webcamEnabled !== undefined) {
                webcamActive = data.webcamEnabled;
                updateEyeTrackingUI();
            }
        });
        
        // Subscribe to square highlighting for gaze tracking
        stompClient.subscribe('/topic/squareHighlight', function (message) {
            const data = JSON.parse(message.body);
            highlightGazeSquare(data.square, data.type || data.color || 'redDot', data.duration);
        });
        
        // Subscribe to piece intention analysis
        stompClient.subscribe('/topic/pieceIntention', function (message) {
            const data = JSON.parse(message.body);
            updatePredictionDisplay(data);
        });
        
        // Subscribe to training status
        stompClient.subscribe('/topic/training', function (message) {
            const status = JSON.parse(message.body);
            const statusDiv = document.getElementById('training-status');
            if (statusDiv) statusDiv.textContent = status.message;
        });
        
        loadBoard();
        connectBinaryWebSocket();
        
    }, function(error) {
        console.log('WebSocket connection failed');
        isConnected = false;
    });
}

// Binary WebSocket Manager with exponential backoff
class BinaryWebSocketManager {
    constructor() {
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.baseReconnectDelay = 1000; // 1 second
        this.maxReconnectDelay = 30000; // 30 seconds
        this.isConnected = false;
        this.reconnectTimeout = null;
    }
    
    connect() {
        try {
            console.log(`Attempting binary WebSocket connection (attempt ${this.reconnectAttempts + 1})`);
            
            this.binaryWebSocket = new WebSocket('ws://localhost:8081/ws-binary');
            this.binaryWebSocket.binaryType = 'arraybuffer';
            
            this.binaryWebSocket.onopen = () => {
                console.log('Binary WebSocket connected successfully');
                this.isConnected = true;
                this.reconnectAttempts = 0;
                isBinaryConnected = true;
                updateEyeTrackingUI();
            };
            
            this.binaryWebSocket.onclose = (event) => {
                console.log(`Binary WebSocket disconnected: ${event.code} - ${event.reason}`);
                this.isConnected = false;
                isBinaryConnected = false;
                updateEyeTrackingUI();
                
                // Stop video streaming if active
                stopVideoFrameStreaming();
                
                // Attempt reconnection if not a clean close
                if (event.code !== 1000 && this.reconnectAttempts < this.maxReconnectAttempts) {
                    this.scheduleReconnect();
                }
            };
            
            this.binaryWebSocket.onerror = (error) => {
                console.error('Binary WebSocket error:', error);
                this.isConnected = false;
                isBinaryConnected = false;
            };
            
        } catch (error) {
            console.error('Failed to create binary WebSocket:', error);
            this.scheduleReconnect();
        }
    }
    
    scheduleReconnect() {
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
        }
        
        const delay = Math.min(
            this.baseReconnectDelay * Math.pow(2, this.reconnectAttempts),
            this.maxReconnectDelay
        );
        
        console.log(`Scheduling reconnection in ${delay}ms (attempt ${this.reconnectAttempts + 1}/${this.maxReconnectAttempts})`);
        
        this.reconnectTimeout = setTimeout(() => {
            this.reconnectAttempts++;
            this.connect();
        }, delay);
    }
    
    disconnect() {
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }
        
        if (this.binaryWebSocket) {
            this.binaryWebSocket.close(1000, 'Client disconnecting');
            this.binaryWebSocket = null;
        }
        
        this.isConnected = false;
        isBinaryConnected = false;
    }
    
    send(data) {
        if (this.isConnected && this.binaryWebSocket && this.binaryWebSocket.readyState === WebSocket.OPEN) {
            this.binaryWebSocket.send(data);
            return true;
        } else {
            console.warn('Binary WebSocket not connected, cannot send data');
            return false;
        }
    }
}

// Create global instance
const binaryWebSocketManager = new BinaryWebSocketManager();

function connectBinaryWebSocket() {
    binaryWebSocketManager.connect();
}

function renderBoard() {
    const boardElement = document.getElementById('chess-board');
    if (!boardElement) return;
    
    boardElement.innerHTML = '';
    
    for (let i = 0; i < 8; i++) {
        const rowElement = document.createElement('div');
        rowElement.className = 'board-row';
        
        for (let j = 0; j < 8; j++) {
            const cellElement = document.createElement('div');
            cellElement.className = `board-cell ${(i + j) % 2 === 0 ? 'light' : 'dark'}`;
            
            if (selectedSquare && selectedSquare.row === i && selectedSquare.col === j) {
                cellElement.classList.add('selected');
            }
            
            if (gameState.kingInCheck && gameState.kingInCheck[0] === i && gameState.kingInCheck[1] === j) {
                cellElement.classList.add('king-in-check');
            }
            
            cellElement.textContent = gameState.board[i][j] || '';
            cellElement.onclick = () => onSquareClick(i, j);
            
            rowElement.appendChild(cellElement);
        }
        
        boardElement.appendChild(rowElement);
    }
}

function onSquareClick(row, col) {
    if (!gameState.whiteTurn) return;
    
    const piece = gameState.board[row][col];
    
    if (!selectedSquare) {
        if (piece && isPieceWhite(piece)) {
            selectedSquare = { row, col };
            renderBoard();
        }
    } else {
        if (piece && isPieceWhite(piece)) {
            selectedSquare = { row, col };
            renderBoard();
            return;
        }
        
        makeMove(selectedSquare.row, selectedSquare.col, row, col);
        selectedSquare = null;
    }
}

function isPieceWhite(piece) {
    return '♔♕♖♗♘♙'.includes(piece);
}

function makeMove(fromRow, fromCol, toRow, toCol) {
    const move = { fromRow, fromCol, toRow, toCol };
    
    if (isConnected && stompClient) {
        stompClient.send("/app/move", {}, JSON.stringify(move));
    }
}

function newGame() {
    if (isConnected && stompClient) {
        stompClient.send("/app/newgame", {}, JSON.stringify({}));
        selectedSquare = null;
    }
    
    // Disable webcam on new game
    if (webcamActive) {
        disableWebcam();
    }
}

function loadBoard() {
    if (isConnected && stompClient) {
        stompClient.send("/app/board", {}, JSON.stringify({}));
    }
}

function updateTurnInfo() {
    const turnInfo = document.getElementById('turn-info');
    if (!turnInfo) return;
    
    if (gameState.gameOver && gameState.checkmate && gameState.winner) {
        turnInfo.textContent = `Congratulations! Checkmate! ${gameState.winner} wins!`;
    } else if (gameState.gameOver) {
        turnInfo.textContent = 'Game Over';
    } else {
        turnInfo.textContent = gameState.whiteTurn ? 'Your turn (White)' : 'Computer thinking...';
    }
}

// Eye-tracking functions
function toggleWebcam() {
    if (!eyeTrackingEnabled) {
        showConsentModal();
        return;
    }
    
    if (webcamActive) {
        disableWebcam();
    } else {
        enableWebcam();
    }
}

function showConsentModal() {
    const modal = document.getElementById('consent-modal');
    if (modal) modal.style.display = 'flex';
}

function acceptConsent() {
    const modal = document.getElementById('consent-modal');
    if (modal) modal.style.display = 'none';
    
    if (isConnected && stompClient) {
        stompClient.send("/app/eye-tracking/consent", {}, JSON.stringify({
            sessionId: generateSessionId(),
            consent: true,
            timestamp: Date.now()
        }));
    }
    
    eyeTrackingEnabled = true;
    updateEyeTrackingUI();
    enableWebcam();
}

function declineConsent() {
    const modal = document.getElementById('consent-modal');
    if (modal) modal.style.display = 'none';
    
    if (isConnected && stompClient) {
        stompClient.send("/app/eye-tracking/consent", {}, JSON.stringify({
            sessionId: generateSessionId(),
            consent: false,
            timestamp: Date.now()
        }));
    }
    
    eyeTrackingEnabled = false;
    updateEyeTrackingUI();
}

async function enableWebcam() {
    if (!eyeTrackingEnabled) return;
    
    try {
        videoStream = await navigator.mediaDevices.getUserMedia({ 
            video: { 
                width: { ideal: 640 }, 
                height: { ideal: 480 },
                facingMode: 'user'
            } 
        });
        
        if (!videoElement) {
            videoElement = document.createElement('video');
            videoElement.id = 'eye-tracking-video';
            videoElement.style.display = 'none';
            videoElement.autoplay = true;
            videoElement.muted = true;
            document.body.appendChild(videoElement);
        }
        
        videoElement.srcObject = videoStream;
        
        await new Promise((resolve) => {
            videoElement.onloadedmetadata = () => {
                videoElement.play();
                resolve();
            };
        });
        
        if (isConnected && stompClient) {
            stompClient.send("/app/eye-tracking/enable", {}, JSON.stringify({
                sessionId: generateSessionId(),
                timestamp: Date.now()
            }));
        }
        
        webcamActive = true;
        updateEyeTrackingUI();
        console.log('Webcam enabled for eye-tracking');
        
    } catch (error) {
        console.error('Failed to access webcam:', error);
        alert('Failed to access webcam. Please ensure camera permissions are granted.');
        webcamActive = false;
        updateEyeTrackingUI();
    }
}

function disableWebcam() {
    if (videoStream) {
        videoStream.getTracks().forEach(track => track.stop());
        videoStream = null;
    }
    
    if (videoElement) {
        videoElement.remove();
        videoElement = null;
    }
    
    if (isConnected && stompClient) {
        stompClient.send("/app/eye-tracking/disable", {}, JSON.stringify({
            sessionId: generateSessionId(),
            timestamp: Date.now()
        }));
    }
    
    webcamActive = false;
    stopVideoFrameStreaming();
    updateEyeTrackingUI();
    console.log('Webcam disabled');
}

function startCalibration() {
    if (!eyeTrackingEnabled || !webcamActive || !isBinaryConnected) {
        alert('Please enable webcam and ensure binary connection is established');
        return;
    }
    
    calibrationActive = true;
    showCalibrationProgress();
    showCalibrationOverlay();
    startCalibrationSequence();
}

function showCalibrationProgress() {
    const progressDiv = document.getElementById('calibration-progress');
    if (progressDiv) {
        progressDiv.style.display = 'block';
    }
}

function updateCalibrationProgress(current, total, message) {
    const progressBar = document.getElementById('calibration-progress-bar');
    const statusDiv = document.getElementById('calibration-status');
    
    if (progressBar) {
        const percentage = (current / total) * 100;
        progressBar.style.width = percentage + '%';
    }
    
    if (statusDiv) {
        statusDiv.textContent = message || `Calibrating ${current}/${total} squares...`;
    }
}

function hideCalibrationProgress() {
    const progressDiv = document.getElementById('calibration-progress');
    if (progressDiv) {
        progressDiv.style.display = 'none';
    }
}

function showCalibrationOverlay() {
    const overlay = document.createElement('div');
    overlay.className = 'calibration-overlay';
    overlay.id = 'calibration-overlay';
    document.body.appendChild(overlay);
}

function startCalibrationSequence() {
    // Generate 64 chess square positions
    const chessSquares = [];
    
    // Sequential order first (a8 to h1) - start with black rook
    for (let domRow = 0; domRow < 8; domRow++) {
        for (let file = 0; file < 8; file++) {
            const rank = 8 - domRow; // DOM row 0 = rank 8, DOM row 7 = rank 1
            const fileChar = String.fromCharCode('a'.charCodeAt(0) + file);
            const squareName = fileChar + rank;
            
            // Validate square name
            if (rank >= 1 && rank <= 8 && file >= 0 && file <= 7) {
                chessSquares.push({ square: squareName, domRow: domRow, file: file });
            } else {
                console.error('Invalid square generated:', squareName, 'rank:', rank, 'file:', file);
            }
        }
    }
    
    // Create random order for second round
    const randomSquares = [...chessSquares];
    for (let i = randomSquares.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [randomSquares[i], randomSquares[j]] = [randomSquares[j], randomSquares[i]];
    }
    
    // Combine sequential + random
    const allCalibrationPoints = [...chessSquares, ...randomSquares];
    
    let currentPoint = 0;
    let isSecondRound = false;
    
    function showNextPoint() {
        if (currentPoint >= allCalibrationPoints.length) {
            finishCalibration();
            return;
        }
        
        // Check if starting second round
        if (currentPoint === chessSquares.length && !isSecondRound) {
            isSecondRound = true;
            showRoundMessage('Round 2: Random Order', 2000);
            setTimeout(showNextPoint, 2000);
            return;
        }
        
        const point = allCalibrationPoints[currentPoint];
        
        // Recalculate board position for each point to handle scrolling
        const boardRect = document.getElementById('chess-board').getBoundingClientRect();
        const squareSize = 80; // Use actual CSS square size
        
        // Calculate fresh coordinates for current board position
        const x = (boardRect.left + (point.file * squareSize) + (squareSize / 2)) / window.innerWidth;
        const y = (boardRect.top + (point.domRow * squareSize) + (squareSize / 2)) / window.innerHeight;
        
        const calibrationPoint = document.createElement('div');
        calibrationPoint.className = 'calibration-point';
        calibrationPoint.style.left = (x * 100) + '%';
        calibrationPoint.style.top = (y * 100) + '%';
        calibrationPoint.id = 'calibration-point';
        
        // Add square label
        calibrationPoint.textContent = point.square;
        calibrationPoint.style.fontSize = '12px';
        calibrationPoint.style.color = 'white';
        calibrationPoint.style.textAlign = 'center';
        calibrationPoint.style.lineHeight = '20px';
        
        const existingPoint = document.getElementById('calibration-point');
        if (existingPoint) existingPoint.remove();
        
        document.body.appendChild(calibrationPoint);
        
        // Update progress
        const roundText = isSecondRound ? 'Round 2' : 'Round 1';
        const roundProgress = isSecondRound ? currentPoint - 64 : currentPoint;
        const roundTotal = 64;
        updateCalibrationProgress(currentPoint + 1, 128, `${roundText}: ${point.square} (${roundProgress + 1}/${roundTotal})`);
        
        console.log(`[JS] Showing red dot for point ${currentPoint}, square ${point.square}`);
        
        // Wait 2.5 seconds for eye saccade and focusing before capturing
        setTimeout(() => {
            if (isBinaryConnected && videoElement) {
                console.log(`[JS] Capturing data for point ${currentPoint}, square ${point.square}`);
                // Send current point index BEFORE incrementing
                sendCalibrationDataBinary(currentPoint, x, y);
            }
            
            // Increment AFTER sending data to maintain sync
            currentPoint++;
            setTimeout(showNextPoint, 1500); // 1.5s buffer after capture
        }, 2500);
    }
    
    // Show initial message
    showRoundMessage('Round 1: Sequential Order (a8 → h1)', 2000);
    setTimeout(showNextPoint, 2000);
}

function showRoundMessage(message, duration) {
    const messageDiv = document.createElement('div');
    messageDiv.style.position = 'fixed';
    messageDiv.style.top = '50%';
    messageDiv.style.left = '50%';
    messageDiv.style.transform = 'translate(-50%, -50%)';
    messageDiv.style.background = 'rgba(0,0,0,0.8)';
    messageDiv.style.color = 'white';
    messageDiv.style.padding = '20px';
    messageDiv.style.borderRadius = '10px';
    messageDiv.style.fontSize = '24px';
    messageDiv.style.zIndex = '2000';
    messageDiv.textContent = message;
    messageDiv.id = 'round-message';
    
    const existing = document.getElementById('round-message');
    if (existing) existing.remove();
    
    document.body.appendChild(messageDiv);
    
    setTimeout(() => {
        messageDiv.remove();
    }, duration);
}

function sendCalibrationDataBinary(point, screenX, screenY) {
    try {
        // Capture frame
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        canvas.width = Math.min(videoElement.videoWidth, 320);
        canvas.height = Math.min(videoElement.videoHeight, 240);
        ctx.drawImage(videoElement, 0, 0, canvas.width, canvas.height);
        
        // Convert to ImageData
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const pixelData = new Uint8Array(imageData.data);
        
        // Create binary message
        const headerSize = 1 + 4 + 4 + 4; // messageType + point + screenX + screenY
        const buffer = new ArrayBuffer(headerSize + pixelData.length);
        const view = new DataView(buffer);
        
        let offset = 0;
        view.setUint8(offset, 1); // messageType = 1 (calibration)
        offset += 1;
        view.setInt32(offset, point, false);
        offset += 4;
        // Convert normalized coordinates to actual screen pixels as integers
        const actualScreenX = Math.round(screenX * window.innerWidth);
        const actualScreenY = Math.round(screenY * window.innerHeight);
        
        // Get the current square being calibrated for logging
        const currentSquare = document.getElementById('calibration-point')?.textContent || 'unknown';
        console.log('[JS] Sending calibration: point=' + point + ', square=' + currentSquare + ', screenX=' + actualScreenX + ', screenY=' + actualScreenY + ', normalized=(' + screenX + ', ' + screenY + ')');
        view.setInt32(offset, actualScreenX, false);
        offset += 4;
        view.setInt32(offset, actualScreenY, false);
        offset += 4;
        
        // Copy pixel data
        new Uint8Array(buffer, offset).set(pixelData);
        
        if (binaryWebSocketManager.send(buffer)) {
            console.log(`Calibration point ${point} sent via binary WebSocket (${buffer.byteLength} bytes)`);
        } else {
            console.error('Failed to send calibration data - WebSocket not connected');
        }
        
    } catch (error) {
        console.error('Failed to send calibration data:', error);
    }
}

function finishCalibration() {
    calibrationActive = false;
    
    const overlay = document.getElementById('calibration-overlay');
    const point = document.getElementById('calibration-point');
    const roundMessage = document.getElementById('round-message');
    if (overlay) overlay.remove();
    if (point) point.remove();
    if (roundMessage) roundMessage.remove();
    
    updateCalibrationProgress(128, 128, 'Calibration Complete!');
    setTimeout(hideCalibrationProgress, 3000);
    
    if (isConnected && stompClient) {
        stompClient.send("/app/eye-tracking/calibration-complete", {}, JSON.stringify({
            timestamp: Date.now()
        }));
    }
    
    console.log('64-square calibration completed, starting video frame streaming...');
    startVideoFrameStreaming();
    updateEyeTrackingUI();
}

function startVideoFrameStreaming() {
    if (!isBinaryConnected || !videoElement || !webcamActive) {
        console.log('Cannot start video streaming - missing requirements');
        return;
    }
    
    console.log('Starting adaptive video frame streaming...');
    
    let currentFrameRate = 60; // Start at 60 FPS for better eye tracking
    let frameInterval = 1000 / currentFrameRate;
    
    function adaptiveFrameStreaming() {
        if (!isBinaryConnected || !videoElement || !webcamActive) {
            return;
        }
        
        sendVideoFrame();
        
        // Adaptive frame rate based on performance
        const now = performance.now();
        if (window.lastFrameTime) {
            const actualInterval = now - window.lastFrameTime;
            if (actualInterval > frameInterval * 1.5) {
                // System is struggling, reduce frame rate
                currentFrameRate = Math.max(30, currentFrameRate - 5);
                frameInterval = 1000 / currentFrameRate;
                console.log('Reduced frame rate to', currentFrameRate, 'FPS');
            } else if (actualInterval < frameInterval * 0.8 && currentFrameRate < 60) {
                // System can handle more, increase frame rate
                currentFrameRate = Math.min(60, currentFrameRate + 2);
                frameInterval = 1000 / currentFrameRate;
                console.log('Increased frame rate to', currentFrameRate, 'FPS');
            }
        }
        window.lastFrameTime = now;
        
        // Schedule next frame
        setTimeout(adaptiveFrameStreaming, frameInterval);
    }
    
    // Start adaptive streaming
    adaptiveFrameStreaming();
}

function stopVideoFrameStreaming() {
    if (window.videoStreamInterval) {
        clearInterval(window.videoStreamInterval);
        window.videoStreamInterval = null;
        console.log('Video frame streaming stopped');
    }
}

function sendVideoFrame() {
    if (!isBinaryConnected || !videoElement) {
        return;
    }
    
    try {
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        // Increase frame size for better face detection
        canvas.width = Math.min(videoElement.videoWidth, 640);
        canvas.height = Math.min(videoElement.videoHeight, 480);
        ctx.drawImage(videoElement, 0, 0);
        
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const pixelData = new Uint8Array(imageData.data);
        
        // Get current board position for accurate eye-tracking
        const boardRect = document.getElementById('chess-board').getBoundingClientRect();
        
        // Create binary message for video frame with board position
        const headerSize = 1 + 8 + 4 + 4 + 4 + 4 + 4 + 4; // messageType + timestamp + width + height + boardLeft + boardTop + boardWidth + boardHeight
        const buffer = new ArrayBuffer(headerSize + pixelData.length);
        const view = new DataView(buffer);
        
        let offset = 0;
        view.setUint8(offset, 2); // messageType = 2 (video frame)
        offset += 1;
        view.setFloat64(offset, Date.now(), false);
        offset += 8;
        view.setInt32(offset, canvas.width, false);
        offset += 4;
        view.setInt32(offset, canvas.height, false);
        offset += 4;
        // Add current board position
        view.setInt32(offset, Math.round(boardRect.left), false);
        offset += 4;
        view.setInt32(offset, Math.round(boardRect.top), false);
        offset += 4;
        view.setInt32(offset, Math.round(boardRect.width), false);
        offset += 4;
        view.setInt32(offset, Math.round(boardRect.height), false);
        offset += 4;
        
        // Copy pixel data
        new Uint8Array(buffer, offset).set(pixelData);
        
        if (!binaryWebSocketManager.send(buffer)) {
            console.warn('Failed to send video frame - WebSocket not connected');
        }
        
    } catch (error) {
        console.error('Failed to send video frame:', error);
    }
}

function updateEyeTrackingUI() {
    const webcamStatus = document.getElementById('webcam-status');
    const webcamToggle = document.getElementById('webcam-toggle');
    const calibrationBtn = document.getElementById('calibration-btn');
    
    if (webcamStatus) {
        webcamStatus.textContent = webcamActive ? 'Webcam: ON' : 'Webcam: OFF';
        webcamStatus.style.color = webcamActive ? '#28a745' : '#dc3545';
    }
    
    if (webcamToggle) {
        webcamToggle.disabled = false;
        webcamToggle.textContent = webcamActive ? 'Disable Eye-Tracking' : 'Enable Eye-Tracking';
    }
    
    if (calibrationBtn) {
        calibrationBtn.disabled = !webcamActive || !isBinaryConnected;
    }
}

function generateSessionId() {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

// Gaze highlighting functions
function highlightGazeSquare(square, type, duration) {
    // Handle backward compatibility
    if (type && type !== 'redDot' && type !== 'blue') {
        // If type is a color name, treat as old format
        type = 'redDot';
    }
    if (!square || square.length < 2) return;
    
    // Convert chess notation to board coordinates
    const col = square.charCodeAt(0) - 'a'.charCodeAt(0);
    const row = 8 - parseInt(square.charAt(1));
    
    if (row < 0 || row > 7 || col < 0 || col > 7) return;
    
    const boardElement = document.getElementById('chess-board');
    if (!boardElement || !boardElement.children[row] || !boardElement.children[row].children[col]) {
        return;
    }
    
    const squareElement = boardElement.children[row].children[col];
    
    if (type === 'redDot') {
        // Remove any existing red dots
        document.querySelectorAll('.gaze-red-dot').forEach(el => el.remove());
        
        // Create red dot like calibration
        const redDot = document.createElement('div');
        redDot.className = 'gaze-red-dot';
        redDot.style.position = 'absolute';
        redDot.style.width = '20px';
        redDot.style.height = '20px';
        redDot.style.backgroundColor = '#ff0000';
        redDot.style.borderRadius = '50%';
        redDot.style.zIndex = '1000';
        redDot.style.pointerEvents = 'none';
        redDot.style.boxShadow = '0 0 10px rgba(255,0,0,0.8)';
        
        // Position at center of square
        const updateDotPosition = () => {
            const rect = squareElement.getBoundingClientRect();
            redDot.style.left = (rect.left + rect.width/2 - 10) + 'px';
            redDot.style.top = (rect.top + rect.height/2 - 10) + 'px';
        };
        
        updateDotPosition();
        document.body.appendChild(redDot);
        
        // Update position on scroll/resize
        const updateHandler = () => updateDotPosition();
        window.addEventListener('scroll', updateHandler);
        window.addEventListener('resize', updateHandler);
        
        console.log(`Showing red dot on square ${square} for ${duration}ms`);
        
        // Remove red dot after duration
        setTimeout(() => {
            window.removeEventListener('scroll', updateHandler);
            window.removeEventListener('resize', updateHandler);
            redDot.remove();
        }, duration || 3000);
    }
}

function updatePredictionDisplay(data) {
    const predictionStatus = document.getElementById('prediction-status');
    if (predictionStatus && data.predictedMove) {
        predictionStatus.textContent = `Prediction: ${data.predictedMove} (${Math.round(data.confidence * 100)}%)`;
        predictionStatus.style.color = data.confidence > 0.7 ? '#28a745' : '#ffc107';
    }
}

// Training functions
function trainAI() {
    if (isConnected && stompClient) {
        stompClient.send("/app/train", {}, JSON.stringify({}));
    }
}

function stopTraining() {
    if (isConnected && stompClient) {
        stompClient.send("/app/stop-training", {}, JSON.stringify({}));
    }
}

// Make functions globally accessible
window.newGame = newGame;
window.trainAI = trainAI;
window.stopTraining = stopTraining;
window.toggleWebcam = toggleWebcam;
window.acceptConsent = acceptConsent;
window.declineConsent = declineConsent;
window.startCalibration = startCalibration;

// Initialize when page loads
window.onload = function() {
    console.log('Page loaded, initializing...');
    connect();
    
    setTimeout(() => {
        updateEyeTrackingUI();
    }, 1000);
};