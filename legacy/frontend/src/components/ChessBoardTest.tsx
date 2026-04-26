import React from 'react';
import useChessStore from '@/stores/chessStore';

export const ChessBoardTest: React.FC = () => {
  const store = useChessStore();
  const gameState = (store as any).gameState || store;

  return (
    <div className="p-4 border border-gray-300 rounded">
      <h3 className="text-lg font-bold mb-2">Chess Board Debug</h3>
      <div className="space-y-2">
        <p><strong>Board exists:</strong> {gameState.board ? 'Yes' : 'No'}</p>
        <p><strong>Board length:</strong> {gameState.board?.length || 0}</p>
        <p><strong>First row length:</strong> {gameState.board?.[0]?.length || 0}</p>
        <p><strong>Current player:</strong> {gameState.currentPlayer}</p>
        <p><strong>Game status:</strong> {gameState.gameStatus}</p>
        
        {gameState.board && (
          <div className="mt-4">
            <p><strong>Board preview (first row):</strong></p>
                         <div className="flex gap-1">
               {gameState.board[0]?.map((piece: any, index: number) => (
                 <span key={index} className="w-8 h-8 border border-gray-400 flex items-center justify-center text-sm">
                   {piece && piece.color && piece.type ? `${piece.color[0]}${piece.type[0]}` : '·'}
                 </span>
               ))}
             </div>
          </div>
        )}
      </div>
    </div>
  );
};
