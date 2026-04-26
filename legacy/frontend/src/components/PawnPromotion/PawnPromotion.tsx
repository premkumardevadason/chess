import React from 'react';
import type { PieceType, PieceColor } from '@/types/chess';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

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
    <Dialog open={isOpen} onOpenChange={(open: boolean) => !open && onCancel()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle className="text-center">Promote Pawn to:</DialogTitle>
          <DialogDescription className="text-center">
            Choose which piece to promote your pawn to
          </DialogDescription>
        </DialogHeader>
        
        <div className="grid grid-cols-2 gap-3 my-4">
          {promotionOptions.map((option) => (
            <Card
              key={option.type}
              className="cursor-pointer hover:bg-muted transition-colors"
              onClick={() => onSelect(option.type)}
            >
              <CardContent className="flex flex-col items-center p-4">
                <span className="text-3xl mb-2">{option.symbol}</span>
                <span className="text-sm font-medium">{option.name}</span>
              </CardContent>
            </Card>
          ))}
        </div>
        
        <div className="flex justify-center">
          <Button
            onClick={onCancel}
            variant="outline"
            className="w-full"
          >
            Cancel
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};
