# Chess Game API Documentation

## Base URL
`http://localhost:8081`

## Game State Endpoints

### GET /api/board
Returns the current game state including board position, turn, and game status.

**Response:**
```json
{
  "board": [["♜","♞","♝","♛","♚","♝","♞","♜"], ...],
  "whiteTurn": true,
  "gameOver": false,
  "kingInCheck": [4, 0],
  "threatenedPieces": [[1,2], [3,4]],
  "awaitingPromotion": false,
  "promotionSquare": null
}
```

### POST /api/validate
Validates if a move is legal without executing it.

**Request:**
```json
{
  "fromRow": 1,
  "fromCol": 0,
  "toRow": 3,
  "toCol": 0
}
```

**Response:**
```json
{
  "valid": true
}
```

### POST /api/move
Executes a chess move and returns the updated game state.

**Request:**
```json
{
  "fromRow": 1,
  "fromCol": 0,
  "toRow": 3,
  "toCol": 0
}
```

**Response:** Same as `/api/board`

## Game Control Endpoints

### POST /api/newgame
Resets the game to initial state.

**Response:** Same as `/api/board`

### POST /api/undo
Undoes the last move if possible.

**Response:**
```json
{
  "board": [["♜","♞","♝","♛","♚","♝","♞","♜"], ...],
  "whiteTurn": true,
  "gameOver": false,
  "success": true,
  "kingInCheck": null,
  "threatenedPieces": []
}
```

### POST /api/redo
Redoes a previously undone move.

**Response:** Same as `/api/undo`

## Pawn Promotion Endpoints

### GET /api/promotion-options
Gets available promotion options when a pawn reaches the end.

**Response:**
```json
{
  "awaitingPromotion": true,
  "square": "a8",
  "options": ["♕", "♖", "♗", "♘"]
}
```

### POST /api/promote
Completes pawn promotion with selected piece.

**Request:**
```json
{
  "piece": "♕"
}
```

**Response:** Same as `/api/board`

## AI Training Endpoints

### POST /api/train
Starts AI training with specified number of games.

**Parameters:**
- `games` (optional, default: 10000) - Number of training games

**Response:**
```json
"All AI systems training started: Q-Learning, Deep Learning, CNN Deep Learning, DQN, AlphaZero, LeelaZero, Genetic Algorithm"
```

### GET /api/ai-status
Returns current status of all AI systems.

**Response:**
```
=== GPU ACCELERATION ===
AMD GPU (OpenCL): NOT AVAILABLE

=== AI SYSTEMS ===
Q-Learning: 510707 entries
Deep Learning: Model saved, 53760 iterations
Backend: Backend: CpuBackend (CPU)
CNN Deep Learning: Model saved, 29504 iterations
CNN Backend: CNN Backend: CpuBackend (CPU)
Game Data: 0 positions
DQN: Enabled with experience replay
MCTS: DISABLED
AlphaZero: Episodes: 0, Cache size: 21, Training: No
Negamax: Enabled with alpha-beta pruning
OpenAI: Enabled with GPT-4
LeelaZero: Enabled with opening book
Genetic Algorithm: Enabled with evolution

=== TOTAL: 9/10 AI SYSTEMS ENABLED ===
```

### GET /api/training-progress
Returns detailed training progress for Q-Learning AI.

**Response:**
```json
{
  "isTraining": true,
  "gamesCompleted": 150,
  "qTableSize": 1500,
  "trainingBoard": [["♜","♞","♝","♛","♚","♝","♞","♜"], ...],
  "status": "Training in progress..."
}
```

### POST /api/stop-deep-training
Stops the deep learning training process.

**Response:**
```json
"Deep Learning training stopped"
```

### POST /api/delete-training
Deletes all AI training data files.

**Response:**
```json
"All AI training data deleted successfully"
```

## Testing Endpoints

### POST /api/test-qtable
Adds test entries to Q-table for debugging.

**Parameters:**
- `entries` (optional, default: 100) - Number of test entries

**Response:**
```json
"Added 100 test entries to Q-table. Current size: 1600"
```

### POST /api/test-training
Runs a quick training test with specified games.

**Parameters:**
- `games` (optional, default: 10) - Number of test games

**Response:**
```json
"Test training completed with 10 games. Q-table size: 1610"
```

### POST /api/verify-qtable
Verifies Q-table save/load functionality.

**Response:**
```json
"Q-table verification completed. Current size: 1610"
```

## Error Responses

All endpoints may return error responses in the following format:

**400 Bad Request:**
```json
{
  "error": "Invalid move coordinates"
}
```

**500 Internal Server Error:**
```json
{
  "error": "Training failed: insufficient memory"
}
```

## Chess Piece Unicode Characters

- White: ♔ (King), ♕ (Queen), ♖ (Rook), ♗ (Bishop), ♘ (Knight), ♙ (Pawn)
- Black: ♚ (King), ♛ (Queen), ♜ (Rook), ♝ (Bishop), ♞ (Knight), ♟ (Pawn)

## Opening Book

The AI uses a comprehensive opening book with **34 different chess openings**:
- Classical openings (Italian, Spanish, Four Knights)
- Defense systems (Sicilian, French, Caro-Kann)
- Gambit play (King's, Budapest, Benko)
- Indian defenses (King's Indian, Nimzo-Indian, Grünfeld)
- Modern systems (Pirc, Modern, Catalan)

See `OPENINGS.md` for complete details.

## Board Coordinates

- Rows: 0-7 (0 = top, 7 = bottom)
- Columns: 0-7 (0 = left, 7 = right)
- Standard chess notation: a1 = [7,0], h8 = [0,7]