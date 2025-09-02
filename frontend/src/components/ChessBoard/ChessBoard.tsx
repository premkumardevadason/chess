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

  return (
    <div className="chess-board w-96 h-96 mx-auto">
      {gameState.board.map((row, rowIndex) =>
        row.map((piece, colIndex) => (
          <ChessSquare
            key={`${rowIndex}-${colIndex}`}
            piece={piece}
            position={[rowIndex, colIndex]}
            isSelected={gameState.selectedSquare?.[0] === rowIndex && gameState.selectedSquare?.[1] === colIndex}
            isHighlighted={gameState.availableMoves.some(([r, c]) => r === rowIndex && c === colIndex)}
            isLastMove={gameState.lastMove && 
              ((gameState.lastMove.from[0] === rowIndex && gameState.lastMove.from[1] === colIndex) ||
               (gameState.lastMove.to[0] === rowIndex && gameState.lastMove.to[1] === colIndex))}
            isCheck={gameState.checkSquares.some(([r, c]) => r === rowIndex && c === colIndex)}
            onClick={() => handleSquareClick(rowIndex, colIndex)}
          />
        ))
      )}
    </div>
  );
};
