import React from 'react';
import { ChessSquare } from './ChessSquare';
import useChessStore from '@/stores/chessStore';
import type { PieceColor } from '@/types/chess';

interface ChessBoardProps {
  onPawnPromotion?: (color: PieceColor, position: [number, number]) => void;
}

export const ChessBoard: React.FC<ChessBoardProps> = ({ onPawnPromotion }) => {
  const { gameState, actions } = useChessStore();



  const handleSquareClick = (row: number, col: number) => {
    if (gameState.selectedSquare) {
      // Make a move
      actions.makeMove(gameState.selectedSquare, [row, col]);
    } else {
      // Select a square
      actions.selectSquare([row, col]);
    }
  };

  // Ensure we have a valid board and game state
  if (!gameState.board || gameState.board.length === 0) {
    console.error('ChessBoard: Invalid board state', gameState.board);
    return (
      <div className="chess-board w-96 h-96 mx-auto flex items-center justify-center border-2 border-gray-800">
        <div className="text-center">
          <p className="text-lg font-semibold text-red-600">Chess Board Error</p>
          <p className="text-sm text-gray-600">Board state is invalid</p>
        </div>
      </div>
    );
  }

  // Ensure we have valid arrays for moves and check squares
  const availableMoves = gameState.availableMoves || [];
  const checkSquares = gameState.checkSquares || [];
  


  return (
    <div className="chess-board w-96 h-96 mx-auto">
      {gameState.board.map((row, rowIndex) =>
        row.map((piece, colIndex) => (
          <ChessSquare
            key={`${rowIndex}-${colIndex}`}
            piece={piece}
            position={[rowIndex, colIndex]}
            isSelected={false}
            isHighlighted={false}
            isLastMove={false}
            isAIMove={gameState.aiMove && 
              (gameState.aiMove.to[0] === rowIndex && gameState.aiMove.to[1] === colIndex)}
            isCheck={false}
            onClick={() => handleSquareClick(rowIndex, colIndex)}
          />
        ))
      )}
    </div>
  );
};
