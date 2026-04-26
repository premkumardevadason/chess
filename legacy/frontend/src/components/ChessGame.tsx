import React, { useState } from 'react';
import { ChessBoard } from './ChessBoard/ChessBoard';
import { GameControls } from './GameControls/GameControls';
import { AIPanel } from './AIPanel/AIPanel';
import { PawnPromotion } from './PawnPromotion/PawnPromotion';
import { OpeningBook } from './OpeningBook/OpeningBook';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { Footer } from './Footer';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

import useChessStore from '@/stores/chessStore';
import { useChessWebSocket } from '@/hooks/useChessWebSocket';
import type { PieceType, PieceColor } from '@/types/chess';

export const ChessGame: React.FC = () => {
  const { } = useChessStore();
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
      <div className="flex flex-col xl:flex-row min-h-[calc(100vh-4rem)] w-full">
        {/* Left Sidebar */}
        <div className="xl:w-64 flex-shrink-0">
          <Sidebar />
        </div>
        
        {/* Main Game Area */}
        <main className="flex-1 flex flex-col xl:flex-row items-center justify-center p-4 gap-12 xl:gap-6 min-w-0">
          {/* Chess Board - Left side when maximized */}
          <div className="flex-shrink-0 mr-4 xl:mr-0" style={{width: '500px', minWidth: '400px'}}>
            <ChessBoard onPawnPromotion={handlePawnPromotion} />
          </div>
          
          {/* Game Controls - Right side when maximized, below when minimized */}
          <div className="flex-shrink-0 xl:ml-8">
            <GameControls />
          </div>
        </main>
        
        {/* Right Panel */}
        <div className="xl:w-80 flex-shrink-0 p-4 space-y-4 overflow-y-auto">
          <Tabs value={activeTab} onValueChange={(value: string) => setActiveTab(value as 'game' | 'training' | 'openings')} className="w-full">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="game">AI Training</TabsTrigger>
              <TabsTrigger value="openings">Openings</TabsTrigger>
            </TabsList>
            
            <TabsContent value="game" className="mt-4">
              <AIPanel />
            </TabsContent>
            
            <TabsContent value="openings" className="mt-4">
              <OpeningBook />
            </TabsContent>
          </Tabs>
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
