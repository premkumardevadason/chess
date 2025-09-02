// Chess game types
export type PieceType = 'king' | 'queen' | 'rook' | 'bishop' | 'knight' | 'pawn';
export type PieceColor = 'white' | 'black';
export type GameStatus = 'active' | 'checkmate' | 'stalemate' | 'draw';
export type ChessNotation = 'algebraic' | 'descriptive';

export interface Piece {
  type: PieceType;
  color: PieceColor;
  hasMoved?: boolean;
}

export interface Square {
  piece: Piece | null;
  position: [number, number]; // [row, col]
  isHighlighted?: boolean;
  isSelected?: boolean;
  isLastMove?: boolean;
  isCheck?: boolean;
}

export interface Move {
  from: [number, number];
  to: [number, number];
  piece: Piece;
  captured?: Piece;
  promotion?: PieceType;
  notation: string;
  timestamp: number;
}

export interface AIMove {
  from: [number, number];
  to: [number, number];
  aiName: string;
}

export interface GameState {
  board: (Piece | null)[][];
  currentPlayer: PieceColor;
  gameStatus: GameStatus;
  moveHistory: Move[];
  selectedSquare?: [number, number];
  aiMove?: AIMove;
  checkSquares: [number, number][];
  availableMoves: [number, number][];
}

export interface AISystem {
  name: string;
  enabled: boolean;
  training: boolean;
  progress: number;
  status: 'idle' | 'training' | 'error';
  lastError?: string;
  responseTime?: number;
  gamesPlayed?: number;
  winRate?: number;
}

export interface TrainingStatus {
  active: boolean;
  progress: Record<string, number>;
  quality: Record<string, TrainingQuality>;
  startTime?: number;
  totalGames?: number;
  completedGames?: number;
}

export interface TrainingQuality {
  winRate: number;
  averageResponseTime: number;
  errorRate: number;
  lastUpdated: number;
}

export interface MCPStatus {
  enabled: boolean;
  connected: boolean;
  activeAgents: number;
  totalSessions: number;
  lastActivity?: number;
}

export interface UserPreferences {
  theme: 'light' | 'dark' | 'system';
  soundEnabled: boolean;
  animationsEnabled: boolean;
  language: string;
  chessNotation: ChessNotation;
  pieceStyle: 'unicode' | 'images';
  accessibilityMode: boolean;
  reducedMotion: boolean;
  highContrast: boolean;
}

export interface WebSocketMessage {
  type: string;
  payload: any;
  timestamp: number;
}

export interface GameData {
  moves: Move[];
  result?: 'white-wins' | 'black-wins' | 'draw';
  pgn?: string;
  fen?: string;
  metadata?: {
    whitePlayer?: string;
    blackPlayer?: string;
    date?: string;
    event?: string;
  };
}
