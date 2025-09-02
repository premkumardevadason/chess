import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';
import type { 
  GameState, 
  AISystem, 
  TrainingStatus, 
  MCPStatus, 
  UserPreferences,
  Move,
  Piece,
  PieceType,
  PieceColor,
  GameStatus
} from '@/types/chess';

// Helper function to create piece from string
const createPiece = (pieceString: string): Piece => {
  const pieceMap: Record<string, { type: PieceType; color: PieceColor }> = {
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
    console.warn('createPiece: Unknown piece string:', pieceString);
    return null as any;
  }
  const piece = { ...pieceData, hasMoved: false };
  return piece;
};

// Initial board state - will be populated by backend via WebSocket
const initialBoard: (Piece | null)[][] = Array(8).fill(null).map(() => Array(8).fill(null));



// Helper function to generate chess notation
const generateNotation = (piece: Piece, _from: [number, number], to: [number, number], captured: Piece | null): string => {
  const pieceMap: Record<PieceType, string> = {
    'king': '', 'queen': 'Q', 'rook': 'R', 'bishop': 'B', 'knight': 'N', 'pawn': ''
  };
  
  const pieceSymbol = pieceMap[piece.type] || '';
  const toSquare = `${String.fromCharCode(97 + to[1])}${8 - to[0]}`;
  const captureSymbol = captured ? 'x' : '';
  
  return `${pieceSymbol}${captureSymbol}${toSquare}`;
};

interface ChessAppState {
  // Game State
  gameState: GameState;
  
  // AI Systems State
  aiSystems: Record<string, AISystem>;
  
  // Training State
  trainingStatus: TrainingStatus;
  
  // MCP Status
  mcpStatus: MCPStatus;
  
  // User Preferences
  userPreferences: UserPreferences;
  
  // WebSocket Connection
  isConnected: boolean;
  connectionError?: string;
  
  // Actions
  actions: {
    makeMove: (from: [number, number], to: [number, number]) => void;
    selectSquare: (position: [number, number] | undefined) => void;
    startTraining: (aiSystems: string[]) => void;
    stopTraining: () => void;
    selectAI: (aiName: string) => void;
    updatePreferences: (prefs: Partial<UserPreferences>) => void;
    resetGame: () => void;
    undoMove: () => void;
    redoMove: () => void;
    setConnectionStatus: (connected: boolean, error?: string) => void;
    updateAISystem: (name: string, updates: Partial<AISystem>) => void;
    updateTrainingStatus: (updates: Partial<TrainingStatus>) => void;
    updateMCPStatus: (updates: Partial<MCPStatus>) => void;
    updateGameState: (updates: Partial<GameState>) => void;
  };
}

const useChessStore = create<ChessAppState>()(
  devtools(
    persist(
      (set) => {

        return {
          gameState: {
            board: initialBoard,
            currentPlayer: 'white' as PieceColor,
            gameStatus: 'active' as GameStatus,
            moveHistory: [],
            selectedSquare: undefined,
            aiMove: undefined,
            checkSquares: [] as [number, number][],
            availableMoves: [] as [number, number][]
          },
          
          aiSystems: {
            'AlphaZero': {
              name: 'AlphaZero',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'LeelaChessZero': {
              name: 'LeelaChessZero',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'MCTS': {
              name: 'MCTS',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'Negamax': {
              name: 'Negamax',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'OpenAI': {
              name: 'OpenAI',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'QLearning': {
              name: 'QLearning',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'DeepLearning': {
              name: 'DeepLearning',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'CNN': {
              name: 'CNN',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'DQN': {
              name: 'DQN',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'Genetic': {
              name: 'Genetic',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'AlphaFold3': {
              name: 'AlphaFold3',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            },
            'A3C': {
              name: 'A3C',
              enabled: true,
              training: false,
              progress: 0,
              status: 'idle'
            }
          },
          
          trainingStatus: {
            active: false,
            progress: {},
            quality: {},
            startTime: undefined,
            totalGames: 0,
            completedGames: 0
          },
          
          mcpStatus: {
            enabled: false,
            connected: false,
            activeAgents: 0,
            totalSessions: 0
          },
          
          userPreferences: {
            theme: 'system',
            soundEnabled: true,
            animationsEnabled: true,
            language: 'en',
            chessNotation: 'algebraic',
            pieceStyle: 'unicode',
            accessibilityMode: false,
            reducedMotion: false,
            highContrast: false
          },
          
          isConnected: false,
          connectionError: undefined,
          
          actions: {
            makeMove: (from, to) => {
              // This will be overridden by the WebSocket hook
              // But we still need to clear the selection locally
              set((state) => ({
                gameState: {
                  ...state.gameState,
                  selectedSquare: undefined,
                  availableMoves: []
                }
              }));
              console.log('Making move:', { from, to });
            },
            
            selectSquare: (position) => {
              set((state) => ({
                gameState: {
                  ...state.gameState,
                  selectedSquare: position || undefined,
                  availableMoves: [] // Will be populated by backend
                }
              }));
            },
            
            startTraining: (aiSystems) => {
              set((state) => {
                const updatedAISystems = { ...state.aiSystems };
                aiSystems.forEach(name => {
                  if (updatedAISystems[name]) {
                    updatedAISystems[name] = {
                      ...updatedAISystems[name],
                      training: true,
                      status: 'training'
                    };
                  }
                });
                
                return {
                  aiSystems: updatedAISystems,
                  trainingStatus: {
                    ...state.trainingStatus,
                    active: true,
                    startTime: Date.now()
                  }
                };
              });
            },
            
            stopTraining: () => {
              set((state) => {
                const updatedAISystems = { ...state.aiSystems };
                Object.keys(updatedAISystems).forEach(name => {
                  updatedAISystems[name] = {
                    ...updatedAISystems[name],
                    training: false,
                    status: 'idle'
                  };
                });
                
                return {
                  aiSystems: updatedAISystems,
                  trainingStatus: {
                    ...state.trainingStatus,
                    active: false
                  }
                };
              });
            },
            
            selectAI: (aiName) => {
              set((state) => ({
                gameState: {
                  ...state.gameState,
                  // Add selectedAI to gameState if needed
                }
              }));
              console.log('AI selected:', aiName);
            },
            
            updatePreferences: (prefs) => {
              set((state) => ({
                userPreferences: {
                  ...state.userPreferences,
                  ...prefs
                }
              }));
            },
            
            resetGame: () => {
              set((state) => ({
                gameState: {
                  ...state.gameState,
                  board: initialBoard,
                  currentPlayer: 'white',
                  gameStatus: 'active',
                  moveHistory: [],
                  selectedSquare: undefined,
                  lastMove: undefined,
                  checkSquares: [],
                  availableMoves: []
                }
              }));
            },
            
            undoMove: () => {
              set((state) => {
                if (state.gameState.moveHistory.length === 0) return state;
                
                const newMoveHistory = [...state.gameState.moveHistory];
                newMoveHistory.pop();
                
                return {
                  gameState: {
                    ...state.gameState,
                    moveHistory: newMoveHistory,
                    currentPlayer: newMoveHistory.length % 2 === 0 ? 'white' : 'black',
                    lastMove: newMoveHistory[newMoveHistory.length - 1],
                    selectedSquare: undefined,
                    availableMoves: []
                  }
                };
              });
            },
            
            redoMove: () => {
              // Implementation for redo functionality
              // This would require storing undone moves
            },
            
            setConnectionStatus: (connected, error) => {
              set(() => ({
                isConnected: connected,
                connectionError: error
              }));
            },
            
            updateAISystem: (name, updates) => {
              set((state) => ({
                aiSystems: {
                  ...state.aiSystems,
                  [name]: {
                    ...state.aiSystems[name],
                    ...updates
                  }
                }
              }));
            },
            
            updateTrainingStatus: (updates) => {
              set((state) => ({
                trainingStatus: {
                  ...state.trainingStatus,
                  ...updates
                }
              }));
            },
            
            updateMCPStatus: (updates) => {
              set((state) => ({
                mcpStatus: {
                  ...state.mcpStatus,
                  ...updates
                }
              }));
            },
            
            updateGameState: (updates) => {
              set((state) => ({
                gameState: {
                  ...state.gameState,
                  ...updates
                }
              }));
            }
          }
        };
      },
      {
        name: 'chess-app-storage',
        partialize: (state) => ({
          userPreferences: state.userPreferences
          // Don't persist game state - let backend provide it
        })
      }
    ),
    {
      name: 'chess-store'
    }
  )
);

export default useChessStore;