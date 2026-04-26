import React, { useState } from 'react';
import useChessStore from '@/stores/chessStore';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';

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
  const { } = useChessStore();

  const categories = ['All', ...Array.from(new Set(OPENINGS.map(o => o.category)))];

  const filteredOpenings = selectedCategory === 'All' 
    ? OPENINGS 
    : OPENINGS.filter(opening => opening.category === selectedCategory);

  const handlePlayOpening = (_opening: Opening) => {
    // TODO: Implement opening play functionality
    // This would send the opening moves to the backend
  };

  const handleAnalyzeOpening = (_opening: Opening) => {
    // TODO: Implement opening analysis
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Opening Book (34 Openings)</CardTitle>
          <Badge variant="outline">
            {filteredOpenings.length} openings
          </Badge>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {/* Category Filter */}
        <div className="flex flex-wrap gap-2">
          {categories.map((category) => (
            <Button
              key={category}
              onClick={() => setSelectedCategory(category)}
              variant={selectedCategory === category ? "default" : "outline"}
              size="sm"
              className="text-xs"
            >
              {category}
            </Button>
          ))}
        </div>

        {/* Openings List */}
        <div className="max-h-64 overflow-y-auto space-y-2">
          {filteredOpenings.map((opening, index) => (
            <Card
              key={index}
              className="cursor-pointer hover:bg-muted/50 transition-colors"
              onClick={() => setSelectedOpening(opening)}
            >
              <CardContent className="p-3">
                <div className="flex items-center justify-between mb-2">
                  <h4 className="font-medium text-sm">{opening.name}</h4>
                  <Badge variant="secondary" className="text-xs">
                    {opening.category}
                  </Badge>
                </div>
                
                <div className="text-xs text-muted-foreground mb-2">
                  {opening.description}
                </div>
                
                <div className="flex items-center justify-between">
                  <div className="flex space-x-1">
                    {opening.moves.map((move, moveIndex) => (
                      <Badge
                        key={moveIndex}
                        variant="outline"
                        className="text-xs font-mono"
                      >
                        {move}
                      </Badge>
                    ))}
                  </div>
                  
                  <div className="flex space-x-1">
                    <Button
                      onClick={(e) => {
                        e.stopPropagation();
                        handlePlayOpening(opening);
                      }}
                      size="sm"
                      variant="default"
                      className="text-xs px-2 py-1 h-auto"
                    >
                      Play
                    </Button>
                    <Button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleAnalyzeOpening(opening);
                      }}
                      size="sm"
                      variant="outline"
                      className="text-xs px-2 py-1 h-auto"
                    >
                      Analyze
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        {/* Opening Details Modal */}
        <Dialog open={!!selectedOpening} onOpenChange={(open: boolean) => !open && setSelectedOpening(null)}>
          <DialogContent className="max-w-md">
            <DialogHeader>
              <DialogTitle>{selectedOpening?.name}</DialogTitle>
              <DialogDescription>
                Detailed information about this chess opening
              </DialogDescription>
            </DialogHeader>
            
            {selectedOpening && (
              <div className="space-y-4">
                <div>
                  <span className="text-sm font-medium">Category: </span>
                  <Badge variant="outline">{selectedOpening.category}</Badge>
                </div>
                
                <div>
                  <span className="text-sm font-medium">Description: </span>
                  <p className="text-sm text-muted-foreground mt-1">{selectedOpening.description}</p>
                </div>
                
                <div>
                  <span className="text-sm font-medium">Moves: </span>
                  <div className="flex flex-wrap gap-1 mt-2">
                    {selectedOpening.moves.map((move, index) => (
                      <Badge
                        key={index}
                        variant="outline"
                        className="text-sm font-mono"
                      >
                        {index + 1}.{move}
                      </Badge>
                    ))}
                  </div>
                </div>
              </div>
            )}
            
            <DialogFooter>
              <Button
                onClick={() => {
                  selectedOpening && handlePlayOpening(selectedOpening);
                  setSelectedOpening(null);
                }}
                className="flex-1"
              >
                Play This Opening
              </Button>
              <Button
                onClick={() => setSelectedOpening(null)}
                variant="outline"
                className="flex-1"
              >
                Close
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardContent>
    </Card>
  );
};
