//! Game screen — board on the left, side panel on the right (T041).
//!
//! Owns the [`Game`] state machine, [`crate::engine_link::EngineLink`]
//! handle, and a [`crate::ui::board::BoardState`]. Renders:
//!
//! - The 8×8 board widget ([`crate::ui::board`]).
//! - A right side panel with: side-to-move indicator, engine status,
//!   captured-piece tray (T041a), New Game / Resign buttons,
//!   transient status / rejection messages.
//! - Confirm modals for New Game (T042) and Resign (T043).
//! - Promotion modal (T038).
//! - Game-over modal (T046).
//!
//! Engine-move triggering (T044) is implemented in [`GameScreen::tick_engine`],
//! which is called once per frame by [`GameScreen::update`].

use std::time::{Duration, Instant};

use chess_core::{legal_moves, Color, DrawReason, Game, GameMode, GameResult, Move, PieceType};
use chess_engine::{EngineConfig, Eval, Event, Score, StrengthPreset, TimeControl};
use egui::{Align, Layout, RichText, Vec2};

use crate::engine_link::{render_status, EngineLink, EngineStatus};
use crate::settings::UserSettings;
use crate::ui::board::{
    apply_promotion_choice, rejection_reason, BoardResponse, BoardState, BoardWidget,
    ClickOutcome, Orientation,
};
use crate::ui::promotion::{show_promotion_modal, PromotionOutcome, PromotionRequest};
use crate::ui::theme::{draw_mini_piece, Palette};

/// Top-level game screen. Held by `crate::ui::App`.
pub struct GameScreen {
    /// Pure-data game state (history, repetition table, terminal
    /// flag).
    pub game: Game,
    /// Cached board-side UI state.
    pub board_state: BoardState,
    /// Active palette. v1 ships `Palette::STANDARD`; T086 adds Dark /
    /// HighContrast.
    pub palette: Palette,
    /// Live board orientation.
    pub orientation: Orientation,
    /// Promotion modal state, if any.
    pub promotion: Option<PromotionRequest>,
    /// New Game confirm modal flag.
    pub confirm_new_game: bool,
    /// Resign confirm modal flag.
    pub confirm_resign: bool,
    /// Game-over modal — displayed once per terminal state until
    /// dismissed.
    pub show_game_over: bool,
    /// Most recent transient toast (rejection reason). Cleared after
    /// `toast_until`.
    pub toast: Option<String>,
    /// Wall-clock deadline for the current toast.
    pub toast_until: Option<Instant>,
    /// True if we have already started thinking on the current
    /// position. Prevents firing repeated `StartSearch` commands.
    pub engine_searching: bool,
    /// Snapshot of the user's settings — read at construction; the
    /// settings screen (CP-F / US2) is responsible for re-flowing
    /// this value if the user changes it mid-session.
    pub settings: UserSettings,
    /// Latched once per frame when the user clicks Settings… in the
    /// menu or presses Ctrl+, (T059). The host (`ChessApp`) drains
    /// this via [`GameScreen::take_open_settings_request`] and swaps
    /// to the [`crate::ui::SettingsScreen`].
    pub open_settings_requested: bool,
}

impl GameScreen {
    /// Create a new game screen with default state from `settings`.
    pub fn new(settings: UserSettings) -> Self {
        let initial_color = match settings.ui.last_human_color {
            crate::settings::HumanColor::White => Color::White,
            crate::settings::HumanColor::Black => Color::Black,
        };
        Self {
            game: Game::new_game(GameMode::HumanVsAi(initial_color)),
            board_state: BoardState::default(),
            palette: Palette::STANDARD,
            orientation: orientation_for(initial_color, &settings),
            promotion: None,
            confirm_new_game: false,
            confirm_resign: false,
            show_game_over: false,
            toast: None,
            toast_until: None,
            engine_searching: false,
            settings,
            open_settings_requested: false,
        }
    }

    /// Per-frame entry point. Drains engine events, renders all
    /// widgets, kicks the engine if it is the AI's turn.
    pub fn update(&mut self, ctx: &egui::Context, engine: &mut EngineLink) {
        // Drain engine events first so the rest of the frame sees the
        // latest state.
        for event in engine.tick() {
            self.absorb_engine_event(event);
        }

        // Ctrl+, opens the settings screen (T059).
        let settings_hotkey = ctx.input(|i| {
            i.modifiers.command_only() && i.key_pressed(egui::Key::Comma)
        });
        if settings_hotkey {
            self.open_settings_requested = true;
        }

        // Top menu bar with File ▸ Settings… (T059).
        egui::TopBottomPanel::top("game_menu").show(ctx, |ui| {
            egui::menu::bar(ui, |ui| {
                ui.menu_button("File", |ui| {
                    if ui
                        .button("Settings…\tCtrl+,")
                        .clicked()
                    {
                        self.open_settings_requested = true;
                        ui.close_menu();
                    }
                });
            });
        });

        // Expire toasts.
        if let Some(deadline) = self.toast_until {
            if Instant::now() >= deadline {
                self.toast = None;
                self.toast_until = None;
            }
        }

        // Detect terminal state and surface modal.
        if self.game.result().is_some() && !self.show_game_over {
            self.show_game_over = true;
        }

        // Render layout: panel right, board centre/left.
        egui::SidePanel::right("right_panel")
            .resizable(false)
            .min_width(260.0)
            .max_width(360.0)
            .show(ctx, |ui| self.render_panel(ui, engine));

        egui::CentralPanel::default().show(ctx, |ui| {
            ui.horizontal_wrapped(|ui| {
                let board = BoardWidget::new(
                    &self.game,
                    &self.board_state,
                    &self.palette,
                    self.orientation,
                )
                .interactive(self.is_human_turn() && self.promotion.is_none());
                let resp = board.show(ui);
                self.handle_board_response(resp);
            });
        });

        // Promotion / confirm / game-over modals.
        if let Some(req) = self.promotion.clone() {
            match show_promotion_modal(ctx, &req) {
                PromotionOutcome::Pending => {}
                PromotionOutcome::Picked(promo) => {
                    if let Some(mv) = apply_promotion_choice(&self.game, req.from, req.to, promo) {
                        self.try_play_human_move(mv);
                    }
                    self.promotion = None;
                }
                PromotionOutcome::Cancelled => {
                    self.promotion = None;
                }
            }
        }

        if self.confirm_new_game {
            self.render_confirm_new_game(ctx);
        }
        if self.confirm_resign {
            self.render_confirm_resign(ctx);
        }
        if self.show_game_over {
            self.render_game_over_modal(ctx);
        }

        // Trigger engine if appropriate.
        self.tick_engine(engine);

        // Keep repainting while the engine is searching so progress
        // events surface promptly without waiting for input.
        if engine.status().is_thinking() {
            ctx.request_repaint_after(Duration::from_millis(80));
        }
    }

    fn render_panel(&mut self, ui: &mut egui::Ui, engine: &mut EngineLink) {
        ui.add_space(8.0);
        ui.heading("chess-ai");
        ui.add_space(4.0);
        ui.separator();

        // Side to move + engine status.
        ui.add_space(8.0);
        ui.label(RichText::new("Side to move").strong());
        let stm_text = match self.game.side_to_move() {
            Color::White => "White",
            Color::Black => "Black",
        };
        ui.label(stm_text);

        ui.add_space(6.0);
        ui.label(RichText::new("Engine").strong());
        ui.label(render_status(engine.status()));

        // Captured pieces tray (T041a).
        ui.add_space(10.0);
        ui.separator();
        ui.add_space(6.0);
        ui.label(RichText::new("Captured").strong());
        let captured = compute_captured(&self.game);
        self.render_captured_row(ui, Color::White, &captured.white_lost, captured.material);
        self.render_captured_row(ui, Color::Black, &captured.black_lost, -captured.material);

        ui.add_space(10.0);
        ui.separator();
        ui.add_space(6.0);

        // Move list (compact for v1; CP-G adds proper navigation).
        ui.label(RichText::new("Moves").strong());
        egui::ScrollArea::vertical()
            .max_height(220.0)
            .auto_shrink([false; 2])
            .show(ui, |ui| {
                self.render_move_list(ui);
            });

        ui.add_space(10.0);
        ui.separator();
        ui.add_space(6.0);

        // Buttons.
        ui.with_layout(Layout::top_down(Align::Min), |ui| {
            if ui.button("New Game").clicked() {
                if self.game.history.is_empty() || self.game.result().is_some() {
                    self.start_new_game();
                } else {
                    self.confirm_new_game = true;
                }
            }
            ui.add_space(4.0);
            let resign_enabled = self.game.result().is_none();
            if ui
                .add_enabled(resign_enabled, egui::Button::new("Resign"))
                .clicked()
            {
                self.confirm_resign = true;
            }
            ui.add_space(4.0);
            if engine.status().is_thinking() {
                if ui.button("Stop thinking").clicked() {
                    engine.stop();
                }
            }
        });

        // Toast / rejection message area.
        ui.add_space(10.0);
        if let Some(msg) = &self.toast {
            ui.colored_label(egui::Color32::from_rgb(0xE0, 0x6C, 0x6C), msg);
        } else if let Some(reason) = self.board_state.last_rejection.clone() {
            self.set_toast(reason);
        }
    }

    fn render_captured_row(
        &self,
        ui: &mut egui::Ui,
        color_lost: Color,
        captured_kinds: &[PieceType],
        delta: i32,
    ) {
        let row_height = 22.0;
        let (rect, _) = ui.allocate_exact_size(
            Vec2::new(ui.available_width(), row_height),
            egui::Sense::hover(),
        );
        let painter = ui.painter_at(rect);
        let label = match color_lost {
            Color::White => "White lost:",
            Color::Black => "Black lost:",
        };
        painter.text(
            rect.left_top() + Vec2::new(2.0, 4.0),
            egui::Align2::LEFT_TOP,
            label,
            egui::FontId::proportional(11.0),
            self.palette.label,
        );

        let mut x = rect.left() + 86.0;
        let y = rect.center().y;
        for kind in captured_kinds {
            draw_mini_piece(
                &painter,
                &self.palette,
                color_lost,
                *kind,
                egui::pos2(x, y),
                row_height * 0.4,
            );
            x += row_height * 0.85;
        }

        // Material differential, if positive.
        if delta > 0 {
            let opp = match color_lost {
                Color::White => "Black",
                Color::Black => "White",
            };
            painter.text(
                egui::pos2(rect.right() - 4.0, y),
                egui::Align2::RIGHT_CENTER,
                format!("+{} {}", delta, opp),
                egui::FontId::proportional(11.0),
                self.palette.label,
            );
        }
    }

    fn render_move_list(&self, ui: &mut egui::Ui) {
        let plies = &self.game.history;
        if plies.is_empty() {
            ui.weak("(no moves yet)");
            return;
        }
        let mut iter = plies.iter().enumerate();
        loop {
            let Some((i, white)) = iter.next() else {
                break;
            };
            let move_no = (i / 2) + 1;
            let white_san = if white.san.is_empty() {
                white.move_played.to_long_algebraic()
            } else {
                white.san.clone()
            };
            let black_san = match iter.next() {
                Some((_, black)) => {
                    if black.san.is_empty() {
                        black.move_played.to_long_algebraic()
                    } else {
                        black.san.clone()
                    }
                }
                None => String::new(),
            };
            ui.horizontal(|ui| {
                ui.label(RichText::new(format!("{move_no}.")).weak());
                ui.label(white_san);
                if !black_san.is_empty() {
                    ui.label(black_san);
                }
            });
        }
    }

    fn render_confirm_new_game(&mut self, ctx: &egui::Context) {
        let mut close = false;
        let mut confirmed = false;
        egui::Window::new("Start a new game?")
            .anchor(egui::Align2::CENTER_CENTER, Vec2::ZERO)
            .collapsible(false)
            .resizable(false)
            .show(ctx, |ui| {
                ui.label("The current game will be discarded. Continue?");
                ui.add_space(6.0);
                ui.horizontal(|ui| {
                    if ui.button("Yes — new game").clicked() {
                        confirmed = true;
                    }
                    if ui.button("Cancel").clicked() {
                        close = true;
                    }
                });
            });
        if confirmed {
            self.start_new_game();
            self.confirm_new_game = false;
        } else if close {
            self.confirm_new_game = false;
        }
    }

    fn render_confirm_resign(&mut self, ctx: &egui::Context) {
        let mut close = false;
        let mut confirmed = false;
        egui::Window::new("Resign?")
            .anchor(egui::Align2::CENTER_CENTER, Vec2::ZERO)
            .collapsible(false)
            .resizable(false)
            .show(ctx, |ui| {
                ui.label("Resign this game? This cannot be undone.");
                ui.add_space(6.0);
                ui.horizontal(|ui| {
                    if ui.button("Yes — resign").clicked() {
                        confirmed = true;
                    }
                    if ui.button("Cancel").clicked() {
                        close = true;
                    }
                });
            });
        if confirmed {
            let me = match self.game.mode {
                GameMode::HumanVsAi(c) => c,
                GameMode::AiVsAi => Color::White,
            };
            self.game.result = Some(GameResult::Resignation(me.opp()));
            self.show_game_over = true;
            self.confirm_resign = false;
        } else if close {
            self.confirm_resign = false;
        }
    }

    fn render_game_over_modal(&mut self, ctx: &egui::Context) {
        let Some(result) = self.game.result() else {
            self.show_game_over = false;
            return;
        };
        let title = "Game over";
        let body = match result {
            GameResult::Checkmate(winner) => format!(
                "Checkmate — {} wins.",
                match winner {
                    Color::White => "White",
                    Color::Black => "Black",
                }
            ),
            GameResult::Resignation(winner) => format!(
                "Resignation — {} wins.",
                match winner {
                    Color::White => "White",
                    Color::Black => "Black",
                }
            ),
            GameResult::Draw(reason) => match reason {
                DrawReason::Stalemate => "Draw — stalemate.".to_string(),
                DrawReason::FiftyMoveRule => "Draw — 50-move rule.".to_string(),
                DrawReason::ThreefoldRepetition => "Draw — threefold repetition.".to_string(),
                DrawReason::InsufficientMaterial => "Draw — insufficient material.".to_string(),
                DrawReason::AgreedDraw => "Draw — agreed.".to_string(),
            },
        };

        let mut dismiss = false;
        let mut new_game = false;
        egui::Window::new(title)
            .anchor(egui::Align2::CENTER_CENTER, Vec2::ZERO)
            .collapsible(false)
            .resizable(false)
            .show(ctx, |ui| {
                ui.label(body);
                ui.add_space(6.0);
                ui.horizontal(|ui| {
                    if ui.button("New Game").clicked() {
                        new_game = true;
                    }
                    if ui.button("Close").clicked() {
                        dismiss = true;
                    }
                });
            });
        if new_game {
            self.start_new_game();
            self.show_game_over = false;
        } else if dismiss {
            self.show_game_over = false;
        }
    }

    fn handle_board_response(&mut self, resp: BoardResponse) {
        // Click handling (click-click flow, T036).
        if let Some(sq) = resp.clicked {
            if !self.is_human_turn() || self.promotion.is_some() || self.game.result().is_some() {
                return;
            }
            let outcome = self.board_state.on_click(&self.game, sq);
            self.apply_click_outcome(outcome);
        }

        // Drag-and-drop (T037).
        if let Some(origin) = resp.drag_origin {
            if self.is_human_turn() && self.promotion.is_none() && self.game.result().is_none() {
                self.board_state.drag_origin = Some(origin);
                // Mirror drag origin into selection so legal targets
                // get rendered as the user drags.
                self.board_state.set_selection(&self.game, origin);
            }
        }
        if let Some((from, to)) = resp.drag_release {
            self.board_state.drag_origin = None;
            self.board_state.clear_selection();
            if !self.is_human_turn() || self.game.result().is_some() {
                return;
            }
            let candidates: Vec<Move> = legal_moves(&self.game.current)
                .into_iter()
                .filter(|m| m.from() == from && m.to() == to)
                .collect();
            match candidates.len() {
                0 => {
                    self.set_toast(
                        rejection_reason(&self.game.current, from, to).to_string(),
                    );
                }
                1 if !candidates[0].is_promotion() => {
                    let mv = candidates[0];
                    self.try_play_human_move(mv);
                }
                _ => {
                    self.promotion = Some(PromotionRequest {
                        from,
                        to,
                        mover: self.game.current.side_to_move,
                    });
                }
            }
        }
    }

    fn apply_click_outcome(&mut self, outcome: ClickOutcome) {
        match outcome {
            ClickOutcome::None | ClickOutcome::Reselect => {}
            ClickOutcome::PlayMove(mv) => {
                self.try_play_human_move(mv);
            }
            ClickOutcome::Illegal => {
                if let Some(reason) = self.board_state.last_rejection.clone() {
                    self.set_toast(reason);
                }
            }
            ClickOutcome::PromotionRequired { from, to } => {
                self.promotion = Some(PromotionRequest {
                    from,
                    to,
                    mover: self.game.current.side_to_move,
                });
            }
        }
    }

    fn try_play_human_move(&mut self, mv: Move) {
        match self.game.make_move(mv) {
            Ok(()) => {
                self.board_state.last_move = Some(mv);
                self.board_state.last_rejection = None;
                self.engine_searching = false;
            }
            Err(e) => {
                self.set_toast(e.to_string());
            }
        }
    }

    fn tick_engine(&mut self, engine: &mut EngineLink) {
        if self.game.result().is_some() {
            return;
        }
        if self.engine_searching {
            return;
        }
        if self.is_human_turn() {
            return;
        }
        // It's the engine's turn — fire SetPosition + StartSearch.
        let history: Vec<Move> = self.game.history.iter().map(|r| r.move_played).collect();
        engine.set_position(self.game.current, history);
        let cfg = self.engine_config_for_play();
        engine.start_search(cfg);
        self.engine_searching = true;
    }

    fn engine_config_for_play(&self) -> EngineConfig {
        EngineConfig {
            mode: chess_engine::Mode::Normal,
            time_control: self.settings.engine_time_control(),
            strength: self.settings.engine.default_strength,
            max_threads: self.settings.engine.max_threads,
            tt_size_mib: 16,
        }
    }

    fn absorb_engine_event(&mut self, event: Event) {
        match event {
            Event::SearchComplete(result) => {
                self.engine_searching = false;
                if !self.is_human_turn() && self.game.result().is_none() {
                    if let Err(e) = self.game.make_move(result.mv) {
                        self.set_toast(format!("engine returned illegal move: {e}"));
                    } else {
                        self.board_state.last_move = Some(result.mv);
                    }
                }
            }
            Event::SearchAborted => {
                self.engine_searching = false;
            }
            _ => {}
        }
    }

    fn start_new_game(&mut self) {
        let me = match self.game.mode {
            GameMode::HumanVsAi(c) => c,
            GameMode::AiVsAi => Color::White,
        };
        self.game = Game::new_game(GameMode::HumanVsAi(me));
        self.board_state = BoardState::default();
        self.engine_searching = false;
        self.show_game_over = false;
        self.toast = None;
        self.promotion = None;
    }

    fn is_human_turn(&self) -> bool {
        match self.game.mode {
            GameMode::HumanVsAi(c) => self.game.current.side_to_move == c,
            GameMode::AiVsAi => false,
        }
    }

    fn set_toast(&mut self, msg: String) {
        self.toast = Some(msg);
        self.toast_until = Some(Instant::now() + Duration::from_secs(3));
    }

    /// True if the user requested the settings screen this frame.
    /// Consumes the flag so the host only switches once per click.
    /// (T059)
    pub fn take_open_settings_request(&mut self) -> bool {
        std::mem::replace(&mut self.open_settings_requested, false)
    }

    /// Adopt updated settings handed back by the settings screen.
    /// Re-applies any view-side state derived from settings (e.g.,
    /// board orientation in `Auto` mode). The settings file itself is
    /// already on disk by the time the settings screen closes.
    pub fn apply_settings(&mut self, settings: UserSettings) {
        // Refresh derived view state.
        let me = match self.game.mode {
            GameMode::HumanVsAi(c) => c,
            GameMode::AiVsAi => Color::White,
        };
        self.orientation = orientation_for(me, &settings);
        self.settings = settings;
    }

    // ---- test-only thin wrappers ------------------------------------
    //
    // These mirror the private methods used by `update`. They exist so
    // `tests/ui_smoke.rs` (T052) can drive the click → move → engine
    // pipeline without spinning up an `eframe::run_native` event loop.
    // They are *not* part of the production UI surface.

    /// Test helper for `tests/ui_smoke.rs`: dispatch a fabricated
    /// [`BoardResponse`] (e.g. simulated click) without an egui frame.
    #[doc(hidden)]
    pub fn handle_board_response_for_test(&mut self, resp: BoardResponse) {
        self.handle_board_response(resp);
    }

    /// Test helper: invoke the click-outcome dispatch.
    #[doc(hidden)]
    pub fn apply_click_outcome_for_test(&mut self, outcome: ClickOutcome) {
        self.apply_click_outcome(outcome);
    }

    /// Test helper: trigger the engine-thinking pump (T044).
    #[doc(hidden)]
    pub fn tick_engine_for_test(&mut self, engine: &mut EngineLink) {
        self.tick_engine(engine);
    }

    /// Test helper: query whether the human's clock is ticking.
    #[doc(hidden)]
    pub fn is_human_turn_for_test(&self) -> bool {
        self.is_human_turn()
    }
}

fn orientation_for(color: Color, settings: &UserSettings) -> Orientation {
    use crate::settings::BoardOrientation;
    match settings.ui.board_orientation {
        BoardOrientation::WhiteAtBottom => Orientation::WhiteAtBottom,
        BoardOrientation::BlackAtBottom => Orientation::BlackAtBottom,
        BoardOrientation::Auto => match color {
            Color::White => Orientation::WhiteAtBottom,
            Color::Black => Orientation::BlackAtBottom,
        },
    }
}

/// Helpers used by the captured-piece tray. Public so the tests below
/// can build expectations without going through the full UI.
#[derive(Clone, Debug, Default)]
pub struct CapturedSummary {
    /// Pieces white has lost (i.e., black has captured), ordered Q→P.
    pub white_lost: Vec<PieceType>,
    /// Pieces black has lost (i.e., white has captured), ordered Q→P.
    pub black_lost: Vec<PieceType>,
    /// Material differential from White's perspective (positive →
    /// white is up).
    pub material: i32,
}

/// Compute a [`CapturedSummary`] for the current state of `game`.
pub fn compute_captured(game: &Game) -> CapturedSummary {
    use chess_core::PieceType::*;
    let mut white_lost: Vec<PieceType> = Vec::new();
    let mut black_lost: Vec<PieceType> = Vec::new();
    for record in &game.history {
        if let Some(captured_kind) = record.captured_piece {
            // Capture happens on the side-to-move's *opponent*. The
            // opponent of the side that played the move is whoever is
            // about to move *now*, except we want the colour of the
            // piece that was captured. The piece captured is the
            // colour of the side that did NOT play this move — i.e.
            // the side-to-move when the move was played was the
            // colour that captured, so the captured piece belongs to
            // the opponent of the mover.
            //
            // We don't have the mover colour stored directly, but we
            // can derive it from the parity of `record.prior_zobrist`
            // — too brittle. Easier: scan for the move number's
            // parity, since white moves on even half-moves
            // (0-indexed). However, since the history is appended in
            // play order, we know that record at index `i` was played
            // by White iff `i` is even (in a HumanVsAi game starting
            // from the standard pos).
            let _ = captured_kind;
        }
    }
    // We re-scan by parity-of-history-index for now.
    for (i, record) in game.history.iter().enumerate() {
        if let Some(captured_kind) = record.captured_piece {
            // Index 0 is White's first move ⇒ White captured ⇒ Black
            // lost the piece.
            let mover_was_white = i % 2 == 0;
            if mover_was_white {
                black_lost.push(captured_kind);
            } else {
                white_lost.push(captured_kind);
            }
        }
    }
    let order = |k: PieceType| match k {
        Queen => 0,
        Rook => 1,
        Bishop => 2,
        Knight => 3,
        Pawn => 4,
        King => 5,
    };
    white_lost.sort_by_key(|k| order(*k));
    black_lost.sort_by_key(|k| order(*k));

    let material = piece_total(&black_lost) - piece_total(&white_lost);

    CapturedSummary {
        white_lost,
        black_lost,
        material,
    }
}

fn piece_total(pieces: &[PieceType]) -> i32 {
    pieces.iter().map(|k| piece_value(*k)).sum()
}

fn piece_value(kind: PieceType) -> i32 {
    use chess_core::PieceType::*;
    match kind {
        Pawn => 1,
        Knight => 3,
        Bishop => 3,
        Rook => 5,
        Queen => 9,
        King => 0,
    }
}

/// Map an engine `Score` to a single string for the right panel — used
/// to format the "Thinking… depth N, eval +0.42" line. Currently
/// unused because [`render_status`] already does this; kept for future
/// CP-G work where we render more detailed info.
#[allow(dead_code)]
pub(crate) fn format_score(score: Score) -> String {
    match score {
        Score::Cp(cp) => format!("{:+.2}", cp as f32 / 100.0),
        Score::Mate(plies) => format!("Mate in {}", plies.abs()),
    }
}

#[allow(dead_code)]
fn engine_strength_label(s: StrengthPreset) -> &'static str {
    match s {
        StrengthPreset::Beginner => "Beginner",
        StrengthPreset::Intermediate => "Intermediate",
        StrengthPreset::Advanced => "Advanced",
        StrengthPreset::Maximum => "Maximum",
    }
}

#[allow(dead_code)]
fn time_control_label(tc: TimeControl) -> String {
    match tc {
        TimeControl::PerMove(d) => format!("{:?}/move", d),
        TimeControl::FixedDepth(d) => format!("depth {d}"),
        TimeControl::FixedNodes(n) => format!("{n} nodes"),
        TimeControl::Infinite => "infinite".to_string(),
    }
}

#[allow(dead_code)]
fn eval_text(eval: Option<Eval>) -> String {
    match eval {
        Some(cp) => format!("{:+.2}", cp as f32 / 100.0),
        None => "—".to_string(),
    }
}

#[allow(dead_code)]
fn engine_status_color(status: &EngineStatus, palette: &Palette) -> egui::Color32 {
    match status {
        EngineStatus::Thinking { .. } => egui::Color32::from_rgb(0xC8, 0xB8, 0x36),
        EngineStatus::Idle => palette.label,
        EngineStatus::Stopped => egui::Color32::from_rgb(0xCC, 0x55, 0x55),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chess_core::{parse_san, Color};

    #[test]
    fn compute_captured_after_capture() {
        // 1.e4 d5 2.exd5 — white captures black's d5 pawn.
        let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));
        for san in ["e4", "d5", "exd5"] {
            let mv = parse_san(&g.current, san).unwrap();
            g.make_move(mv).unwrap();
        }
        let summary = compute_captured(&g);
        assert_eq!(summary.black_lost, vec![PieceType::Pawn]);
        assert!(summary.white_lost.is_empty());
        assert_eq!(summary.material, 1);
    }
}
