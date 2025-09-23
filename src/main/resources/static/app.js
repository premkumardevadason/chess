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
            highlightGazeSquare(data.square, data.color, data.duration);
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
    showCalibrationOverlay();
    startCalibrationSequence();
}

function showCalibrationOverlay() {
    const overlay = document.createElement('div');
    overlay.className = 'calibration-overlay';
    overlay.id = 'calibration-overlay';
    document.body.appendChild(overlay);
}

function startCalibrationSequence() {
    const points = [
        { x: 0.1, y: 0.1 }, { x: 0.5, y: 0.1 }, { x: 0.9, y: 0.1 },
        { x: 0.1, y: 0.5 }, { x: 0.5, y: 0.5 }, { x: 0.9, y: 0.5 },
        { x: 0.1, y: 0.9 }, { x: 0.5, y: 0.9 }, { x: 0.9, y: 0.9 }
    ];
    
    let currentPoint = 0;
    
    function showNextPoint() {
        if (currentPoint >= points.length) {
            finishCalibration();
            return;
        }
        
        const point = points[currentPoint];
        const calibrationPoint = document.createElement('div');
        calibrationPoint.className = 'calibration-point';
        calibrationPoint.style.left = (point.x * 100) + '%';
        calibrationPoint.style.top = (point.y * 100) + '%';
        calibrationPoint.id = 'calibration-point';
        
        const existingPoint = document.getElementById('calibration-point');
        if (existingPoint) existingPoint.remove();
        
        document.body.appendChild(calibrationPoint);
        
        // Send calibration data via binary WebSocket
        if (isBinaryConnected && videoElement) {
            sendCalibrationDataBinary(currentPoint, point.x, point.y);
        }
        
        currentPoint++;
        setTimeout(showNextPoint, 2000);
    }
    
    showNextPoint();
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
        const actualScreenX = Math.round(screenX * window.screen.width);
        const actualScreenY = Math.round(screenY * window.screen.height);
        
        console.log('[JS] Sending calibration: point=' + point + ', screenX=' + actualScreenX + ', screenY=' + actualScreenY + ', normalized=(' + screenX + ', ' + screenY + ')');
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
    if (overlay) overlay.remove();
    if (point) point.remove();
    
    if (isConnected && stompClient) {
        stompClient.send("/app/eye-tracking/calibration-complete", {}, JSON.stringify({
            timestamp: Date.now()
        }));
    }
    
    console.log('Calibration completed, starting video frame streaming...');
    startVideoFrameStreaming();
    updateEyeTrackingUI();
}

function startVideoFrameStreaming() {
    if (!isBinaryConnected || !videoElement || !webcamActive) {
        console.log('Cannot start video streaming - missing requirements');
        return;
    }
    
    console.log('Starting adaptive video frame streaming...');
    
    let currentFrameRate = 30; // Start at 30 FPS
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
                currentFrameRate = Math.max(10, currentFrameRate - 2);
                frameInterval = 1000 / currentFrameRate;
                console.log('Reduced frame rate to', currentFrameRate, 'FPS');
            } else if (actualInterval < frameInterval * 0.8 && currentFrameRate < 30) {
                // System can handle more, increase frame rate
                currentFrameRate = Math.min(30, currentFrameRate + 1);
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
        
        // Create binary message for video frame
        const headerSize = 1 + 8 + 4 + 4; // messageType + timestamp + width + height
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
        
        console.log('[JS] Sending video frame: width=' + canvas.width + ', height=' + canvas.height + ', dataSize=' + pixelData.length);
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
function highlightGazeSquare(square, color, duration) {
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
    
    // Remove any existing gaze highlight
    document.querySelectorAll('.gaze-highlight').forEach(el => {
        el.classList.remove('gaze-highlight');
    });
    
    // Add gaze highlight
    squareElement.classList.add('gaze-highlight');
    
    console.log(`Highlighting square ${square} in ${color} for ${duration}ms`);
    
    // Remove highlight after duration
    setTimeout(() => {
        squareElement.classList.remove('gaze-highlight');
    }, duration || 3000);
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