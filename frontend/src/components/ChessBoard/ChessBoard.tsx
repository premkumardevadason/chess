import React, { useState, useEffect } from 'react';
import { ChessSquare } from './ChessSquare';
import useChessStore from '@/stores/chessStore';
import type { PieceColor } from '@/types/chess';

interface ChessBoardProps {
  onPawnPromotion?: (color: PieceColor, position: [number, number]) => void;
}

export const ChessBoard: React.FC<ChessBoardProps> = ({ onPawnPromotion }) => {
  const { backendGameState, uiState, actions, isConnected } = useChessStore();
  // Remove local invalid move state - now handled by game state
  const [aiMoveSquare, setAiMoveSquare] = useState<[number, number] | null>(null);



  // Handle AI move animation
  useEffect(() => {
    if (backendGameState?.aiMove) {
      setAiMoveSquare(backendGameState.aiMove.to);
      // Clear AI move animation after 2 seconds
      const timer = setTimeout(() => {
        setAiMoveSquare(null);
      }, 2000);
      return () => clearTimeout(timer);
    }
  }, [backendGameState?.aiMove]);

  // Clear invalid move animation after it completes
  useEffect(() => {
    if (backendGameState?.invalidMove) {
      const timer = setTimeout(() => {
        actions.updateBackendGameState({ invalidMove: undefined });
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [backendGameState?.invalidMove, actions]);

  const handleSquareClick = (row: number, col: number) => {
    if (uiState.selectedSquare) {
      // Make a move - let the backend handle validation
      actions.makeMove(uiState.selectedSquare, [row, col]);
      actions.selectSquare(undefined);
    } else {
      // Select a square (only if it has a piece of the current player's color)
      const piece = backendGameState?.board[row][col];
      if (piece && piece.color === backendGameState?.currentPlayer) {
        actions.selectSquare([row, col]);
      }
    }
  };



  // Ensure we have a valid board and game state
  if (!backendGameState?.board || backendGameState.board.length === 0) {
    return (
      <div className="chess-board w-96 h-96 mx-auto flex items-center justify-center border-2 border-gray-800">
        <div className="text-center">
          <p className="text-lg font-semibold text-blue-600">Loading Chess Board</p>
          <p className="text-sm text-gray-600">
            {!isConnected ? 'Connecting to server...' : 'Waiting for game state...'}
          </p>
        </div>
      </div>
    );
  }

  // Ensure we have valid arrays for moves and check squares
  const availableMoves = uiState.availableMoves || [];
  const checkSquares = backendGameState?.checkSquares || [];
  
  // Debug logging removed for production
  


  return (
    <div className="chess-board w-96 h-96 mx-auto">
      {backendGameState.board.map((row, rowIndex) =>
        row.map((piece, colIndex) => {
          const isSelected = uiState.selectedSquare && 
            uiState.selectedSquare[0] === rowIndex && 
            uiState.selectedSquare[1] === colIndex;
          
          const isHighlighted = uiState.availableMoves?.some(move => 
            move[0] === rowIndex && move[1] === colIndex
          ) || false;
          
          // TODO: Implement last move highlighting when lastMove is added to BackendGameState
          const isLastMove = false;
          
          const isAIMove = aiMoveSquare && 
            aiMoveSquare[0] === rowIndex && 
            aiMoveSquare[1] === colIndex;
          
          const isCheck = backendGameState?.checkSquares?.some(checkSquare => 
            checkSquare[0] === rowIndex && checkSquare[1] === colIndex
          ) || false;
          
          // Debug logging removed for production
          
          const isInvalidMove = backendGameState?.invalidMove && 
            backendGameState.invalidMove[0] === rowIndex && 
            backendGameState.invalidMove[1] === colIndex;
          
          // Debug logging removed for production

          return (
            <ChessSquare
              key={`${rowIndex}-${colIndex}`}
              piece={piece}
              position={[rowIndex, colIndex]}
              isSelected={isSelected}
              isHighlighted={isHighlighted}
              isLastMove={isLastMove}
              isAIMove={isAIMove || false}
              isCheck={isCheck}
              isInvalidMove={isInvalidMove || false}
              onClick={() => handleSquareClick(rowIndex, colIndex)}
            />
          );
        })
      )}
    </div>
  );
};
