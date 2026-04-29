//! CLI flag parsing (T027) per
//! [contracts/cli-flags.md](../../specs/001-chess-ai-rewrite/contracts/cli-flags.md).
//!
//! The flags are minimal — `chess-ai.exe` is a desktop GUI app and the
//! flags exist for support, testing, and reproducibility, not primary
//! use. Launching with no flags opens the GUI normally.
//!
//! ## Mutually exclusive exit-immediate flags
//!
//! `--version`, `--help`, and `--self-test` each cause the app to exit
//! without opening the GUI. They are mutually exclusive — passing more
//! than one is a parse error (exit code 2). Enforced via clap's
//! `ArgGroup`.
//!
//! ## Failure mode
//!
//! Unknown flags cause clap to print an error to stderr and exit with
//! code 2 (default clap behaviour); see the contract for the exit-code
//! table.

use clap::{ArgGroup, Parser};

/// Parsed command-line arguments.
///
/// Per the contract:
/// - At most one of `--version`, `--help`, `--self-test` may be set.
/// - Unknown flags fail parsing.
/// - There are no positional arguments.
#[derive(Debug, Parser)]
#[command(
    name = "chess-ai.exe",
    about = "chess-ai — single-binary native Windows chess",
    long_about = None,
    disable_version_flag = true,
    disable_help_flag = true,
    arg_required_else_help = false,
)]
#[command(group(
    ArgGroup::new("exit_actions")
        .args(["version", "help", "self_test"])
        .multiple(false)
))]
pub struct Cli {
    /// Enable file logging at WARN+ level to
    /// `%LOCALAPPDATA%\chess-ai\logs\` for this run only (does NOT
    /// persist to settings.toml).
    #[arg(long = "log-debug", default_value_t = false)]
    pub log_debug: bool,

    /// Move existing settings.toml to settings.toml.bak-<timestamp> and
    /// write a fresh defaults file. Then continue startup normally.
    #[arg(long = "reset-settings", default_value_t = false)]
    pub reset_settings: bool,

    /// Treat the directory containing chess-ai.exe as the data root.
    /// Reads/writes settings.toml from `<exe-dir>` instead of `%APPDATA%`.
    #[arg(long = "portable", default_value_t = false)]
    pub portable: bool,

    /// Print version line and exit.
    #[arg(long = "version", default_value_t = false)]
    pub version: bool,

    /// Print help text and exit.
    #[arg(long = "help", short = 'h', default_value_t = false)]
    pub help: bool,

    /// Run a built-in 30-second sanity test and exit.
    #[arg(long = "self-test", default_value_t = false)]
    pub self_test: bool,
}

/// Branches the boot sequence takes after CLI parse — either an
/// "exit-immediate" mode (`--version` / `--help` are handled by clap;
/// `--self-test` is handled by the boot path), or a normal GUI launch.
#[derive(Debug, PartialEq, Eq)]
pub enum CliAction {
    /// Print the version line and exit 0.
    PrintVersion,
    /// Print help text and exit 0.
    PrintHelp,
    /// Run the self-test and exit 0/1.
    RunSelfTest {
        /// Reset the on-disk settings before running self-test.
        reset_settings: bool,
        /// Use `<exe-dir>\settings.toml` instead of `%APPDATA%`.
        portable: bool,
    },
    /// Open the GUI normally, applying the given runtime overrides.
    Run {
        /// Force `[diagnostics] debug_logging = true` for this run.
        log_debug: bool,
        /// Reset the on-disk settings before reading them.
        reset_settings: bool,
        /// Use `<exe-dir>\settings.toml` instead of `%APPDATA%`.
        portable: bool,
    },
}

impl Cli {
    /// Decide which top-level boot branch to take. Mutually exclusive
    /// flags are already enforced by clap; this is a small dispatcher
    /// over the booleans.
    pub fn action(&self) -> CliAction {
        if self.version {
            CliAction::PrintVersion
        } else if self.help {
            CliAction::PrintHelp
        } else if self.self_test {
            CliAction::RunSelfTest {
                reset_settings: self.reset_settings,
                portable: self.portable,
            }
        } else {
            CliAction::Run {
                log_debug: self.log_debug,
                reset_settings: self.reset_settings,
                portable: self.portable,
            }
        }
    }
}

/// Compose the version line printed by `--version`.
///
/// Format (from [contracts/cli-flags.md §Flags](../../specs/001-chess-ai-rewrite/contracts/cli-flags.md)):
///
/// ```text
/// chess-ai <version> (engine <engine-version>, network <net-hash>)
/// ```
///
/// The `<net-hash>` is the SHA-256 computed at build time by `build.rs`
/// (T023) and exposed as `CHESS_NETWORK_HASH`. We show only the first
/// 16 hex characters for brevity.
pub fn version_line() -> String {
    let app = env!("CARGO_PKG_VERSION");
    let engine = chess_engine::version();
    let net_full = env!("CHESS_NETWORK_HASH");
    let net = &net_full[..net_full.len().min(16)];
    format!("chess-ai {app} (engine {engine}, network {net})")
}

#[cfg(test)]
mod tests {
    use super::*;
    use clap::Parser;

    #[test]
    fn parses_no_args() {
        let cli = Cli::parse_from(["chess-ai"]);
        assert!(matches!(cli.action(), CliAction::Run { .. }));
    }

    #[test]
    fn parses_version_flag() {
        let cli = Cli::parse_from(["chess-ai", "--version"]);
        assert_eq!(cli.action(), CliAction::PrintVersion);
    }

    #[test]
    fn parses_self_test_flag() {
        let cli = Cli::parse_from(["chess-ai", "--self-test"]);
        assert_eq!(
            cli.action(),
            CliAction::RunSelfTest {
                reset_settings: false,
                portable: false,
            }
        );
    }

    #[test]
    fn version_and_self_test_are_mutually_exclusive() {
        let result = Cli::try_parse_from(["chess-ai", "--version", "--self-test"]);
        assert!(result.is_err());
    }

    #[test]
    fn version_and_help_are_mutually_exclusive() {
        let result = Cli::try_parse_from(["chess-ai", "--version", "--help"]);
        assert!(result.is_err());
    }

    #[test]
    fn rejects_unknown_flag() {
        let result = Cli::try_parse_from(["chess-ai", "--bogus-flag"]);
        assert!(result.is_err());
    }

    #[test]
    fn rejects_positional_argument() {
        let result = Cli::try_parse_from(["chess-ai", "extra-arg"]);
        assert!(result.is_err());
    }

    #[test]
    fn portable_and_reset_can_combine() {
        let cli = Cli::try_parse_from(["chess-ai", "--portable", "--reset-settings"]).unwrap();
        let action = cli.action();
        match action {
            CliAction::Run {
                portable,
                reset_settings,
                ..
            } => {
                assert!(portable);
                assert!(reset_settings);
            }
            _ => panic!("expected Run action"),
        }
    }

    #[test]
    fn version_line_format() {
        let s = version_line();
        assert!(s.starts_with("chess-ai "), "got {s}");
        assert!(s.contains("engine "), "got {s}");
        assert!(s.contains("network "), "got {s}");
    }
}
