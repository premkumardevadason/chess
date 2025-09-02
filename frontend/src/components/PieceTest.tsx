import React from 'react';

export const PieceTest: React.FC = () => {
  // Test piece creation
  const testPieces = ['r', 'n', 'b', 'q', 'k', 'p', 'R', 'N', 'B', 'Q', 'K', 'P'];
  
  const createTestPiece = (pieceString: string) => {
    const pieceMap: Record<string, { type: string; color: string }> = {
      'K': { type: 'king', color: 'white' },
      'Q': { type: 'queen', color: 'white' },
      'R': { type: 'rook', color: 'white' },
      'B': { type: 'bishop', color: 'white' },
      'N': { type: 'knight', color: 'white' },
      'P': { type: 'pawn', color: 'white' },
      'k': { type: 'king', color: 'black' },
      'q': { type: 'queen', color: 'black' },
      'r': { type: 'rook', color: 'black' },
      'b': { type: 'bishop', color: 'black' },
      'n': { type: 'knight', color: 'black' },
      'p': { type: 'pawn', color: 'black' }
    };
    
    const pieceData = pieceMap[pieceString];
    if (!pieceData) {
      console.warn('Unknown piece:', pieceString);
      return null;
    }
    return { ...pieceData, hasMoved: false };
  };

  return (
    <div className="p-4 border border-blue-300 rounded">
      <h3 className="text-lg font-bold mb-2">Piece Creation Test</h3>
      <div className="grid grid-cols-6 gap-2">
        {testPieces.map(pieceString => {
          const piece = createTestPiece(pieceString);
          return (
            <div key={pieceString} className="text-center">
              <div className="w-8 h-8 border border-gray-400 flex items-center justify-center text-sm">
                {piece ? `${piece.color[0]}${piece.type[0]}` : '?'}
              </div>
              <div className="text-xs text-gray-600">{pieceString}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
