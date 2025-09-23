let gameState = { board: [], whiteTurn: true, gameOver: false };
let selectedSquare = null;
let lastMoveTime = 0;
const MOVE_COOLDOWN = 100; // Prevent rapid-fire moves
let stompClient = null;
let videoStompClient = null;
let calibrationStompClient = null;
let isConnected = false;
let isVideoConnected = false;
let isCalibrationConnected = false;

function loadBoard() {
    if (isConnected && stompClient) {
        stompClient.send("/app/board", {}, JSON.stringify({}));
    }
}

function renderBoard() {
    const boardElement = document.getElementById('chess-board');
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
            
            // Highlight king if in check
            if (gameState.kingInCheck && gameState.kingInCheck[0] === i && gameState.kingInCheck[1] === j) {
                cellElement.classList.add('king-in-check');
            }
            
            // Highlight threatened high-value pieces (King/Queen)
            if (gameState.threatenedPieces) {
                for (let threat of gameState.threatenedPieces) {
                    if (threat[0] === i && threat[1] === j) {
                        cellElement.classList.add('king-in-check');
                        break;
                    }
                }
            }
            
            cellElement.textContent = gameState.board[i][j] || '';
            cellElement.onclick = () => onSquareClick(i, j);
            
            rowElement.appendChild(cellElement);
        }
        
        boardElement.appendChild(rowElement);
    }
}

async function onSquareClick(row, col) {
    // Input validation
    if (typeof row !== 'number' || typeof col !== 'number' || 
        row < 0 || row > 7 || col < 0 || col > 7) {
        console.warn('Invalid coordinates');
        return;
    }
    
    // Rate limiting
    const now = Date.now();
    if (now - lastMoveTime < MOVE_COOLDOWN) {
        return;
    }
    
    if (!gameState.whiteTurn) return;
    
    const piece = gameState.board[row][col];
    
    if (!selectedSquare) {
        if (piece && isPieceWhite(piece)) {
            selectedSquare = { row, col };
            renderBoard();
        }
    } else {
        // If clicking on another white piece, select it instead
        if (piece && isPieceWhite(piece)) {
            selectedSquare = { row, col };
            renderBoard();
            return;
        }
        
        const valid = await isValidMove(selectedSquare.row, selectedSquare.col, row, col);
        if (valid) {
            lastMoveTime = Date.now();
            makeMove(selectedSquare.row, selectedSquare.col, row, col);
            selectedSquare = null;
        } else {
            blinkSquare(row, col);
            selectedSquare = null; // Clear selection after invalid move
        }
    }
}

function isPieceWhite(piece) {
    return '♔♕♖♗♘♙'.includes(piece);
}

function isValidMove(fromRow, fromCol, toRow, toCol) {
    return new Promise((resolve) => {
        if (isConnected && stompClient) {
            const move = { fromRow, fromCol, toRow, toCol };
            
            // Subscribe to validation response
            const subscription = stompClient.subscribe('/topic/validation', function (message) {
                const data = JSON.parse(message.body);
                subscription.unsubscribe();
                resolve(data.valid);
            });
            
            stompClient.send("/app/validate", {}, JSON.stringify(move));
        } else {
            resolve(false);
        }
    });
}

function makeMove(fromRow, fromCol, toRow, toCol) {
    const move = { fromRow, fromCol, toRow, toCol };
    
    if (isConnected && stompClient) {
        stompClient.send("/app/move", {}, JSON.stringify(move));
    }
}

// AI moves are now handled automatically via WebSocket updates

function blinkSquare(row, col) {
    const boardElement = document.getElementById('chess-board');
    const square = boardElement.children[row].children[col];
    
    square.style.backgroundColor = '#ff0000';
    
    setTimeout(() => {
        square.style.backgroundColor = '';
        setTimeout(() => {
            square.style.backgroundColor = '#ff0000';
            setTimeout(() => {
                square.style.backgroundColor = '';
                renderBoard(); // Re-render to clear selection
            }, 200);
        }, 200);
    }, 200);
}

function blinkAIMove(row, col) {
    const boardElement = document.getElementById('chess-board');
    const square = boardElement.children[row].children[col];
    
    if (!square) return;
    
    let blinkCount = 0;
    const maxBlinks = 5;
    
    function doBlink() {
        if (blinkCount >= maxBlinks) {
            square.style.boxShadow = '';
            square.style.transition = '';
            return;
        }
        
        square.style.boxShadow = 'inset 0 0 0 3px #00ff00';
        square.style.transition = 'box-shadow 0.2s';
        
        setTimeout(() => {
            square.style.boxShadow = '';
            blinkCount++;
            setTimeout(doBlink, 200);
        }, 200);
    }
    
    doBlink();
}



function findAIMoveSquare(previousBoard, currentBoard) {
    // Find all differences between boards
    for (let i = 0; i < 8; i++) {
        for (let j = 0; j < 8; j++) {
            const prevPiece = previousBoard[i][j] || '';
            const currPiece = currentBoard[i][j] || '';
            
            if (prevPiece !== currPiece) {
                // If current square has a black piece and it's different from before
                if (currPiece !== '' && '♚♛♜♝♞♟'.includes(currPiece)) {
                    return { row: i, col: j };
                }
            }
        }
    }
    
    return null;
}

function newGame() {
    console.log('New Game button clicked');
    
    if (isConnected && stompClient) {
        stompClient.send("/app/newgame", {}, JSON.stringify({}));
        selectedSquare = null;
    }
}

function updateTurnInfo() {
    const turnInfo = document.getElementById('turn-info');
    
    if (gameState.gameOver && gameState.checkmate && gameState.winner) {
        turnInfo.textContent = `Congratulations! Checkmate! ${gameState.winner} wins!`;
    } else if (gameState.gameOver) {
        turnInfo.textContent = 'Game Over';
    } else {
        turnInfo.textContent = gameState.whiteTurn ? 'Your turn (White)' : 'Computer thinking...';
    }
}

function undoMove() {
    if (isConnected && stompClient) {
        stompClient.send("/app/undo", {}, JSON.stringify({}));
        selectedSquare = null;
    }
}

function redoMove() {
    if (isConnected && stompClient) {
        stompClient.send("/app/redo", {}, JSON.stringify({}));
        selectedSquare = null;
    }
}

// Handle keyboard events
document.addEventListener('keydown', function(event) {
    if (event.ctrlKey && event.key === 'z') {
        event.preventDefault();
        undoMove();
    }
    if (event.ctrlKey && event.key === 'y') {
        event.preventDefault();
        redoMove();
    }
});

function trainAI() {
    console.log('trainAI function called');
    const statusDiv = document.getElementById('training-status');
    statusDiv.textContent = 'Starting training...';
    
    if (isConnected && stompClient) {
        stompClient.send("/app/train", {}, JSON.stringify({}));
    }
}



function stopTraining() {
    console.log('stopTraining function called');
    const statusDiv = document.getElementById('training-status');
    statusDiv.textContent = 'Stopping all AI training...';
    
    if (isConnected && stompClient) {
        stompClient.send("/app/stop-training", {}, JSON.stringify({}));
    }
}

function deleteTraining() {
    console.log('deleteTraining function called');
    const statusDiv = document.getElementById('training-status');
    
    if (confirm('Are you sure you want to delete the training file? This will reset all AI learning.')) {
        statusDiv.textContent = 'Deleting training file...';
        
        if (isConnected && stompClient) {
            stompClient.send("/app/delete-training", {}, JSON.stringify({}));
        }
    }
}

// Test functions removed - use WebSocket training instead





function checkAIStatus() {
    console.log('checkAIStatus function called');
    const statusDiv = document.getElementById('training-status');
    statusDiv.textContent = 'Checking all AI status...';
    
    if (isConnected && stompClient) {
        stompClient.send("/app/ai-status", {}, JSON.stringify({}));
    }
}



// Training progress updates are handled automatically via WebSocket

function renderTrainingBoard(trainingBoard) {
    const boardElement = document.getElementById('chess-board');
    boardElement.innerHTML = '';
    
    for (let i = 0; i < 8; i++) {
        const rowElement = document.createElement('div');
        rowElement.className = 'board-row';
        
        for (let j = 0; j < 8; j++) {
            const cellElement = document.createElement('div');
            cellElement.className = `board-cell ${(i + j) % 2 === 0 ? 'light' : 'dark'}`;
            cellElement.style.border = '2px solid #00ff00'; // Green border for training
            cellElement.textContent = trainingBoard[i][j] || '';
            rowElement.appendChild(cellElement);
        }
        
        boardElement.appendChild(rowElement);
    }
}

// Make functions globally accessible
window.checkAIStatus = checkAIStatus;
window.newGame = newGame;
window.trainAI = trainAI;
window.stopTraining = stopTraining;
window.deleteTraining = deleteTraining;

// Function to display which AI is being used
function updateAIInfo(selectedAI, lastMoveAI) {
    const aiInfoElement = document.getElementById('ai-info');
    
    if (aiInfoElement) {
        let displayText = `Playing against: ${selectedAI || 'None'}`;
        if (lastMoveAI && selectedAI === 'All AIs') {
            displayText += ` (last move: ${lastMoveAI})`;
        }
        aiInfoElement.textContent = displayText;
    }
}

// WebSocket connection functions
function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(() => socket);
    
    // Configure reconnection
    stompClient.reconnectDelay = 5000;
    stompClient.heartbeatIncoming = 4000;
    stompClient.heartbeatOutgoing = 4000;
    
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        isConnected = true;
        
        // Subscribe to game state updates
        stompClient.subscribe('/topic/gameState', function (message) {
            console.log('*** RECEIVED GAME STATE MESSAGE ***');
            const data = JSON.parse(message.body);
            console.log('Game state data:', data);
            console.log('Board data:', data.board);
            
            // Check if AI made a move and trigger blinking animation
            if (data.aiLastMove && data.aiLastMove.length === 4) {
                // Blink the destination square of the AI move
                setTimeout(() => {
                    blinkAIMove(data.aiLastMove[2], data.aiLastMove[3]);
                }, 100); // Small delay to ensure board is rendered first
            }
            
            gameState = data;
            renderBoard();
            updateTurnInfo();
            
            // Update AI information
            updateAIInfo(data.selectedAI, data.lastMoveAI);
        });
        
        // Subscribe to training progress updates
        stompClient.subscribe('/topic/trainingProgress', function (message) {
            const progress = JSON.parse(message.body);
            updateTrainingProgress(progress);
        });
        
        // Subscribe to CNN training progress updates
        stompClient.subscribe('/topic/cnnTrainingProgress', function (message) {
            const progress = JSON.parse(message.body);
            updateCNNTrainingProgress(progress);
        });
        
        // Subscribe to training status updates
        stompClient.subscribe('/topic/training', function (message) {
            const status = JSON.parse(message.body);
            const statusDiv = document.getElementById('training-status');
            statusDiv.textContent = status.message;
        });
        
        // Subscribe to AI status updates
        stompClient.subscribe('/topic/aiStatus', function (message) {
            const status = JSON.parse(message.body);
            const statusDiv = document.getElementById('training-status');
            statusDiv.innerHTML = status.status.replace(/\n/g, '<br>');
            setTimeout(() => statusDiv.textContent = '', 10000);
        });
        
        // Subscribe to training board updates for real-time visualization
        stompClient.subscribe('/topic/trainingBoard', function (message) {
            const boardState = JSON.parse(message.body);
            if (boardState.board) {
                renderTrainingBoard(boardState.board);
            }
        });
        
        // Subscribe to eye-tracking WebSocket messages
        stompClient.subscribe('/topic/squareHighlight', function (message) {
            const data = JSON.parse(message.body);
            if (data.action === 'remove') {
                clearGazeHighlights();
            } else {
                highlightSquare(data.square);
            }
        });
        
        stompClient.subscribe('/topic/pieceIntention', function (message) {
            const data = JSON.parse(message.body);
            console.log('Piece intention:', data);
            // Could display intention analysis in UI
        });
        
        stompClient.subscribe('/topic/movePrediction', function (message) {
            const data = JSON.parse(message.body);
            currentPrediction = data;
            updateEyeTrackingUI();
            console.log('Move prediction:', data);
        });
        
        stompClient.subscribe('/topic/eyeTrackingStatus', function (message) {
            const data = JSON.parse(message.body);
            if (data.webcamEnabled !== undefined) {
                webcamActive = data.webcamEnabled;
                updateEyeTrackingUI();
            }
        });
        
        // Load initial board state
        loadBoard();
        
        // Connect video WebSocket
        connectVideoWebSocket();
        
        // Connect calibration WebSocket
        connectCalibrationWebSocket();
        
    }, function(error) {
        console.log('WebSocket connection failed');
        isConnected = false;
        const statusDiv = document.getElementById('training-status');
        statusDiv.textContent = 'WebSocket connection failed. Please refresh the page.';
    });
}

function updateTrainingProgress(progress) {
    const statusDiv = document.getElementById('training-status');
    
    if (progress.isTraining) {
        statusDiv.textContent = `${progress.status} - Games: ${progress.gamesCompleted}, Q-table: ${progress.qTableSize}`;
        
        // Show training board if available
        if (progress.trainingBoard) {
            renderTrainingBoard(progress.trainingBoard);
        }
    } else {
        statusDiv.textContent = `Training completed! Final Q-table: ${progress.qTableSize} entries`;
        setTimeout(() => statusDiv.textContent = '', 5000);
    }
}

function updateCNNTrainingProgress(progress) {
    const statusDiv = document.getElementById('training-status');
    
    if (progress.isTraining) {
        statusDiv.textContent = `CNN ${progress.status} - Iterations: ${progress.iterations}, Game Data: ${progress.gameDataSize}`;
    } else {
        statusDiv.textContent = `CNN Training completed! Final iterations: ${progress.iterations}`;
        setTimeout(() => statusDiv.textContent = '', 5000);
    }
}

// Eye-Tracking Variables
let eyeTrackingEnabled = false;
let webcamActive = false;
let calibrationActive = false;
let currentPrediction = null;
let gazeHighlightTimeout = null;
let videoStream = null;
let videoElement = null;

// Eye-Tracking Functions
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
    modal.style.display = 'flex';
}

function acceptConsent() {
    const modal = document.getElementById('consent-modal');
    modal.style.display = 'none';
    
    // Send consent to backend
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
    modal.style.display = 'none';
    
    // Send decline to backend
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
        // Request webcam access from browser
        videoStream = await navigator.mediaDevices.getUserMedia({ 
            video: { 
                width: { ideal: 640 }, 
                height: { ideal: 480 },
                facingMode: 'user'
            } 
        });
        
        // Create video element if it doesn't exist
        if (!videoElement) {
            videoElement = document.createElement('video');
            videoElement.id = 'eye-tracking-video';
            videoElement.style.display = 'none'; // Hidden video element
            videoElement.autoplay = true;
            videoElement.muted = true;
            document.body.appendChild(videoElement);
        }
        
        // Set video source to webcam stream
        videoElement.srcObject = videoStream;
        
        // Wait for video to be ready
        await new Promise((resolve) => {
            videoElement.onloadedmetadata = () => {
                videoElement.play();
                resolve();
            };
        });
        
        // Send enable message to backend
        if (isConnected && stompClient) {
            stompClient.send("/app/eye-tracking/enable", {}, JSON.stringify({
                sessionId: generateSessionId(),
                timestamp: Date.now(),
                videoWidth: videoElement.videoWidth,
                videoHeight: videoElement.videoHeight
            }));
        }
        
        webcamActive = true;
        updateEyeTrackingUI();
        console.log('Webcam enabled for eye-tracking:', videoElement.videoWidth + 'x' + videoElement.videoHeight);
        
        // Don't start video capture immediately - wait for user action
        console.log('Webcam ready - video capture will start after calibration or user interaction');
        
    } catch (error) {
        console.error('Failed to access webcam:', error);
        alert('Failed to access webcam. Please ensure you have granted camera permissions and your camera is not being used by another application.');
        webcamActive = false;
        updateEyeTrackingUI();
    }
}

function disableWebcam() {
    // Stop video stream
    if (videoStream) {
        videoStream.getTracks().forEach(track => track.stop());
        videoStream = null;
    }
    
    // Remove video element
    if (videoElement) {
        videoElement.remove();
        videoElement = null;
    }
    
    // Send disable message to backend
    if (isConnected && stompClient) {
        stompClient.send("/app/eye-tracking/disable", {}, JSON.stringify({
            sessionId: generateSessionId(),
            timestamp: Date.now()
        }));
    }
    
    webcamActive = false;
    currentPrediction = null;
    updateEyeTrackingUI();
    clearGazeHighlights();
    console.log('Webcam disabled');
}

function startCalibration() {
    if (!eyeTrackingEnabled || !webcamActive) return;
    
    // Ensure calibration WebSocket is connected
    if (!isCalibrationConnected || !calibrationStompClient || !calibrationStompClient.connected) {
        console.log('Calibration WebSocket not connected, connecting...');
        connectCalibrationWebSocket();
        // Wait a moment for connection to establish
        setTimeout(() => {
            if (isCalibrationConnected) {
                calibrationActive = true;
                showCalibrationOverlay();
                startCalibrationSequence();
            } else {
                alert('Failed to connect calibration service. Please try again.');
            }
        }, 2000);
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
        { x: '10%', y: '10%' },   // Top-left
        { x: '50%', y: '10%' },   // Top-center
        { x: '90%', y: '10%' },   // Top-right
        { x: '10%', y: '50%' },   // Middle-left
        { x: '50%', y: '50%' },   // Center
        { x: '90%', y: '50%' },   // Middle-right
        { x: '10%', y: '90%' },   // Bottom-left
        { x: '50%', y: '90%' },   // Bottom-center
        { x: '90%', y: '90%' }    // Bottom-right
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
        calibrationPoint.style.left = point.x;
        calibrationPoint.style.top = point.y;
        calibrationPoint.id = 'calibration-point';
        
        // Remove previous point
        const existingPoint = document.getElementById('calibration-point');
        if (existingPoint) {
            existingPoint.remove();
        }
        
        document.body.appendChild(calibrationPoint);
        
        // Capture actual gaze data during calibration
        if (isConnected && stompClient && videoElement) {
            // Capture current video frame for gaze analysis
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            canvas.width = videoElement.videoWidth;
            canvas.height = videoElement.videoHeight;
            ctx.drawImage(videoElement, 0, 0);
            // Compress image more aggressively to reduce data size
            const frameData = canvas.toDataURL('image/jpeg', 0.5);
            
            // Send calibration point via dedicated calibration WebSocket
            if (isCalibrationConnected && calibrationStompClient && calibrationStompClient.connected) {
                try {
                    calibrationStompClient.send("/app/eye-tracking/calibration", {}, JSON.stringify({
                        point: currentPoint,
                        screenX: point.x,
                        screenY: point.y,
                        frameData: frameData,
                        timestamp: Date.now()
                    }));
                    console.log(`Calibration point ${currentPoint} sent successfully`);
                } catch (error) {
                    console.error('Failed to send calibration point:', error);
                    // Attempt to reconnect
                    isCalibrationConnected = false;
                    connectCalibrationWebSocket();
                }
            } else {
                console.warn('Calibration WebSocket not connected, attempting to reconnect...');
                isCalibrationConnected = false;
                connectCalibrationWebSocket();
            }
        }
        
        currentPoint++;
        setTimeout(showNextPoint, 2000); // 2 seconds per point
    }
    
    showNextPoint();
}

function finishCalibration() {
    calibrationActive = false;
    
    // Remove calibration overlay and point
    const overlay = document.getElementById('calibration-overlay');
    const point = document.getElementById('calibration-point');
    if (overlay) overlay.remove();
    if (point) point.remove();
    
    // Send calibration complete to backend via calibration WebSocket
    if (isCalibrationConnected && calibrationStompClient && calibrationStompClient.connected) {
        calibrationStompClient.send("/app/eye-tracking/calibration-complete", {}, JSON.stringify({
            timestamp: Date.now()
        }));
    }
    
    console.log('Calibration completed, connecting to video WebSocket...');
    updateEyeTrackingUI();
    
    // Connect to video WebSocket and start frame capture
    // The video WebSocket connection will automatically start frame capture when connected
    connectVideoWebSocket();
}

function showPrivacySettings() {
    alert('Privacy Settings:\n\n' +
          '• Gaze data is encrypted and stored securely\n' +
          '• Data is automatically deleted after 7 days\n' +
          '• No personal information is collected\n' +
          '• You can disable eye-tracking at any time\n' +
          '• Data is used only for move prediction');
}

function updateEyeTrackingUI() {
    const webcamStatus = document.getElementById('webcam-status');
    const predictionStatus = document.getElementById('prediction-status');
    const webcamToggle = document.getElementById('webcam-toggle');
    const calibrationBtn = document.getElementById('calibration-btn');
    
    if (webcamStatus) {
        webcamStatus.textContent = webcamActive ? 'Webcam: ON' : 'Webcam: OFF';
        webcamStatus.style.color = webcamActive ? '#28a745' : '#dc3545';
    }
    
    if (predictionStatus) {
        if (currentPrediction) {
            predictionStatus.textContent = `Prediction: ${currentPrediction.move} (${Math.round(currentPrediction.confidence * 100)}%)`;
            predictionStatus.style.color = '#007bff';
        } else {
            predictionStatus.textContent = 'Prediction: None';
            predictionStatus.style.color = '#6c757d';
        }
    }
    
    if (webcamToggle) {
        webcamToggle.disabled = false; // Enable by default when services are available
        webcamToggle.textContent = webcamActive ? 'Disable Eye-Tracking' : 'Enable Eye-Tracking';
    }
    
    if (calibrationBtn) {
        calibrationBtn.disabled = !webcamActive; // Only require webcam to be active
    }
}

// Manual function to start video capture (for users who skip calibration)
function startEyeTracking() {
    if (webcamActive && !calibrationActive) {
        // Connect to video WebSocket first, then start frame capture
        connectVideoWebSocket();
        console.log('Connecting to video WebSocket for manual eye-tracking start...');
    }
}

// Connect video WebSocket
function connectVideoWebSocket() {
    const videoSocket = new SockJS('/ws-video');
    videoStompClient = Stomp.over(() => videoSocket);
    
    // Configure STOMP client for better reliability
    videoStompClient.configure({
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: function (str) {
            console.log('Video STOMP: ' + str);
        }
    });
    
    videoStompClient.connect({}, function (frame) {
        console.log('Video WebSocket connected');
        isVideoConnected = true;
        // Start video frame capture only after connection is established
        if (webcamActive && !calibrationActive) {
            startVideoFrameCapture();
        }
    }, function(error) {
        console.log('Video WebSocket connection failed:', error);
        isVideoConnected = false;
        // Attempt to reconnect after 5 seconds
        setTimeout(connectVideoWebSocket, 5000);
    });
}

// Connect calibration WebSocket
function connectCalibrationWebSocket() {
    const calibrationSocket = new SockJS('/ws-calibration');
    calibrationStompClient = Stomp.over(() => calibrationSocket);
    
    // Configure STOMP client for better reliability
    calibrationStompClient.configure({
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: function (str) {
            console.log('STOMP: ' + str);
        }
    });
    
    calibrationStompClient.connect({}, function (frame) {
        console.log('Calibration WebSocket connected');
        isCalibrationConnected = true;
    }, function(error) {
        console.log('Calibration WebSocket connection failed:', error);
        isCalibrationConnected = false;
        // Attempt to reconnect after 5 seconds
        setTimeout(connectCalibrationWebSocket, 5000);
    });
}

function highlightSquare(square) {
    // Remove existing highlights
    clearGazeHighlights();
    
    // Find the square element
    const boardElement = document.getElementById('chess-board');
    if (!boardElement) return;
    
    // Convert square notation (e.g., "e4") to row/col
    const { row, col } = squareNotationToCoords(square);
    if (row === -1 || col === -1) return;
    
    const squareElement = boardElement.children[row]?.children[col];
    if (squareElement) {
        squareElement.classList.add('gaze-highlight');
        
        // Auto-remove highlight after 3 seconds
        gazeHighlightTimeout = setTimeout(() => {
            squareElement.classList.remove('gaze-highlight');
        }, 3000);
    }
}

function clearGazeHighlights() {
    if (gazeHighlightTimeout) {
        clearTimeout(gazeHighlightTimeout);
        gazeHighlightTimeout = null;
    }
    
    const boardElement = document.getElementById('chess-board');
    if (!boardElement) return;
    
    for (let row = 0; row < 8; row++) {
        for (let col = 0; col < 8; col++) {
            const squareElement = boardElement.children[row]?.children[col];
            if (squareElement) {
                squareElement.classList.remove('gaze-highlight');
            }
        }
    }
}

function squareNotationToCoords(square) {
    if (!square || square.length !== 2) return { row: -1, col: -1 };
    
    const file = square.charAt(0).toLowerCase();
    const rank = parseInt(square.charAt(1));
    
    if (file < 'a' || file > 'h' || rank < 1 || rank > 8) {
        return { row: -1, col: -1 };
    }
    
    const col = file.charCodeAt(0) - 'a'.charCodeAt(0);
    const row = 8 - rank;
    
    return { row, col };
}

function generateSessionId() {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

// Video frame capture for eye-tracking analysis
function startVideoFrameCapture() {
    if (!webcamActive || !videoElement) {
        console.log('Cannot start frame capture: webcamActive=' + webcamActive + ', videoElement=' + !!videoElement);
        return;
    }
    
    console.log('Starting video frame capture...');
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    let frameCount = 0;
    let sending = false; // Prevent frame queue buildup
    
    function captureFrame() {
        if (!webcamActive || !videoElement) {
            console.log('Stopping frame capture: webcamActive=' + webcamActive + ', videoElement=' + !!videoElement);
            return;
        }
        
        // Check video WebSocket connection
        if (!isVideoConnected || !videoStompClient || !videoStompClient.connected) {
            console.log('Video WebSocket disconnected, stopping frame capture');
            return;
        }
        
        // Set canvas size to match video
        canvas.width = videoElement.videoWidth || 640;
        canvas.height = videoElement.videoHeight || 480;
        
        // Draw current video frame to canvas
        ctx.drawImage(videoElement, 0, 0);
        
        // Convert to base64 image data
        const imageData = canvas.toDataURL('image/jpeg', 0.8);
        
        // Send frame via dedicated video WebSocket
        if (!sending && isVideoConnected && videoStompClient && videoStompClient.connected) {
            sending = true;
            try {
                videoStompClient.send("/app/eye-tracking/frame", {}, JSON.stringify({
                    sessionId: generateSessionId(),
                    imageData: imageData,
                    timestamp: Date.now(),
                    width: canvas.width,
                    height: canvas.height
                }));
                
                frameCount++;
                if (frameCount % 30 === 0) { // Log every second
                    console.log('Sent frame #' + frameCount + ' (' + canvas.width + 'x' + canvas.height + ') size: ' + Math.round(imageData.length/1024) + 'KB');
                }
                
                // Reset sending flag after a short delay
                setTimeout(() => { sending = false; }, 10);
                
            } catch (error) {
                console.error('Failed to send frame:', error);
                sending = false;
                // Attempt to reconnect video WebSocket
                isVideoConnected = false;
                connectVideoWebSocket();
                return;
            }
        } else if (!isVideoConnected || !videoStompClient || !videoStompClient.connected) {
            console.warn('Video WebSocket not connected, attempting to reconnect...');
            isVideoConnected = false;
            connectVideoWebSocket();
        }
        
        // Capture next frame (30 FPS for eye-tracking)
        setTimeout(captureFrame, 33);
    }
    
    // Start capturing frames
    captureFrame();
}

// Override newGame to disable webcam on reset
const originalNewGame = newGame;
function newGame() {
    if (webcamActive) {
        disableWebcam();
    }
    originalNewGame();
}

// Initialize the game when page loads
window.onload = function() {
    console.log('Page loaded, initializing...');
    connect(); // Start WebSocket connection
    
    // Enable eye-tracking buttons since services are configured
    setTimeout(() => {
        const webcamToggle = document.getElementById('webcam-toggle');
        const calibrationBtn = document.getElementById('calibration-btn');
        
        if (webcamToggle) {
            webcamToggle.disabled = false;
        }
        if (calibrationBtn) {
            calibrationBtn.disabled = !webcamActive;
        }
        
        updateEyeTrackingUI();
    }, 1000); // Small delay to ensure DOM is ready
    
    console.log('Page initialized');
};