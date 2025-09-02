import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import useChessStore from '@/stores/chessStore';
import type { WebSocketMessage, Piece } from '@/types/chess';

interface UseWebSocketOptions {
  url?: string;
  reconnectDelay?: number;
  maxReconnectAttempts?: number;
}

export const useWebSocket = (options: UseWebSocketOptions = {}) => {
  const {
    url = 'http://localhost:8081/ws', // Changed to HTTP for SockJS
    reconnectDelay = 1000,
    maxReconnectAttempts = 5
  } = options;

  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reconnectAttempts, setReconnectAttempts] = useState(0);
  
  const clientRef = useRef<Client | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const isConnectingRef = useRef<boolean>(false);
  
  const { actions } = useChessStore();

  const connect = useCallback(() => {
    if (clientRef.current?.connected || clientRef.current?.active || isConnectingRef.current) {
      return;
    }
    
    isConnectingRef.current = true;

    const client = new Client({
      webSocketFactory: () => new SockJS(url), // Use SockJS instead of direct WebSocket
      reconnectDelay: reconnectDelay,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        console.log('STOMP Debug:', str);
      },
    });

    client.onConnect = (frame) => {
      console.log('WebSocket connected:', frame);
      setIsConnected(true);
      setError(null);
      setReconnectAttempts(0);
      isConnectingRef.current = false;
      actions.setConnectionStatus(true);

      // Subscribe to game state updates
      client.subscribe('/topic/gameState', (message) => {
        try {
          const gameState = JSON.parse(message.body);
          handleGameStateUpdate(gameState);
        } catch (err) {
          console.error('Error parsing game state:', err);
        }
      });

      // Request initial board state
      client.publish({
        destination: '/app/board',
        body: JSON.stringify({})
      });

      // Subscribe to AI training updates
      client.subscribe('/topic/training-updates', (message) => {
        try {
          const data: WebSocketMessage = JSON.parse(message.body);
          handleTrainingUpdate(data);
        } catch (err) {
          console.error('Error parsing training update:', err);
        }
      });

      // Subscribe to MCP status updates
      client.subscribe('/topic/mcp-updates', (message) => {
        try {
          const data: WebSocketMessage = JSON.parse(message.body);
          handleMCPUpdate(data);
        } catch (err) {
          console.error('Error parsing MCP update:', err);
        }
      });
    };

    client.onStompError = (frame) => {
      console.error('STOMP error:', frame);
      setError(`STOMP Error: ${frame.headers.message}`);
      actions.setConnectionStatus(false, frame.headers.message);
    };

    client.onWebSocketError = (error) => {
      console.error('WebSocket error:', error);
      setError(`WebSocket Error: ${error.message}`);
      actions.setConnectionStatus(false, error.message);
    };

    client.onWebSocketClose = (event) => {
      console.log('WebSocket closed:', event);
      setIsConnected(false);
      isConnectingRef.current = false;
      actions.setConnectionStatus(false, 'Connection closed');
      
      // Attempt to reconnect
      if (reconnectAttempts < maxReconnectAttempts) {
        const delay = reconnectDelay * Math.pow(2, reconnectAttempts);
        console.log(`Attempting to reconnect in ${delay}ms (attempt ${reconnectAttempts + 1}/${maxReconnectAttempts})`);
        
        reconnectTimeoutRef.current = setTimeout(() => {
          setReconnectAttempts(prev => prev + 1);
          connect();
        }, delay);
      } else {
        setError('Max reconnection attempts reached');
      }
    };

    clientRef.current = client;
    client.activate();
  }, [url, reconnectDelay, maxReconnectAttempts, reconnectAttempts, actions]);

  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    
    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }
    
    isConnectingRef.current = false;
    setIsConnected(false);
    setError(null);
    setReconnectAttempts(0);
    actions.setConnectionStatus(false);
  }, [actions]);

  const sendMessage = useCallback((destination: string, message: any) => {
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(message)
      });
    } else {
      console.warn('WebSocket not connected. Cannot send message:', message);
    }
  }, []);

  // Convert backend board format to frontend format
  const convertBackendBoard = (backendBoard: string[][]): (Piece | null)[][] => {
    const pieceMap: Record<string, { type: string; color: string }> = {
      '♔': { type: 'king', color: 'white' },
      '♕': { type: 'queen', color: 'white' },
      '♖': { type: 'rook', color: 'white' },
      '♗': { type: 'bishop', color: 'white' },
      '♘': { type: 'knight', color: 'white' },
      '♙': { type: 'pawn', color: 'white' },
      '♚': { type: 'king', color: 'black' },
      '♛': { type: 'queen', color: 'black' },
      '♜': { type: 'rook', color: 'black' },
      '♝': { type: 'bishop', color: 'black' },
      '♞': { type: 'knight', color: 'black' },
      '♟': { type: 'pawn', color: 'black' }
    };

    return backendBoard.map(row => 
      row.map(cell => {
        if (!cell || cell.trim() === '') return null;
        const pieceInfo = pieceMap[cell];
        if (!pieceInfo) return null;
        return {
          type: pieceInfo.type as any,
          color: pieceInfo.color as any,
          hasMoved: false // We don't track this from backend yet
        };
      })
    );
  };

  // Game state update handler
  const handleGameStateUpdate = (gameState: any) => {
    console.log('Received game state update:', gameState);
    
    // Update the chess store with the new game state
    if (gameState.board) {
      const convertedBoard = convertBackendBoard(gameState.board);
      
      actions.updateGameState({
        board: convertedBoard,
        currentPlayer: gameState.whiteTurn ? 'white' : 'black',
        gameStatus: gameState.gameOver ? 'checkmate' : 'active',
        availableMoves: [],
        checkSquares: gameState.kingInCheck ? [gameState.kingInCheck] : [],
        aiMove: gameState.aiLastMove ? {
          from: [gameState.aiLastMove[0], gameState.aiLastMove[1]],
          to: [gameState.aiLastMove[2], gameState.aiLastMove[3]],
          aiName: gameState.lastMoveAI || 'AI'
        } : undefined
      });
    }
  };

  const handleTrainingUpdate = (data: WebSocketMessage) => {
    switch (data.type) {
      case 'TRAINING_STARTED':
        actions.startTraining(data.payload.aiSystems);
        break;
      case 'TRAINING_STOPPED':
        actions.stopTraining();
        break;
      case 'TRAINING_PROGRESS':
        actions.updateTrainingStatus(data.payload);
        break;
      case 'AI_STATUS_UPDATE':
        actions.updateAISystem(data.payload.name, data.payload.updates);
        break;
      default:
        console.log('Unknown training update type:', data.type);
    }
  };

  const handleMCPUpdate = (data: WebSocketMessage) => {
    switch (data.type) {
      case 'MCP_STATUS':
        actions.updateMCPStatus(data.payload);
        break;
      case 'MCP_CONNECTION':
        actions.updateMCPStatus({ connected: data.payload.connected });
        break;
      default:
        console.log('Unknown MCP update type:', data.type);
    }
  };

  // Chess-specific message sending
  const makeMove = useCallback((from: [number, number], to: [number, number]) => {
    sendMessage('/app/move', { 
      fromRow: from[0], 
      fromCol: from[1], 
      toRow: to[0], 
      toCol: to[1] 
    });
  }, [sendMessage]);

  const startTraining = useCallback((aiSystems: string[]) => {
    sendMessage('/app/train', { aiSystems });
  }, [sendMessage]);

  const stopTraining = useCallback(() => {
    sendMessage('/app/stop-training', {});
  }, [sendMessage]);

  const selectAI = useCallback((aiName: string) => {
    sendMessage('/app/select-ai', { aiName });
  }, [sendMessage]);

  const newGame = useCallback(() => {
    sendMessage('/app/newgame', {});
  }, [sendMessage]);

  const undoMove = useCallback(() => {
    sendMessage('/app/undo-move', {});
  }, [sendMessage]);

  const redoMove = useCallback(() => {
    sendMessage('/app/redo-move', {});
  }, [sendMessage]);

  useEffect(() => {
    connect();
    
    return () => {
      disconnect();
    };
  }, []); // Empty dependency array to prevent multiple connections

  return {
    isConnected,
    error,
    reconnectAttempts,
    connect,
    disconnect,
    sendMessage,
    makeMove,
    startTraining,
    stopTraining,
    selectAI,
    newGame,
    undoMove,
    redoMove
  };
};
