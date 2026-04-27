//! Promotion modal (T038).
//!
//! Rendered above the board when a pawn move reaches rank 1/8. The
//! user picks from Q/R/B/N; `Esc` cancels the move per
//! [contracts/ui-interactions.md §2.1].

use chess_core::{Color, PieceType, Promotion, Square};
use egui::{Align2, Context, Vec2};

/// Whether a promotion modal is currently active.
#[derive(Clone, Debug)]
pub struct PromotionRequest {
    /// Origin square of the promoting pawn.
    pub from: Square,
    /// Destination square (rank 1 or rank 8).
    pub to: Square,
    /// Side to move (used to colour the buttons).
    pub mover: Color,
}

/// Result of one frame of the promotion modal.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum PromotionOutcome {
    /// User has not yet picked.
    Pending,
    /// User picked a promotion piece.
    Picked(Promotion),
    /// User pressed `Esc` or clicked outside the modal.
    Cancelled,
}

/// Render the modal. Call once per frame while a [`PromotionRequest`]
/// is active. Returns the outcome of this frame.
pub fn show_promotion_modal(ctx: &Context, request: &PromotionRequest) -> PromotionOutcome {
    if ctx.input(|i| i.key_pressed(egui::Key::Escape)) {
        return PromotionOutcome::Cancelled;
    }

    let title = format!(
        "Promote {} pawn on {}",
        match request.mover {
            Color::White => "white",
            Color::Black => "black",
        },
        request.to.algebraic()
    );

    let mut picked: Option<Promotion> = None;
    let mut close = false;

    egui::Window::new(title)
        .anchor(Align2::CENTER_CENTER, Vec2::ZERO)
        .collapsible(false)
        .resizable(false)
        .show(ctx, |ui| {
            ui.label("Choose the piece to promote to:");
            ui.add_space(6.0);
            ui.horizontal(|ui| {
                for (label, kind, promo) in [
                    ("Queen", PieceType::Queen, Promotion::Queen),
                    ("Rook", PieceType::Rook, Promotion::Rook),
                    ("Bishop", PieceType::Bishop, Promotion::Bishop),
                    ("Knight", PieceType::Knight, Promotion::Knight),
                ] {
                    let _ = kind;
                    if ui.button(label).clicked() {
                        picked = Some(promo);
                    }
                }
            });
            ui.add_space(4.0);
            ui.horizontal(|ui| {
                if ui.button("Cancel (Esc)").clicked() {
                    close = true;
                }
            });
        });

    if let Some(promo) = picked {
        return PromotionOutcome::Picked(promo);
    }
    if close {
        return PromotionOutcome::Cancelled;
    }
    PromotionOutcome::Pending
}
