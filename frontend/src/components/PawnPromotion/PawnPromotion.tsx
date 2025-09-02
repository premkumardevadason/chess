import React from 'react';
import type { PieceType, PieceColor } from '@/types/chess';

interface PawnPromotionProps {
  isOpen: boolean;
  color: PieceColor;
  onSelect: (pieceType: PieceType) => void;
  onCancel: () => void;
}

export const PawnPromotion: React.FC<PawnPromotionProps> = ({
  isOpen,
  color,
  onSelect,
  onCancel
}) => {
  if (!isOpen) return null;

  const promotionOptions: { type: PieceType; symbol: string; name: string }[] = [
    { type: 'queen', symbol: color === 'white' ? '♕' : '♛', name: 'Queen' },
    { type: 'rook', symbol: color === 'white' ? '♖' : '♜', name: 'Rook' },
    { type: 'bishop', symbol: color === 'white' ? '♗' : '♝', name: 'Bishop' },
    { type: 'knight', symbol: color === 'white' ? '♘' : '♞', name: 'Knight' }
  ];

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-card border border-border rounded-lg p-6 max-w-sm w-full mx-4">
        <h3 className="text-lg font-semibold mb-4 text-center">
          Promote Pawn to:
        </h3>
        
        <div className="grid grid-cols-2 gap-3 mb-6">
          {promotionOptions.map((option) => (
            <button
              key={option.type}
              onClick={() => onSelect(option.type)}
              className="flex flex-col items-center p-4 border border-border rounded-lg hover:bg-muted transition-colors"
            >
              <span className="text-3xl mb-2">{option.symbol}</span>
              <span className="text-sm font-medium">{option.name}</span>
            </button>
          ))}
        </div>
        
        <div className="flex space-x-3">
          <button
            onClick={onCancel}
            className="flex-1 px-4 py-2 bg-secondary text-secondary-foreground rounded-md hover:bg-secondary/90 transition-colors"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
};
