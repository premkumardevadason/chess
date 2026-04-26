import { useEffect } from 'react';
import { useWebSocket } from './useWebSocket';
import useChessStore from '@/stores/chessStore';

export const useChessWebSocket = () => {
  const { actions } = useChessStore();
  const { makeMove: sendMove, newGame: sendNewGame, isConnected } = useWebSocket();

  // Override actions to use WebSocket
  useEffect(() => {
    // Store the original actions
    const originalMakeMove = actions.makeMove;
    const originalResetGame = actions.resetGame;
    
    // Override with WebSocket versions
    actions.makeMove = (from: [number, number], to: [number, number]) => {
      // Clear selection immediately when move is made
      actions.selectSquare(undefined);
      sendMove(from, to);
    };

    actions.resetGame = () => {
      sendNewGame();
    };

    // Cleanup: restore original actions
    return () => {
      actions.makeMove = originalMakeMove;
      actions.resetGame = originalResetGame;
    };
  }, [actions, sendMove, sendNewGame]);

  return {
    isConnected,
    makeMove: actions.makeMove,
    resetGame: actions.resetGame
  };
};
