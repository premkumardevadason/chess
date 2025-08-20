let gameState = { board: [], whiteTurn: true, gameOver: false };
let selectedSquare = null;
let lastMoveTime = 0;
const MOVE_COOLDOWN = 100; // Prevent rapid-fire moves
let stompClient = null;
let isConnected = false;

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
        
        // Load initial board state
        loadBoard();
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

// Initialize the game when page loads
window.onload = function() {
    console.log('Page loaded, initializing...');
    connect(); // Start WebSocket connection
    console.log('Page initialized');
};