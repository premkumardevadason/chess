//! `chess-ai` — single-binary native Windows chess application.
//!
//! ## Boot sequence (T028)
//!
//! 1. Parse CLI flags ([`cli::Cli`]).
//! 2. Dispatch on [`cli::CliAction`]:
//!    - `PrintVersion` → write [`cli::version_line`] to stdout, exit 0.
//!    - `RunSelfTest` → run [`run_self_test`] and exit 0/1.
//!    - `Run { .. }` → continue boot.
//! 3. Initialise tracing-subscriber (env-filter; WARN+ to stderr,
//!    optional file sink under `%LOCALAPPDATA%\chess-ai\logs\` when
//!    `--log-debug` is set).
//! 4. Resolve the settings path (`%APPDATA%\chess-ai\settings.toml`
//!    by default; `<exe-dir>\settings.toml` when `--portable`).
//! 5. If `--reset-settings` is set, rename any existing settings file
//!    to `settings.toml.bak-<unix-ts>` before reading.
//! 6. Load settings via [`settings::UserSettings::load`] (corruption-
//!    tolerant — falls back to defaults).
//! 7. Spawn the engine ([`engine_link::EngineLink::spawn`]).
//! 8. Hand off to `eframe::run_native` to enter the GUI loop.
//!
//! The egui UI itself is the placeholder shipped in T028; the full
//! Game / Settings / About screens land in CP-E (US1) onwards.

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::path::PathBuf;
use std::process::ExitCode;
use std::time::{SystemTime, UNIX_EPOCH};

use clap::{CommandFactory, Parser};
use tracing::{info, warn};
use tracing_subscriber::{filter::LevelFilter, fmt, prelude::*, EnvFilter};

mod cli;
mod engine_link;
mod self_test;
mod settings;
mod ui;

use cli::{Cli, CliAction};
use engine_link::EngineLink;
use settings::UserSettings;
use ui::{AboutScreen, GameScreen, SettingsOutcome, SettingsScreen};

/// Exit codes per [contracts/cli-flags.md §Exit codes](../../specs/001-chess-ai-rewrite/contracts/cli-flags.md).
const EXIT_OK: u8 = 0;
const EXIT_SELF_TEST_FAILED: u8 = 1;
const EXIT_ENGINE_INIT_FAILED: u8 = 3;

fn main() -> ExitCode {
    let cli = Cli::parse();

    match cli.action() {
        CliAction::PrintVersion => {
            init_tracing(false, false);
            println!("{}", cli::version_line());
            ExitCode::from(EXIT_OK)
        }
        CliAction::PrintHelp => {
            init_tracing(false, false);
            let mut cmd = Cli::command();
            cmd.print_help().expect("print help");
            println!();
            ExitCode::from(EXIT_OK)
        }
        CliAction::RunSelfTest {
            reset_settings,
            portable,
        } => {
            init_tracing(cli.log_debug, cli.log_debug);
            if reset_settings {
                if let Ok(path) = settings::resolve_settings_path(portable) {
                    reset_settings_file(&path);
                }
            }
            match run_self_test() {
                Ok(()) => ExitCode::from(EXIT_OK),
                Err(e) => {
                    eprintln!("self-test failed: {e}");
                    ExitCode::from(EXIT_SELF_TEST_FAILED)
                }
            }
        }
        CliAction::Run {
            log_debug,
            reset_settings,
            portable,
        } => {
            let settings_path = match settings::resolve_settings_path(portable) {
                Ok(p) => p,
                Err(e) => {
                    eprintln!("startup error: {e}");
                    return ExitCode::from(EXIT_ENGINE_INIT_FAILED);
                }
            };

            if reset_settings {
                reset_settings_file(&settings_path);
            }

            let mut settings = UserSettings::load(&settings_path);
            if log_debug {
                settings.diagnostics.debug_logging = true;
            }

            let file_logging = log_debug || settings.diagnostics.debug_logging;
            init_tracing(log_debug || settings.diagnostics.debug_logging, file_logging);

            match run_gui(settings_path, settings) {
                Ok(()) => ExitCode::from(EXIT_OK),
                Err(BootError::EngineInit(msg)) => {
                    eprintln!("engine init failed: {msg}");
                    ExitCode::from(EXIT_ENGINE_INIT_FAILED)
                }
                Err(BootError::Other(e)) => {
                    eprintln!("startup error: {e}");
                    ExitCode::from(EXIT_ENGINE_INIT_FAILED)
                }
            }
        }
    }
}

/// Errors that can stop the boot sequence after CLI parse. Settings
/// problems are *not* fatal (they degrade to in-memory defaults per
/// contracts/settings-file.md §5).
enum BootError {
    EngineInit(String),
    Other(anyhow::Error),
}

impl<E: Into<anyhow::Error>> From<E> for BootError {
    fn from(err: E) -> Self {
        BootError::Other(err.into())
    }
}

/// Initialise `tracing` with WARN+ to stderr by default and optional
/// daily file logging under `%LOCALAPPDATA%\\chess-ai\\logs`.
fn init_tracing(debug_filter: bool, file_logging: bool) {
    let default = if debug_filter { "debug" } else { "warn" };
    let filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new(default));
    let stderr_layer = fmt::layer()
        .with_target(false)
        .with_writer(std::io::stderr)
        .with_filter(LevelFilter::WARN);

    if file_logging {
        if let Some(appender) = make_file_appender() {
            let _ = tracing_subscriber::registry()
                .with(filter)
                .with(stderr_layer)
                .with(
                    fmt::layer()
                        .with_target(false)
                        .with_ansi(false)
                        .with_writer(appender)
                        .with_filter(LevelFilter::DEBUG),
                )
                .try_init();
            return;
        }
    }

    let _ = tracing_subscriber::registry()
        .with(filter)
        .with(stderr_layer)
        .try_init();
}

fn make_file_appender() -> Option<tracing_appender::rolling::RollingFileAppender> {
    let proj = directories::ProjectDirs::from("", "", "chess-ai")?;
    let log_dir = proj.data_local_dir().join("logs");
    if std::fs::create_dir_all(&log_dir).is_err() {
        return None;
    }

    prune_old_log_files(&log_dir, 7);
    Some(tracing_appender::rolling::daily(&log_dir, "chess-ai.log"))
}

fn prune_old_log_files(dir: &std::path::Path, keep: usize) {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return;
    };

    let mut files: Vec<PathBuf> = entries
        .flatten()
        .map(|e| e.path())
        .filter(|p| {
            p.file_name()
                .and_then(|n| n.to_str())
                .map(|n| n.starts_with("chess-ai.log."))
                .unwrap_or(false)
        })
        .collect();

    files.sort();
    if files.len() <= keep {
        return;
    }

    let remove_count = files.len().saturating_sub(keep);
    for old in files.into_iter().take(remove_count) {
        let _ = std::fs::remove_file(old);
    }
}

/// Move existing settings file out of the way for a clean restart per
/// `--reset-settings` semantics in
/// [contracts/cli-flags.md](../../specs/001-chess-ai-rewrite/contracts/cli-flags.md).
fn reset_settings_file(path: &PathBuf) {
    if !path.exists() {
        return;
    }
    let ts = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let mut bak_name = path.as_os_str().to_owned();
    bak_name.push(format!(".bak-{ts}"));
    let bak_path = PathBuf::from(bak_name);
    match std::fs::rename(path, &bak_path) {
        Ok(()) => info!(backup = %bak_path.display(), "settings reset; original archived"),
        Err(e) => warn!(error = %e, "could not archive existing settings.toml"),
    }
}

/// Normal GUI boot: load settings, spawn engine, run the egui app.
fn run_gui(settings_path: PathBuf, settings: UserSettings) -> Result<(), BootError> {
    info!(settings_path = %settings_path.display(), "resolved settings path");
    info!(?settings.engine, "settings loaded");

    let engine = EngineLink::spawn();
    info!("engine spawned");

    let native_options = eframe::NativeOptions::default();
    let app = ChessApp::new(settings, engine, settings_path);

    eframe::run_native(
        "chess-ai",
        native_options,
        Box::new(|_cc| Ok(Box::new(app))),
    )
    .map_err(|e| BootError::EngineInit(format!("eframe failed: {e}")))?;

    Ok(())
}

/// Run the built-in 30-second sanity test per `--self-test` per
/// [contracts/cli-flags.md](../../specs/001-chess-ai-rewrite/contracts/cli-flags.md).
///
/// **CP-D scope (placeholder)**: spawn the engine, send a
/// `FixedDepth(4)` search at startpos, assert a legal best move is
/// returned. The richer 1000-position rules suite + reproducibility
/// determinism check is wired in CP-J (T093).
fn run_self_test() -> anyhow::Result<()> {
    self_test::run_self_test()
}

/// Top-level eframe app for CP-E (US1) onwards. Owns the [`EngineLink`]
/// and the active [`GameScreen`]; on window close it issues
/// `Stop` + `Shutdown` so the engine worker exits cleanly even mid-search
/// (T047, per [contracts/ui-interactions.md §6.4]).
///
/// Also hosts the [`SettingsScreen`] (T053–T060). When the game screen
/// raises a "open settings" intent, we swap to the settings screen for
/// subsequent frames and swap back on close.
struct ChessApp {
    game: GameScreen,
    settings_screen: Option<SettingsScreen>,
    about_screen: Option<AboutScreen>,
    engine: EngineLink,
    settings_path: PathBuf,
}

impl ChessApp {
    fn new(settings: UserSettings, engine: EngineLink, settings_path: PathBuf) -> Self {
        Self {
            game: GameScreen::new(settings),
            settings_screen: None,
            about_screen: None,
            engine,
            settings_path,
        }
    }
}

impl eframe::App for ChessApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        if let Some(screen) = self.settings_screen.as_mut() {
            match screen.update(ctx, &mut self.engine) {
                SettingsOutcome::Pending => {}
                SettingsOutcome::Closed(updated) => {
                    self.game.apply_settings(updated);
                    self.settings_screen = None;
                }
            }
        } else if let Some(screen) = self.about_screen.as_mut() {
            if screen.update(ctx) {
                self.about_screen = None;
            }
        } else {
            self.game.update(ctx, &mut self.engine);
            if self.game.take_open_settings_request() {
                self.settings_screen = Some(SettingsScreen::new(
                    self.game.settings.clone(),
                    self.settings_path.clone(),
                ));
            } else if self.game.take_open_about_request() {
                self.about_screen = Some(AboutScreen::new());
            }
        }
    }

    fn on_exit(&mut self) {
        // T047: shut the engine worker down cleanly on window close.
        // - `stop()` aborts any in-flight search (idempotent if idle).
        // - `shutdown()` terminates the worker thread.
        self.engine.stop();
        self.engine.shutdown();
    }
}
