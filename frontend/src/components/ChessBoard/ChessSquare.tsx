import React from 'react';
import type { Piece } from '@/types/chess';

interface ChessSquareProps {
  piece: Piece | null;
  position: [number, number];
  isSelected?: boolean;
  isHighlighted?: boolean;
  isLastMove?: boolean;
  isCheck?: boolean;
  onClick: () => void;
}

export const ChessSquare: React.FC<ChessSquareProps> = ({
  piece,
  position,
  isSelected,
  isHighlighted,
  isLastMove,
  isCheck,
  onClick
}) => {
  const [row, col] = position;
  const isLight = (row + col) % 2 === 0;

  const getSquareClasses = () => {
    let classes = 'chess-square ';
    
    if (isLight) {
      classes += 'light ';
    } else {
      classes += 'dark ';
    }
    
    if (isSelected) {
      classes += 'selected ';
    }
    
    if (isHighlighted) {
      classes += 'highlight ';
    }
    
    if (isLastMove) {
      classes += 'last-move ';
    }
    
    if (isCheck) {
      classes += 'check ';
    }
    
    return classes.trim();
  };

  const getPieceSymbol = (piece: Piece) => {
    const pieceMap: Record<string, string> = {
      'K': '♔', 'Q': '♕', 'R': '♖', 'B': '♗', 'N': '♘', 'P': '♙',
      'k': '♚', 'q': '♛', 'r': '♜', 'b': '♝', 'n': '♞', 'p': '♟'
    };
    
    // Convert Piece object to string representation
    const pieceString = piece.color === 'white' 
      ? piece.type.charAt(0).toUpperCase()
      : piece.type.charAt(0).toLowerCase();
    
    return pieceMap[pieceString] || pieceString;
  };

  return (
    <div
      className={getSquareClasses()}
      onClick={onClick}
      role="gridcell"
      aria-label={`${isLight ? 'Light' : 'Dark'} square ${String.fromCharCode(97 + col)}${8 - row}${piece ? ` with ${piece}` : ''}`}
      tabIndex={0}
    >
      {piece && (
        <span className="chess-piece">
          {getPieceSymbol(piece)}
        </span>
      )}
    </div>
  );
};
