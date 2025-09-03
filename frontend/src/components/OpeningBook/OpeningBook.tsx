import React, { useState } from 'react';
import useChessStore from '@/stores/chessStore';

interface Opening {
  name: string;
  moves: string[];
  description: string;
  category: string;
}

const OPENINGS: Opening[] = [
  // King's Pawn Openings
  { name: "Italian Game", moves: ["e4", "e5", "Nf3", "Nc6", "Bc4"], description: "Classical opening with rapid development", category: "King's Pawn" },
  { name: "Spanish (Ruy Lopez)", moves: ["e4", "e5", "Nf3", "Nc6", "Bb5"], description: "Most popular opening in chess", category: "King's Pawn" },
  { name: "Sicilian Defense", moves: ["e4", "c5"], description: "Aggressive response to 1.e4", category: "King's Pawn" },
  { name: "French Defense", moves: ["e4", "e6"], description: "Solid defensive opening", category: "King's Pawn" },
  { name: "Caro-Kann Defense", moves: ["e4", "c6"], description: "Solid and reliable opening", category: "King's Pawn" },
  
  // Queen's Pawn Openings
  { name: "Queen's Gambit", moves: ["d4", "d5", "c4"], description: "Classical opening with pawn sacrifice", category: "Queen's Pawn" },
  { name: "King's Indian Defense", moves: ["d4", "Nf6", "c4", "g6"], description: "Dynamic counter-attacking opening", category: "Queen's Pawn" },
  { name: "Nimzo-Indian Defense", moves: ["d4", "Nf6", "c4", "e6", "Nc3", "Bb4"], description: "Strategic opening with piece pressure", category: "Queen's Pawn" },
  { name: "Grünfeld Defense", moves: ["d4", "Nf6", "c4", "g6", "Nc3", "d5"], description: "Dynamic counter-gambit", category: "Queen's Pawn" },
  { name: "English Opening", moves: ["c4"], description: "Flank opening with flexible development", category: "Queen's Pawn" },
  
  // Flank Openings
  { name: "Reti Opening", moves: ["Nf3"], description: "Flexible opening system", category: "Flank" },
  { name: "Bird's Opening", moves: ["f4"], description: "Aggressive flank opening", category: "Flank" },
  { name: "Nimzowitsch-Larsen Attack", moves: ["b3"], description: "Unconventional flank opening", category: "Flank" },
  
  // Irregular Openings
  { name: "Alekhine's Defense", moves: ["e4", "Nf6"], description: "Provocative opening inviting pawn advance", category: "Irregular" },
  { name: "Pirc Defense", moves: ["e4", "d6"], description: "Hypermodern opening", category: "Irregular" },
  { name: "Modern Defense", moves: ["e4", "g6"], description: "Hypermodern opening with fianchetto", category: "Irregular" },
  
  // Gambits
  { name: "King's Gambit", moves: ["e4", "e5", "f4"], description: "Aggressive gambit opening", category: "Gambit" },
  { name: "Evans Gambit", moves: ["e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "b4"], description: "Gambit in Italian Game", category: "Gambit" },
  { name: "Benko Gambit", moves: ["d4", "Nf6", "c4", "c5", "d5", "b5"], description: "Pawn sacrifice for long-term compensation", category: "Gambit" },
  
  // Indian Defenses
  { name: "Queen's Indian Defense", moves: ["d4", "Nf6", "c4", "e6", "Nf3", "b6"], description: "Solid Indian defense", category: "Indian" },
  { name: "Bogo-Indian Defense", moves: ["d4", "Nf6", "c4", "e6", "Nf3", "Bb4+"], description: "Indian defense with bishop check", category: "Indian" },
  { name: "Old Indian Defense", moves: ["d4", "Nf6", "c4", "d6"], description: "Traditional Indian defense", category: "Indian" },
  
  // Semi-Open Games
  { name: "Scandinavian Defense", moves: ["e4", "d5"], description: "Direct counter-attack", category: "Semi-Open" },
  { name: "Center Counter", moves: ["e4", "d5", "exd5"], description: "Immediate pawn capture", category: "Semi-Open" },
  { name: "Philidor Defense", moves: ["e4", "e5", "Nf3", "d6"], description: "Solid but passive defense", category: "Semi-Open" },
  
  // Closed Games
  { name: "Vienna Game", moves: ["e4", "e5", "Nc3"], description: "Flexible development system", category: "Closed" },
  { name: "Petrov Defense", moves: ["e4", "e5", "Nf3", "Nf6"], description: "Symmetrical opening", category: "Closed" },
  { name: "Scotch Game", moves: ["e4", "e5", "Nf3", "Nc6", "d4"], description: "Direct central advance", category: "Closed" },
  
  // Additional Openings
  { name: "Dutch Defense", moves: ["d4", "f5"], description: "Unusual but playable opening", category: "Unusual" },
  { name: "Budapest Gambit", moves: ["d4", "Nf6", "c4", "e5"], description: "Gambit in Queen's Pawn", category: "Gambit" },
  { name: "Albin Counter-Gambit", moves: ["d4", "d5", "c4", "e5"], description: "Counter-gambit response", category: "Gambit" },
  { name: "Latvian Gambit", moves: ["e4", "e5", "Nf3", "f5"], description: "Aggressive gambit", category: "Gambit" },
  { name: "Elephant Gambit", moves: ["e4", "e5", "Nf3", "d5"], description: "Unusual gambit", category: "Gambit" },
  { name: "St. George Defense", moves: ["e4", "a6"], description: "Unconventional opening", category: "Unusual" }
];

export const OpeningBook: React.FC = () => {
  const [selectedCategory, setSelectedCategory] = useState<string>('All');
  const [selectedOpening, setSelectedOpening] = useState<Opening | null>(null);
  const { actions } = useChessStore();

  const categories = ['All', ...Array.from(new Set(OPENINGS.map(o => o.category)))];

  const filteredOpenings = selectedCategory === 'All' 
    ? OPENINGS 
    : OPENINGS.filter(opening => opening.category === selectedCategory);

  const handlePlayOpening = (opening: Opening) => {
    // TODO: Implement opening play functionality
    // This would send the opening moves to the backend
  };

  const handleAnalyzeOpening = (opening: Opening) => {
    // TODO: Implement opening analysis
  };

  return (
    <div className="bg-card border border-border rounded-lg p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Opening Book (34 Openings)</h3>
        <div className="text-sm text-muted-foreground">
          {filteredOpenings.length} openings
        </div>
      </div>

      {/* Category Filter */}
      <div className="flex flex-wrap gap-2">
        {categories.map((category) => (
          <button
            key={category}
            onClick={() => setSelectedCategory(category)}
            className={`px-3 py-1 text-xs rounded-full transition-colors ${
              selectedCategory === category
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted text-muted-foreground hover:bg-muted/80'
            }`}
          >
            {category}
          </button>
        ))}
      </div>

      {/* Openings List */}
      <div className="max-h-64 overflow-y-auto space-y-2">
        {filteredOpenings.map((opening, index) => (
          <div
            key={index}
            className="p-3 border border-border rounded-lg hover:bg-muted/50 transition-colors cursor-pointer"
            onClick={() => setSelectedOpening(opening)}
          >
            <div className="flex items-center justify-between mb-2">
              <h4 className="font-medium text-sm">{opening.name}</h4>
              <span className="text-xs text-muted-foreground bg-muted px-2 py-1 rounded">
                {opening.category}
              </span>
            </div>
            
            <div className="text-xs text-muted-foreground mb-2">
              {opening.description}
            </div>
            
            <div className="flex items-center justify-between">
              <div className="flex space-x-1">
                {opening.moves.map((move, moveIndex) => (
                  <span
                    key={moveIndex}
                    className="text-xs bg-muted px-2 py-1 rounded font-mono"
                  >
                    {move}
                  </span>
                ))}
              </div>
              
              <div className="flex space-x-1">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handlePlayOpening(opening);
                  }}
                  className="px-2 py-1 text-xs bg-green-600 text-white rounded hover:bg-green-700 transition-colors"
                >
                  Play
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleAnalyzeOpening(opening);
                  }}
                  className="px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
                >
                  Analyze
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Opening Details Modal */}
      {selectedOpening && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-card border border-border rounded-lg p-6 max-w-md w-full mx-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold">{selectedOpening.name}</h3>
              <button
                onClick={() => setSelectedOpening(null)}
                className="text-muted-foreground hover:text-foreground"
              >
                ✕
              </button>
            </div>
            
            <div className="space-y-3">
              <div>
                <span className="text-sm font-medium">Category: </span>
                <span className="text-sm text-muted-foreground">{selectedOpening.category}</span>
              </div>
              
              <div>
                <span className="text-sm font-medium">Description: </span>
                <span className="text-sm text-muted-foreground">{selectedOpening.description}</span>
              </div>
              
              <div>
                <span className="text-sm font-medium">Moves: </span>
                <div className="flex flex-wrap gap-1 mt-1">
                  {selectedOpening.moves.map((move, index) => (
                    <span
                      key={index}
                      className="text-sm bg-muted px-2 py-1 rounded font-mono"
                    >
                      {index + 1}.{move}
                    </span>
                  ))}
                </div>
              </div>
            </div>
            
            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  handlePlayOpening(selectedOpening);
                  setSelectedOpening(null);
                }}
                className="flex-1 px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors"
              >
                Play This Opening
              </button>
              <button
                onClick={() => setSelectedOpening(null)}
                className="flex-1 px-4 py-2 bg-secondary text-secondary-foreground rounded-md hover:bg-secondary/90 transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
