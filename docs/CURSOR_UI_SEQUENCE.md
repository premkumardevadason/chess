# Chess Application React UI - Sequence Diagrams

## Overview

This document contains sequence diagrams for all major interactions and flows implemented in the new React UI for the Chess Application. The diagrams illustrate the communication patterns between components, state management, WebSocket connections, and user interactions.

---

## 1. Application Initialization Flow

```mermaid
sequenceDiagram
    participant User
    participant App as ChessGame Component
    participant Store as Zustand Store
    participant WS as WebSocket Hook
    participant Backend as Java Backend

    User->>App: Navigate to /newage/chess/
    App->>Store: Initialize chess store
    Store->>Store: Load persisted preferences
    App->>WS: Initialize useChessWebSocket()
    WS->>WS: Create STOMP client with SockJS
    WS->>Backend: Connect to /ws endpoint
    Backend-->>WS: Connection established
    WS->>Store: setConnectionStatus(true)
    WS->>Backend: Subscribe to /topic/gameState
    WS->>Backend: Subscribe to /topic/training-updates
    WS->>Backend: Subscribe to /topic/mcp-updates
    WS->>Backend: Request initial board state (/app/board)
    Backend-->>WS: Send current game state
    WS->>Store: updateGameState(board, currentPlayer, etc.)
    Store-->>App: State updated
    App-->>User: Render chess board with current state
```

---

## 2. Chess Move Flow

```mermaid
sequenceDiagram
    participant User
    participant Board as ChessBoard Component
    participant Store as Zustand Store
    participant WS as WebSocket Hook
    participant Backend as Java Backend

    User->>Board: Click on chess square
    Board->>Store: selectSquare(position)
    Store->>Store: Update selectedSquare state
    Store-->>Board: Re-render with selection highlight
    
    User->>Board: Click on destination square
    Board->>Store: makeMove(from, to)
    Store->>WS: Override makeMove with WebSocket version
    WS->>WS: Clear selection immediately
    WS->>Backend: Send move via /app/move
    Note over WS,Backend: {fromRow, fromCol, toRow, toCol}
    
    Backend->>Backend: Validate and execute move
    Backend->>Backend: Update game state
    Backend->>Backend: Check for pawn promotion
    Backend->>Backend: Generate AI move (if applicable)
    
    Backend-->>WS: Send updated game state via /topic/gameState
    WS->>WS: Convert backend board format to frontend format
    WS->>Store: updateGameState(newBoard, currentPlayer, etc.)
    Store-->>Board: Re-render with updated board
    
    alt Pawn Promotion Required
        Backend-->>WS: Include promotion flag in game state
        WS->>Store: Update game state with promotion info
        Store-->>Board: Trigger pawn promotion dialog
        Board->>User: Show promotion piece selection
        User->>Board: Select promotion piece
        Board->>WS: Send promotion choice
        WS->>Backend: Complete promotion move
    end
```

---

## 3. AI Training Management Flow

```mermaid
sequenceDiagram
    participant User
    participant AIPanel as AIPanel Component
    participant Store as Zustand Store
    participant WS as WebSocket Hook
    participant Backend as Java Backend

    User->>AIPanel: Select AI systems for training
    AIPanel->>AIPanel: Update selectedAIs state
    
    User->>AIPanel: Click "Start Training"
    AIPanel->>Store: startTraining(selectedAIs)
    Store->>Store: Update AI systems status to 'training'
    Store->>Store: Set trainingStatus.active = true
    Store->>WS: Override startTraining with WebSocket version
    WS->>Backend: Send training request via /app/train
    Note over WS,Backend: {aiSystems: ["AlphaZero", "MCTS", ...]}
    
    Backend->>Backend: Initialize training for selected AIs
    Backend->>Backend: Start training processes
    
    loop Training Progress Updates
        Backend-->>WS: Send progress via /topic/training-updates
        Note over WS,Backend: {type: "TRAINING_PROGRESS", payload: {aiName, progress, quality}}
        WS->>Store: updateTrainingStatus(progress, quality)
        Store-->>AIPanel: Re-render with updated progress bars
    end
    
    User->>AIPanel: Click "Stop Training"
    AIPanel->>Store: stopTraining()
    Store->>Store: Update all AI systems status to 'idle'
    Store->>Store: Set trainingStatus.active = false
    Store->>WS: Override stopTraining with WebSocket version
    WS->>Backend: Send stop request via /app/stop-training
    Backend->>Backend: Stop training processes
    Backend-->>WS: Confirm training stopped
    WS->>Store: updateTrainingStatus({active: false})
    Store-->>AIPanel: Re-render with stopped status
```

---

## 4. WebSocket Connection Management Flow

```mermaid
sequenceDiagram
    participant WS as WebSocket Hook
    participant Store as Zustand Store
    participant Backend as Java Backend
    participant User

    WS->>WS: Initialize STOMP client
    WS->>Backend: Attempt connection via SockJS
    Backend-->>WS: Connection successful
    WS->>Store: setConnectionStatus(true)
    WS->>Backend: Subscribe to /topic/gameState
    WS->>Backend: Subscribe to /topic/training-updates
    WS->>Backend: Subscribe to /topic/mcp-updates
    
    Note over WS,Backend: Connection established and stable
    
    alt Connection Lost
        Backend-->>WS: Connection closed
        WS->>Store: setConnectionStatus(false, "Connection closed")
        WS->>WS: Start reconnection timer
        loop Reconnection Attempts (max 5)
            WS->>Backend: Attempt reconnection
            alt Reconnection Successful
                Backend-->>WS: Connection restored
                WS->>Store: setConnectionStatus(true)
                WS->>Backend: Re-subscribe to all topics
                break
            else Reconnection Failed
                WS->>WS: Wait with exponential backoff
                WS->>Store: setConnectionStatus(false, "Reconnecting...")
            end
        end
        
        alt Max Attempts Reached
            WS->>Store: setConnectionStatus(false, "Max reconnection attempts reached")
            WS-->>User: Show connection error
        end
    end
```

---

## 5. Game State Synchronization Flow

```mermaid
sequenceDiagram
    participant Backend as Java Backend
    participant WS as WebSocket Hook
    participant Store as Zustand Store
    participant Board as ChessBoard Component
    participant AIPanel as AIPanel Component

    Backend->>Backend: Game state changes (move, AI response, etc.)
    Backend->>WS: Send game state via /topic/gameState
    Note over Backend,WS: {board: [][], whiteTurn: boolean, gameOver: boolean, aiLastMove: [], kingInCheck: []}
    
    WS->>WS: Convert backend board format to frontend format
    Note over WS: Convert Unicode pieces to Piece objects
    
    WS->>Store: updateGameState({
        board: convertedBoard,
        currentPlayer: gameState.whiteTurn ? 'white' : 'black',
        gameStatus: gameState.gameOver ? 'checkmate' : 'active',
        aiMove: gameState.aiLastMove ? {from, to, aiName} : undefined,
        checkSquares: gameState.kingInCheck ? [position] : []
    })
    
    Store-->>Board: Re-render with new board state
    Store-->>AIPanel: Update AI move indicators
    
    alt AI Move Made
        Backend->>WS: Include aiLastMove in game state
        WS->>Store: Set aiMove with move details
        Store-->>Board: Highlight AI move on board
    end
    
    alt King in Check
        Backend->>WS: Include kingInCheck position
        WS->>Store: Set checkSquares array
        Store-->>Board: Highlight check squares
    end
```

---

## 6. Pawn Promotion Flow

```mermaid
sequenceDiagram
    participant User
    participant Board as ChessBoard Component
    participant Game as ChessGame Component
    participant Store as Zustand Store
    participant WS as WebSocket Hook
    participant Backend as Java Backend

    User->>Board: Make move that triggers pawn promotion
    Board->>Store: makeMove(from, to)
    Store->>WS: Send move via WebSocket
    WS->>Backend: Send move via /app/move
    
    Backend->>Backend: Validate move and detect pawn promotion
    Backend->>Backend: Set promotion required flag
    Backend-->>WS: Send game state with promotion flag
    WS->>Store: updateGameState with promotion info
    
    Store-->>Board: Re-render with promotion state
    Board->>Game: onPawnPromotion(color, position)
    Game->>Game: setPawnPromotion({isOpen: true, color, position})
    Game-->>User: Show PawnPromotion modal
    
    User->>Game: Select promotion piece (Queen, Rook, Bishop, Knight)
    Game->>Game: handlePromotionSelect(pieceType)
    Game->>Game: setPawnPromotion({isOpen: false})
    Game->>WS: Send promotion choice
    WS->>Backend: Complete promotion move
    
    Backend->>Backend: Execute promotion
    Backend-->>WS: Send updated game state
    WS->>Store: updateGameState with promoted piece
    Store-->>Board: Re-render with promoted piece
    Game-->>User: Hide promotion modal
```

---

## 7. MCP (Model Context Protocol) Status Flow

```mermaid
sequenceDiagram
    participant Backend as Java Backend
    participant WS as WebSocket Hook
    participant Store as Zustand Store
    participant AIPanel as AIPanel Component

    Backend->>Backend: MCP server status changes
    Backend->>WS: Send MCP update via /topic/mcp-updates
    Note over Backend,WS: {type: "MCP_STATUS", payload: {connected, activeAgents, totalSessions, lastActivity}}
    
    WS->>WS: handleMCPUpdate(data)
    WS->>Store: updateMCPStatus(payload)
    Store->>Store: Update mcpStatus state
    
    Store-->>AIPanel: Re-render MCP status section
    AIPanel-->>User: Display updated MCP connection status
    
    alt MCP Connection Established
        Backend->>WS: Send MCP_CONNECTION message
        WS->>Store: updateMCPStatus({connected: true})
        Store-->>AIPanel: Show "Connected" status
    else MCP Connection Lost
        Backend->>WS: Send MCP_CONNECTION message
        WS->>Store: updateMCPStatus({connected: false})
        Store-->>AIPanel: Show "Disconnected" status
    end
```

---

## 8. Error Handling and Recovery Flow

```mermaid
sequenceDiagram
    participant User
    participant Component as React Component
    participant Store as Zustand Store
    participant WS as WebSocket Hook
    participant Backend as Java Backend

    alt WebSocket Connection Error
        WS->>WS: Connection fails
        WS->>Store: setConnectionStatus(false, errorMessage)
        Store-->>Component: Re-render with error state
        Component-->>User: Show connection error message
        
        WS->>WS: Start reconnection attempts
        loop Reconnection Loop
            WS->>Backend: Attempt reconnection
            alt Success
                Backend-->>WS: Connection restored
                WS->>Store: setConnectionStatus(true)
                Store-->>Component: Re-render with connected state
                Component-->>User: Hide error message
                break
            else Failure
                WS->>Store: setConnectionStatus(false, "Reconnecting...")
                Store-->>Component: Show reconnecting message
            end
        end
    end
    
    alt Invalid Game State
        Backend-->>WS: Send malformed game state
        WS->>WS: JSON parsing fails
        WS->>WS: Log error and continue
        WS->>Store: Keep previous valid state
        Store-->>Component: No state change
    end
    
    alt Component Error
        Component->>Component: Runtime error occurs
        Component->>Component: Error boundary catches error
        Component-->>User: Show error fallback UI
        Component->>Component: Log error for debugging
    end
```

---

## 9. User Preferences Management Flow

```mermaid
sequenceDiagram
    participant User
    participant Component as React Component
    participant Store as Zustand Store
    participant LocalStorage as Browser Storage

    User->>Component: Change preference (theme, sound, etc.)
    Component->>Store: updatePreferences(newPrefs)
    Store->>Store: Update userPreferences state
    Store->>LocalStorage: Persist preferences via Zustand persist middleware
    LocalStorage-->>Store: Preferences saved
    
    Store-->>Component: Re-render with new preferences
    Component-->>User: Apply preference changes (theme switch, etc.)
    
    Note over Store,LocalStorage: Preferences persist across browser sessions
    
    alt App Restart
        User->>Component: Reload application
        Component->>Store: Initialize store
        Store->>LocalStorage: Load persisted preferences
        LocalStorage-->>Store: Return saved preferences
        Store->>Store: Restore userPreferences state
        Store-->>Component: Re-render with restored preferences
        Component-->>User: Apply saved preferences
    end
```

---

## 10. Performance Monitoring Flow

```mermaid
sequenceDiagram
    participant User
    participant Component as React Component
    participant Monitor as PerformanceMonitor
    participant Store as Zustand Store

    Component->>Monitor: usePerformanceMonitoring() hook
    Monitor->>Monitor: Initialize performance tracking
    
    loop During User Interaction
        User->>Component: User action (click, move, etc.)
        Component->>Monitor: Track interaction start
        Component->>Component: Process action
        Component->>Monitor: Track interaction end
        Monitor->>Monitor: Calculate performance metrics
        Monitor->>Store: Update performance data
    end
    
    Monitor->>Monitor: Monitor WebSocket message frequency
    Monitor->>Monitor: Track component re-render counts
    Monitor->>Monitor: Measure memory usage
    
    alt Performance Threshold Exceeded
        Monitor->>Store: Set performance warning
        Store-->>Component: Re-render with warning
        Component-->>User: Show performance indicator
    end
    
    Monitor->>Monitor: Generate performance report
    Monitor-->>User: Display performance metrics
```

---

## Technical Implementation Notes

### WebSocket Communication
- Uses **STOMP over SockJS** for reliable WebSocket communication
- Automatic reconnection with exponential backoff (max 5 attempts)
- Heartbeat mechanism (4-second intervals) for connection health
- Message queuing during disconnection periods

### State Management
- **Zustand** for lightweight, performant state management
- **Persistent storage** for user preferences only
- **Immutable updates** to prevent unnecessary re-renders
- **Action overrides** for WebSocket integration

### Component Architecture
- **Functional components** with React hooks
- **Custom hooks** for WebSocket and performance monitoring
- **Error boundaries** for graceful error handling
- **Responsive design** with Tailwind CSS

### Data Flow
- **Unidirectional data flow** from store to components
- **WebSocket updates** trigger store updates
- **Component re-renders** based on state changes
- **Optimistic updates** for immediate user feedback

---

## Conclusion

These sequence diagrams illustrate the comprehensive implementation of the React UI for the Chess Application. The architecture ensures:

1. **Real-time communication** via WebSocket
2. **Robust error handling** and recovery
3. **Efficient state management** with Zustand
4. **Responsive user interface** with modern React patterns
5. **Performance monitoring** and optimization
6. **Persistent user preferences** across sessions

The implementation provides a solid foundation for the dual UI approach, allowing both the existing Java-based UI and the new React UI to coexist seamlessly.
