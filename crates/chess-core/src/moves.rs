//! [`Move`] — the 16-bit packed chess move, and [`MoveRecord`] — the move
//! together with the data required to undo it.
//!
//! 16-bit encoding (Stockfish-style 4-bit `kind` field):
//!
//! ```text
//! bits  0-5  : from-square     (0..64)
//! bits  6-11 : to-square       (0..64)
//! bits 12-15 : kind            (0..=11; see MoveKind below)
//! ```
//!
//! The 4-bit `kind` field encodes the 12 distinct move shapes a chess move can
//! take:
//!
//! ```text
//! 0  Normal              non-capturing, non-promoting, non-castling
//! 1  Capture             non-promoting capture
//! 2  Castle              O-O / O-O-O
//! 3  EnPassant           en-passant capture
//! 4  PromoKnight         quiet promotion to knight
//! 5  PromoCaptKnight     capturing promotion to knight
//! 6  PromoBishop         quiet promotion to bishop
//! 7  PromoCaptBishop     capturing promotion to bishop
//! 8  PromoRook           quiet promotion to rook
//! 9  PromoCaptRook       capturing promotion to rook
//! 10 PromoQueen          quiet promotion to queen
//! 11 PromoCaptQueen      capturing promotion to queen
//! ```
//!
//! The decomposition into `MoveFlag` (Normal/Capture/Castle/EnPassant) and
//! `Promotion` (None/N/B/R/Q) used by the rest of the codebase is provided by
//! the `Move::flag` and `Move::promotion` accessors, matching the public API
//! described in [`data-model.md §2`].
//!
//! [`data-model.md §2`]: ../../../specs/001-chess-ai-rewrite/data-model.md

use crate::position::{CastlingRights, PieceType, Square};

// ---------------------------------------------------------------------------
// MoveFlag / Promotion (public, externally-visible)
// ---------------------------------------------------------------------------

/// One of the four mutually-exclusive move shapes (orthogonal to promotion).
///
/// Capturing promotions report [`MoveFlag::Capture`]; quiet promotions report
/// [`MoveFlag::Normal`].
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
pub enum MoveFlag {
    /// A non-capturing, non-castling, non-en-passant move (incl. quiet
    /// promotions).
    Normal,
    /// A regular (non-en-passant) capture (incl. capturing promotions).
    Capture,
    /// O-O or O-O-O.
    Castle,
    /// An en-passant capture.
    EnPassant,
}

impl MoveFlag {
    /// Returns true iff this flag implies a capture (Capture or EnPassant).
    #[inline]
    pub const fn is_capture(self) -> bool {
        matches!(self, MoveFlag::Capture | MoveFlag::EnPassant)
    }
}

/// Promotion choice (only valid for pawn moves landing on rank 1 or 8).
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
#[repr(u8)]
pub enum Promotion {
    /// Not a promotion.
    None = 0,
    /// Promote to knight.
    Knight = 1,
    /// Promote to bishop.
    Bishop = 2,
    /// Promote to rook.
    Rook = 3,
    /// Promote to queen.
    Queen = 4,
}

// ---------------------------------------------------------------------------
// MoveKind (private 4-bit tag)
// ---------------------------------------------------------------------------

#[derive(Copy, Clone, Eq, PartialEq, Debug)]
#[repr(u8)]
enum MoveKind {
    Normal = 0,
    Capture = 1,
    Castle = 2,
    EnPassant = 3,
    PromoKnight = 4,
    PromoCaptKnight = 5,
    PromoBishop = 6,
    PromoCaptBishop = 7,
    PromoRook = 8,
    PromoCaptRook = 9,
    PromoQueen = 10,
    PromoCaptQueen = 11,
}

impl MoveKind {
    #[inline]
    fn from_u8(v: u8) -> Self {
        match v {
            0 => MoveKind::Normal,
            1 => MoveKind::Capture,
            2 => MoveKind::Castle,
            3 => MoveKind::EnPassant,
            4 => MoveKind::PromoKnight,
            5 => MoveKind::PromoCaptKnight,
            6 => MoveKind::PromoBishop,
            7 => MoveKind::PromoCaptBishop,
            8 => MoveKind::PromoRook,
            9 => MoveKind::PromoCaptRook,
            10 => MoveKind::PromoQueen,
            11 => MoveKind::PromoCaptQueen,
            _ => panic!("invalid MoveKind value {}", v),
        }
    }

    #[inline]
    fn to_flag(self) -> MoveFlag {
        match self {
            MoveKind::Normal
            | MoveKind::PromoKnight
            | MoveKind::PromoBishop
            | MoveKind::PromoRook
            | MoveKind::PromoQueen => MoveFlag::Normal,
            MoveKind::Capture
            | MoveKind::PromoCaptKnight
            | MoveKind::PromoCaptBishop
            | MoveKind::PromoCaptRook
            | MoveKind::PromoCaptQueen => MoveFlag::Capture,
            MoveKind::Castle => MoveFlag::Castle,
            MoveKind::EnPassant => MoveFlag::EnPassant,
        }
    }

    #[inline]
    fn to_promotion(self) -> Promotion {
        match self {
            MoveKind::PromoKnight | MoveKind::PromoCaptKnight => Promotion::Knight,
            MoveKind::PromoBishop | MoveKind::PromoCaptBishop => Promotion::Bishop,
            MoveKind::PromoRook | MoveKind::PromoCaptRook => Promotion::Rook,
            MoveKind::PromoQueen | MoveKind::PromoCaptQueen => Promotion::Queen,
            _ => Promotion::None,
        }
    }

    #[inline]
    fn from_promotion(promo: Promotion, is_capture: bool) -> Self {
        match (promo, is_capture) {
            (Promotion::Knight, false) => MoveKind::PromoKnight,
            (Promotion::Knight, true) => MoveKind::PromoCaptKnight,
            (Promotion::Bishop, false) => MoveKind::PromoBishop,
            (Promotion::Bishop, true) => MoveKind::PromoCaptBishop,
            (Promotion::Rook, false) => MoveKind::PromoRook,
            (Promotion::Rook, true) => MoveKind::PromoCaptRook,
            (Promotion::Queen, false) => MoveKind::PromoQueen,
            (Promotion::Queen, true) => MoveKind::PromoCaptQueen,
            (Promotion::None, _) => panic!("from_promotion called with Promotion::None"),
        }
    }
}

// ---------------------------------------------------------------------------
// Move (16-bit packed)
// ---------------------------------------------------------------------------

/// A chess move encoded as a single `u16` for cache-friendliness and
/// transposition-table efficiency.
///
/// Construct via the typed helpers ([`Move::new_quiet`], [`Move::new_capture`],
/// [`Move::new_castle`], [`Move::new_en_passant`], [`Move::new_promotion`]),
/// not by manipulating the bits directly.
#[derive(Copy, Clone, Eq, PartialEq, Hash, Debug)]
pub struct Move(u16);

impl Move {
    const FROM_BITS: u16 = 0x003F;
    const TO_SHIFT: u32 = 6;
    const TO_BITS: u16 = 0x0FC0;
    const KIND_SHIFT: u32 = 12;
    const KIND_BITS: u16 = 0xF000;

    /// A null move (`from == to == a1`, `Normal`). Useful as a sentinel.
    pub const NULL: Move = Move(0);

    fn pack(from: Square, to: Square, kind: MoveKind) -> Self {
        let bits = (from.raw() as u16)
            | ((to.raw() as u16) << Self::TO_SHIFT)
            | ((kind as u16) << Self::KIND_SHIFT);
        Move(bits)
    }

    /// Construct a quiet (non-capturing, non-castling, non-promoting) move.
    #[inline]
    pub fn new_quiet(from: Square, to: Square) -> Self {
        Self::pack(from, to, MoveKind::Normal)
    }

    /// Construct a regular (non-en-passant, non-promoting) capture.
    #[inline]
    pub fn new_capture(from: Square, to: Square) -> Self {
        Self::pack(from, to, MoveKind::Capture)
    }

    /// Construct a castling move. `from` is the king's home square; `to` is
    /// `g1`/`c1`/`g8`/`c8`.
    #[inline]
    pub fn new_castle(from: Square, to: Square) -> Self {
        Self::pack(from, to, MoveKind::Castle)
    }

    /// Construct an en-passant capture.
    #[inline]
    pub fn new_en_passant(from: Square, to: Square) -> Self {
        Self::pack(from, to, MoveKind::EnPassant)
    }

    /// Construct a (possibly capturing) promotion. `promo` MUST NOT be
    /// [`Promotion::None`].
    #[inline]
    pub fn new_promotion(from: Square, to: Square, promo: Promotion, is_capture: bool) -> Self {
        debug_assert_ne!(
            promo,
            Promotion::None,
            "use new_quiet/new_capture for non-promotions"
        );
        Self::pack(from, to, MoveKind::from_promotion(promo, is_capture))
    }

    /// Underlying 16-bit representation.
    #[inline]
    pub const fn raw(self) -> u16 {
        self.0
    }

    /// Reconstruct a move from its raw `u16`. Caller must ensure the bits were
    /// produced by a prior `Move::raw()`; this is unchecked except for the
    /// debug assertion that the kind tag is in range.
    #[inline]
    pub fn from_raw(bits: u16) -> Self {
        let kind_tag = (bits & Self::KIND_BITS) >> Self::KIND_SHIFT;
        debug_assert!(kind_tag < 12, "raw bits encode an invalid MoveKind");
        Move(bits)
    }

    /// "From" square.
    #[inline]
    pub fn from(self) -> Square {
        Square::new((self.0 & Self::FROM_BITS) as u8)
    }

    /// "To" square.
    #[inline]
    pub fn to(self) -> Square {
        Square::new(((self.0 & Self::TO_BITS) >> Self::TO_SHIFT) as u8)
    }

    /// Move flag (Normal / Capture / Castle / EnPassant).
    #[inline]
    pub fn flag(self) -> MoveFlag {
        self.kind().to_flag()
    }

    /// Promotion piece, if any.
    #[inline]
    pub fn promotion(self) -> Promotion {
        self.kind().to_promotion()
    }

    /// True iff this move captures a piece (incl. en-passant and capturing
    /// promotions).
    #[inline]
    pub fn is_capture(self) -> bool {
        self.flag().is_capture()
    }

    /// True iff this move is a promotion.
    #[inline]
    pub fn is_promotion(self) -> bool {
        self.promotion() != Promotion::None
    }

    /// True iff this move is castling (`O-O` or `O-O-O`).
    #[inline]
    pub fn is_castle(self) -> bool {
        self.flag() == MoveFlag::Castle
    }

    /// True iff this move is `Move::NULL`.
    #[inline]
    pub fn is_null(self) -> bool {
        self.0 == 0
    }

    #[inline]
    fn kind(self) -> MoveKind {
        MoveKind::from_u8(((self.0 & Self::KIND_BITS) >> Self::KIND_SHIFT) as u8)
    }

    /// Long-algebraic notation (`e2e4`, `e7e8q`). Useful for debugging and for
    /// UCI-style transcripts (the engine itself does NOT use UCI).
    pub fn to_long_algebraic(self) -> String {
        let mut s = String::with_capacity(5);
        s.push_str(&self.from().algebraic());
        s.push_str(&self.to().algebraic());
        match self.promotion() {
            Promotion::None => {}
            Promotion::Knight => s.push('n'),
            Promotion::Bishop => s.push('b'),
            Promotion::Rook => s.push('r'),
            Promotion::Queen => s.push('q'),
        }
        s
    }
}

impl std::fmt::Display for Move {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_long_algebraic())
    }
}

// ---------------------------------------------------------------------------
// MoveRecord (undo data + SAN)
// ---------------------------------------------------------------------------

/// A [`Move`] paired with the state required to reverse it.
#[derive(Clone, Eq, PartialEq, Debug)]
pub struct MoveRecord {
    /// The move itself.
    pub move_played: Move,
    /// The piece kind that was captured, if any (incl. en-passant).
    pub captured_piece: Option<PieceType>,
    /// Castling rights *before* the move.
    pub prior_castling: CastlingRights,
    /// En-passant target *before* the move.
    pub prior_en_passant: Option<Square>,
    /// Halfmove clock *before* the move.
    pub prior_halfmove_clock: u8,
    /// Zobrist hash *before* the move.
    pub prior_zobrist: u64,
    /// Standard algebraic notation, computed at make-time.
    ///
    /// Empty until [`crate::san::annotate_move_record`] is called by the move
    /// generator (which knows the legal move-list of the source position).
    pub san: String,
}

/// Convenience type alias: a move list grows up to 218 (theoretical max for
/// standard chess) but typically holds far fewer; [`smallvec::SmallVec`]
/// keeps the common case allocation-free.
pub type MoveList = smallvec::SmallVec<[Move; 64]>;

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::position::Square;

    #[test]
    fn quiet_move_roundtrip() {
        let m = Move::new_quiet(
            Square::from_algebraic("e2").unwrap(),
            Square::from_algebraic("e4").unwrap(),
        );
        assert_eq!(m.from().algebraic(), "e2");
        assert_eq!(m.to().algebraic(), "e4");
        assert_eq!(m.flag(), MoveFlag::Normal);
        assert_eq!(m.promotion(), Promotion::None);
        assert!(!m.is_capture());
        assert!(!m.is_promotion());
    }

    #[test]
    fn capture_move() {
        let m = Move::new_capture(Square::new(0), Square::new(7));
        assert_eq!(m.flag(), MoveFlag::Capture);
        assert!(m.is_capture());
    }

    #[test]
    fn castle_and_ep() {
        let c = Move::new_castle(
            Square::from_algebraic("e1").unwrap(),
            Square::from_algebraic("g1").unwrap(),
        );
        assert_eq!(c.flag(), MoveFlag::Castle);
        assert!(c.is_castle());

        let ep = Move::new_en_passant(
            Square::from_algebraic("e5").unwrap(),
            Square::from_algebraic("d6").unwrap(),
        );
        assert_eq!(ep.flag(), MoveFlag::EnPassant);
        assert!(ep.is_capture());
    }

    #[test]
    fn promotions_capturing_and_quiet() {
        let from = Square::from_algebraic("e7").unwrap();
        let to = Square::from_algebraic("e8").unwrap();
        let to_d8 = Square::from_algebraic("d8").unwrap();

        let q_quiet = Move::new_promotion(from, to, Promotion::Queen, false);
        assert_eq!(q_quiet.promotion(), Promotion::Queen);
        assert_eq!(q_quiet.flag(), MoveFlag::Normal);

        let q_capt = Move::new_promotion(from, to_d8, Promotion::Queen, true);
        assert_eq!(q_capt.promotion(), Promotion::Queen);
        assert_eq!(q_capt.flag(), MoveFlag::Capture);

        for promo in [
            Promotion::Knight,
            Promotion::Bishop,
            Promotion::Rook,
            Promotion::Queen,
        ] {
            for is_cap in [false, true] {
                let m = Move::new_promotion(from, to, promo, is_cap);
                assert_eq!(m.promotion(), promo);
                assert_eq!(
                    m.flag(),
                    if is_cap {
                        MoveFlag::Capture
                    } else {
                        MoveFlag::Normal
                    }
                );
            }
        }
    }

    #[test]
    fn long_algebraic_renders() {
        let from = Square::from_algebraic("e7").unwrap();
        let to = Square::from_algebraic("e8").unwrap();
        assert_eq!(Move::new_quiet(from, to).to_long_algebraic(), "e7e8");
        assert_eq!(
            Move::new_promotion(from, to, Promotion::Queen, false).to_long_algebraic(),
            "e7e8q"
        );
    }

    #[test]
    fn raw_roundtrip() {
        let from = Square::from_algebraic("a2").unwrap();
        let to = Square::from_algebraic("a4").unwrap();
        let m = Move::new_quiet(from, to);
        let m2 = Move::from_raw(m.raw());
        assert_eq!(m, m2);
    }
}
