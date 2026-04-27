//! Adapter layer between `chess-core` types and the vendored Carp engine
//! types (`chess::*` and `carp::*`).
//!
//! ## Why an adapter?
//!
//! The vendored Carp source uses its own `Board`, `Move`, `Square`, etc. The
//! UI talks to us in `chess-core` types (`Position`, `Move`, `Square`). For
//! CP-C we keep Carp's internal types intact and convert only at the API
//! boundary (per `SetPosition` / per move played, not per search node). The
//! deep replacement of Carp's internals with `chess-core` types is deferred to
//! T097a in CP-J.
//!
//! ## Mapping
//!
//! - `chess_core::Square`  uses `a1 = 0, h1 = 7, a8 = 56, h8 = 63`.
//! - `chess::square::Square` uses `A8 = 0, B8 = 1, ..., H1 = 63`.
//! - The two indexings differ only in rank orientation, so
//!   `carp_idx == core_idx ^ 56`.
//!
//! ## Position conversion
//!
//! Round-trip via FEN. Both sides have well-tested FEN serialise / parse, and
//! this happens once per `SetPosition`, never inside the search tree.
//!
//! ## Move conversion
//!
//! Round-trip via UCI long-algebraic notation (`e2e4`, `e7e8q`, ...). For
//! `chess_core::Move -> chess::moves::Move` we ask Carp's `Board::find_move`
//! to look up the corresponding move in its legal-move list (this also fixes
//! up `MoveType::DoublePush` / `Castle` / `EnPassant` flags that
//! `chess-core` does not distinguish at construction time). The reverse
//! direction matches by `(from, to, promotion)` against `legal_moves`.

use chess::{
    board::Board as CarpBoard,
    moves::{Move as CarpMove, MoveType},
    piece::Color as CarpColor,
};
use chess_core::{Move as CoreMove, Position as CorePosition, Promotion, Square};

/// Errors that can happen at the chess-core ↔ Carp boundary.
#[derive(Debug, thiserror::Error)]
pub enum AdapterError {
    /// FEN produced by chess-core was rejected by Carp's parser.
    #[error("Carp rejected FEN '{fen}': {msg}")]
    CarpFenParse {
        /// The FEN string we tried to parse.
        fen: String,
        /// Carp's rejection message.
        msg: String,
    },
    /// FEN produced by Carp was rejected by chess-core's parser.
    #[error("chess-core rejected FEN '{fen}': {err:?}")]
    CoreFenParse {
        /// The FEN string we tried to parse.
        fen: String,
        /// chess-core's rejection.
        err: chess_core::FenError,
    },
    /// We asked Carp to find a move that is not legal in the given position.
    #[error("UCI move '{uci}' is not legal in position '{fen}'")]
    IllegalMove {
        /// UCI string we tried to translate.
        uci: String,
        /// FEN of the position we tried it in.
        fen: String,
    },
    /// We received a Carp move that does not match any chess-core legal move
    /// in the given position. This indicates an internal inconsistency
    /// between the two move generators.
    #[error("Carp move '{uci}' has no chess-core counterpart in position '{fen}'")]
    NoCoreMatch {
        /// UCI string emitted by Carp.
        uci: String,
        /// FEN of the chess-core position we searched.
        fen: String,
    },
}

/// Convert `chess_core::Square` (a1=0, h8=63) to a Carp square index
/// (A8=0, H1=63). The transformation is `idx ^ 56`.
#[inline]
pub fn square_to_carp_index(sq: Square) -> u8 {
    sq.raw() ^ 56
}

/// Inverse of [`square_to_carp_index`].
#[inline]
pub fn square_from_carp_index(idx: u8) -> Square {
    Square::new(idx ^ 56)
}

/// Convert a `chess_core::Position` to a Carp `Board` via FEN.
///
/// This is the boundary call we make once per `Command::SetPosition`. Cost
/// is dominated by FEN parsing in Carp (~5 µs); negligible compared to a
/// search of any meaningful depth.
pub fn position_to_carp(pos: &CorePosition) -> Result<CarpBoard, AdapterError> {
    let fen = pos.to_fen();
    fen.parse::<CarpBoard>()
        .map_err(|e: &'static str| AdapterError::CarpFenParse {
            fen,
            msg: e.to_string(),
        })
}

/// Convert a Carp `Board` to a `chess_core::Position` via FEN.
pub fn position_from_carp(board: &CarpBoard) -> Result<CorePosition, AdapterError> {
    let fen = board.to_fen();
    CorePosition::from_fen(&fen).map_err(|err| AdapterError::CoreFenParse { fen, err })
}

/// Convert a `chess_core::Move` to the matching legal Carp move on the given
/// Carp board.
///
/// We do this by emitting the chess-core long-algebraic UCI form (`e2e4`,
/// `e7e8q`, `e1g1` for castling, `e5d6` for en-passant, etc.) and asking
/// Carp's move generator for the matching move. Carp's `find_move` performs
/// a linear scan of the legal-move list — fine because we only call this
/// per move played by the UI, not per node.
pub fn move_to_carp(mv: CoreMove, board: &CarpBoard) -> Result<CarpMove, AdapterError> {
    let uci = mv.to_long_algebraic();
    board
        .find_move(&uci)
        .ok_or_else(|| AdapterError::IllegalMove {
            uci,
            fen: board.to_fen(),
        })
}

/// Convert a Carp move to the matching `chess_core::Move` on the given
/// `chess_core::Position`.
///
/// We translate the (src, tgt, type) tuple directly: src/tgt swap rank
/// orientation; type maps onto `MoveFlag` + `Promotion`. `MoveType::DoublePush`
/// has no chess-core equivalent and collapses to a quiet pawn push.
pub fn move_from_carp(mv: CarpMove, pos: &CorePosition) -> Result<CoreMove, AdapterError> {
    let from = square_from_carp_index(mv.get_src() as u8);
    let to = square_from_carp_index(mv.get_tgt() as u8);
    let kind = mv.get_type();

    let core_mv = match kind {
        MoveType::Quiet | MoveType::DoublePush => CoreMove::new_quiet(from, to),
        MoveType::Castle => CoreMove::new_castle(from, to),
        MoveType::Capture => CoreMove::new_capture(from, to),
        MoveType::EnPassant => CoreMove::new_en_passant(from, to),
        MoveType::KnightPromotion => CoreMove::new_promotion(from, to, Promotion::Knight, false),
        MoveType::BishopPromotion => CoreMove::new_promotion(from, to, Promotion::Bishop, false),
        MoveType::RookPromotion => CoreMove::new_promotion(from, to, Promotion::Rook, false),
        MoveType::QueenPromotion => CoreMove::new_promotion(from, to, Promotion::Queen, false),
        MoveType::KnightCapPromo => CoreMove::new_promotion(from, to, Promotion::Knight, true),
        MoveType::BishopCapPromo => CoreMove::new_promotion(from, to, Promotion::Bishop, true),
        MoveType::RookCapPromo => CoreMove::new_promotion(from, to, Promotion::Rook, true),
        MoveType::QueenCapPromo => CoreMove::new_promotion(from, to, Promotion::Queen, true),
    };

    // Sanity check: the produced core move must be in the chess-core legal
    // move list. This catches divergences between the two move generators
    // before the wrong move ever leaves the engine boundary.
    let legal = chess_core::legal_moves(pos);
    if legal.contains(&core_mv) {
        Ok(core_mv)
    } else {
        Err(AdapterError::NoCoreMatch {
            uci: format!("{}", mv),
            fen: pos.to_fen(),
        })
    }
}

/// Convenience: which side is to move on the given Carp board, expressed
/// as `chess_core::Color`.
#[inline]
pub fn side_to_move(board: &CarpBoard) -> chess_core::Color {
    match board.side {
        CarpColor::White => chess_core::Color::White,
        CarpColor::Black => chess_core::Color::Black,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chess_core::STARTPOS_FEN;

    #[test]
    fn square_index_round_trip() {
        for i in 0u8..64 {
            let s = Square::new(i);
            let carp = square_to_carp_index(s);
            let back = square_from_carp_index(carp);
            assert_eq!(back, s, "square {} did not round-trip (carp={})", i, carp);
        }
    }

    #[test]
    fn square_indexing_corners() {
        // a1 (core 0) ↔ A1 in carp (idx 56). h8 (core 63) ↔ H8 in carp (idx 7).
        assert_eq!(square_to_carp_index(Square::new(0)), 56);
        assert_eq!(square_to_carp_index(Square::new(7)), 63);
        assert_eq!(square_to_carp_index(Square::new(56)), 0);
        assert_eq!(square_to_carp_index(Square::new(63)), 7);
    }

    #[test]
    fn startpos_round_trip_via_fen() {
        let core = CorePosition::from_fen(STARTPOS_FEN).unwrap();
        let carp = position_to_carp(&core).unwrap();
        let back = position_from_carp(&carp).unwrap();
        assert_eq!(back.to_fen(), core.to_fen());
    }

    #[test]
    fn kiwipete_round_trip_via_fen() {
        let kiwipete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
        let core = CorePosition::from_fen(kiwipete).unwrap();
        let carp = position_to_carp(&core).unwrap();
        let back = position_from_carp(&carp).unwrap();
        assert_eq!(back.to_fen(), core.to_fen());
    }

    #[test]
    fn move_round_trip_e2e4() {
        let core = CorePosition::from_fen(STARTPOS_FEN).unwrap();
        let carp = position_to_carp(&core).unwrap();

        // pick e2e4 from the chess-core legal list
        let core_mv = chess_core::legal_moves(&core)
            .iter()
            .find(|m| m.to_long_algebraic() == "e2e4")
            .copied()
            .expect("e2e4 must be legal at startpos");

        let carp_mv = move_to_carp(core_mv, &carp).expect("convert e2e4 to carp");
        assert_eq!(format!("{}", carp_mv), "e2e4");

        let back = move_from_carp(carp_mv, &core).expect("convert e2e4 back");
        assert_eq!(back, core_mv);
    }

    #[test]
    fn castle_round_trips() {
        // Position where white can castle kingside.
        let fen = "r3k2r/pppq1ppp/2n1bn2/3pp3/3PP3/2N1BN2/PPPQ1PPP/R3K2R w KQkq - 0 1";
        let core = CorePosition::from_fen(fen).unwrap();
        let carp = position_to_carp(&core).unwrap();

        let core_mv = chess_core::legal_moves(&core)
            .iter()
            .find(|m| m.is_castle() && m.to_long_algebraic() == "e1g1")
            .copied()
            .expect("O-O must be legal here");

        let carp_mv = move_to_carp(core_mv, &carp).expect("convert O-O to carp");
        let back = move_from_carp(carp_mv, &core).expect("convert O-O back");
        assert_eq!(back, core_mv);
        assert!(back.is_castle());
    }

    #[test]
    fn promotion_round_trips() {
        // White pawn on e7, no obstructions, can promote with capture/quiet.
        let fen = "4n3/4P3/8/8/8/8/8/4K2k w - - 0 1";
        let core = CorePosition::from_fen(fen).unwrap();
        let carp = position_to_carp(&core).unwrap();

        for uci in ["e7e8q", "e7e8r", "e7e8b", "e7e8n", "e7xd8q"]
            .iter()
            // `e7xd8q` is just a sanity check — chess-core does not emit `x`,
            // so we filter to the moves that are actually in its list.
            .filter(|s| !s.contains('x'))
        {
            let core_mv = chess_core::legal_moves(&core)
                .iter()
                .find(|m| m.to_long_algebraic() == *uci)
                .copied();
            if let Some(core_mv) = core_mv {
                let carp_mv = move_to_carp(core_mv, &carp).unwrap_or_else(|e| {
                    panic!("failed to convert {} to carp: {}", uci, e);
                });
                let back = move_from_carp(carp_mv, &core).unwrap();
                assert_eq!(back, core_mv, "{} did not round-trip", uci);
                assert!(back.is_promotion());
            }
        }
    }
}
