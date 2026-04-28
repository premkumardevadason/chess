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

use clap::Parser;
use tracing::{info, warn};
use tracing_subscriber::{fmt, prelude::*, EnvFilter};

mod cli;
mod engine_link;
mod settings;
mod ui;

use cli::{Cli, CliAction};
use engine_link::EngineLink;
use settings::UserSettings;
use ui::{GameScreen, SettingsOutcome, SettingsScreen};

/// Exit codes per [contracts/cli-flags.md §Exit codes](../../specs/001-chess-ai-rewrite/contracts/cli-flags.md).
const EXIT_OK: u8 = 0;
const EXIT_SELF_TEST_FAILED: u8 = 1;
const EXIT_ENGINE_INIT_FAILED: u8 = 3;

fn main() -> ExitCode {
    let cli = Cli::parse();

    // Set up tracing first so subsequent steps emit structured logs.
    init_tracing(cli.log_debug);

    match cli.action() {
        CliAction::PrintVersion => {
            println!("{}", cli::version_line());
            ExitCode::from(EXIT_OK)
        }
        CliAction::RunSelfTest => match run_self_test() {
            Ok(()) => ExitCode::from(EXIT_OK),
            Err(e) => {
                eprintln!("self-test failed: {e}");
                ExitCode::from(EXIT_SELF_TEST_FAILED)
            }
        },
        CliAction::Run {
            log_debug: _,
            reset_settings,
            portable,
        } => match run_gui(reset_settings, portable) {
            Ok(()) => ExitCode::from(EXIT_OK),
            Err(BootError::EngineInit(msg)) => {
                eprintln!("engine init failed: {msg}");
                ExitCode::from(EXIT_ENGINE_INIT_FAILED)
            }
            Err(BootError::Other(e)) => {
                eprintln!("startup error: {e}");
                ExitCode::from(EXIT_ENGINE_INIT_FAILED)
            }
        },
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

/// Initialise `tracing` with WARN+ to stderr by default; respects
/// `RUST_LOG` for fine-grained control. The file-sink path will be
/// wired in CP-J (T088); for now `--log-debug` simply lowers the
/// stderr level to DEBUG.
fn init_tracing(log_debug: bool) {
    let default = if log_debug { "debug" } else { "warn" };
    let filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new(default));
    let _ = tracing_subscriber::registry()
        .with(filter)
        .with(fmt::layer().with_target(false).with_writer(std::io::stderr))
        .try_init();
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
fn run_gui(reset_settings: bool, portable: bool) -> Result<(), BootError> {
    let settings_path = settings::resolve_settings_path(portable).map_err(BootError::Other)?;
    info!(settings_path = %settings_path.display(), "resolved settings path");

    if reset_settings {
        reset_settings_file(&settings_path);
    }

    let settings = UserSettings::load(&settings_path);
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
    use chess_core::{legal_moves, Position, STARTPOS_FEN};
    use chess_engine::{Command, EngineConfig, Event, TimeControl};
    use std::time::{Duration, Instant};

    println!("chess-ai self-test starting…");
    let engine = chess_engine::Engine::new();
    let handle = engine.spawn();

    let pos = Position::from_fen(STARTPOS_FEN)?;
    handle.send(Command::SetPosition {
        position: pos,
        history: vec![],
    })?;

    let cfg = EngineConfig {
        time_control: TimeControl::FixedDepth(4),
        max_threads: 1,
        ..EngineConfig::default()
    };
    handle.send(Command::StartSearch { config: cfg })?;

    let started = Instant::now();
    let mut best_move = None;
    while started.elapsed() < Duration::from_secs(30) {
        for event in handle.drain() {
            match event {
                Event::SearchComplete(result) => {
                    best_move = Some(result.mv);
                }
                Event::SearchAborted => {
                    anyhow::bail!("search was unexpectedly aborted");
                }
                _ => {}
            }
        }
        if best_move.is_some() {
            break;
        }
        std::thread::sleep(Duration::from_millis(20));
    }

    let _ = handle.send(Command::Shutdown);

    let mv = best_move.ok_or_else(|| anyhow::anyhow!("no SearchComplete in 30 s"))?;
    let legal: Vec<_> = legal_moves(&pos).into_iter().collect();
    if !legal.contains(&mv) {
        anyhow::bail!(
            "returned move {mv} is illegal at startpos",
            mv = mv.to_long_algebraic()
        );
    }
    println!(
        "self-test ok: best move {} found at depth 4",
        mv.to_long_algebraic()
    );
    Ok(())
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
    engine: EngineLink,
    settings_path: PathBuf,
}

impl ChessApp {
    fn new(settings: UserSettings, engine: EngineLink, settings_path: PathBuf) -> Self {
        Self {
            game: GameScreen::new(settings),
            settings_screen: None,
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
        } else {
            self.game.update(ctx, &mut self.engine);
            if self.game.take_open_settings_request() {
                self.settings_screen = Some(SettingsScreen::new(
                    self.game.settings.clone(),
                    self.settings_path.clone(),
                ));
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
