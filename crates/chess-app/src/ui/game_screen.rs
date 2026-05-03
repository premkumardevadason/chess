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

use chess_core::{
    legal_moves, parse_san, Color, DrawReason, Game, GameMode, GameResult, Move, PieceType,
};
use chess_engine::{EngineConfig, Eval, Event, Score, SearchResult, StrengthPreset, TimeControl};
use egui::{Align, Layout, RichText, Vec2};

use crate::engine_link::{render_status, EngineLink, EngineStatus};
use crate::settings::UserSettings;
use crate::ui::board::{
    apply_promotion_choice, rejection_reason, BoardResponse, BoardState, BoardWidget,
    ClickOutcome, Orientation,
};
use crate::ui::promotion::{show_promotion_modal, PromotionOutcome, PromotionRequest};
use crate::ui::theme::{apply_egui_theme, draw_mini_piece, palette_for, Palette};

/// Selected mode in the New Game modal (T072). Maps to either a
/// `GameMode::HumanVsAi(_)` or `GameMode::AiVsAi` on commit.
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
enum NewGameModeChoice {
    HumanPlaysWhite,
    HumanPlaysBlack,
    AiVsAi,
}

/// In-progress New Game modal state (T072).
#[derive(Clone, Debug)]
struct NewGameDialogState {
    mode: NewGameModeChoice,
    /// White-side strength when `mode == AiVsAi`.
    white_strength: StrengthPreset,
    /// Black-side strength when `mode == AiVsAi`.
    black_strength: StrengthPreset,
}

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
    /// Runtime Reproducible Mode toggle (T082). Controls whether
    /// searches are started with `Mode::Reproducible` or `Mode::Normal`.
    pub reproducible_mode: bool,
    /// Latched once per frame when the user clicks Settings… in the
    /// menu or presses Ctrl+, (T059). The host (`ChessApp`) drains
    /// this via [`GameScreen::take_open_settings_request`] and swaps
    /// to the [`crate::ui::SettingsScreen`].
    pub open_settings_requested: bool,
    /// Latched when user opens Help ▸ About (T085). Drained by the
    /// host app to swap to `AboutScreen`.
    pub open_about_requested: bool,
    /// History-navigation cursor (T065). `None` ⇒ live game; `Some(n)`
    /// ⇒ viewing the position **after** `n` plies have been played
    /// (`0` = start position, `history.len()` = live).
    ///
    /// While `Some(_)`, the board is read-only per
    /// [contracts/ui-interactions.md §2.4](../../specs/001-chess-ai-rewrite/contracts/ui-interactions.md);
    /// the user must click the latest ply or "Return to live" to play
    /// further moves.
    pub nav_index: Option<usize>,
    /// Pending undo requested while the engine is mid-search. Drained
    /// when `SearchAborted` arrives (T066).
    pending_undo_after_abort: bool,
    /// Pending redo similarly held off while the engine is searching.
    pending_redo_after_abort: bool,
    /// Per-side strengths for the active AI-vs-AI game (T071/T073).
    /// `Some((white, black))` ⇒ engine plays at the corresponding
    /// preset depending on `side_to_move`. Always `None` in
    /// HumanVsAi mode.
    ai_strengths: Option<(StrengthPreset, StrengthPreset)>,
    /// Pause flag for AI-vs-AI mode (T074). When `true`, `tick_engine`
    /// is a no-op and any in-flight search is aborted on the next
    /// frame.
    pub paused: bool,
    /// Working state for the New Game modal (T072). `Some(_)` while
    /// the modal is open; the contents are committed by
    /// `start_new_game(...)` when the user clicks "Start".
    new_game_dialog: Option<NewGameDialogState>,
    /// Most recent hint result from `Command::StartSearch` with
    /// `analysis_only=true` (T076). Auto-clears 5s after arrival or
    /// on next user click. Used by board overlay (T077) + right panel
    /// (T078).
    hint_result: Option<SearchResult>,
    /// Deadline for clearing the current hint (T077).
    hint_expires_at: Option<Instant>,
    /// Tracks whether the most recent `StartSearch` was for analysis
    /// only (T076). Used by `absorb_engine_event` to distinguish
    /// hints from game moves.
    last_search_analysis_only: bool,
    /// Hidden SAN input toggle (T088).
    san_entry_active: bool,
    /// Buffer for SAN typing (e.g. "Nf3", "O-O").
    san_buffer: String,
    /// Request keyboard focus for SAN text field next frame.
    san_focus_requested: bool,
    /// Error highlight deadline for invalid SAN input.
    san_error_until: Option<Instant>,
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
            palette: palette_for(settings.ui.theme),
            orientation: orientation_for(initial_color, &settings),
            promotion: None,
            confirm_new_game: false,
            confirm_resign: false,
            show_game_over: false,
            toast: None,
            toast_until: None,
            engine_searching: false,
            reproducible_mode: settings.engine.reproducible_mode_default,
            settings,
            open_settings_requested: false,
            open_about_requested: false,
            nav_index: None,
            pending_undo_after_abort: false,
            pending_redo_after_abort: false,
            ai_strengths: None,
            paused: false,
            new_game_dialog: None,
            hint_result: None,
            hint_expires_at: None,
            last_search_analysis_only: false,
            san_entry_active: false,
            san_buffer: String::new(),
            san_focus_requested: false,
            san_error_until: None,
        }
    }

    /// Per-frame entry point. Drains engine events, renders all
    /// widgets, kicks the engine if it is the AI's turn.
    pub fn update(&mut self, ctx: &egui::Context, engine: &mut EngineLink) {
        apply_egui_theme(ctx, self.settings.ui.theme);

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

        // Undo / Redo hotkeys (T066). Ctrl+Z = undo, Ctrl+Shift+Z = redo.
        let (undo_hotkey, redo_hotkey) = ctx.input(|i| {
            let ctrl = i.modifiers.command;
            let shift = i.modifiers.shift;
            let z = i.key_pressed(egui::Key::Z);
            (ctrl && !shift && z, ctrl && shift && z)
        });
        if undo_hotkey {
            self.request_undo(engine);
        }
        if redo_hotkey {
            self.request_redo(engine);
        }

        // Reproducible-mode hotkey (T082). Ctrl+R toggles search mode.
        let reproducible_hotkey = ctx.input(|i| {
            i.modifiers.command_only() && i.key_pressed(egui::Key::R)
        });
        if reproducible_hotkey {
            self.reproducible_mode = !self.reproducible_mode;
            let label = if self.reproducible_mode {
                "Reproducible"
            } else {
                "Default"
            };
            self.set_toast(format!("Search mode: {label}"));
            if self.engine_searching {
                engine.stop();
            }
        }

        // Hint hotkey (T076). H = request hint.
        let hint_hotkey = ctx.input(|i| {
            i.key_pressed(egui::Key::H) && !i.modifiers.any()
        });
        if hint_hotkey {
            self.request_hint(engine);
        }

        // SAN input hotkey (T088). Tab activates hidden SAN entry.
        let san_hotkey = ctx.input(|i| i.key_pressed(egui::Key::Tab) && !i.modifiers.any());
        if san_hotkey {
            self.san_entry_active = true;
            self.san_focus_requested = true;
        }
        let san_cancel_hotkey = ctx.input(|i| i.key_pressed(egui::Key::Escape) && !i.modifiers.any());
        if san_cancel_hotkey && self.san_entry_active {
            self.san_entry_active = false;
            self.san_buffer.clear();
            self.san_error_until = None;
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

                ui.menu_button("Help", |ui| {
                    if ui.button("About").clicked() {
                        self.open_about_requested = true;
                        ui.close_menu();
                    }
                });

                ui.separator();
                if ui
                    .button("Toggle Reproducible\tCtrl+R")
                    .clicked()
                {
                    self.reproducible_mode = !self.reproducible_mode;
                    let label = if self.reproducible_mode {
                        "Reproducible"
                    } else {
                        "Default"
                    };
                    self.set_toast(format!("Search mode: {label}"));
                    if self.engine_searching {
                        engine.stop();
                    }
                }

                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    let (text, color) = if self.reproducible_mode {
                        ("Reproducible", egui::Color32::from_rgb(30, 120, 30))
                    } else {
                        ("Default", egui::Color32::from_rgb(60, 60, 60))
                    };
                    ui.label(
                        RichText::new(text)
                            .strong()
                            .color(egui::Color32::WHITE)
                            .background_color(color),
                    );
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
            // Center the board vertically and horizontally, and make it fill available space.
            ui.vertical_centered(|ui| {
                ui.with_layout(Layout::top_down(Align::Center), |ui| {
                    // Calculate the largest possible square size for the board.
                    let avail = ui.available_size();
                    let side = avail.x.min(avail.y).max(128.0); // Use a larger minimum for usability
                    let nav_view = self.navigation_view();
                    let view_game = nav_view.as_ref().unwrap_or(&self.game);
                    let interactive = nav_view.is_none()
                        && self.is_human_turn()
                        && self.promotion.is_none();
                    let board = BoardWidget::new(
                        view_game,
                        &self.board_state,
                        &self.palette,
                        self.orientation,
                    )
                    .interactive(interactive)
                    .with_hint_move(self.hint_result.as_ref().map(|hr| hr.mv));
                    // Allocate the board with the computed size
                    let resp = ui.allocate_ui_with_layout(
                        Vec2::splat(side),
                        Layout::centered_and_justified(egui::Direction::TopDown),
                        |ui| board.show(ui)
                    ).inner;
                    // Only honour click/drag when not navigating — past views are read-only
                    if nav_view.is_none() {
                        self.handle_board_response(resp);
                    }
                });
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
        if self.new_game_dialog.is_some() {
            self.render_new_game_dialog(ctx, engine);
        }
        if self.confirm_resign {
            self.render_confirm_resign(ctx);
        }
        if self.show_game_over {
            self.render_game_over_modal(ctx);
        }

        // Clear expired hints (T076).
        if let Some(deadline) = self.hint_expires_at {
            if Instant::now() >= deadline {
                self.hint_result = None;
                self.hint_expires_at = None;
            }
        }

        if let Some(deadline) = self.san_error_until {
            if Instant::now() >= deadline {
                self.san_error_until = None;
            }
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

        // Hint result display (T078).
        if let Some(result) = &self.hint_result {
            ui.add_space(10.0);
            ui.separator();
            ui.add_space(6.0);
            ui.label(RichText::new("Hint").strong());
            
            // Display evaluation using existing helper.
            ui.label(format!("Eval: {}", format_score(result.info.score)));
            
            // Display principal variation (first few moves).
            if !result.info.pv.is_empty() {
                let pv_str = result.info.pv.iter()
                    .take(5)  // Show first 5 moves
                    .map(|m| m.to_string())
                    .collect::<Vec<_>>()
                    .join(" ");
                ui.label(format!("PV: {}", pv_str));
            }
            
            // Display search depth if available.
            if result.info.depth > 0 {
                ui.label(format!("Depth: {}", result.info.depth));
            }
        }

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
        let mut clicked_index: Option<usize> = None;
        egui::ScrollArea::vertical()
            .max_height(220.0)
            .auto_shrink([false; 2])
            .show(ui, |ui| {
                clicked_index = self.render_move_list(ui);
            });
        if let Some(idx) = clicked_index {
            self.navigate_to(idx);
        }

        // History-navigation indicator + return-to-live button (T065).
        if let Some(n) = self.nav_index {
            ui.add_space(4.0);
            ui.horizontal(|ui| {
                ui.colored_label(
                    egui::Color32::from_rgb(0xC8, 0x9E, 0x3B),
                    format!("Viewing ply {} of {}", n, self.game.history.len()),
                );
                if ui.button("Return to live").clicked() {
                    self.nav_index = None;
                }
            });
        }

        ui.add_space(10.0);
        ui.separator();
        ui.add_space(6.0);

        // Buttons.
        ui.with_layout(Layout::top_down(Align::Min), |ui| {
            // Undo / Redo (T066).
            ui.horizontal(|ui| {
                let can_undo = !self.game.history.is_empty();
                let can_redo = !self.game.redo_stack.is_empty();
                if ui
                    .add_enabled(can_undo, egui::Button::new("Undo (Ctrl+Z)"))
                    .clicked()
                {
                    self.request_undo(engine);
                }
                if ui
                    .add_enabled(can_redo, egui::Button::new("Redo (Ctrl+Shift+Z)"))
                    .clicked()
                {
                    self.request_redo(engine);
                }
            });
            ui.add_space(4.0);
            if ui.button("New Game").clicked() {
                if self.game.history.is_empty() || self.game.result().is_some() {
                    // Skip the "discard?" confirmation but still
                    // surface the mode-selection dialog (T072).
                    self.open_new_game_dialog();
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
            // Swap Sides — visible only in Human-vs-AI mode while the game
            // is in progress (T106). Cancels any in-flight engine search,
            // flips the user's colour and the board orientation, and
            // immediately schedules a new search if it is now the AI's turn.
            if matches!(self.game.mode, GameMode::HumanVsAi(_))
                && self.game.result().is_none()
            {
                if ui.button("Swap Sides").clicked() {
                    self.swap_sides(engine);
                }
            }
            ui.add_space(4.0);
            // Pause / Resume — visible only in AI-vs-AI mode (T074).
            if matches!(self.game.mode, GameMode::AiVsAi)
                && self.game.result().is_none()
            {
                let label = if self.paused { "Resume" } else { "Pause" };
                if ui.button(label).clicked() {
                    self.toggle_paused(engine);
                }
            }
            // Hint button — visible only during human's turn (T076).
            if self.is_human_turn() && self.game.result().is_none() {
                if ui
                    .add_enabled(
                        !self.engine_searching && self.hint_result.is_none(),
                        egui::Button::new("Hint (H)"),
                    )
                    .clicked()
                {
                    self.request_hint(engine);
                }
            }
            ui.add_space(4.0);
            if engine.status().is_thinking() {
                if ui.button("Stop thinking").clicked() {
                    engine.stop();
                }
            }

            ui.add_space(8.0);
            ui.separator();
            ui.add_space(6.0);
            if self.san_entry_active {
                ui.label(RichText::new("SAN Input").strong());
                let has_error = self
                    .san_error_until
                    .map(|d| Instant::now() < d)
                    .unwrap_or(false);
                ui.scope(|ui| {
                    if has_error {
                        let err_fill = egui::Color32::from_rgb(120, 24, 24);
                        ui.visuals_mut().widgets.inactive.bg_fill = err_fill;
                        ui.visuals_mut().widgets.hovered.bg_fill = err_fill;
                        ui.visuals_mut().widgets.active.bg_fill = err_fill;
                    }

                    let resp = ui.add(
                        egui::TextEdit::singleline(&mut self.san_buffer)
                            .hint_text("e4, Nf3, O-O")
                            .id_source("san_input_field"),
                    );
                    if self.san_focus_requested {
                        resp.request_focus();
                        self.san_focus_requested = false;
                    }
                    if resp.has_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
                        self.submit_san_input();
                        self.san_focus_requested = true;
                    }
                });
                ui.label(RichText::new("Enter: play SAN  Esc: close input").small().weak());
            } else {
                ui.label(RichText::new("Press Tab to enter SAN move").small().weak());
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

    /// Render the move list and return the ply-index (1-based) the
    /// user clicked, if any. Each entry is a clickable label; the
    /// currently displayed half-move is highlighted (T064).
    ///
    /// Returns:
    /// - `Some(0)` when the "Start position" item is clicked (navigate
    ///   to before-any-moves).
    /// - `Some(n)` when ply `n` (1-indexed in play order) is clicked.
    /// - `None` if nothing was clicked this frame.
    fn render_move_list(&self, ui: &mut egui::Ui) -> Option<usize> {
        let plies = &self.game.history;
        // Currently-displayed ply (1-indexed); 0 = before any moves.
        let current_ply = self.nav_index.unwrap_or(plies.len());
        let mut clicked: Option<usize> = None;

        // Start-position row.
        ui.horizontal(|ui| {
            ui.label(RichText::new("0.").weak());
            let label = RichText::new("(start)");
            let label = if current_ply == 0 {
                label.strong().underline()
            } else {
                label.weak()
            };
            if ui.selectable_label(current_ply == 0, label).clicked() {
                clicked = Some(0);
            }
        });

        if plies.is_empty() {
            ui.weak("(no moves yet)");
            return clicked;
        }

        let mut i = 0;
        while i < plies.len() {
            let move_no = (i / 2) + 1;
            let white_idx = i + 1; // 1-indexed ply count after this move
            let white_ply = &plies[i];
            let white_san = ply_san(white_ply);
            let black_pair = plies.get(i + 1).map(|p| (i + 2, ply_san(p)));

            ui.horizontal(|ui| {
                ui.label(RichText::new(format!("{move_no}.")).weak());
                if move_list_item(ui, &white_san, current_ply == white_idx).clicked() {
                    clicked = Some(white_idx);
                }
                if let Some((black_idx, black_san)) = black_pair {
                    if move_list_item(ui, &black_san, current_ply == black_idx).clicked() {
                        clicked = Some(black_idx);
                    }
                }
            });
            i += 2;
        }

        clicked
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
            // Open the richer mode-selection dialog rather than
            // immediately starting a default game (T072).
            self.open_new_game_dialog();
            self.confirm_new_game = false;
        } else if close {
            self.confirm_new_game = false;
        }
    }

    /// Initialise the New Game modal with sensible defaults sourced
    /// from the user's settings (T072).
    fn open_new_game_dialog(&mut self) {
        let last_color = self.settings.ui.last_human_color;
        let mode = match last_color {
            crate::settings::HumanColor::White => NewGameModeChoice::HumanPlaysWhite,
            crate::settings::HumanColor::Black => NewGameModeChoice::HumanPlaysBlack,
        };
        let default_strength = self.settings.engine.default_strength;
        self.new_game_dialog = Some(NewGameDialogState {
            mode,
            white_strength: default_strength,
            black_strength: default_strength,
        });
    }

    /// Render the New Game mode + strength dialog (T072). Emits a
    /// fresh game on "Start"; cancel just closes the modal.
    fn render_new_game_dialog(
        &mut self,
        ctx: &egui::Context,
        engine: &mut EngineLink,
    ) {
        let Some(mut dlg) = self.new_game_dialog.clone() else {
            return;
        };
        let mut start = false;
        let mut cancel = false;
        egui::Window::new("New Game")
            .anchor(egui::Align2::CENTER_CENTER, Vec2::ZERO)
            .collapsible(false)
            .resizable(false)
            .show(ctx, |ui| {
                ui.label(RichText::new("Mode").strong());
                ui.radio_value(
                    &mut dlg.mode,
                    NewGameModeChoice::HumanPlaysWhite,
                    "Human vs AI (play White)",
                );
                ui.radio_value(
                    &mut dlg.mode,
                    NewGameModeChoice::HumanPlaysBlack,
                    "Human vs AI (play Black)",
                );
                ui.radio_value(
                    &mut dlg.mode,
                    NewGameModeChoice::AiVsAi,
                    "AI vs AI",
                );

                if dlg.mode == NewGameModeChoice::AiVsAi {
                    ui.add_space(6.0);
                    ui.label(RichText::new("Per-side strength").strong());
                    strength_dropdown(ui, "White:", &mut dlg.white_strength);
                    strength_dropdown(ui, "Black:", &mut dlg.black_strength);
                }

                ui.add_space(8.0);
                ui.horizontal(|ui| {
                    if ui.button("Start").clicked() {
                        start = true;
                    }
                    if ui.button("Cancel").clicked() {
                        cancel = true;
                    }
                });
            });

        // Persist the in-progress edits back to the option.
        self.new_game_dialog = Some(dlg.clone());

        if start {
            // Stop any in-flight search before swapping the game.
            if engine.status().is_thinking() {
                engine.stop();
            }
            self.commit_new_game(dlg);
            self.new_game_dialog = None;
        } else if cancel {
            self.new_game_dialog = None;
        }
    }

    /// Apply the user's selection from the New Game modal (T072).
    fn commit_new_game(&mut self, dlg: NewGameDialogState) {
        match dlg.mode {
            NewGameModeChoice::HumanPlaysWhite => {
                self.start_new_game_human(Color::White);
            }
            NewGameModeChoice::HumanPlaysBlack => {
                self.start_new_game_human(Color::Black);
            }
            NewGameModeChoice::AiVsAi => {
                self.start_new_game_ai_vs_ai(dlg.white_strength, dlg.black_strength);
            }
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
        if let Some(focus) = resp.keyboard_focus {
            self.board_state.keyboard_focus = Some(focus);
        }

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
                // Clear any active hint when the human makes a move (T076).
                self.hint_result = None;
                self.hint_expires_at = None;
            }
            Err(e) => {
                self.set_toast(e.to_string());
            }
        }
    }

    fn submit_san_input(&mut self) {
        if !self.is_human_turn() || self.game.result().is_some() {
            return;
        }
        let raw = self.san_buffer.trim();
        if raw.is_empty() {
            return;
        }
        match parse_san(&self.game.current, raw) {
            Ok(mv) => {
                self.san_error_until = None;
                self.try_play_human_move(mv);
                self.san_buffer.clear();
            }
            Err(_) => {
                self.san_error_until = Some(Instant::now() + Duration::from_secs(2));
                self.set_toast(format!("Invalid SAN: {raw}"));
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
        // Pause autoplay while the user is browsing history (T065).
        if self.nav_index.is_some() {
            return;
        }
        // AI-vs-AI pause gate (T074).
        if self.paused {
            return;
        }
        // It's the engine's turn — fire SetPosition + StartSearch.
        // Send `start_position` as the root and the full history so
        // Carp's repetition detector sees every prior position. Using
        // `game.current` here would cause the worker to double-apply
        // every move (see `tests/ai_vs_ai.rs`).
        let history: Vec<Move> = self.game.history.iter().map(|r| r.move_played).collect();
        engine.set_position(self.game.start_position, history);
        let cfg = self.engine_config_for_play();
        engine.start_search(cfg);
        self.engine_searching = true;
    }

    fn engine_config_for_play(&self) -> EngineConfig {
        // In AI-vs-AI mode, pick the strength of the side currently
        // on move (T071/T073). Otherwise use the user's default.
        let strength = match (self.ai_strengths, self.game.current.side_to_move) {
            (Some((white, _)), Color::White) => white,
            (Some((_, black)), Color::Black) => black,
            _ => self.settings.engine.default_strength,
        };
        EngineConfig {
            mode: if self.reproducible_mode {
                chess_engine::Mode::Reproducible {
                    seed: 0xDEAD_BEEF_CAFE_BABEu64,
                }
            } else {
                chess_engine::Mode::Normal
            },
            time_control: self.settings.engine_time_control(),
            strength,
            max_threads: self.settings.engine.max_threads,
            tt_size_mib: 16,
            analysis_only: false,
        }
    }

    /// Toggle the AI-vs-AI pause flag (T074). Aborts any in-flight
    /// search on transition into the paused state; resumption is
    /// handled implicitly by the next `tick_engine` call.
    fn toggle_paused(&mut self, engine: &mut EngineLink) {
        self.paused = !self.paused;
        if self.paused && engine.status().is_thinking() {
            engine.stop();
        }
    }

    /// Swap sides during a Human-vs-AI game (T106 / FR-012).
    ///
    /// Cancels any in-flight engine search, flips the user's colour
    /// inside `GameMode::HumanVsAi`, flips the board orientation
    /// (respecting `BoardOrientation::Auto` from settings), clears
    /// transient board view state (selection, hint), and lets the
    /// next `tick_engine` call start a search if it is now the AI's
    /// turn. No-op outside Human-vs-AI mode or after the game ends.
    fn swap_sides(&mut self, engine: &mut EngineLink) {
        let GameMode::HumanVsAi(current) = self.game.mode else {
            return;
        };
        if self.game.result().is_some() {
            return;
        }
        if engine.status().is_thinking() {
            engine.stop();
        }
        let new_color = current.opp();
        self.game.mode = GameMode::HumanVsAi(new_color);
        self.orientation = orientation_for(new_color, &self.settings);
        // Clear ephemeral interaction state so the new perspective
        // starts cleanly. Keep history / move list intact.
        self.board_state.selected = None;
        self.board_state.legal_targets.clear();
        self.board_state.drag_origin = None;
        self.hint_result = None;
        self.hint_expires_at = None;
        self.last_search_analysis_only = false;
        self.engine_searching = false;
        self.set_toast(format!("You now play {:?}", new_color));
    }

    /// Request a hint (T076): issue `StartSearch` with
    /// `analysis_only=true` and `hint_time_ms` budget. Only callable
    /// during the human's turn and while no hint is already pending.
    fn request_hint(&mut self, engine: &mut EngineLink) {
        if !self.is_human_turn() {
            self.set_toast("Cannot request hint when it is the AI's turn".to_string());
            return;
        }
        if self.engine_searching || self.hint_result.is_some() {
            self.set_toast("Hint already pending".to_string());
            return;
        }
        let mut cfg = self.engine_config_for_play();
        cfg.analysis_only = true;
        cfg.time_control = TimeControl::PerMove(std::time::Duration::from_millis(
            self.settings.engine.hint_time_ms as u64,
        ));
        self.last_search_analysis_only = true;
        let history: Vec<Move> = self.game.history.iter().map(|r| r.move_played).collect();
        engine.set_position(self.game.start_position, history);
        engine.start_search(cfg);
    }

    fn absorb_engine_event(&mut self, event: Event) {
        match event {
            Event::SearchComplete(result) => {
                self.engine_searching = false;
                if self.last_search_analysis_only {
                    // Analysis-only search: store as hint, don't apply move (T076/T079).
                    self.hint_result = Some(result);
                    self.hint_expires_at = Some(Instant::now() + std::time::Duration::from_secs(5));
                    self.last_search_analysis_only = false;
                } else if !self.is_human_turn() && self.game.result().is_none() {
                    // Game move: apply to board.
                    if let Err(e) = self.game.make_move(result.mv) {
                        self.set_toast(format!("engine returned illegal move: {e}"));
                    } else {
                        self.board_state.last_move = Some(result.mv);
                    }
                }
            }
            Event::SearchAborted => {
                self.engine_searching = false;
                if self.pending_undo_after_abort {
                    self.pending_undo_after_abort = false;
                    self.do_undo();
                } else if self.pending_redo_after_abort {
                    self.pending_redo_after_abort = false;
                    self.do_redo();
                }
            }
            _ => {}
        }
    }

    fn start_new_game(&mut self) {
        // Used by the empty-board "New Game" button and from tests.
        // Default to a Human-vs-AI game with the player's last colour.
        let me = match self.game.mode {
            GameMode::HumanVsAi(c) => c,
            GameMode::AiVsAi => Color::White,
        };
        self.start_new_game_human(me);
    }

    /// Start a fresh Human-vs-AI game with `me` to play (T072).
    fn start_new_game_human(&mut self, me: Color) {
        self.game = Game::new_game(GameMode::HumanVsAi(me));
        self.reset_game_view_state();
        self.ai_strengths = None;
        self.paused = false;
        self.orientation = orientation_for(me, &self.settings);
    }

    /// Start a fresh AI-vs-AI game with the chosen per-side strengths
    /// (T071/T072/T073).
    fn start_new_game_ai_vs_ai(
        &mut self,
        white: StrengthPreset,
        black: StrengthPreset,
    ) {
        self.game = Game::new_game(GameMode::AiVsAi);
        self.reset_game_view_state();
        self.ai_strengths = Some((white, black));
        self.paused = false;
        // For AI-vs-AI, default to White-at-bottom regardless of
        // settings auto-flipping rules.
        self.orientation = Orientation::WhiteAtBottom;
    }

    /// Common state-reset shared by `start_new_game_*`.
    fn reset_game_view_state(&mut self) {
        self.board_state = BoardState::default();
        self.engine_searching = false;
        self.show_game_over = false;
        self.toast = None;
        self.toast_until = None;
        self.promotion = None;
        self.nav_index = None;
        self.pending_undo_after_abort = false;
        self.pending_redo_after_abort = false;
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

    /// True if the user requested the About screen this frame.
    /// Consumes the flag so the host only switches once.
    pub fn take_open_about_request(&mut self) -> bool {
        std::mem::replace(&mut self.open_about_requested, false)
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
        self.palette = palette_for(settings.ui.theme);
        self.settings = settings;
    }

    // ---- US3: history navigation + undo/redo (T064–T067) ------------

    /// Build a synthetic [`Game`] showing the position after the
    /// `nav_index`-th ply, or `None` while in the live view. The
    /// returned game is a throw-away clone — callers must not mutate
    /// it (the board widget treats it as read-only).
    fn navigation_view(&self) -> Option<Game> {
        let n = self.nav_index?;
        let mut view = self.game.clone();
        // Roll back from live to ply n by undoing repeatedly.
        while view.history.len() > n {
            if view.undo().is_err() {
                break;
            }
        }
        Some(view)
    }

    /// User clicked ply `n` in the move list (T065).
    /// `0` = start position, `history.len()` = live game.
    fn navigate_to(&mut self, n: usize) {
        let live_len = self.game.history.len();
        if n >= live_len {
            self.nav_index = None;
        } else {
            self.nav_index = Some(n);
        }
    }

    /// Public test/UI helper: drain the undo path (T066).
    pub fn request_undo(&mut self, engine: &mut EngineLink) {
        if self.game.history.is_empty() {
            return;
        }
        // Always return to live view — undo branches off the live
        // history, not the navigated one.
        self.nav_index = None;

        if engine.status().is_thinking() {
            // Defer the actual undo until SearchAborted fires.
            engine.stop();
            self.pending_undo_after_abort = true;
            self.pending_redo_after_abort = false;
            return;
        }
        self.do_undo();
    }

    /// Public test/UI helper: drain the redo path (T066).
    pub fn request_redo(&mut self, engine: &mut EngineLink) {
        if self.game.redo_stack.is_empty() {
            return;
        }
        self.nav_index = None;

        if engine.status().is_thinking() {
            engine.stop();
            self.pending_redo_after_abort = true;
            self.pending_undo_after_abort = false;
            return;
        }
        self.do_redo();
    }

    fn do_undo(&mut self) {
        // In Human-vs-AI mode, undo pops both the engine's last move
        // AND the human's prior move so the human gets their turn
        // back. (Per contracts/ui-interactions.md §2.3.)
        let pop_pairs = matches!(self.game.mode, GameMode::HumanVsAi(_));
        let _ = self.game.undo();
        if pop_pairs && !self.game.history.is_empty() {
            // Only pop a second time if the new side-to-move is the
            // opponent of the human (i.e., the engine just moved).
            if let GameMode::HumanVsAi(human) = self.game.mode {
                if self.game.side_to_move() != human {
                    let _ = self.game.undo();
                }
            }
        }
        self.engine_searching = false;
        self.board_state = BoardState::default();
        if let Some(last) = self.game.history.last() {
            self.board_state.last_move = Some(last.move_played);
        }
        self.show_game_over = false;
    }

    fn do_redo(&mut self) {
        let push_pairs = matches!(self.game.mode, GameMode::HumanVsAi(_));
        let _ = self.game.redo();
        if push_pairs && !self.game.redo_stack.is_empty() {
            if let GameMode::HumanVsAi(human) = self.game.mode {
                // Replay until it's the human's turn again (mirror of
                // do_undo's pair-popping).
                if self.game.side_to_move() != human {
                    let _ = self.game.redo();
                }
            }
        }
        self.engine_searching = false;
        self.board_state = BoardState::default();
        if let Some(last) = self.game.history.last() {
            self.board_state.last_move = Some(last.move_played);
        }
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

/// Render a single clickable SAN cell in the move list with a
/// highlight when it represents the currently displayed ply (T064).
fn move_list_item(ui: &mut egui::Ui, san: &str, current: bool) -> egui::Response {
    let text = if current {
        RichText::new(san).strong().underline()
    } else {
        RichText::new(san)
    };
    ui.selectable_label(current, text)
}

/// Resolve the cached SAN for a played ply, falling back to long
/// algebraic if SAN was not stored (should not happen post-T015 but
/// defends against regressions).
fn ply_san(record: &chess_core::MoveRecord) -> String {
    if record.san.is_empty() {
        record.move_played.to_long_algebraic()
    } else {
        record.san.clone()
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

/// Render a labelled strength `ComboBox` used by the New Game modal
/// (T072).
fn strength_dropdown(ui: &mut egui::Ui, label: &str, value: &mut StrengthPreset) {
    ui.horizontal(|ui| {
        ui.label(label);
        egui::ComboBox::from_id_salt(label)
            .selected_text(engine_strength_label(*value))
            .show_ui(ui, |ui| {
                for s in [
                    StrengthPreset::Beginner,
                    StrengthPreset::Intermediate,
                    StrengthPreset::Advanced,
                    StrengthPreset::Maximum,
                ] {
                    ui.selectable_value(value, s, engine_strength_label(s));
                }
            });
    });
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
