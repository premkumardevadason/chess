import React, { useState } from 'react';
import { ChessBoard } from './ChessBoard/ChessBoard';
import { GameControls } from './GameControls/GameControls';
import { AIPanel } from './AIPanel/AIPanel';
import { PawnPromotion } from './PawnPromotion/PawnPromotion';
import { OpeningBook } from './OpeningBook/OpeningBook';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { Footer } from './Footer';

import useChessStore from '@/stores/chessStore';
import { useChessWebSocket } from '@/hooks/useChessWebSocket';
import type { PieceType, PieceColor } from '@/types/chess';

export const ChessGame: React.FC = () => {
  const { gameState } = useChessStore();
  useChessWebSocket(); // Initialize WebSocket connection
  const [activeTab, setActiveTab] = useState<'game' | 'training' | 'openings'>('game');
  const [pawnPromotion, setPawnPromotion] = useState<{
    isOpen: boolean;
    color: PieceColor;
    position: [number, number];
  }>({
    isOpen: false,
    color: 'white',
    position: [0, 0]
  });

  const handlePawnPromotion = (color: PieceColor, position: [number, number]) => {
    setPawnPromotion({
      isOpen: true,
      color,
      position
    });
  };

  const handlePromotionSelect = (pieceType: PieceType) => {
    // TODO: Implement pawn promotion logic
    console.log('Promoting pawn to:', pieceType);
    setPawnPromotion(prev => ({ ...prev, isOpen: false }));
  };

  const handlePromotionCancel = () => {
    setPawnPromotion(prev => ({ ...prev, isOpen: false }));
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <Header />
      
      {/* Main Layout */}
      <div className="flex flex-col lg:flex-row min-h-[calc(100vh-4rem)]">
        {/* Sidebar */}
        <Sidebar />
        
        {/* Main Game Area */}
        <main className="flex-1 flex flex-col lg:flex-row items-center justify-center p-4 gap-6">
          {/* Chess Board */}
          <div className="flex-shrink-0">
            <ChessBoard onPawnPromotion={handlePawnPromotion} />
          </div>
          
          {/* Game Controls */}
          <div className="flex-shrink-0">
            <GameControls />
          </div>
        </main>
        
        {/* Right Panel */}
        <div className="lg:w-80 flex-shrink-0 p-4 space-y-4">
          {/* Tab Navigation */}
          <div className="flex bg-muted rounded-lg p-1">
            <button
              onClick={() => setActiveTab('game')}
              className={`flex-1 px-3 py-2 text-sm rounded-md transition-colors ${
                activeTab === 'game' 
                  ? 'bg-background text-foreground shadow-sm' 
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              AI Training
            </button>
            <button
              onClick={() => setActiveTab('openings')}
              className={`flex-1 px-3 py-2 text-sm rounded-md transition-colors ${
                activeTab === 'openings' 
                  ? 'bg-background text-foreground shadow-sm' 
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              Openings
            </button>
          </div>
          
          {/* Tab Content */}
          {activeTab === 'game' && <AIPanel />}
          {activeTab === 'openings' && <OpeningBook />}
        </div>
      </div>
      
      {/* Pawn Promotion Modal */}
      <PawnPromotion
        isOpen={pawnPromotion.isOpen}
        color={pawnPromotion.color}
        onSelect={handlePromotionSelect}
        onCancel={handlePromotionCancel}
      />
      
      {/* Footer */}
      <Footer />
    </div>
  );
};
