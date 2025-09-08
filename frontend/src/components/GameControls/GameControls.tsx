import React, { useState } from 'react';
import useChessStore from '@/stores/chessStore';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';

export const GameControls: React.FC = () => {
  const { backendGameState, actions, isConnected } = useChessStore();
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


  const getCurrentPlayerDisplay = (player: string) => {
    return player === 'white' ? 'White ♔' : 'Black ♚';
  };

  const getGameStateText = (gameStatus?: string, currentPlayer?: string) => {
    if (!isConnected) return 'Offline';
    
    switch (gameStatus) {
      case 'checkmate':
        return 'Checkmate';
      case 'stalemate':
        return 'Stalemate';
      case 'draw':
        return 'Draw';
      case 'resigned':
        return 'Resigned';
      case 'active':
        return currentPlayer === 'white' ? 'White to Move' : 'Black to Move';
      default:
        return 'Ready';
    }
  };

  const getGameStateVariant = (gameStatus?: string, _currentPlayer?: string) => {
    if (!isConnected) return 'destructive';
    
    switch (gameStatus) {
      case 'checkmate':
      case 'resigned':
        return 'destructive';
      case 'stalemate':
      case 'draw':
        return 'secondary';
      case 'active':
        return 'default';
      default:
        return 'outline';
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Game Controls</CardTitle>
        <Badge 
          variant={getGameStateVariant(backendGameState?.gameStatus, backendGameState?.currentPlayer)}
          className="bg-sky-500 text-white hover:bg-sky-600"
        >
          {getGameStateText(backendGameState?.gameStatus, backendGameState?.currentPlayer)}
        </Badge>
      </CardHeader>
      
      <CardContent className="space-y-4">
        {/* Primary Controls */}
        <div className="space-y-2">
          <Button
            onClick={actions.resetGame}
            className="w-full"
            size="lg"
          >
            New Game
          </Button>
          
          <div className="grid grid-cols-2 gap-2">
            <Button
              onClick={() => {}} // TODO: Implement undo via backend
              disabled={true} // TODO: Backend should provide move history
              variant="secondary"
              size="sm"
            >
              ↶ Undo
            </Button>
            
            <Button
              onClick={() => {}} // TODO: Implement redo via backend
              disabled={true} // TODO: Backend should provide move history
              variant="secondary"
              size="sm"
            >
              ↷ Redo
            </Button>
          </div>
        </div>
        
        {/* Game Status */}
        <div className="space-y-3 pt-4 border-t">
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">Current Player:</span>
            <Badge variant="outline" className="text-sm">
              {getCurrentPlayerDisplay(backendGameState?.currentPlayer || 'white')}
            </Badge>
          </div>
          
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">Game Status:</span>
            <Badge 
              variant={backendGameState?.gameStatus === 'active' ? 'default' : 'secondary'}
              className="text-sm"
            >
              {(backendGameState?.gameStatus || 'active').charAt(0).toUpperCase() + (backendGameState?.gameStatus || 'active').slice(1)}
            </Badge>
          </div>
          
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">Moves:</span>
            <span className="text-sm font-medium">0</span> {/* TODO: Backend should provide move count */}
          </div>
        </div>
        
        {/* Game Actions */}
        {(backendGameState?.gameStatus || 'active') === 'active' && (
          <div className="pt-4 border-t space-y-2">
            <div className="grid grid-cols-2 gap-2">
              <Dialog open={showConfirmDialog === 'resign'} onOpenChange={(open: boolean) => !open && setShowConfirmDialog(null)}>
                <DialogTrigger asChild>
                  <Button
                    onClick={handleResign}
                    variant="destructive"
                    size="sm"
                  >
                    Resign
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Resign Game?</DialogTitle>
                    <DialogDescription>
                      Are you sure you want to resign? This will end the game in your opponent's favor.
                    </DialogDescription>
                  </DialogHeader>
                  <DialogFooter>
                    <Button variant="outline" onClick={cancelDialog}>
                      Cancel
                    </Button>
                    <Button variant="destructive" onClick={confirmResign}>
                      Resign
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
              
              <Dialog open={showConfirmDialog === 'draw'} onOpenChange={(open: boolean) => !open && setShowConfirmDialog(null)}>
                <DialogTrigger asChild>
                  <Button
                    onClick={handleOfferDraw}
                    variant="secondary"
                    size="sm"
                  >
                    Offer Draw
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Offer Draw?</DialogTitle>
                    <DialogDescription>
                      Do you want to offer a draw to your opponent?
                    </DialogDescription>
                  </DialogHeader>
                  <DialogFooter>
                    <Button variant="outline" onClick={cancelDialog}>
                      Cancel
                    </Button>
                    <Button variant="secondary" onClick={confirmDraw}>
                      Offer Draw
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
};
