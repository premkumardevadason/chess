import React from 'react';
import type { Piece } from '@/types/chess';

interface ChessSquareProps {
  piece: Piece | null;
  position: [number, number];
  isSelected?: boolean;
  isHighlighted?: boolean;
  isLastMove?: boolean;
  isAIMove?: boolean;
  isCheck?: boolean;
  onClick: () => void;
}

export const ChessSquare: React.FC<ChessSquareProps> = ({
  piece,
  position,
  isSelected,
  isHighlighted,
  isLastMove,
  isAIMove,
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
    
    if (isAIMove) {
      classes += 'ai-move ';
    }
    
    if (isCheck) {
      classes += 'check ';
    }
    
    return classes.trim();
  };

  const getPieceSymbol = (piece: Piece) => {
    if (!piece || !piece.color || !piece.type) {
      return '?';
    }
    
    // Direct mapping from piece type to Unicode symbols
    const pieceMap: Record<string, Record<string, string>> = {
      'white': {
        'king': '♔',
        'queen': '♕', 
        'rook': '♖',
        'bishop': '♗',
        'knight': '♘',
        'pawn': '♙'
      },
      'black': {
        'king': '♚︎',
        'queen': '♛︎',
        'rook': '♜︎', 
        'bishop': '♝︎',
        'knight': '♞︎',
        'pawn': '♟︎'
      }
    };
    
    return pieceMap[piece.color]?.[piece.type] || '?';
  };

  return (
    <div
      className={getSquareClasses()}
      onClick={onClick}
      role="gridcell"
      aria-label={`${isLight ? 'Light' : 'Dark'} square ${String.fromCharCode(97 + col)}${8 - row}${piece ? ` with ${piece}` : ''}`}
      tabIndex={0}
    >
      {piece ? (
        <span className="chess-piece">
          {getPieceSymbol(piece)}
        </span>
      ) : null}
    </div>
  );
};
