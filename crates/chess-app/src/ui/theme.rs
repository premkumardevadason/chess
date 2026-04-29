//! Theme palette + procedural piece drawing (T033, T034).
//!
//! ### Why no PNG sprite atlas?
//!
//! T033 originally specified a Cburnett-derived sprite atlas plus an
//! OFL-licensed font. To keep the v1 build hermetic (no external asset
//! download, no extra licence files, no extra binary mass) we draw
//! pieces *procedurally* using a coloured disc + the canonical
//! single-letter SAN designator (`K Q R B N P`). The result is fully
//! recognisable at the 32–96 px tile sizes our board uses, plays nicely
//! with both Standard and high-contrast palettes, and renders without
//! any extra font registration on top of egui's bundled
//! `default_fonts`.
//!
//! A future polish task can swap `draw_piece` for an
//! `egui::Image`-based atlas without touching any other module — the
//! call site in [`crate::ui::board`] consumes only this function.

use chess_core::{Color as PColor, PieceType};
use egui::{Align2, Color32, FontId, Painter, Pos2, Rect, Stroke};

use crate::settings::Theme;

/// All colours used by the board, pieces, and overlays.
///
/// Organised by their semantic role (board squares, highlights, piece
/// fills, etc.) so we can ship multiple themes (T086 — Dark,
/// HighContrast) by varying only this struct.
#[derive(Copy, Clone, Debug)]
pub struct Palette {
    /// Light board square (e.g. `a1`, `c1`, …).
    pub light_square: Color32,
    /// Dark board square.
    pub dark_square: Color32,
    /// Tint overlaid on the from/to squares of the most recent move.
    pub last_move: Color32,
    /// Tint overlaid on the currently-selected source square.
    pub selection: Color32,
    /// Dot/ring drawn on legal target squares.
    pub legal_target: Color32,
    /// Glow colour drawn on the king's square when it is in check.
    pub check_glow: Color32,
    /// Background of side panels.
    #[allow(dead_code)] // consumed by the side-panel theming pass in T086
    pub panel_bg: Color32,
    /// Outer border drawn around the whole board.
    pub board_border: Color32,
    /// Fill colour of white pieces.
    pub white_piece: Color32,
    /// Fill colour of black pieces.
    pub black_piece: Color32,
    /// Letter colour drawn on white pieces.
    pub white_piece_text: Color32,
    /// Letter colour drawn on black pieces.
    pub black_piece_text: Color32,
    /// Outline drawn around every piece for legibility on either square.
    pub piece_outline: Color32,
    /// File / rank label colour drawn on the board's perimeter.
    pub label: Color32,
}

impl Palette {
    /// Standard light theme — the default per
    /// [data-model.md §8](../../../specs/001-chess-ai-rewrite/data-model.md).
    pub const STANDARD: Self = Self {
        light_square: Color32::from_rgb(0xF0, 0xD9, 0xB5),
        dark_square: Color32::from_rgb(0xB5, 0x88, 0x63),
        last_move: Color32::from_rgba_premultiplied(170, 162, 58, 110),
        selection: Color32::from_rgba_premultiplied(106, 158, 96, 140),
        legal_target: Color32::from_rgba_premultiplied(0, 0, 0, 80),
        check_glow: Color32::from_rgba_premultiplied(220, 60, 60, 150),
        panel_bg: Color32::from_rgb(0x2B, 0x2D, 0x31),
        board_border: Color32::from_rgb(0x3B, 0x2A, 0x18),
        white_piece: Color32::from_rgb(0xF8, 0xF8, 0xF2),
        black_piece: Color32::from_rgb(0x1B, 0x1B, 0x1B),
        white_piece_text: Color32::from_rgb(0x1B, 0x1B, 0x1B),
        black_piece_text: Color32::from_rgb(0xF8, 0xF8, 0xF2),
        piece_outline: Color32::from_rgb(0x10, 0x10, 0x10),
        label: Color32::from_rgb(0x88, 0x88, 0x88),
    };

    /// Dark board + dark chrome variant (T086).
    pub const DARK: Self = Self {
        light_square: Color32::from_rgb(0x96, 0xA5, 0x8A),
        dark_square: Color32::from_rgb(0x4E, 0x61, 0x4B),
        last_move: Color32::from_rgba_premultiplied(214, 196, 94, 120),
        selection: Color32::from_rgba_premultiplied(84, 168, 120, 150),
        legal_target: Color32::from_rgba_premultiplied(255, 255, 255, 70),
        check_glow: Color32::from_rgba_premultiplied(230, 85, 85, 170),
        panel_bg: Color32::from_rgb(0x17, 0x1B, 0x1F),
        board_border: Color32::from_rgb(0x22, 0x2A, 0x24),
        white_piece: Color32::from_rgb(0xF2, 0xF5, 0xEF),
        black_piece: Color32::from_rgb(0x0E, 0x12, 0x14),
        white_piece_text: Color32::from_rgb(0x10, 0x15, 0x12),
        black_piece_text: Color32::from_rgb(0xF3, 0xF7, 0xF5),
        piece_outline: Color32::from_rgb(0x08, 0x0A, 0x0B),
        label: Color32::from_rgb(0xB7, 0xC0, 0xB4),
    };

    /// Accessibility-first high-contrast palette (T086).
    pub const HIGH_CONTRAST: Self = Self {
        light_square: Color32::from_rgb(0xF2, 0xF2, 0xF2),
        dark_square: Color32::from_rgb(0x1F, 0x1F, 0x1F),
        last_move: Color32::from_rgba_premultiplied(255, 215, 0, 170),
        selection: Color32::from_rgba_premultiplied(0, 180, 255, 180),
        legal_target: Color32::from_rgba_premultiplied(255, 0, 120, 170),
        check_glow: Color32::from_rgba_premultiplied(255, 40, 40, 220),
        panel_bg: Color32::from_rgb(0x00, 0x00, 0x00),
        board_border: Color32::from_rgb(0xFF, 0xFF, 0xFF),
        white_piece: Color32::from_rgb(0xFF, 0xFF, 0xFF),
        black_piece: Color32::from_rgb(0x00, 0x00, 0x00),
        white_piece_text: Color32::from_rgb(0x00, 0x00, 0x00),
        black_piece_text: Color32::from_rgb(0xFF, 0xFF, 0xFF),
        piece_outline: Color32::from_rgb(0xFF, 0x00, 0x80),
        label: Color32::from_rgb(0xF0, 0xF0, 0xF0),
    };
}

/// Select palette by persisted theme enum.
pub fn palette_for(theme: Theme) -> Palette {
    match theme {
        Theme::Standard => Palette::STANDARD,
        Theme::Dark => Palette::DARK,
        Theme::HighContrast => Palette::HIGH_CONTRAST,
    }
}

/// Apply egui visuals for the selected theme.
pub fn apply_egui_theme(ctx: &egui::Context, theme: Theme) {
    let mut visuals = match theme {
        Theme::Standard => egui::Visuals::light(),
        Theme::Dark => egui::Visuals::dark(),
        Theme::HighContrast => egui::Visuals::dark(),
    };

    if matches!(theme, Theme::HighContrast) {
        visuals.override_text_color = Some(Color32::WHITE);
        visuals.widgets.noninteractive.bg_fill = Color32::from_rgb(0x00, 0x00, 0x00);
        visuals.widgets.noninteractive.fg_stroke = Stroke::new(1.8, Color32::WHITE);
        visuals.widgets.active.bg_fill = Color32::from_rgb(0xFF, 0xFF, 0xFF);
        visuals.widgets.active.fg_stroke = Stroke::new(2.0, Color32::BLACK);
        visuals.selection.bg_fill = Color32::from_rgb(0x00, 0x7A, 0xCC);
        visuals.selection.stroke = Stroke::new(2.0, Color32::WHITE);
    }

    ctx.set_visuals(visuals);
}

/// SAN designator used as the on-piece glyph.
pub fn piece_letter(kind: PieceType) -> &'static str {
    match kind {
        PieceType::King => "K",
        PieceType::Queen => "Q",
        PieceType::Rook => "R",
        PieceType::Bishop => "B",
        PieceType::Knight => "N",
        PieceType::Pawn => "P",
    }
}

/// Draw a chess piece centred inside `rect`.
///
/// The glyph is a coloured disc with the SAN designator overlaid in
/// contrasting colour. Outlined for legibility on both light and dark
/// squares.
pub fn draw_piece(
    painter: &Painter,
    palette: &Palette,
    color: PColor,
    kind: PieceType,
    rect: Rect,
) {
    let center = rect.center();
    let radius = rect.width().min(rect.height()) * 0.42;

    let (fill, text_color) = match color {
        PColor::White => (palette.white_piece, palette.white_piece_text),
        PColor::Black => (palette.black_piece, palette.black_piece_text),
    };

    painter.circle_filled(center, radius, fill);
    painter.circle_stroke(center, radius, Stroke::new(1.5, palette.piece_outline));

    let font = FontId::proportional(radius * 1.05);
    painter.text(
        center,
        Align2::CENTER_CENTER,
        piece_letter(kind),
        font,
        text_color,
    );
}

/// Draw a small "captured piece" glyph, sized to fit the right-panel
/// captured-piece tray (T041a).
pub fn draw_mini_piece(
    painter: &Painter,
    palette: &Palette,
    color: PColor,
    kind: PieceType,
    center: Pos2,
    radius: f32,
) {
    let (fill, text_color) = match color {
        PColor::White => (palette.white_piece, palette.white_piece_text),
        PColor::Black => (palette.black_piece, palette.black_piece_text),
    };
    painter.circle_filled(center, radius, fill);
    painter.circle_stroke(center, radius, Stroke::new(1.0, palette.piece_outline));
    let font = FontId::proportional(radius * 1.1);
    painter.text(
        center,
        Align2::CENTER_CENTER,
        piece_letter(kind),
        font,
        text_color,
    );
}

/// Draw a translucent overlay on `rect` (used for last-move and
/// selection highlights).
pub fn draw_overlay(painter: &Painter, rect: Rect, color: Color32) {
    painter.rect_filled(rect, 0.0, color);
}

/// Draw a hollow rectangle outline (used for the keyboard-focus square
/// in T087 — pre-wired here via four line segments to remain robust
/// across small egui API tweaks).
#[allow(dead_code)] // consumed by keyboard-focus pass in T087
pub fn draw_outline(painter: &Painter, rect: Rect, color: Color32, width: f32) {
    let s = Stroke::new(width, color);
    let tl = rect.left_top();
    let tr = rect.right_top();
    let bl = rect.left_bottom();
    let br = rect.right_bottom();
    painter.line_segment([tl, tr], s);
    painter.line_segment([tr, br], s);
    painter.line_segment([br, bl], s);
    painter.line_segment([bl, tl], s);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn standard_palette_distinguishes_squares() {
        assert_ne!(Palette::STANDARD.light_square, Palette::STANDARD.dark_square);
    }

    #[test]
    fn alternate_palettes_distinguish_squares() {
        assert_ne!(Palette::DARK.light_square, Palette::DARK.dark_square);
        assert_ne!(
            Palette::HIGH_CONTRAST.light_square,
            Palette::HIGH_CONTRAST.dark_square
        );
    }

    #[test]
    fn piece_letter_is_san_designator() {
        assert_eq!(piece_letter(PieceType::King), "K");
        assert_eq!(piece_letter(PieceType::Queen), "Q");
        assert_eq!(piece_letter(PieceType::Pawn), "P");
    }
}
