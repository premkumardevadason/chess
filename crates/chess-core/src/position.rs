//! [`Position`] — a complete, immutable description of one chess board state.
//!
//! Mutations are performed by producing a new [`Position`] via
//! [`Position::make_move`], which also returns a [`crate::MoveRecord`] containing
//! the data required to undo the move (FR-014).
//!
//! The encoding follows [`data-model.md §1`] in the feature spec:
//!
//! - `pieces: [Bitboard; 12]` — one bitboard per `(color × piece-type)`.
//! - `side_to_move: Color`
//! - `castling: CastlingRights` — 4-bit bitfield (WK, WQ, BK, BQ).
//! - `en_passant: Option<Square>` — set only on the half-move immediately after a
//!   double pawn push.
//! - `halfmove_clock: u8` — half-moves since last pawn push or capture
//!   (50-move rule).
//! - `fullmove_number: u16` — increments after Black moves; starts at 1.
//! - `zobrist: u64` — incrementally maintained for the transposition table and
//!   threefold-repetition detection.
//!
//! [`data-model.md §1`]: ../../../specs/001-chess-ai-rewrite/data-model.md

use std::fmt;
use std::ops::{BitAnd, BitAndAssign, BitOr, BitOrAssign, BitXor, BitXorAssign, Not};
use std::sync::OnceLock;

use crate::moves::{Move, MoveFlag, MoveRecord, Promotion};

// ---------------------------------------------------------------------------
// Color
// ---------------------------------------------------------------------------

/// Side to move / piece colour.
#[derive(Copy, Clone, Eq, PartialEq, Hash, Debug)]
#[repr(u8)]
pub enum Color {
    /// White, the side that moves first.
    White = 0,
    /// Black.
    Black = 1,
}

impl Color {
    /// Returns the opposite colour.
    #[inline]
    pub const fn opp(self) -> Self {
        match self {
            Color::White => Color::Black,
            Color::Black => Color::White,
        }
    }

    /// Returns the small-integer index `0..2`.
    #[inline]
    pub const fn index(self) -> usize {
        self as usize
    }
}

// ---------------------------------------------------------------------------
// PieceType / Piece
// ---------------------------------------------------------------------------

/// Chess piece kind, irrespective of colour.
#[derive(Copy, Clone, Eq, PartialEq, Hash, Debug)]
#[repr(u8)]
pub enum PieceType {
    /// Pawn.
    Pawn = 0,
    /// Knight.
    Knight = 1,
    /// Bishop.
    Bishop = 2,
    /// Rook.
    Rook = 3,
    /// Queen.
    Queen = 4,
    /// King.
    King = 5,
}

impl PieceType {
    /// Returns the small-integer index `0..6`.
    #[inline]
    pub const fn index(self) -> usize {
        self as usize
    }

    /// Returns all piece kinds, in indexing order.
    pub const ALL: [PieceType; 6] = [
        PieceType::Pawn,
        PieceType::Knight,
        PieceType::Bishop,
        PieceType::Rook,
        PieceType::Queen,
        PieceType::King,
    ];
}

/// A coloured piece.
#[derive(Copy, Clone, Eq, PartialEq, Hash, Debug)]
pub struct Piece {
    /// Piece colour.
    pub color: Color,
    /// Piece kind.
    pub kind: PieceType,
}

impl Piece {
    /// Constructor.
    #[inline]
    pub const fn new(color: Color, kind: PieceType) -> Self {
        Self { color, kind }
    }

    /// Returns the canonical index `0..12` used to address `Position::pieces`.
    /// Whites are `0..6`, blacks are `6..12`, in `PieceType::ALL` order.
    #[inline]
    pub const fn index(self) -> usize {
        self.color.index() * 6 + self.kind.index()
    }

    /// Inverse of [`Piece::index`].
    #[inline]
    pub fn from_index(idx: usize) -> Option<Self> {
        if idx >= 12 {
            return None;
        }
        let color = if idx < 6 { Color::White } else { Color::Black };
        let kind = PieceType::ALL[idx % 6];
        Some(Piece { color, kind })
    }

    /// FEN single-char representation: uppercase = white, lowercase = black.
    pub fn fen_char(self) -> char {
        let c = match self.kind {
            PieceType::Pawn => 'p',
            PieceType::Knight => 'n',
            PieceType::Bishop => 'b',
            PieceType::Rook => 'r',
            PieceType::Queen => 'q',
            PieceType::King => 'k',
        };
        if self.color == Color::White {
            c.to_ascii_uppercase()
        } else {
            c
        }
    }

    /// Inverse of [`Piece::fen_char`]. Returns `None` for any other character.
    pub fn from_fen_char(c: char) -> Option<Self> {
        let kind = match c.to_ascii_lowercase() {
            'p' => PieceType::Pawn,
            'n' => PieceType::Knight,
            'b' => PieceType::Bishop,
            'r' => PieceType::Rook,
            'q' => PieceType::Queen,
            'k' => PieceType::King,
            _ => return None,
        };
        let color = if c.is_ascii_uppercase() {
            Color::White
        } else {
            Color::Black
        };
        Some(Piece::new(color, kind))
    }
}

// ---------------------------------------------------------------------------
// Square
// ---------------------------------------------------------------------------

/// A square index in the range `0..64`. `0 = a1`, `7 = h1`, `56 = a8`, `63 = h8`.
#[derive(Copy, Clone, Eq, PartialEq, Hash, Debug)]
pub struct Square(u8);

impl Square {
    /// Constructor (panics on out-of-range index in debug builds).
    #[inline]
    pub const fn new(idx: u8) -> Self {
        debug_assert!(idx < 64);
        Self(idx)
    }

    /// Constructor that returns `None` for out-of-range input.
    #[inline]
    pub const fn try_new(idx: u8) -> Option<Self> {
        if idx < 64 {
            Some(Self(idx))
        } else {
            None
        }
    }

    /// Construct from `(file, rank)`, both in `0..8`.
    #[inline]
    pub const fn from_file_rank(file: u8, rank: u8) -> Self {
        debug_assert!(file < 8 && rank < 8);
        Self(rank * 8 + file)
    }

    /// Raw `0..64` index.
    #[inline]
    pub const fn index(self) -> usize {
        self.0 as usize
    }

    /// Raw `0..64` index as `u8`.
    #[inline]
    pub const fn raw(self) -> u8 {
        self.0
    }

    /// File index `0..8` (`0 = a`, `7 = h`).
    #[inline]
    pub const fn file(self) -> u8 {
        self.0 & 7
    }

    /// Rank index `0..8` (`0 = rank 1`, `7 = rank 8`).
    #[inline]
    pub const fn rank(self) -> u8 {
        self.0 >> 3
    }

    /// Single-bit bitboard for this square.
    #[inline]
    pub const fn bb(self) -> Bitboard {
        Bitboard(1u64 << self.0)
    }

    /// Two-character algebraic name, e.g. `e4`.
    pub fn algebraic(self) -> String {
        let file = (b'a' + self.file()) as char;
        let rank = (b'1' + self.rank()) as char;
        format!("{}{}", file, rank)
    }

    /// Parse a two-character algebraic square (`a1`..`h8`).
    pub fn from_algebraic(s: &str) -> Option<Self> {
        let bytes = s.as_bytes();
        if bytes.len() != 2 {
            return None;
        }
        let file = bytes[0].to_ascii_lowercase().checked_sub(b'a')?;
        let rank = bytes[1].checked_sub(b'1')?;
        if file < 8 && rank < 8 {
            Some(Self::from_file_rank(file, rank))
        } else {
            None
        }
    }
}

impl fmt::Display for Square {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.algebraic())
    }
}

// ---------------------------------------------------------------------------
// Bitboard
// ---------------------------------------------------------------------------

/// A 64-square bitboard. Bit `i` corresponds to [`Square::new(i)`] = `Square(i)`.
#[derive(Copy, Clone, Eq, PartialEq, Hash, Default, Debug)]
pub struct Bitboard(pub u64);

impl Bitboard {
    /// All squares empty.
    pub const EMPTY: Bitboard = Bitboard(0);
    /// All squares occupied.
    pub const FULL: Bitboard = Bitboard(!0u64);

    /// Constructor.
    #[inline]
    pub const fn new(raw: u64) -> Self {
        Self(raw)
    }

    /// Underlying `u64`.
    #[inline]
    pub const fn raw(self) -> u64 {
        self.0
    }

    /// True iff no squares are set.
    #[inline]
    pub const fn is_empty(self) -> bool {
        self.0 == 0
    }

    /// True iff at least one square is set.
    #[inline]
    pub const fn any(self) -> bool {
        self.0 != 0
    }

    /// Number of set squares.
    #[inline]
    pub const fn count(self) -> u32 {
        self.0.count_ones()
    }

    /// Whether `sq` is set.
    #[inline]
    pub const fn contains(self, sq: Square) -> bool {
        self.0 & (1u64 << sq.0) != 0
    }

    /// Returns `self` with `sq` set.
    #[inline]
    pub const fn with(self, sq: Square) -> Self {
        Self(self.0 | (1u64 << sq.0))
    }

    /// Returns `self` with `sq` cleared.
    #[inline]
    pub const fn without(self, sq: Square) -> Self {
        Self(self.0 & !(1u64 << sq.0))
    }

    /// Returns the lowest-indexed set square, if any.
    #[inline]
    pub fn lsb(self) -> Option<Square> {
        if self.0 == 0 {
            None
        } else {
            Some(Square(self.0.trailing_zeros() as u8))
        }
    }

    /// Removes and returns the lowest-indexed set square, if any.
    #[inline]
    pub fn pop_lsb(&mut self) -> Option<Square> {
        let sq = self.lsb()?;
        self.0 &= self.0 - 1;
        Some(sq)
    }

    /// Iterator over the set squares, lowest first.
    pub fn iter(self) -> BitboardIter {
        BitboardIter(self)
    }
}

/// Iterator that pops squares out of a [`Bitboard`].
pub struct BitboardIter(Bitboard);

impl Iterator for BitboardIter {
    type Item = Square;
    fn next(&mut self) -> Option<Self::Item> {
        self.0.pop_lsb()
    }
}

macro_rules! bb_op {
    ($trait:ident, $method:ident, $op:tt) => {
        impl $trait for Bitboard {
            type Output = Bitboard;
            #[inline]
            fn $method(self, rhs: Bitboard) -> Bitboard { Bitboard(self.0 $op rhs.0) }
        }
    };
}
bb_op!(BitAnd, bitand, &);
bb_op!(BitOr, bitor, |);
bb_op!(BitXor, bitxor, ^);

impl BitAndAssign for Bitboard {
    #[inline]
    fn bitand_assign(&mut self, rhs: Bitboard) {
        self.0 &= rhs.0;
    }
}
impl BitOrAssign for Bitboard {
    #[inline]
    fn bitor_assign(&mut self, rhs: Bitboard) {
        self.0 |= rhs.0;
    }
}
impl BitXorAssign for Bitboard {
    #[inline]
    fn bitxor_assign(&mut self, rhs: Bitboard) {
        self.0 ^= rhs.0;
    }
}
impl Not for Bitboard {
    type Output = Bitboard;
    #[inline]
    fn not(self) -> Bitboard {
        Bitboard(!self.0)
    }
}

// File and rank masks ------------------------------------------------------

/// File-A through file-H bitboards.
pub const FILES: [Bitboard; 8] = {
    let mut out = [Bitboard(0); 8];
    let mut f = 0;
    while f < 8 {
        let mut bits: u64 = 0;
        let mut r = 0;
        while r < 8 {
            bits |= 1u64 << (r * 8 + f);
            r += 1;
        }
        out[f as usize] = Bitboard(bits);
        f += 1;
    }
    out
};

/// Rank-1 through rank-8 bitboards.
pub const RANKS: [Bitboard; 8] = {
    let mut out = [Bitboard(0); 8];
    let mut r = 0u8;
    while r < 8 {
        out[r as usize] = Bitboard(0xFFu64 << (r * 8));
        r += 1;
    }
    out
};

// ---------------------------------------------------------------------------
// CastlingRights
// ---------------------------------------------------------------------------

/// 4-bit bitfield: white-king-side, white-queen-side, black-king-side,
/// black-queen-side castling rights.
#[derive(Copy, Clone, Eq, PartialEq, Hash, Default, Debug)]
pub struct CastlingRights(u8);

impl CastlingRights {
    /// White-king-side bit.
    pub const WK: u8 = 0b0001;
    /// White-queen-side bit.
    pub const WQ: u8 = 0b0010;
    /// Black-king-side bit.
    pub const BK: u8 = 0b0100;
    /// Black-queen-side bit.
    pub const BQ: u8 = 0b1000;
    /// All castling rights set.
    pub const ALL: CastlingRights = CastlingRights(Self::WK | Self::WQ | Self::BK | Self::BQ);
    /// No castling rights set.
    pub const NONE: CastlingRights = CastlingRights(0);

    /// Constructor from raw bits.
    #[inline]
    pub const fn from_bits(bits: u8) -> Self {
        Self(bits & 0b1111)
    }

    /// Raw bits.
    #[inline]
    pub const fn bits(self) -> u8 {
        self.0
    }

    /// True iff `flag` is set.
    #[inline]
    pub const fn has(self, flag: u8) -> bool {
        self.0 & flag != 0
    }

    /// Returns a copy with `flag` cleared.
    #[inline]
    pub const fn cleared(self, flag: u8) -> Self {
        Self(self.0 & !flag)
    }

    /// Returns a copy with `flag` set.
    #[inline]
    pub const fn with(self, flag: u8) -> Self {
        Self(self.0 | flag)
    }

    /// FEN representation, e.g. `KQkq` or `-`.
    pub fn to_fen(self) -> String {
        if self.0 == 0 {
            return "-".to_string();
        }
        let mut s = String::with_capacity(4);
        if self.has(Self::WK) {
            s.push('K');
        }
        if self.has(Self::WQ) {
            s.push('Q');
        }
        if self.has(Self::BK) {
            s.push('k');
        }
        if self.has(Self::BQ) {
            s.push('q');
        }
        s
    }

    /// Parse FEN representation. Returns `None` on unknown character.
    pub fn from_fen(s: &str) -> Option<Self> {
        if s == "-" {
            return Some(CastlingRights::NONE);
        }
        let mut bits = 0u8;
        for c in s.chars() {
            match c {
                'K' => bits |= Self::WK,
                'Q' => bits |= Self::WQ,
                'k' => bits |= Self::BK,
                'q' => bits |= Self::BQ,
                _ => return None,
            }
        }
        Some(Self(bits))
    }
}

// ---------------------------------------------------------------------------
// Zobrist keys
// ---------------------------------------------------------------------------

/// Zobrist key table. 12 piece-square keys, 16 castling keys (4-bit set), 8
/// en-passant file keys, and a single side-to-move key (XORed when Black to
/// move).
struct ZobristKeys {
    piece_sq: [[u64; 64]; 12],
    castling: [u64; 16],
    ep_file: [u64; 8],
    side: u64,
}

const fn splitmix64(mut z: u64) -> u64 {
    z = z.wrapping_add(0x9E3779B97F4A7C15);
    let mut x = z;
    x = (x ^ (x >> 30)).wrapping_mul(0xBF58476D1CE4E5B9);
    x = (x ^ (x >> 27)).wrapping_mul(0x94D049BB133111EB);
    x ^ (x >> 31)
}

fn zobrist_keys() -> &'static ZobristKeys {
    static KEYS: OnceLock<ZobristKeys> = OnceLock::new();
    KEYS.get_or_init(|| {
        let mut state: u64 = 0xDEADBEEF_CAFEBABE;
        let mut piece_sq = [[0u64; 64]; 12];
        for piece in piece_sq.iter_mut() {
            for sq in piece.iter_mut() {
                state = splitmix64(state);
                *sq = state;
            }
        }
        let mut castling = [0u64; 16];
        for slot in castling.iter_mut() {
            state = splitmix64(state);
            *slot = state;
        }
        let mut ep_file = [0u64; 8];
        for slot in ep_file.iter_mut() {
            state = splitmix64(state);
            *slot = state;
        }
        state = splitmix64(state);
        let side = state;
        ZobristKeys {
            piece_sq,
            castling,
            ep_file,
            side,
        }
    })
}

// ---------------------------------------------------------------------------
// Position
// ---------------------------------------------------------------------------

/// FEN of the standard opening position.
pub const STARTPOS_FEN: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

/// One complete description of a chess position.
#[derive(Copy, Clone, Eq, PartialEq, Hash, Debug)]
pub struct Position {
    /// Bitboard per `(color × piece-type)`. Indexed via [`Piece::index`].
    pub pieces: [Bitboard; 12],
    /// Side to move.
    pub side_to_move: Color,
    /// Castling availability.
    pub castling: CastlingRights,
    /// En-passant target square (the square the pawn skipped over), if any.
    pub en_passant: Option<Square>,
    /// Half-moves since last pawn push or capture (50-move rule).
    pub halfmove_clock: u8,
    /// Full-move counter (starts at 1; increments after Black's move).
    pub fullmove_number: u16,
    /// Incrementally-maintained Zobrist hash.
    pub zobrist: u64,
}

impl Default for Position {
    fn default() -> Self {
        Position::startpos()
    }
}

/// Errors returned by [`Position::from_fen`].
#[derive(thiserror::Error, Debug, PartialEq, Eq)]
pub enum FenError {
    /// FEN had a wrong number of space-separated fields.
    #[error("expected 6 FEN fields, found {0}")]
    BadFieldCount(usize),
    /// Piece-placement field was malformed.
    #[error("malformed piece placement: {0}")]
    BadPiecePlacement(String),
    /// Side-to-move field was not `w` or `b`.
    #[error("invalid side-to-move {0:?}")]
    BadSideToMove(String),
    /// Castling-rights field could not be parsed.
    #[error("invalid castling rights {0:?}")]
    BadCastling(String),
    /// En-passant field could not be parsed.
    #[error("invalid en-passant target {0:?}")]
    BadEnPassant(String),
    /// Halfmove or fullmove counter could not be parsed as an integer.
    #[error("invalid {field} counter {value:?}")]
    BadCounter {
        /// Counter name.
        field: &'static str,
        /// Offending input.
        value: String,
    },
}

impl Position {
    /// Returns the standard opening position.
    pub fn startpos() -> Self {
        Self::from_fen(STARTPOS_FEN).expect("startpos FEN is well-formed")
    }

    /// Returns an empty position with no pieces, white to move, no rights.
    /// Used internally; not legal as a chess position.
    pub fn empty() -> Self {
        let mut p = Position {
            pieces: [Bitboard::EMPTY; 12],
            side_to_move: Color::White,
            castling: CastlingRights::NONE,
            en_passant: None,
            halfmove_clock: 0,
            fullmove_number: 1,
            zobrist: 0,
        };
        p.zobrist = p.compute_zobrist();
        p
    }

    /// Returns the [`Piece`] occupying `sq`, if any.
    pub fn piece_on(&self, sq: Square) -> Option<Piece> {
        for idx in 0..12 {
            if self.pieces[idx].contains(sq) {
                return Piece::from_index(idx);
            }
        }
        None
    }

    /// Returns the bitboard of all pieces of `color`.
    #[inline]
    pub fn occupied_by(&self, color: Color) -> Bitboard {
        let base = color.index() * 6;
        self.pieces[base]
            | self.pieces[base + 1]
            | self.pieces[base + 2]
            | self.pieces[base + 3]
            | self.pieces[base + 4]
            | self.pieces[base + 5]
    }

    /// Returns the bitboard of all pieces of any colour.
    #[inline]
    pub fn occupied(&self) -> Bitboard {
        self.occupied_by(Color::White) | self.occupied_by(Color::Black)
    }

    /// Returns the bitboard of pieces of `color` and kind `kind`.
    #[inline]
    pub fn pieces_of(&self, color: Color, kind: PieceType) -> Bitboard {
        self.pieces[Piece::new(color, kind).index()]
    }

    /// Returns the square of the king of `color` (panics if missing — kings are
    /// invariant-required).
    pub fn king_square(&self, color: Color) -> Square {
        self.pieces_of(color, PieceType::King)
            .lsb()
            .expect("king must be present")
    }

    /// Compute the Zobrist hash from scratch (slow; used in debug invariant
    /// checks and at FEN-load time).
    pub fn compute_zobrist(&self) -> u64 {
        let keys = zobrist_keys();
        let mut h = 0u64;
        for idx in 0..12 {
            let mut bb = self.pieces[idx];
            while let Some(sq) = bb.pop_lsb() {
                h ^= keys.piece_sq[idx][sq.index()];
            }
        }
        h ^= keys.castling[self.castling.bits() as usize];
        if let Some(ep) = self.en_passant {
            h ^= keys.ep_file[ep.file() as usize];
        }
        if self.side_to_move == Color::Black {
            h ^= keys.side;
        }
        h
    }

    /// In debug builds, panics if the position violates any invariant from
    /// [`data-model.md §1`]. No-op in release builds.
    ///
    /// [`data-model.md §1`]: ../../../specs/001-chess-ai-rewrite/data-model.md
    #[track_caller]
    pub fn assert_invariants(&self) {
        if !cfg!(debug_assertions) {
            return;
        }

        for c in [Color::White, Color::Black] {
            assert_eq!(
                self.pieces_of(c, PieceType::King).count(),
                1,
                "exactly one {:?} king",
                c
            );
        }
        let pawns_w = self.pieces_of(Color::White, PieceType::Pawn);
        let pawns_b = self.pieces_of(Color::Black, PieceType::Pawn);
        assert!(
            (pawns_w & (RANKS[0] | RANKS[7])).is_empty(),
            "white pawn on rank 1 or 8"
        );
        assert!(
            (pawns_b & (RANKS[0] | RANKS[7])).is_empty(),
            "black pawn on rank 1 or 8"
        );

        if let Some(ep) = self.en_passant {
            let r = ep.rank();
            assert!(r == 2 || r == 5, "en-passant square must be on rank 3 or 6");
        }

        if self.castling.has(CastlingRights::WK) {
            assert_eq!(
                self.piece_on(Square::from_file_rank(4, 0)),
                Some(Piece::new(Color::White, PieceType::King)),
                "WK castling requires white king on e1"
            );
            assert_eq!(
                self.piece_on(Square::from_file_rank(7, 0)),
                Some(Piece::new(Color::White, PieceType::Rook)),
                "WK castling requires white rook on h1"
            );
        }
        if self.castling.has(CastlingRights::WQ) {
            assert_eq!(
                self.piece_on(Square::from_file_rank(4, 0)),
                Some(Piece::new(Color::White, PieceType::King)),
                "WQ castling requires white king on e1"
            );
            assert_eq!(
                self.piece_on(Square::from_file_rank(0, 0)),
                Some(Piece::new(Color::White, PieceType::Rook)),
                "WQ castling requires white rook on a1"
            );
        }
        if self.castling.has(CastlingRights::BK) {
            assert_eq!(
                self.piece_on(Square::from_file_rank(4, 7)),
                Some(Piece::new(Color::Black, PieceType::King)),
                "BK castling requires black king on e8"
            );
            assert_eq!(
                self.piece_on(Square::from_file_rank(7, 7)),
                Some(Piece::new(Color::Black, PieceType::Rook)),
                "BK castling requires black rook on h8"
            );
        }
        if self.castling.has(CastlingRights::BQ) {
            assert_eq!(
                self.piece_on(Square::from_file_rank(4, 7)),
                Some(Piece::new(Color::Black, PieceType::King)),
                "BQ castling requires black king on e8"
            );
            assert_eq!(
                self.piece_on(Square::from_file_rank(0, 7)),
                Some(Piece::new(Color::Black, PieceType::Rook)),
                "BQ castling requires black rook on a8"
            );
        }

        assert_eq!(
            self.zobrist,
            self.compute_zobrist(),
            "zobrist hash drifted from canonical value"
        );
    }
}

// ---------------------------------------------------------------------------
// FEN parse / serialize
// ---------------------------------------------------------------------------

impl Position {
    /// Parse a FEN string into a [`Position`]. Accepts both 6-field FEN and the
    /// extended FEN/EPD with extra annotations after field 6 (extras ignored).
    pub fn from_fen(fen: &str) -> Result<Self, FenError> {
        let trimmed = fen.trim();
        let parts: Vec<&str> = trimmed.split_ascii_whitespace().collect();
        if parts.len() < 6 {
            return Err(FenError::BadFieldCount(parts.len()));
        }
        let placement = parts[0];
        let stm = parts[1];
        let castling = parts[2];
        let ep = parts[3];
        let halfmove = parts[4];
        let fullmove = parts[5];

        let mut pieces = [Bitboard::EMPTY; 12];
        let ranks: Vec<&str> = placement.split('/').collect();
        if ranks.len() != 8 {
            return Err(FenError::BadPiecePlacement(format!(
                "expected 8 ranks, got {}",
                ranks.len()
            )));
        }
        // FEN places rank 8 first.
        for (rank_idx, rank_str) in ranks.iter().enumerate() {
            let rank = 7 - rank_idx as u8;
            let mut file: u8 = 0;
            for c in rank_str.chars() {
                if let Some(d) = c.to_digit(10) {
                    if !(1..=8).contains(&d) {
                        return Err(FenError::BadPiecePlacement(format!(
                            "bad skip count {} in rank {}",
                            d,
                            rank + 1
                        )));
                    }
                    file += d as u8;
                } else if let Some(piece) = Piece::from_fen_char(c) {
                    if file >= 8 {
                        return Err(FenError::BadPiecePlacement(format!(
                            "rank {} overflows file 'h'",
                            rank + 1
                        )));
                    }
                    let sq = Square::from_file_rank(file, rank);
                    pieces[piece.index()] = pieces[piece.index()].with(sq);
                    file += 1;
                } else {
                    return Err(FenError::BadPiecePlacement(format!(
                        "unknown character {:?}",
                        c
                    )));
                }
            }
            if file != 8 {
                return Err(FenError::BadPiecePlacement(format!(
                    "rank {} ended at file {}",
                    rank + 1,
                    file
                )));
            }
        }

        let side_to_move = match stm {
            "w" => Color::White,
            "b" => Color::Black,
            _ => return Err(FenError::BadSideToMove(stm.to_string())),
        };

        let castling = CastlingRights::from_fen(castling)
            .ok_or_else(|| FenError::BadCastling(castling.to_string()))?;

        let en_passant = if ep == "-" {
            None
        } else {
            Some(Square::from_algebraic(ep).ok_or_else(|| FenError::BadEnPassant(ep.to_string()))?)
        };

        let halfmove_clock: u8 = halfmove.parse().map_err(|_| FenError::BadCounter {
            field: "halfmove",
            value: halfmove.to_string(),
        })?;
        let fullmove_number: u16 = fullmove.parse().map_err(|_| FenError::BadCounter {
            field: "fullmove",
            value: fullmove.to_string(),
        })?;

        let mut p = Position {
            pieces,
            side_to_move,
            castling,
            en_passant,
            halfmove_clock,
            fullmove_number,
            zobrist: 0,
        };
        p.zobrist = p.compute_zobrist();
        Ok(p)
    }

    /// Serialize the position to a 6-field FEN string.
    pub fn to_fen(&self) -> String {
        let mut placement = String::with_capacity(72);
        for rank_idx in 0..8 {
            let rank = 7 - rank_idx as u8;
            let mut empties = 0u8;
            for file in 0..8u8 {
                let sq = Square::from_file_rank(file, rank);
                match self.piece_on(sq) {
                    Some(piece) => {
                        if empties > 0 {
                            placement.push((b'0' + empties) as char);
                            empties = 0;
                        }
                        placement.push(piece.fen_char());
                    }
                    None => empties += 1,
                }
            }
            if empties > 0 {
                placement.push((b'0' + empties) as char);
            }
            if rank_idx < 7 {
                placement.push('/');
            }
        }
        let stm = if self.side_to_move == Color::White {
            "w"
        } else {
            "b"
        };
        let castling = self.castling.to_fen();
        let ep = match self.en_passant {
            Some(sq) => sq.algebraic(),
            None => "-".to_string(),
        };
        format!(
            "{} {} {} {} {} {}",
            placement, stm, castling, ep, self.halfmove_clock, self.fullmove_number
        )
    }
}

// ---------------------------------------------------------------------------
// make_move / unmake_move
// ---------------------------------------------------------------------------

/// Castling-rights mask: subtracting these bits from `castling` whenever the
/// piece on the corresponding home square moves or is captured keeps the rights
/// in sync without needing a piece-by-piece check elsewhere.
const CASTLING_MASK: [u8; 64] = {
    let mut m = [0xFFu8; 64];
    // White rooks.
    m[0] = !CastlingRights::WQ; // a1
    m[7] = !CastlingRights::WK; // h1
    m[4] = !(CastlingRights::WK | CastlingRights::WQ); // e1 (king)
                                                       // Black rooks.
    m[56] = !CastlingRights::BQ; // a8
    m[63] = !CastlingRights::BK; // h8
    m[60] = !(CastlingRights::BK | CastlingRights::BQ); // e8 (king)
    m
};

impl Position {
    /// Apply `mv` and return `(new_position, undo_record)`.
    ///
    /// `mv` MUST be a legal move for `self` (or at least pseudo-legal — this
    /// function does not validate king-safety). The caller (move generator,
    /// game state machine) is responsible for legality.
    ///
    /// The returned [`MoveRecord`] contains the data required to reverse the
    /// move via [`Position::unmake_move`].
    pub fn make_move(&self, mv: Move) -> (Position, MoveRecord) {
        let mut next = *self;
        let keys = zobrist_keys();
        let stm = self.side_to_move;
        let opp = stm.opp();

        let from = mv.from();
        let to = mv.to();
        let flag = mv.flag();
        let promo = mv.promotion();

        let moving_piece = self
            .piece_on(from)
            .expect("make_move called with no piece on `from`");

        // Determine captured piece (if any).
        let captured: Option<Piece> = match flag {
            MoveFlag::EnPassant => {
                // EP captures the pawn on the rank "behind" `to`.
                let cap_sq = match stm {
                    Color::White => Square::new(to.raw() - 8),
                    Color::Black => Square::new(to.raw() + 8),
                };
                Some(Piece::new(opp, PieceType::Pawn)).filter(|_| self.piece_on(cap_sq).is_some())
            }
            MoveFlag::Castle => None,
            _ => self.piece_on(to),
        };

        // Remove the moving piece's contribution to zobrist before mutating.
        next.zobrist ^= keys.piece_sq[moving_piece.index()][from.index()];
        next.pieces[moving_piece.index()] = next.pieces[moving_piece.index()].without(from);

        // Capture handling.
        let mut captured_piece_record: Option<PieceType> = None;
        if let Some(cap) = captured {
            let cap_sq = if flag == MoveFlag::EnPassant {
                match stm {
                    Color::White => Square::new(to.raw() - 8),
                    Color::Black => Square::new(to.raw() + 8),
                }
            } else {
                to
            };
            next.zobrist ^= keys.piece_sq[cap.index()][cap_sq.index()];
            next.pieces[cap.index()] = next.pieces[cap.index()].without(cap_sq);
            captured_piece_record = Some(cap.kind);
        }

        // Place the moving piece on `to` (apply promotion if any).
        let placed_kind = match promo {
            Promotion::None => moving_piece.kind,
            Promotion::Knight => PieceType::Knight,
            Promotion::Bishop => PieceType::Bishop,
            Promotion::Rook => PieceType::Rook,
            Promotion::Queen => PieceType::Queen,
        };
        let placed_piece = Piece::new(stm, placed_kind);
        next.zobrist ^= keys.piece_sq[placed_piece.index()][to.index()];
        next.pieces[placed_piece.index()] = next.pieces[placed_piece.index()].with(to);

        // Castling — also move the rook.
        if flag == MoveFlag::Castle {
            let (rook_from_file, rook_to_file) = if to.file() == 6 {
                (7u8, 5u8) // king-side: h-file rook to f-file
            } else {
                (0u8, 3u8) // queen-side: a-file rook to d-file
            };
            let rank = from.rank();
            let rook_from = Square::from_file_rank(rook_from_file, rank);
            let rook_to = Square::from_file_rank(rook_to_file, rank);
            let rook = Piece::new(stm, PieceType::Rook);
            next.zobrist ^= keys.piece_sq[rook.index()][rook_from.index()];
            next.pieces[rook.index()] = next.pieces[rook.index()].without(rook_from);
            next.zobrist ^= keys.piece_sq[rook.index()][rook_to.index()];
            next.pieces[rook.index()] = next.pieces[rook.index()].with(rook_to);
        }

        // Castling rights update.
        let prior_castling = self.castling;
        let new_bits =
            prior_castling.bits() & CASTLING_MASK[from.index()] & CASTLING_MASK[to.index()];
        let new_castling = CastlingRights::from_bits(new_bits);
        next.zobrist ^= keys.castling[prior_castling.bits() as usize];
        next.zobrist ^= keys.castling[new_castling.bits() as usize];
        next.castling = new_castling;

        // En-passant target update.
        let prior_ep = self.en_passant;
        if let Some(ep) = prior_ep {
            next.zobrist ^= keys.ep_file[ep.file() as usize];
        }
        let new_ep = if moving_piece.kind == PieceType::Pawn {
            let from_rank = from.rank() as i8;
            let to_rank = to.rank() as i8;
            if (to_rank - from_rank).abs() == 2 {
                let mid_rank = (from_rank + to_rank) / 2;
                Some(Square::from_file_rank(from.file(), mid_rank as u8))
            } else {
                None
            }
        } else {
            None
        };
        if let Some(ep) = new_ep {
            next.zobrist ^= keys.ep_file[ep.file() as usize];
        }
        next.en_passant = new_ep;

        // Halfmove clock & fullmove number.
        let prior_halfmove = self.halfmove_clock;
        if moving_piece.kind == PieceType::Pawn || captured.is_some() {
            next.halfmove_clock = 0;
        } else {
            next.halfmove_clock = prior_halfmove.saturating_add(1);
        }
        if stm == Color::Black {
            next.fullmove_number = next.fullmove_number.wrapping_add(1);
        }

        // Side to move.
        next.side_to_move = opp;
        next.zobrist ^= keys.side;

        let record = MoveRecord {
            move_played: mv,
            captured_piece: captured_piece_record,
            prior_castling,
            prior_en_passant: prior_ep,
            prior_halfmove_clock: prior_halfmove,
            prior_zobrist: self.zobrist,
            san: String::new(),
        };

        debug_assert_eq!(next.zobrist, next.compute_zobrist());
        (next, record)
    }

    /// Reverse the effect of [`Position::make_move`] on `self`, given the
    /// [`MoveRecord`] returned by it.
    pub fn unmake_move(&self, record: &MoveRecord) -> Position {
        let mut prev = *self;
        let keys = zobrist_keys();
        let stm_then = self.side_to_move.opp(); // who moved
        let opp_then = self.side_to_move;

        let mv = record.move_played;
        let from = mv.from();
        let to = mv.to();
        let flag = mv.flag();
        let promo = mv.promotion();

        // Determine what piece is currently on `to` (post-move).
        let placed_kind = match promo {
            Promotion::None => {
                self.piece_on(to)
                    .expect("piece must occupy `to` after make_move")
                    .kind
            }
            Promotion::Knight => PieceType::Knight,
            Promotion::Bishop => PieceType::Bishop,
            Promotion::Rook => PieceType::Rook,
            Promotion::Queen => PieceType::Queen,
        };
        let placed_piece = Piece::new(stm_then, placed_kind);

        // Remove placed piece from `to`.
        prev.pieces[placed_piece.index()] = prev.pieces[placed_piece.index()].without(to);

        // Restore the original moving piece (pawn before promotion) to `from`.
        let original_kind = if promo == Promotion::None {
            placed_kind
        } else {
            PieceType::Pawn
        };
        let original_piece = Piece::new(stm_then, original_kind);
        prev.pieces[original_piece.index()] = prev.pieces[original_piece.index()].with(from);

        // Restore captured piece.
        if let Some(cap_kind) = record.captured_piece {
            let cap_sq = if flag == MoveFlag::EnPassant {
                match stm_then {
                    Color::White => Square::new(to.raw() - 8),
                    Color::Black => Square::new(to.raw() + 8),
                }
            } else {
                to
            };
            let cap_piece = Piece::new(opp_then, cap_kind);
            prev.pieces[cap_piece.index()] = prev.pieces[cap_piece.index()].with(cap_sq);
        }

        // Castling — also undo rook move.
        if flag == MoveFlag::Castle {
            let (rook_from_file, rook_to_file) = if to.file() == 6 {
                (7u8, 5u8)
            } else {
                (0u8, 3u8)
            };
            let rank = from.rank();
            let rook_from = Square::from_file_rank(rook_from_file, rank);
            let rook_to = Square::from_file_rank(rook_to_file, rank);
            let rook = Piece::new(stm_then, PieceType::Rook);
            prev.pieces[rook.index()] = prev.pieces[rook.index()].without(rook_to);
            prev.pieces[rook.index()] = prev.pieces[rook.index()].with(rook_from);
        }

        // Restore scalar state.
        prev.castling = record.prior_castling;
        prev.en_passant = record.prior_en_passant;
        prev.halfmove_clock = record.prior_halfmove_clock;
        prev.side_to_move = stm_then;
        if stm_then == Color::Black {
            prev.fullmove_number = self.fullmove_number.wrapping_sub(1);
        } else {
            prev.fullmove_number = self.fullmove_number;
        }
        prev.zobrist = record.prior_zobrist;

        let _ = keys; // suppress unused-warning if zobrist branch elided
        debug_assert_eq!(prev.zobrist, prev.compute_zobrist());
        prev
    }
}

// ---------------------------------------------------------------------------
// Tests (FEN round-trip + invariants)
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn startpos_roundtrips_via_fen() {
        let p = Position::startpos();
        assert_eq!(p.to_fen(), STARTPOS_FEN);
        p.assert_invariants();
    }

    #[test]
    fn fen_roundtrip_preserves_state() {
        let cases = [
            STARTPOS_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
        ];
        for fen in cases {
            let p = Position::from_fen(fen).expect("valid FEN");
            assert_eq!(p.to_fen(), fen, "round-trip for {}", fen);
            p.assert_invariants();
        }
    }

    #[test]
    fn fen_rejects_obvious_garbage() {
        assert!(Position::from_fen("not a fen").is_err());
        // Wrong rank count.
        assert!(Position::from_fen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1").is_err());
    }

    #[test]
    fn zobrist_changes_with_side_to_move() {
        let p1 = Position::startpos();
        let mut p2 = p1;
        p2.side_to_move = Color::Black;
        p2.zobrist = p2.compute_zobrist();
        assert_ne!(p1.zobrist, p2.zobrist);
    }

    #[test]
    fn castling_rights_fen() {
        let r = CastlingRights::ALL;
        assert_eq!(r.to_fen(), "KQkq");
        assert_eq!(CastlingRights::from_fen("KQkq"), Some(r));
        assert_eq!(CastlingRights::from_fen("-"), Some(CastlingRights::NONE));
        assert!(CastlingRights::from_fen("X").is_none());
    }

    #[test]
    fn square_helpers() {
        assert_eq!(Square::from_algebraic("a1"), Some(Square::new(0)));
        assert_eq!(Square::from_algebraic("h8"), Some(Square::new(63)));
        assert_eq!(Square::new(28).algebraic(), "e4");
        assert_eq!(Square::from_algebraic("zz"), None);
    }

    #[test]
    fn piece_indexing_roundtrips() {
        for idx in 0..12 {
            let p = Piece::from_index(idx).unwrap();
            assert_eq!(p.index(), idx);
        }
    }
}
