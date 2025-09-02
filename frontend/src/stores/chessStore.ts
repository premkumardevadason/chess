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
  PieceColor
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
  return pieceData ? { ...pieceData, hasMoved: false } : null as any;
};

// Initial board state
const initialBoard: (Piece | null)[][] = [
  ['r', 'n', 'b', 'q', 'k', 'b', 'n', 'r'].map(createPiece),
  ['p', 'p', 'p', 'p', 'p', 'p', 'p', 'p'].map(createPiece),
  [null, null, null, null, null, null, null, null],
  [null, null, null, null, null, null, null, null],
  [null, null, null, null, null, null, null, null],
  [null, null, null, null, null, null, null, null],
  ['P', 'P', 'P', 'P', 'P', 'P', 'P', 'P'].map(createPiece),
  ['R', 'N', 'B', 'Q', 'K', 'B', 'N', 'R'].map(createPiece)
];

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
    selectSquare: (position: [number, number]) => void;
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
  };
}

const useChessStore = create<ChessAppState>()(
  devtools(
    persist(
      (set) => ({
        gameState: {
          board: initialBoard,
          currentPlayer: 'white',
          gameStatus: 'active',
          moveHistory: [],
          selectedSquare: undefined,
          lastMove: undefined,
          checkSquares: [],
          availableMoves: []
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
            set((state) => {
              const piece = state.gameState.board[from[0]][from[1]];
              const targetPiece = state.gameState.board[to[0]][to[1]];
              
              // Basic move validation
              if (!piece) {
                console.warn('No piece at source square');
                return state;
              }
              
              // Check if it's the current player's piece
              const isCurrentPlayerPiece = piece.color === state.gameState.currentPlayer;
              
              if (!isCurrentPlayerPiece) {
                console.warn('Not current player\'s piece');
                return state;
              }
              
              // Check if target square has own piece
              if (targetPiece && targetPiece.color === piece.color) {
                console.warn('Cannot capture own piece');
                return state;
              }
              
              const newMove: Move = {
                from,
                to,
                piece: piece,
                captured: targetPiece || undefined,
                notation: generateNotation(piece, from, to, targetPiece),
                timestamp: Date.now()
              };
              
              // Create new board state
              const newBoard = state.gameState.board.map(row => [...row]);
              newBoard[to[0]][to[1]] = piece;
              newBoard[from[0]][from[1]] = null;
              
              return {
                gameState: {
                  ...state.gameState,
                  board: newBoard,
                  currentPlayer: state.gameState.currentPlayer === 'white' ? 'black' : 'white',
                  moveHistory: [...state.gameState.moveHistory, newMove],
                  lastMove: newMove,
                  selectedSquare: undefined,
                  availableMoves: []
                }
              };
            });
          },
          
          selectSquare: (position) => {
            set((state) => ({
              gameState: {
                ...state.gameState,
                selectedSquare: position,
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
          }
        }
      }),
      {
        name: 'chess-app-storage',
        partialize: (state) => ({
          userPreferences: state.userPreferences,
          gameState: {
            board: state.gameState.board,
            currentPlayer: state.gameState.currentPlayer,
            gameStatus: state.gameState.gameStatus,
            moveHistory: state.gameState.moveHistory
          }
        })
      }
    ),
    {
      name: 'chess-store'
    }
  )
);

export default useChessStore;
