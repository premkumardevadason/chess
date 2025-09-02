import React, { useState } from 'react';
import useChessStore from '@/stores/chessStore';

export const GameControls: React.FC = () => {
  const { gameState, actions, isConnected } = useChessStore();
  const [showConfirmDialog, setShowConfirmDialog] = useState<'resign' | 'draw' | null>(null);

  const handleResign = () => {
    setShowConfirmDialog('resign');
  };

  const handleOfferDraw = () => {
    setShowConfirmDialog('draw');
  };

  const confirmResign = () => {
    // TODO: Send resign message to backend
    actions.resetGame();
    setShowConfirmDialog(null);
  };

  const confirmDraw = () => {
    // TODO: Send draw offer to backend
    setShowConfirmDialog(null);
  };

  const cancelDialog = () => {
    setShowConfirmDialog(null);
  };

  const getGameStatusColor = (status: string) => {
    switch (status) {
      case 'active': return 'text-green-600';
      case 'checkmate': return 'text-red-600';
      case 'stalemate': return 'text-yellow-600';
      case 'draw': return 'text-blue-600';
      default: return 'text-gray-600';
    }
  };

  const getCurrentPlayerDisplay = (player: string) => {
    return player === 'white' ? 'White ♔' : 'Black ♚';
  };

  return (
    <div className="bg-card border border-border rounded-lg p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Game Controls</h3>
        <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} 
             title={isConnected ? 'Connected' : 'Disconnected'} />
      </div>
      
      {/* Primary Controls */}
      <div className="space-y-2">
        <button
          onClick={actions.resetGame}
          className="w-full px-4 py-2 bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors font-medium"
        >
          New Game
        </button>
        
        <div className="grid grid-cols-2 gap-2">
          <button
            onClick={actions.undoMove}
            disabled={gameState.moveHistory.length === 0}
            className="px-3 py-2 bg-secondary text-secondary-foreground rounded-md hover:bg-secondary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm"
          >
            ↶ Undo
          </button>
          
          <button
            onClick={actions.redoMove}
            className="px-3 py-2 bg-secondary text-secondary-foreground rounded-md hover:bg-secondary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm"
          >
            ↷ Redo
          </button>
        </div>
      </div>
      
      {/* Game Status */}
      <div className="pt-4 border-t border-border">
        <div className="text-sm space-y-2">
          <div className="flex justify-between">
            <span>Current Player:</span>
            <span className={`font-medium ${gameState.currentPlayer === 'white' ? 'text-white' : 'text-gray-800'}`}>
              {getCurrentPlayerDisplay(gameState.currentPlayer)}
            </span>
          </div>
          
          <div className="flex justify-between">
            <span>Game Status:</span>
            <span className={`font-medium ${getGameStatusColor(gameState.gameStatus)}`}>
              {gameState.gameStatus.charAt(0).toUpperCase() + gameState.gameStatus.slice(1)}
            </span>
          </div>
          
          <div className="flex justify-between">
            <span>Moves:</span>
            <span className="font-medium">{gameState.moveHistory.length}</span>
          </div>
          
          {gameState.lastMove && (
            <div className="flex justify-between">
              <span>Last Move:</span>
              <span className="font-mono text-xs">{gameState.lastMove.notation}</span>
            </div>
          )}
        </div>
      </div>
      
      {/* Game Actions */}
      {gameState.gameStatus === 'active' && (
        <div className="pt-4 border-t border-border">
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={handleResign}
              className="px-3 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition-colors text-sm"
            >
              Resign
            </button>
            
            <button
              onClick={handleOfferDraw}
              className="px-3 py-2 bg-yellow-600 text-white rounded-md hover:bg-yellow-700 transition-colors text-sm"
            >
              Offer Draw
            </button>
          </div>
        </div>
      )}
      
      {/* Confirmation Dialogs */}
      {showConfirmDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-card border border-border rounded-lg p-6 max-w-sm w-full mx-4">
            <h4 className="text-lg font-semibold mb-4">
              {showConfirmDialog === 'resign' ? 'Resign Game?' : 'Offer Draw?'}
            </h4>
            
            <p className="text-sm text-muted-foreground mb-6">
              {showConfirmDialog === 'resign' 
                ? 'Are you sure you want to resign? This will end the game in your opponent\'s favor.'
                : 'Do you want to offer a draw to your opponent?'
              }
            </p>
            
            <div className="flex space-x-3">
              <button
                onClick={showConfirmDialog === 'resign' ? confirmResign : confirmDraw}
                className={`flex-1 px-4 py-2 rounded-md text-white transition-colors ${
                  showConfirmDialog === 'resign' 
                    ? 'bg-red-600 hover:bg-red-700' 
                    : 'bg-yellow-600 hover:bg-yellow-700'
                }`}
              >
                {showConfirmDialog === 'resign' ? 'Resign' : 'Offer Draw'}
              </button>
              
              <button
                onClick={cancelDialog}
                className="flex-1 px-4 py-2 bg-secondary text-secondary-foreground rounded-md hover:bg-secondary/90 transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
