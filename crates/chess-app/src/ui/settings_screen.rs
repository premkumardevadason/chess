//! Settings screen — section-organised form for `UserSettings`.
//!
//! Implements US2 tasks T053 (scaffold), T054 (strength dropdown), T055
//! (time-control picker incl. custom-grammar input), T056 (threads
//! slider, repro checkbox, hint-time input), T057 (live `SetConfig` to
//! the running engine on threads/log changes), T058 (debounced
//! `dirty`-flag → atomic save every 250 ms while edits are coming in
//! and on close), T060 ("Reset to defaults" button).
//!
//! See:
//! - [contracts/ui-interactions.md §3](../../specs/001-chess-ai-rewrite/contracts/ui-interactions.md)
//! - [contracts/settings-file.md §2 / §3 / §4.2](../../specs/001-chess-ai-rewrite/contracts/settings-file.md)
//! - [data-model.md §8](../../specs/001-chess-ai-rewrite/data-model.md)

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use chess_engine::StrengthPreset;
use egui::{Align, Layout, RichText};
use tracing::warn;

use crate::engine_link::EngineLink;
use crate::settings::{
    parse_time_control_str, num_cpus_estimate, BoardOrientation, HumanColor, Theme,
    TimeControlParseError, UserSettings,
};

/// Hard ceiling on the threads slider regardless of CPU count.
const MAX_THREAD_CAP: u8 = 8;
/// Wall-clock budget we wait for after the last edit before flushing
/// the working settings to disk. Per
/// [contracts/settings-file.md §4.2](../../specs/001-chess-ai-rewrite/contracts/settings-file.md).
const SAVE_DEBOUNCE: Duration = Duration::from_millis(250);

/// Outcome of one [`SettingsScreen::update`] call. Tells the host
/// whether the user dismissed the screen and, if so, what the latest
/// (already-persisted) settings are.
#[derive(Debug)]
pub enum SettingsOutcome {
    /// Screen still active; keep rendering it next frame.
    Pending,
    /// User pressed Done / Esc. The host should switch back to the
    /// game screen and adopt these settings as canonical.
    Closed(UserSettings),
}

/// Standalone settings screen. Owns a working copy of `UserSettings`,
/// debounces writes to disk, and forwards thread / debug-logging
/// changes to the live engine.
pub struct SettingsScreen {
    /// Working copy. Edits are applied to this struct first, then
    /// flushed to disk by the debounce driver.
    working: UserSettings,
    /// On-disk path resolved at app boot.
    settings_path: PathBuf,
    /// `true` between an edit and the next successful save.
    dirty: bool,
    /// Deadline at which a dirty `working` will be flushed.
    save_after: Option<Instant>,
    /// Last save error message, if any. Surfaced as a toast.
    save_error: Option<String>,
    /// Editable time-control text field. Mirrors
    /// `working.engine.default_time_control` but holds free-form text
    /// while the user is typing.
    tc_text: String,
    /// Last parse error for `tc_text`, displayed under the field.
    tc_error: Option<String>,
    /// Snapshot of the values we last forwarded to the engine via
    /// `Command::SetConfig`. Used to debounce live updates.
    last_pushed_threads: u8,
    last_pushed_debug: bool,
    /// Maximum slider upper bound (computed once at construction).
    thread_upper: u8,
}

impl SettingsScreen {
    /// Construct from the host's canonical `UserSettings` and the
    /// resolved on-disk path.
    pub fn new(initial: UserSettings, settings_path: PathBuf) -> Self {
        let cpus = num_cpus_estimate();
        let thread_upper = MAX_THREAD_CAP.min(cpus.max(1));
        let last_pushed_threads = initial.engine.max_threads;
        let last_pushed_debug = initial.diagnostics.debug_logging;
        let tc_text = initial.engine.default_time_control.clone();
        Self {
            working: initial,
            settings_path,
            dirty: false,
            save_after: None,
            save_error: None,
            tc_text,
            tc_error: None,
            last_pushed_threads,
            last_pushed_debug,
            thread_upper,
        }
    }

    /// Render one frame. Returns [`SettingsOutcome::Closed`] when the
    /// user dismisses the screen.
    pub fn update(
        &mut self,
        ctx: &egui::Context,
        engine: &mut EngineLink,
    ) -> SettingsOutcome {
        // Esc closes the screen.
        let escape_pressed = ctx.input(|i| i.key_pressed(egui::Key::Escape));

        let mut close_requested = escape_pressed;

        egui::TopBottomPanel::top("settings_top").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.heading("Settings");
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    if ui.button("Done").clicked() {
                        close_requested = true;
                    }
                    if ui.button("Reset to defaults").clicked() {
                        self.reset_to_defaults();
                    }
                });
            });
            ui.add_space(4.0);
        });

        egui::CentralPanel::default().show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                self.render_engine_section(ui);
                ui.add_space(12.0);
                self.render_ui_section(ui);
                ui.add_space(12.0);
                self.render_diagnostics_section(ui);

                if let Some(err) = &self.save_error {
                    ui.add_space(12.0);
                    ui.colored_label(
                        egui::Color32::from_rgb(0xE0, 0x6C, 0x6C),
                        format!("Could not save settings: {err}"),
                    );
                }
            });
        });

        // Debounced write-back.
        self.maybe_flush_to_disk(false);

        // Live engine reconfig (T057).
        self.maybe_push_engine_config(engine);

        if close_requested {
            // Force-flush before exiting (T058 close-flush per
            // contracts/settings-file.md §4.2).
            self.maybe_flush_to_disk(true);
            return SettingsOutcome::Closed(self.working.clone());
        }

        // Keep ticking so the debounce timer fires even without input.
        if self.dirty {
            ctx.request_repaint_after(SAVE_DEBOUNCE);
        }

        SettingsOutcome::Pending
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    fn render_engine_section(&mut self, ui: &mut egui::Ui) {
        ui.label(RichText::new("Engine").heading());
        ui.add_space(4.0);

        // Strength dropdown (T054).
        ui.horizontal(|ui| {
            ui.label("Default strength:");
            let mut current = self.working.engine.default_strength;
            let label = strength_label(current);
            egui::ComboBox::from_id_salt("strength_preset")
                .selected_text(label)
                .show_ui(ui, |ui| {
                    for preset in [
                        StrengthPreset::Beginner,
                        StrengthPreset::Intermediate,
                        StrengthPreset::Advanced,
                        StrengthPreset::Maximum,
                    ] {
                        ui.selectable_value(&mut current, preset, strength_label(preset));
                    }
                });
            if current != self.working.engine.default_strength {
                self.working.engine.default_strength = current;
                self.mark_dirty();
            }
        });

        // Time control picker (T055): preset combo + custom-input row.
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            ui.label("Default time control:");
            let mut chosen_preset = current_tc_preset(&self.tc_text);
            let label = preset_label(chosen_preset);
            egui::ComboBox::from_id_salt("tc_preset")
                .selected_text(label)
                .show_ui(ui, |ui| {
                    for preset in TC_PRESETS.iter().copied() {
                        ui.selectable_value(&mut chosen_preset, Some(preset), preset_label(Some(preset)));
                    }
                    ui.selectable_value(&mut chosen_preset, None, "Custom…");
                });
            if let Some(p) = chosen_preset {
                if Some(p) != current_tc_preset(&self.tc_text) {
                    self.tc_text = p.to_string();
                    self.commit_time_control();
                }
            }
        });

        ui.horizontal(|ui| {
            ui.label("Custom:");
            let resp = ui.text_edit_singleline(&mut self.tc_text);
            if resp.changed() {
                self.commit_time_control();
            }
        });
        if let Some(err) = &self.tc_error {
            ui.colored_label(
                egui::Color32::from_rgb(0xE0, 0x6C, 0x6C),
                format!("Invalid time control: {err}"),
            );
        }
        ui.label(
            RichText::new(
                "Grammar: PerMove:<dur>, Total:<game>:<inc>, FixedDepth:<n>, FixedNodes:<n>",
            )
            .small()
            .weak(),
        );

        // Max threads slider (T056).
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            ui.label("Max threads:");
            let mut threads = self.working.engine.max_threads as u32;
            let upper = self.thread_upper as u32;
            let resp = ui.add(egui::Slider::new(&mut threads, 1..=upper));
            if resp.changed() {
                self.working.engine.max_threads = threads.clamp(1, upper) as u8;
                self.mark_dirty();
            }
        });

        // Reproducible mode default (T056).
        ui.add_space(4.0);
        let mut repro = self.working.engine.reproducible_mode_default;
        if ui.checkbox(&mut repro, "Default new games to Reproducible Mode").changed() {
            self.working.engine.reproducible_mode_default = repro;
            self.mark_dirty();
        }

        // Hint time (T056).
        ui.add_space(4.0);
        ui.horizontal(|ui| {
            ui.label("Hint budget (ms):");
            let mut hint = self.working.engine.hint_time_ms;
            let resp = ui.add(
                egui::DragValue::new(&mut hint)
                    .range(100..=60_000)
                    .speed(50.0),
            );
            if resp.changed() {
                self.working.engine.hint_time_ms = hint.clamp(100, 60_000);
                self.mark_dirty();
            }
        });
    }

    fn render_ui_section(&mut self, ui: &mut egui::Ui) {
        ui.label(RichText::new("UI").heading());
        ui.add_space(4.0);

        ui.horizontal(|ui| {
            ui.label("Theme:");
            let mut theme = self.working.ui.theme;
            egui::ComboBox::from_id_salt("ui_theme")
                .selected_text(theme_label(theme))
                .show_ui(ui, |ui| {
                    for t in [Theme::Standard, Theme::Dark, Theme::HighContrast] {
                        ui.selectable_value(&mut theme, t, theme_label(t));
                    }
                });
            if theme != self.working.ui.theme {
                self.working.ui.theme = theme;
                self.mark_dirty();
            }
        });

        ui.horizontal(|ui| {
            ui.label("Board orientation:");
            let mut bo = self.working.ui.board_orientation;
            egui::ComboBox::from_id_salt("ui_orientation")
                .selected_text(orientation_label(bo))
                .show_ui(ui, |ui| {
                    for o in [
                        BoardOrientation::Auto,
                        BoardOrientation::WhiteAtBottom,
                        BoardOrientation::BlackAtBottom,
                    ] {
                        ui.selectable_value(&mut bo, o, orientation_label(o));
                    }
                });
            if bo != self.working.ui.board_orientation {
                self.working.ui.board_orientation = bo;
                self.mark_dirty();
            }
        });

        ui.horizontal(|ui| {
            ui.label("Last colour played:");
            let mut hc = self.working.ui.last_human_color;
            egui::ComboBox::from_id_salt("ui_last_color")
                .selected_text(match hc {
                    HumanColor::White => "White",
                    HumanColor::Black => "Black",
                })
                .show_ui(ui, |ui| {
                    ui.selectable_value(&mut hc, HumanColor::White, "White");
                    ui.selectable_value(&mut hc, HumanColor::Black, "Black");
                });
            if hc != self.working.ui.last_human_color {
                self.working.ui.last_human_color = hc;
                self.mark_dirty();
            }
        });

        let mut anim = self.working.ui.animate_moves;
        if ui.checkbox(&mut anim, "Animate moves").changed() {
            self.working.ui.animate_moves = anim;
            self.mark_dirty();
        }
    }

    fn render_diagnostics_section(&mut self, ui: &mut egui::Ui) {
        ui.label(RichText::new("Diagnostics").heading());
        ui.add_space(4.0);

        let mut dbg = self.working.diagnostics.debug_logging;
        if ui.checkbox(&mut dbg, "Enable debug logging to file").changed() {
            self.working.diagnostics.debug_logging = dbg;
            self.mark_dirty();
        }
        ui.label(
            RichText::new(
                "Logs land under %LOCALAPPDATA%\\chess-ai\\logs\\ when enabled.",
            )
            .small()
            .weak(),
        );
    }

    // ------------------------------------------------------------------
    // Persistence + live engine push
    // ------------------------------------------------------------------

    fn mark_dirty(&mut self) {
        self.dirty = true;
        self.save_after = Some(Instant::now() + SAVE_DEBOUNCE);
    }

    fn commit_time_control(&mut self) {
        match parse_time_control_str(&self.tc_text) {
            Ok(_) => {
                self.tc_error = None;
                if self.tc_text != self.working.engine.default_time_control {
                    self.working.engine.default_time_control = self.tc_text.clone();
                    self.mark_dirty();
                }
            }
            Err(e) => {
                self.tc_error = Some(format_tc_error(e));
            }
        }
    }

    fn reset_to_defaults(&mut self) {
        self.working = UserSettings::default();
        self.tc_text = self.working.engine.default_time_control.clone();
        self.tc_error = None;
        self.dirty = true;
        // Force immediate write per T060: "save immediately".
        self.save_after = Some(Instant::now());
    }

    fn maybe_flush_to_disk(&mut self, force: bool) {
        if !self.dirty {
            return;
        }
        if !force {
            let Some(when) = self.save_after else {
                return;
            };
            if Instant::now() < when {
                return;
            }
        }
        match self.working.save(&self.settings_path) {
            Ok(()) => {
                self.dirty = false;
                self.save_after = None;
                self.save_error = None;
            }
            Err(e) => {
                warn!(error = %e, "settings save failed");
                self.save_error = Some(e.to_string());
                // Back off briefly before retrying.
                self.save_after = Some(Instant::now() + Duration::from_millis(500));
            }
        }
    }

    fn maybe_push_engine_config(&mut self, engine: &mut EngineLink) {
        let threads = self.working.engine.max_threads;
        let dbg = self.working.diagnostics.debug_logging;
        if threads != self.last_pushed_threads || dbg != self.last_pushed_debug {
            engine.set_config(threads, dbg);
            self.last_pushed_threads = threads;
            self.last_pushed_debug = dbg;
        }
    }

    // ------------------------------------------------------------------
    // Test helpers (used by tests/settings_screen_smoke.rs in CP-G+)
    // ------------------------------------------------------------------

    /// Read-only view of the working copy.
    #[doc(hidden)]
    pub fn working_for_test(&self) -> &UserSettings {
        &self.working
    }

    /// Force a flush regardless of the debounce timer (test only).
    #[doc(hidden)]
    pub fn flush_for_test(&mut self) {
        self.maybe_flush_to_disk(true);
    }

    /// Mark working dirty without going through the UI (test only).
    #[doc(hidden)]
    pub fn touch_for_test(&mut self) {
        self.mark_dirty();
    }

    /// Borrow the working settings for direct test mutation. Bypasses
    /// the debounce timer; callers must follow up with [`Self::touch_for_test`].
    #[doc(hidden)]
    pub fn working_mut_for_test(&mut self) -> &mut UserSettings {
        &mut self.working
    }

    /// Path the screen flushes to. Test helper.
    #[doc(hidden)]
    pub fn settings_path_for_test(&self) -> &Path {
        &self.settings_path
    }
}

// ----------------------------------------------------------------------
// Static labels + presets
// ----------------------------------------------------------------------

const TC_PRESETS: &[&str] = &[
    "PerMove:1s",
    "PerMove:5s",
    "PerMove:10s",
    "PerMove:30s",
    "PerMove:60s",
    "FixedDepth:8",
    "FixedDepth:12",
    "FixedDepth:16",
    "Total:5m:3s",
    "Total:10m:0s",
];

fn current_tc_preset(text: &str) -> Option<&'static str> {
    TC_PRESETS.iter().copied().find(|p| *p == text)
}

fn preset_label(p: Option<&'static str>) -> &'static str {
    match p {
        Some(s) => s,
        None => "Custom…",
    }
}

fn strength_label(p: StrengthPreset) -> &'static str {
    match p {
        StrengthPreset::Beginner => "Beginner",
        StrengthPreset::Intermediate => "Intermediate",
        StrengthPreset::Advanced => "Advanced",
        StrengthPreset::Maximum => "Maximum",
    }
}

fn theme_label(t: Theme) -> &'static str {
    match t {
        Theme::Standard => "Standard",
        Theme::Dark => "Dark",
        Theme::HighContrast => "High contrast",
    }
}

fn orientation_label(o: BoardOrientation) -> &'static str {
    match o {
        BoardOrientation::Auto => "Auto (follow player colour)",
        BoardOrientation::WhiteAtBottom => "White at bottom",
        BoardOrientation::BlackAtBottom => "Black at bottom",
    }
}

fn format_tc_error(e: TimeControlParseError) -> String {
    e.to_string()
}
