//! About screen (T084).

use egui::{Align, Layout, RichText};

const REPO_URL: &str = "https://github.com/premkumardevadason/chess";

const MIT_LICENSE_TEXT: &str = "MIT License\n\nCopyright (c) 2026 chess-ai contributors\n\nPermission is hereby granted, free of charge, to any person obtaining a copy\nof this software and associated documentation files (the \"Software\"), to deal\nin the Software without restriction, including without limitation the rights\nto use, copy, modify, merge, publish, distribute, sublicense, and/or sell\ncopies of the Software, and to permit persons to whom the Software is\nfurnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all\ncopies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\nIMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\nFITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\nAUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\nLIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\nOUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\nSOFTWARE.";

/// Standalone about screen.
pub struct AboutScreen {
    open_link_error: Option<String>,
}

impl AboutScreen {
    /// Create an empty about screen state.
    pub fn new() -> Self {
        Self {
            open_link_error: None,
        }
    }

    /// Render the screen; returns `true` if user requested close.
    pub fn update(&mut self, ctx: &egui::Context) -> bool {
        let mut close = false;
        let network_hash = env!("CHESS_NETWORK_HASH");
        let short_network_hash = network_hash.get(0..8).unwrap_or(network_hash);

        egui::TopBottomPanel::top("about_top_bar").show(ctx, |ui| {
            ui.horizontal(|ui| {
                if ui.button("Back").clicked() {
                    close = true;
                }
                ui.heading("About chess-ai");
            });
        });

        egui::CentralPanel::default().show(ctx, |ui| {
            ui.with_layout(Layout::top_down(Align::Min), |ui| {
                ui.heading("chess-ai");
                ui.label(format!("Version: {}", env!("CARGO_PKG_VERSION")));
                ui.label("Engine: forked from Carp 3.0.1");
                ui.label(format!("Network hash: {short_network_hash}"));
                ui.label(format!("Build commit: {}", env!("VERGEN_GIT_SHA")));
                ui.label(format!(
                    "Build timestamp: {}",
                    env!("VERGEN_BUILD_TIMESTAMP")
                ));

                ui.add_space(8.0);
                if ui
                    .button("Open Repository")
                    .on_hover_text(REPO_URL)
                    .clicked()
                {
                    if let Err(err) = open::that(REPO_URL) {
                        self.open_link_error = Some(format!("failed to open link: {err}"));
                    }
                }
                if let Some(err) = &self.open_link_error {
                    ui.colored_label(egui::Color32::RED, err);
                }

                ui.separator();
                ui.label(RichText::new("MIT License").strong());
                egui::ScrollArea::vertical()
                    .id_salt("about_license_scroll")
                    .max_height(360.0)
                    .show(ui, |ui| {
                        ui.add(
                            egui::Label::new(MIT_LICENSE_TEXT)
                                .wrap_mode(egui::TextWrapMode::Wrap),
                        );
                    });
            });
        });

        close
    }
}
