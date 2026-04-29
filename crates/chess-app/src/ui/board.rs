//! Board widget — squares, pieces, last-move / selection / legal-target
//! highlights, click-click and drag-and-drop interaction (T035, T036,
//! T037, T039, T040).
//!
//! ### Coordinate model
//!
//! - `chess_core::Square` is `0..64`, with `0 = a1`, `7 = h1`,
//!   `56 = a8`, `63 = h8`. File `0..8 = a..h`; rank `0..8 = 1..8`.
//! - On screen, with `WhiteAtBottom` orientation we draw rank 8 at the
//!   top and file `a` on the left. With `BlackAtBottom` we mirror both
//!   axes.
//! - Tile size is computed from the available rect: the board is
//!   always square and centred horizontally inside its allocated area.
//!
//! ### Interaction
//!
//! Each frame the widget allocates a single `Sense::click_and_drag`
//! response over the whole 8×8 grid and converts pointer coordinates
//! into squares. The result of one frame is summarised as
//! [`BoardResponse`], which the [`crate::ui::game_screen`] caller uses
//! to drive game-state changes.

use chess_core::{Game, Move, Position, Promotion, Square};
use egui::{FontId, Pos2, Rect, Response, Sense, Ui, Vec2};

use crate::ui::theme::{draw_outline, draw_overlay, draw_piece, Palette};

/// Board orientation as displayed on screen.
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
pub enum Orientation {
    /// White pieces start at the bottom of the screen.
    WhiteAtBottom,
    /// Black pieces start at the bottom of the screen.
    BlackAtBottom,
}

/// Per-frame interaction summary returned by [`BoardWidget::show`].
#[derive(Clone, Debug, Default)]
pub struct BoardResponse {
    /// Square clicked this frame, if any. Click-click selection lives
    /// in [`BoardState::on_click`] — this is a raw signal.
    pub clicked: Option<Square>,
    /// `(from, to)` pair when the user just released a drag. The
    /// caller is responsible for validating legality and triggering
    /// the move.
    pub drag_release: Option<(Square, Square)>,
    /// The square currently under the cursor while a drag is active,
    /// used for crosshair highlighting.
    pub drag_hover: Option<Square>,
    /// Square the drag began on — set on `drag_started` and cleared
    /// on release.
    pub drag_origin: Option<Square>,
    /// Updated keyboard-focused square (T087).
    pub keyboard_focus: Option<Square>,
}

/// UI-side, *engine-independent* state for the board widget. Lives in
/// [`crate::ui::game_screen::GameScreen`].
#[derive(Clone, Debug, Default)]
pub struct BoardState {
    /// Currently-selected source square (click-click flow).
    pub selected: Option<Square>,
    /// Cached legal targets for `selected`. Re-derived from the live
    /// position each time [`BoardState::on_click`] runs.
    pub legal_targets: Vec<Square>,
    /// Last move played (highlighted on both from and to squares).
    pub last_move: Option<Move>,
    /// Active drag origin, cleared on release.
    pub drag_origin: Option<Square>,
    /// Keyboard-focused square used by Tab/arrow navigation (T087).
    pub keyboard_focus: Option<Square>,
    /// Whether the most recent click-click attempt was illegal — the
    /// game screen reads this to surface a transient toast.
    pub last_rejection: Option<String>,
}

impl BoardState {
    /// Click-click handler. Returns `Some(mv)` when the click completes
    /// a legal move *without* promotion ambiguity, `None` if the click
    /// only updated the selection (or a promotion modal is required —
    /// see [`detect_promotion_request`]).
    pub fn on_click(&mut self, game: &Game, sq: Square) -> ClickOutcome {
        // First click: select if it's our own piece.
        let stm = game.current.side_to_move;
        let on_sq = game.current.piece_on(sq);

        if let Some(from) = self.selected {
            // Second click.
            if from == sq {
                self.clear_selection();
                return ClickOutcome::Reselect;
            }
            // If we click a different own-piece, treat as new
            // selection.
            if on_sq.map(|p| p.color) == Some(stm) {
                self.set_selection(game, sq);
                return ClickOutcome::Reselect;
            }
            let target = sq;
            // Find a legal move from -> to (handle promotion).
            let candidates: Vec<Move> = chess_core::legal_moves(&game.current)
                .into_iter()
                .filter(|m| m.from() == from && m.to() == target)
                .collect();
            match candidates.len() {
                0 => {
                    self.last_rejection =
                        Some(rejection_reason(&game.current, from, target).to_string());
                    self.clear_selection();
                    ClickOutcome::Illegal
                }
                1 => {
                    let mv = candidates[0];
                    self.clear_selection();
                    if mv.is_promotion() {
                        ClickOutcome::PromotionRequired { from, to: target }
                    } else {
                        ClickOutcome::PlayMove(mv)
                    }
                }
                _ => {
                    // Multiple candidates ⇒ promotion choices.
                    self.clear_selection();
                    ClickOutcome::PromotionRequired { from, to: target }
                }
            }
        } else if on_sq.map(|p| p.color) == Some(stm) {
            self.set_selection(game, sq);
            ClickOutcome::Reselect
        } else {
            ClickOutcome::None
        }
    }

    /// Select `sq` and recompute its legal targets.
    pub fn set_selection(&mut self, game: &Game, sq: Square) {
        self.selected = Some(sq);
        self.legal_targets = chess_core::legal_moves(&game.current)
            .into_iter()
            .filter(|m| m.from() == sq)
            .map(|m| m.to())
            .collect();
        self.last_rejection = None;
    }

    /// Drop selection + cached targets.
    pub fn clear_selection(&mut self) {
        self.selected = None;
        self.legal_targets.clear();
    }
}

/// Result of a single click on the board.
#[derive(Clone, Debug)]
pub enum ClickOutcome {
    /// No relevant action (e.g., clicked an empty square with nothing
    /// selected).
    None,
    /// Selection changed, board state updated.
    Reselect,
    /// A legal non-promoting move was completed; play it.
    PlayMove(Move),
    /// The user tried an illegal move; a rejection reason is on
    /// `last_rejection`.
    Illegal,
    /// The user picked a promotion target; UI must open a modal and
    /// then call [`apply_promotion_choice`] when the user picks a
    /// piece.
    PromotionRequired {
        /// Origin square of the pawn.
        from: Square,
        /// Promotion target square.
        to: Square,
    },
}

/// After the user picks a promotion piece, look up the matching legal
/// move. Returns `None` only if the position changed underneath us
/// (defensive: should never happen in practice).
pub fn apply_promotion_choice(
    game: &Game,
    from: Square,
    to: Square,
    promo: Promotion,
) -> Option<Move> {
    chess_core::legal_moves(&game.current)
        .into_iter()
        .find(|m| m.from() == from && m.to() == to && m.promotion() == promo)
}

/// Whether the legal moves at this `from -> to` shape force a
/// promotion choice (i.e., would the click be ambiguous between
/// Q/R/B/N).
#[allow(dead_code)] // surface-level helper for keyboard-input flow (T087)
pub fn detect_promotion_request(pos: &Position, from: Square, to: Square) -> bool {
    chess_core::legal_moves(pos)
        .into_iter()
        .any(|m| m.from() == from && m.to() == to && m.is_promotion())
}

/// The actual board widget. Stateless across frames — the persistent
/// state lives in [`BoardState`] held by the parent screen.
pub struct BoardWidget<'a> {
    game: &'a Game,
    state: &'a BoardState,
    palette: &'a Palette,
    orientation: Orientation,
    /// True iff the human (whose-turn-it-is) is allowed to move.
    /// `false` blocks pointer interaction (engine is thinking, game is
    /// over, navigating history, etc.).
    interactive: bool,
    /// Optional hint move to render as an arrow overlay (T077).
    hint_move: Option<Move>,
}

impl<'a> BoardWidget<'a> {
    /// Construct a widget for a single frame.
    pub fn new(
        game: &'a Game,
        state: &'a BoardState,
        palette: &'a Palette,
        orientation: Orientation,
    ) -> Self {
        Self {
            game,
            state,
            palette,
            orientation,
            interactive: true,
            hint_move: None,
        }
    }

    /// Disable pointer interaction (still renders).
    pub fn interactive(mut self, on: bool) -> Self {
        self.interactive = on;
        self
    }

    /// Set the optional hint move to render as an arrow overlay (T077).
    pub fn with_hint_move(mut self, hint_move: Option<Move>) -> Self {
        self.hint_move = hint_move;
        self
    }

    /// Render and return the per-frame interaction summary.
    pub fn show(self, ui: &mut Ui) -> BoardResponse {
        let avail = ui.available_size();
        let side = avail.x.min(avail.y).max(64.0);
        let (rect, response) = ui.allocate_exact_size(
            Vec2::splat(side),
            if self.interactive {
                Sense::click_and_drag()
            } else {
                Sense::hover()
            },
        );

        // Slight inset so the labels can fit. Keep ~6% of side as a
        // border margin.
        let margin = (side * 0.04).floor().max(8.0);
        let board_side = side - 2.0 * margin;
        let tile = board_side / 8.0;
        let board_origin = rect.min + Vec2::splat(margin);
        let board_rect = Rect::from_min_size(board_origin, Vec2::splat(board_side));

        let painter = ui.painter_at(rect);

        // Outer border.
        painter.rect_filled(rect, 0.0, self.palette.board_border);
        painter.rect_filled(board_rect, 0.0, self.palette.light_square);

        // Squares.
        for raw in 0..64u8 {
            let sq = Square::new(raw);
            let r = square_rect(sq, self.orientation, board_origin, tile);
            let dark = ((sq.file() + sq.rank()) & 1) == 0;
            let color = if dark {
                self.palette.dark_square
            } else {
                self.palette.light_square
            };
            painter.rect_filled(r, 0.0, color);
        }

        // Last-move highlight (T039).
        if let Some(mv) = self.state.last_move {
            for sq in [mv.from(), mv.to()] {
                let r = square_rect(sq, self.orientation, board_origin, tile);
                draw_overlay(&painter, r, self.palette.last_move);
            }
        }

        // Selection + legal targets (T039).
        if let Some(sel) = self.state.selected {
            let r = square_rect(sel, self.orientation, board_origin, tile);
            draw_overlay(&painter, r, self.palette.selection);
            for tgt in &self.state.legal_targets {
                let tr = square_rect(*tgt, self.orientation, board_origin, tile);
                let center = tr.center();
                // Capture targets get a hollow ring; quiet targets get
                // a solid centre dot. We classify by checking whether
                // the target square is occupied by an opposite-colour
                // piece.
                let opp = self.game.current.side_to_move.opp();
                let is_capture = self.game.current.piece_on(*tgt).map(|p| p.color) == Some(opp);
                if is_capture {
                    painter.circle_stroke(
                        center,
                        tile * 0.42,
                        egui::Stroke::new(tile * 0.06, self.palette.legal_target),
                    );
                } else {
                    painter.circle_filled(center, tile * 0.12, self.palette.legal_target);
                }
            }
        }

        // Hint arrow overlay (T077). Draw semi-transparent arrow from
        // source to destination square.
        if let Some(hint_mv) = self.hint_move {
            let from_rect = square_rect(hint_mv.from(), self.orientation, board_origin, tile);
            let to_rect = square_rect(hint_mv.to(), self.orientation, board_origin, tile);
            let from_center = from_rect.center();
            let to_center = to_rect.center();
            
            // Draw arrow shaft (semi-transparent green line)
            let arrow_color = egui::Color32::from_rgba_unmultiplied(100, 200, 100, 150);
            let arrow_stroke = egui::Stroke::new(tile * 0.08, arrow_color);
            painter.line_segment([from_center, to_center], arrow_stroke);
            
            // Draw arrow head at destination
            let direction = (to_center - from_center).normalized();
            let perpendicular = Vec2::new(-direction.y, direction.x);
            let arrow_size = tile * 0.15;
            let head_base = to_center - direction * arrow_size;
            let head_left = head_base + perpendicular * (arrow_size * 0.5);
            let head_right = head_base - perpendicular * (arrow_size * 0.5);
            
            painter.line_segment([to_center, head_left], arrow_stroke);
            painter.line_segment([to_center, head_right], arrow_stroke);
        }

        // Check / mate glow on king-square (T040).
        if !matches!(self.game.result(), Some(_)) && chess_core::is_in_check(&self.game.current) {
            let king_sq = self.game.current.king_square(self.game.current.side_to_move);
            let r = square_rect(king_sq, self.orientation, board_origin, tile);
            draw_overlay(&painter, r, self.palette.check_glow);
        } else if matches!(
            self.game.result(),
            Some(chess_core::GameResult::Checkmate(_))
        ) {
            // After mate, side-to-move has no legal moves and is in
            // check; highlight that king to make the terminal state
            // visually obvious.
            let king_sq = self.game.current.king_square(self.game.current.side_to_move);
            let r = square_rect(king_sq, self.orientation, board_origin, tile);
            draw_overlay(&painter, r, self.palette.check_glow);
        }

        // Pieces.
        for raw in 0..64u8 {
            let sq = Square::new(raw);
            // Skip the dragged piece — we draw it under the cursor at
            // the end so it floats above other tiles.
            if Some(sq) == self.state.drag_origin {
                continue;
            }
            if let Some(piece) = self.game.current.piece_on(sq) {
                let r = square_rect(sq, self.orientation, board_origin, tile);
                draw_piece(&painter, self.palette, piece.color, piece.kind, r);
            }
        }

        // File/rank labels (drawn in the border margin).
        for f in 0..8u8 {
            let label = (b'a' + f) as char;
            let display_file = match self.orientation {
                Orientation::WhiteAtBottom => f,
                Orientation::BlackAtBottom => 7 - f,
            };
            let x = board_origin.x + (display_file as f32 + 0.5) * tile;
            let y = rect.bottom() - margin * 0.5;
            painter.text(
                Pos2::new(x, y),
                egui::Align2::CENTER_CENTER,
                label,
                FontId::proportional(margin * 0.7),
                self.palette.label,
            );
        }
        for r in 0..8u8 {
            let label = (b'1' + r) as char;
            let display_rank = match self.orientation {
                Orientation::WhiteAtBottom => 7 - r,
                Orientation::BlackAtBottom => r,
            };
            let x = rect.left() + margin * 0.5;
            let y = board_origin.y + (display_rank as f32 + 0.5) * tile;
            painter.text(
                Pos2::new(x, y),
                egui::Align2::CENTER_CENTER,
                label,
                FontId::proportional(margin * 0.7),
                self.palette.label,
            );
        }

        // Compute interaction response.
        let mut out = BoardResponse::default();
        let pointer_sq = response
            .interact_pointer_pos()
            .or_else(|| response.hover_pos())
            .and_then(|pos| pos_to_square(pos, board_origin, tile, self.orientation));

        if response.clicked() {
            response.request_focus();
            out.clicked = pointer_sq;
            out.keyboard_focus = pointer_sq.or(self.state.keyboard_focus);
        }

        if self.interactive && response.has_focus() {
            let mut focus = self
                .state
                .keyboard_focus
                .or(pointer_sq)
                .or_else(|| default_focus_square(self.game));

            if let Some(current) = focus {
                let mut next = current;
                if ui.input(|i| i.key_pressed(egui::Key::ArrowLeft)) {
                    next = shift_focus(current, self.orientation, egui::Key::ArrowLeft);
                } else if ui.input(|i| i.key_pressed(egui::Key::ArrowRight)) {
                    next = shift_focus(current, self.orientation, egui::Key::ArrowRight);
                } else if ui.input(|i| i.key_pressed(egui::Key::ArrowUp)) {
                    next = shift_focus(current, self.orientation, egui::Key::ArrowUp);
                } else if ui.input(|i| i.key_pressed(egui::Key::ArrowDown)) {
                    next = shift_focus(current, self.orientation, egui::Key::ArrowDown);
                }

                if next != current {
                    focus = Some(next);
                }

                if ui.input(|i| i.key_pressed(egui::Key::Space)) && out.clicked.is_none() {
                    out.clicked = focus;
                }

                if let Some(fs) = focus {
                    let fr = square_rect(fs, self.orientation, board_origin, tile).shrink(tile * 0.06);
                    draw_outline(&painter, fr, self.palette.selection, tile * 0.07);
                    out.keyboard_focus = Some(fs);
                }
            }
        }

        if !self.interactive {
            return out;
        }

        if response.drag_started() {
            if let Some(sq) = pointer_sq {
                let stm = self.game.current.side_to_move;
                if self.game.current.piece_on(sq).map(|p| p.color) == Some(stm) {
                    out.drag_origin = Some(sq);
                }
            }
        }

        if response.dragged() {
            out.drag_hover = pointer_sq;
            // Draw the floating dragged piece if we have an origin.
            if let Some(origin) = self.state.drag_origin {
                if let Some(piece) = self.game.current.piece_on(origin) {
                    if let Some(pos) = response.hover_pos() {
                        let drag_rect = Rect::from_center_size(pos, Vec2::splat(tile));
                        draw_piece(&painter, self.palette, piece.color, piece.kind, drag_rect);
                    }
                }
            }
        }

        if response.drag_stopped() {
            if let (Some(origin), Some(release)) = (self.state.drag_origin, pointer_sq) {
                if origin != release {
                    out.drag_release = Some((origin, release));
                }
            }
        }

        out.drag_hover = out.drag_hover.or(pointer_sq);

        out
    }

    /// Expose the underlying `Response` for callers that need
    /// keyboard-focus integration. (T087.)
    pub fn _expose_response(&self) -> Option<Response> {
        None
    }
}

/// Build a tight `Rect` around the on-screen position of `sq`.
fn square_rect(sq: Square, orientation: Orientation, board_origin: Pos2, tile: f32) -> Rect {
    let (col, row) = match orientation {
        Orientation::WhiteAtBottom => (sq.file() as f32, (7 - sq.rank()) as f32),
        Orientation::BlackAtBottom => ((7 - sq.file()) as f32, sq.rank() as f32),
    };
    Rect::from_min_size(
        board_origin + Vec2::new(col * tile, row * tile),
        Vec2::splat(tile),
    )
}

/// Convert a screen position back to a `Square`, or `None` if it is
/// outside the 8×8 board.
fn pos_to_square(
    pos: Pos2,
    board_origin: Pos2,
    tile: f32,
    orientation: Orientation,
) -> Option<Square> {
    let local = pos - board_origin;
    if local.x < 0.0 || local.y < 0.0 {
        return None;
    }
    let col = (local.x / tile).floor() as i32;
    let row = (local.y / tile).floor() as i32;
    if !(0..8).contains(&col) || !(0..8).contains(&row) {
        return None;
    }
    let (file, rank) = match orientation {
        Orientation::WhiteAtBottom => (col as u8, 7 - row as u8),
        Orientation::BlackAtBottom => (7 - col as u8, row as u8),
    };
    Some(Square::from_file_rank(file, rank))
}

fn default_focus_square(game: &Game) -> Option<Square> {
    let side = game.current.side_to_move;
    let preferred = match side {
        chess_core::Color::White => Square::from_algebraic("e2"),
        chess_core::Color::Black => Square::from_algebraic("e7"),
    };
    if let Some(sq) = preferred {
        if game.current.piece_on(sq).map(|p| p.color) == Some(side) {
            return Some(sq);
        }
    }
    Some(game.current.king_square(side))
}

fn shift_focus(current: Square, orientation: Orientation, key: egui::Key) -> Square {
    let (mut x, mut y) = square_to_screen_xy(current, orientation);
    match key {
        egui::Key::ArrowLeft => x -= 1,
        egui::Key::ArrowRight => x += 1,
        egui::Key::ArrowUp => y -= 1,
        egui::Key::ArrowDown => y += 1,
        _ => {}
    }
    x = x.clamp(0, 7);
    y = y.clamp(0, 7);
    screen_xy_to_square(x as u8, y as u8, orientation)
}

fn square_to_screen_xy(sq: Square, orientation: Orientation) -> (i32, i32) {
    match orientation {
        Orientation::WhiteAtBottom => (sq.file() as i32, 7 - sq.rank() as i32),
        Orientation::BlackAtBottom => (7 - sq.file() as i32, sq.rank() as i32),
    }
}

fn screen_xy_to_square(x: u8, y: u8, orientation: Orientation) -> Square {
    let (file, rank) = match orientation {
        Orientation::WhiteAtBottom => (x, 7 - y),
        Orientation::BlackAtBottom => (7 - x, y),
    };
    Square::from_file_rank(file, rank)
}

/// Best-effort human-readable rejection reason for an illegal move
/// attempt, per [contracts/ui-interactions.md §2.1].
pub fn rejection_reason(pos: &Position, from: Square, to: Square) -> &'static str {
    let stm = pos.side_to_move;
    let from_piece = pos.piece_on(from);

    match from_piece {
        None => "no piece on source square",
        Some(p) if p.color != stm => "that piece is not yours",
        Some(p) => {
            // Same-colour destination?
            if pos.piece_on(to).map(|x| x.color) == Some(stm) {
                return "blocked by your own piece";
            }
            // Would-leave-king-in-check is the most common rejection.
            // Probe a pseudo-move via a candidate; if any candidate
            // exists at all (including any promotion variant) but none
            // is legal, the king must be at risk.
            let any_to_target = chess_core::legal_moves(pos)
                .into_iter()
                .any(|m| m.from() == from && m.to() == to);
            if !any_to_target {
                if matches!(p.kind, chess_core::PieceType::King) {
                    if (from.file() as i8 - to.file() as i8).abs() == 2 {
                        return "cannot castle (rights, blocked, or through check)";
                    }
                    return "king cannot move into check";
                }
                return "would leave king in check or piece cannot reach there";
            }
            "illegal move"
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chess_core::{Color, GameMode, Position};

    #[test]
    fn pos_to_square_round_trip() {
        let origin = Pos2::new(0.0, 0.0);
        let tile = 64.0;
        for f in 0..8u8 {
            for r in 0..8u8 {
                let sq = Square::from_file_rank(f, r);
                let rect = square_rect(sq, Orientation::WhiteAtBottom, origin, tile);
                let centre = rect.center();
                let back = pos_to_square(centre, origin, tile, Orientation::WhiteAtBottom).unwrap();
                assert_eq!(back, sq);
            }
        }
    }

    #[test]
    fn pos_to_square_returns_none_outside_board() {
        assert!(pos_to_square(
            Pos2::new(-1.0, 0.0),
            Pos2::new(0.0, 0.0),
            64.0,
            Orientation::WhiteAtBottom
        )
        .is_none());
        assert!(pos_to_square(
            Pos2::new(64.0 * 9.0, 0.0),
            Pos2::new(0.0, 0.0),
            64.0,
            Orientation::WhiteAtBottom
        )
        .is_none());
    }

    #[test]
    fn click_white_pawn_then_e4_yields_play_move() {
        let g = Game::new_game(GameMode::HumanVsAi(Color::White));
        let mut state = BoardState::default();
        let e2 = Square::from_algebraic("e2").unwrap();
        let e4 = Square::from_algebraic("e4").unwrap();
        match state.on_click(&g, e2) {
            ClickOutcome::Reselect => {}
            o => panic!("expected Reselect, got {o:?}"),
        }
        assert_eq!(state.selected, Some(e2));
        assert!(state.legal_targets.contains(&e4));

        match state.on_click(&g, e4) {
            ClickOutcome::PlayMove(m) => {
                assert_eq!(m.from(), e2);
                assert_eq!(m.to(), e4);
            }
            o => panic!("expected PlayMove, got {o:?}"),
        }
        assert!(state.selected.is_none());
    }

    #[test]
    fn click_illegal_target_records_rejection() {
        let g = Game::new_game(GameMode::HumanVsAi(Color::White));
        let mut state = BoardState::default();
        let e2 = Square::from_algebraic("e2").unwrap();
        let e5 = Square::from_algebraic("e5").unwrap();
        let _ = state.on_click(&g, e2);
        match state.on_click(&g, e5) {
            ClickOutcome::Illegal => {
                assert!(state.last_rejection.is_some());
            }
            o => panic!("expected Illegal, got {o:?}"),
        }
    }

    #[test]
    fn detect_promotion_request_recognises_pawn_to_eighth() {
        let pos = Position::from_fen("8/P7/8/8/8/8/8/k6K w - - 0 1").unwrap();
        let from = Square::from_algebraic("a7").unwrap();
        let to = Square::from_algebraic("a8").unwrap();
        assert!(detect_promotion_request(&pos, from, to));
    }

    #[test]
    fn shift_focus_honours_orientation() {
        let e2 = Square::from_algebraic("e2").unwrap();
        let right_white = shift_focus(e2, Orientation::WhiteAtBottom, egui::Key::ArrowRight);
        assert_eq!(right_white, Square::from_algebraic("f2").unwrap());

        let right_black = shift_focus(e2, Orientation::BlackAtBottom, egui::Key::ArrowRight);
        assert_eq!(right_black, Square::from_algebraic("d2").unwrap());
    }
}
