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

function connectBinaryWebSocket() {
    binaryWebSocket = new WebSocket('ws://localhost:8081/ws-binary');
    binaryWebSocket.binaryType = 'arraybuffer';
    
    binaryWebSocket.onopen = function() {
        console.log('Binary WebSocket connected');
        isBinaryConnected = true;
    };
    
    binaryWebSocket.onclose = function() {
        console.log('Binary WebSocket disconnected');
        isBinaryConnected = false;
        // Reconnect after 5 seconds
        setTimeout(connectBinaryWebSocket, 5000);
    };
    
    binaryWebSocket.onerror = function(error) {
        console.error('Binary WebSocket error:', error);
    };
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
        if (isBinaryConnected && binaryWebSocket && videoElement) {
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
        view.setInt32(offset, point, true);
        offset += 4;
        view.setFloat32(offset, screenX, true);
        offset += 4;
        view.setFloat32(offset, screenY, true);
        offset += 4;
        
        // Copy pixel data
        new Uint8Array(buffer, offset).set(pixelData);
        
        binaryWebSocket.send(buffer);
        console.log(`Calibration point ${point} sent via binary WebSocket (${buffer.byteLength} bytes)`);
        
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
    
    console.log('Calibration completed');
    updateEyeTrackingUI();
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