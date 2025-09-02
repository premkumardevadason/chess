import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import useChessStore from '@/stores/chessStore';
import type { WebSocketMessage } from '@/types/chess';

interface UseWebSocketOptions {
  url?: string;
  reconnectDelay?: number;
  maxReconnectAttempts?: number;
}

export const useWebSocket = (options: UseWebSocketOptions = {}) => {
  const {
    url = 'ws://localhost:8081/ws',
    reconnectDelay = 1000,
    maxReconnectAttempts = 5
  } = options;

  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reconnectAttempts, setReconnectAttempts] = useState(0);
  
  const clientRef = useRef<Client | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  
  const { actions } = useChessStore();

  const connect = useCallback(() => {
    if (clientRef.current?.connected) {
      return;
    }

    const client = new Client({
      brokerURL: url,
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
      actions.setConnectionStatus(true);

      // Subscribe to game updates
      client.subscribe('/topic/game-updates', (message) => {
        try {
          const data: WebSocketMessage = JSON.parse(message.body);
          handleGameUpdate(data);
        } catch (err) {
          console.error('Error parsing game update:', err);
        }
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

  // Game update handlers
  const handleGameUpdate = (data: WebSocketMessage) => {
    switch (data.type) {
      case 'BOARD_UPDATE':
        // Update board state
        break;
      case 'MOVE_MADE':
        // Handle move made
        break;
      case 'GAME_STATUS':
        // Update game status
        break;
      case 'AVAILABLE_MOVES':
        // Update available moves
        break;
      default:
        console.log('Unknown game update type:', data.type);
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
    sendMessage('/app/make-move', { from, to });
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
    sendMessage('/app/new-game', {});
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
  }, [connect, disconnect]);

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
