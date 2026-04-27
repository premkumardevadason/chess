//! User settings — schema (T024), TOML I/O with atomic writes (T025), and
//! the `TimeControl` grammar parser (T026).
//!
//! See [contracts/settings-file.md](../../specs/001-chess-ai-rewrite/contracts/settings-file.md)
//! for the on-disk format, atomicity contract, recovery policy, and grammar.
//! See [data-model.md §8](../../specs/001-chess-ai-rewrite/data-model.md) for
//! the field reference.
//!
//! ## Module shape
//!
//! ```text
//! UserSettings ── EngineSettings
//!              ├─ UiSettings
//!              └─ DiagnosticsSettings
//! ```
//!
//! All four structs derive `serde::{Serialize, Deserialize}` with
//! `#[serde(deny_unknown_fields)]` so unknown TOML keys cause a parse
//! failure (per contracts §2). Sensible `Default` implementations match
//! the table in contracts/settings-file.md §2.
//!
//! ## I/O surface
//!
//! - [`UserSettings::load`] reads the file and falls back to defaults on
//!   any failure, optionally renaming the corrupt file to a
//!   `.bad-<timestamp>` sibling so the user can recover it.
//! - [`UserSettings::save`] does a write-to-tmp + atomic rename per
//!   contracts §4.1.
//! - [`resolve_settings_path`] picks `%APPDATA%\chess-ai\settings.toml`
//!   (default) or `<exe-dir>\settings.toml` (`--portable`).
//!
//! ## Grammar
//!
//! - [`parse_time_control_str`] implements contracts §3 and is exposed as
//!   a free function so CP-F (UI) can reuse it for the custom-input row.

#![allow(dead_code)] // helpers consumed by CP-E (UI) and CP-F (settings UI)

use std::fmt::Write as _;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{anyhow, Context, Result};
use chess_engine::{StrengthPreset, TimeControl};
use serde::{Deserialize, Serialize};
use tracing::{debug, info, warn};

/// Schema version recognised by this build. Bumped on any breaking
/// change to the on-disk shape.
pub const SCHEMA_VERSION: u32 = 1;

/// Bare minimum thread count the engine accepts.
const MIN_THREADS: u8 = 1;
/// Hard ceiling on `engine.max_threads` regardless of CPU count, per
/// contracts/settings-file.md §2.
const MAX_THREAD_CAP: u8 = 8;
/// Default fall-back time control written when the file's value is
/// invalid.
pub const DEFAULT_TIME_CONTROL_STR: &str = "PerMove:5s";

// ---------------------------------------------------------------------------
// Schema (T024)
// ---------------------------------------------------------------------------

/// Top-level on-disk schema.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UserSettings {
    /// Schema version. v1 → must equal [`SCHEMA_VERSION`].
    pub schema_version: u32,
    /// Engine-side defaults (search depth, threads, …).
    pub engine: EngineSettings,
    /// UI-side preferences (theme, animations, last colour, …).
    pub ui: UiSettings,
    /// Diagnostic toggles (verbose logging).
    pub diagnostics: DiagnosticsSettings,
}

/// Engine-related preferences. All fields have safe defaults.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EngineSettings {
    /// Default playing strength applied to new games. Wire-format
    /// matches [`StrengthPreset`].
    pub default_strength: StrengthPreset,
    /// Default time control for new games, as a TimeControl-grammar
    /// string. Parsed by [`parse_time_control_str`].
    pub default_time_control: String,
    /// Soft cap on engine worker threads; clamped to
    /// `[MIN_THREADS, min(num_cpus, MAX_THREAD_CAP)]` on load.
    pub max_threads: u8,
    /// Whether new games default to Reproducible Mode.
    pub reproducible_mode_default: bool,
    /// Hint button budget in milliseconds. `100 ≤ n ≤ 60_000`.
    pub hint_time_ms: u32,
}

/// UI-side cosmetic preferences.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UiSettings {
    /// Board orientation policy.
    pub board_orientation: BoardOrientation,
    /// Visual theme.
    pub theme: Theme,
    /// Last colour the user picked when starting a Human-vs-AI game.
    /// Persisted so the next session offers the same colour.
    pub last_human_color: HumanColor,
    /// Whether move animations are played.
    pub animate_moves: bool,
}

/// Diagnostics block. Currently only debug-logging.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DiagnosticsSettings {
    /// Enable WARN+-level file logging to `%LOCALAPPDATA%\chess-ai\logs\`.
    /// Equivalent to passing `--log-debug` on the CLI but persistent.
    pub debug_logging: bool,
}

/// Board orientation policy for new games.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum BoardOrientation {
    /// White pieces always at the bottom of the screen.
    WhiteAtBottom,
    /// Black pieces always at the bottom of the screen.
    BlackAtBottom,
    /// Bottom of the board follows the human player's colour. Default.
    Auto,
}

/// Cosmetic theme selector.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum Theme {
    /// Light board, dark pieces. Default.
    Standard,
    /// High-contrast accessibility theme.
    HighContrast,
    /// Dark UI chrome with a light board.
    Dark,
}

/// Human player's last-played colour.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum HumanColor {
    /// White.
    White,
    /// Black.
    Black,
}

// ---------------------------------------------------------------------------
// Defaults
// ---------------------------------------------------------------------------

impl Default for UserSettings {
    fn default() -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            engine: EngineSettings::default(),
            ui: UiSettings::default(),
            diagnostics: DiagnosticsSettings::default(),
        }
    }
}

impl Default for EngineSettings {
    fn default() -> Self {
        let cpus = num_cpus_estimate();
        let suggested = (cpus as f32 * 0.75).floor() as u8;
        let max_threads = suggested.clamp(MIN_THREADS, MAX_THREAD_CAP.min(cpus));
        Self {
            default_strength: StrengthPreset::Maximum,
            default_time_control: DEFAULT_TIME_CONTROL_STR.to_string(),
            max_threads,
            reproducible_mode_default: false,
            hint_time_ms: 3_000,
        }
    }
}

impl Default for UiSettings {
    fn default() -> Self {
        Self {
            board_orientation: BoardOrientation::Auto,
            theme: Theme::Standard,
            last_human_color: HumanColor::White,
            animate_moves: true,
        }
    }
}

// ---------------------------------------------------------------------------
// Settings I/O (T025)
// ---------------------------------------------------------------------------

impl UserSettings {
    /// Load settings from `path`. On any kind of failure (missing file,
    /// permission denied, malformed TOML, schema mismatch) returns
    /// defaults and logs a warning. The corrupt file, if any, is renamed
    /// to a `.bad-<timestamp>` sibling so the user can recover it.
    ///
    /// Per [contracts/settings-file.md §5](../../specs/001-chess-ai-rewrite/contracts/settings-file.md):
    /// startup MUST succeed even if the settings file is unreadable.
    pub fn load(path: &Path) -> Self {
        match Self::try_load(path) {
            Ok(settings) => settings,
            Err(e) => {
                warn!(
                    error = %e,
                    settings_path = %path.display(),
                    "settings load failed; using defaults"
                );
                if path.exists() {
                    let _ = quarantine_corrupt(path);
                }
                let defaults = UserSettings::default();
                if let Err(err) = defaults.save(path) {
                    warn!(error = %err, "could not write fresh defaults");
                }
                defaults
            }
        }
    }

    /// Try to load settings without falling back. Used internally by
    /// [`UserSettings::load`] and exposed for testing.
    pub fn try_load(path: &Path) -> Result<Self> {
        if !path.exists() {
            let defaults = UserSettings::default();
            defaults
                .save(path)
                .with_context(|| format!("creating {}", path.display()))?;
            return Ok(defaults);
        }

        let raw =
            fs::read_to_string(path).with_context(|| format!("reading {}", path.display()))?;
        let mut parsed: UserSettings =
            toml::from_str(&raw).with_context(|| format!("parsing {}", path.display()))?;

        if parsed.schema_version != SCHEMA_VERSION {
            return Err(anyhow!(
                "schema_version {} not supported (expected {})",
                parsed.schema_version,
                SCHEMA_VERSION
            ));
        }

        let mut rewrite = false;

        let max_cpus = num_cpus_estimate();
        let upper = MAX_THREAD_CAP.min(max_cpus);
        if parsed.engine.max_threads < MIN_THREADS || parsed.engine.max_threads > upper {
            warn!(
                got = parsed.engine.max_threads,
                clamped_to = upper,
                "engine.max_threads out of range, clamping"
            );
            parsed.engine.max_threads = parsed.engine.max_threads.clamp(MIN_THREADS, upper);
            rewrite = true;
        }
        if parsed.engine.hint_time_ms < 100 || parsed.engine.hint_time_ms > 60_000 {
            warn!(
                got = parsed.engine.hint_time_ms,
                "engine.hint_time_ms out of range, clamping"
            );
            parsed.engine.hint_time_ms = parsed.engine.hint_time_ms.clamp(100, 60_000);
            rewrite = true;
        }

        if parse_time_control_str(&parsed.engine.default_time_control).is_err() {
            warn!(
                got = %parsed.engine.default_time_control,
                fallback = %DEFAULT_TIME_CONTROL_STR,
                "engine.default_time_control unparseable, falling back"
            );
            parsed.engine.default_time_control = DEFAULT_TIME_CONTROL_STR.to_string();
            rewrite = true;
        }

        if rewrite {
            if let Err(err) = parsed.save(path) {
                warn!(error = %err, "could not rewrite normalised settings");
            }
        }

        debug!(?parsed, "loaded settings");
        Ok(parsed)
    }

    /// Save settings to `path` atomically. Writes to a sibling
    /// `<path>.tmp` then `fs::rename`s into place per
    /// [contracts/settings-file.md §4.1](../../specs/001-chess-ai-rewrite/contracts/settings-file.md).
    /// On NTFS the rename is atomic.
    pub fn save(&self, path: &Path) -> Result<()> {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).with_context(|| format!("creating {}", parent.display()))?;
        }

        let toml_text = toml::to_string_pretty(self).context("serialising UserSettings to TOML")?;

        let tmp = match path.extension() {
            Some(_) => path.with_extension("toml.tmp"),
            None => path.with_extension("tmp"),
        };

        fs::write(&tmp, toml_text.as_bytes())
            .with_context(|| format!("writing {}", tmp.display()))?;
        fs::rename(&tmp, path)
            .with_context(|| format!("renaming {} → {}", tmp.display(), path.display()))?;

        debug!(path = %path.display(), "settings saved");
        Ok(())
    }

    /// Convert the on-disk default time-control string into an engine
    /// `TimeControl`. Returns the spec fallback (`PerMove:5s`) if the
    /// string is malformed — the loader keeps the on-disk file in sync
    /// already, so this should never panic in practice.
    pub fn engine_time_control(&self) -> TimeControl {
        parse_time_control_str(&self.engine.default_time_control)
            .unwrap_or_else(|_| TimeControl::PerMove(Duration::from_secs(5)))
    }
}

/// Resolve the on-disk settings path.
///
/// - **Default**: `%APPDATA%\chess-ai\settings.toml` via `directories`.
/// - **Portable** (`--portable`): `<exe-dir>\settings.toml`.
pub fn resolve_settings_path(portable: bool) -> Result<PathBuf> {
    if portable {
        let exe = std::env::current_exe().context("locating current exe for --portable mode")?;
        let dir = exe
            .parent()
            .ok_or_else(|| anyhow!("current_exe has no parent dir"))?
            .to_path_buf();
        return Ok(dir.join("settings.toml"));
    }

    let proj = directories::ProjectDirs::from("", "", "chess-ai")
        .ok_or_else(|| anyhow!("could not determine APPDATA path"))?;
    let dir = proj.config_dir().to_path_buf();
    Ok(dir.join("settings.toml"))
}

/// Move the existing settings file out of the way, preserving the bad
/// content for support. Best-effort: failure is logged, not propagated.
fn quarantine_corrupt(path: &Path) -> Result<()> {
    let ts = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let mut bad = path.as_os_str().to_owned();
    bad.push(format!(".bad-{ts}"));
    let bad_path = PathBuf::from(bad);
    fs::rename(path, &bad_path)
        .with_context(|| format!("rename {} → {}", path.display(), bad_path.display()))?;
    info!(corrupt = %bad_path.display(), "moved corrupt settings out of the way");
    Ok(())
}

/// Cheap, dependency-free CPU-count estimate. Used to clamp
/// `max_threads`. Falls back to 1 when the platform refuses to answer.
pub fn num_cpus_estimate() -> u8 {
    std::thread::available_parallelism()
        .map(|n| n.get().min(u8::MAX as usize) as u8)
        .unwrap_or(1)
}

// ---------------------------------------------------------------------------
// TimeControl grammar (T026)
// ---------------------------------------------------------------------------

/// Parse a TimeControl string per
/// [contracts/settings-file.md §3](../../specs/001-chess-ai-rewrite/contracts/settings-file.md):
///
/// ```text
/// PerMove:<duration>     e.g. "PerMove:5s", "PerMove:500ms", "PerMove:1m"
/// Total:<game>:<inc>     e.g. "Total:5m:3s"        (5+3 blitz)
/// FixedDepth:<n>         e.g. "FixedDepth:10"
/// FixedNodes:<n>         e.g. "FixedNodes:1000000"
/// ```
///
/// `Total:` collapses to `PerMove(game / 40 + inc)` — a pragmatic
/// per-move budget for 40-move games. The richer game-clock model
/// arrives in CP-F.
///
/// `<duration>` is parsed via `humantime::parse_duration`. `<n>` is a
/// positive `u64` (depth is also bounded to `u8`).
pub fn parse_time_control_str(input: &str) -> Result<TimeControl, TimeControlParseError> {
    let trimmed = input.trim();
    let (kind, rest) = trimmed
        .split_once(':')
        .ok_or(TimeControlParseError::MissingSeparator)?;

    match kind {
        "PerMove" => {
            let dur =
                humantime::parse_duration(rest).map_err(TimeControlParseError::InvalidDuration)?;
            Ok(TimeControl::PerMove(dur))
        }
        "Total" => {
            let (game_s, inc_s) = rest
                .split_once(':')
                .ok_or(TimeControlParseError::MissingSeparator)?;
            let game = humantime::parse_duration(game_s)
                .map_err(TimeControlParseError::InvalidDuration)?;
            let inc =
                humantime::parse_duration(inc_s).map_err(TimeControlParseError::InvalidDuration)?;
            let per_move = game / 40 + inc;
            Ok(TimeControl::PerMove(per_move))
        }
        "FixedDepth" => {
            let n: u32 = rest
                .parse()
                .map_err(|_| TimeControlParseError::InvalidNumber(rest.to_string()))?;
            if n == 0 {
                return Err(TimeControlParseError::ZeroValue);
            }
            let depth = u8::try_from(n).map_err(|_| TimeControlParseError::DepthTooLarge(n))?;
            Ok(TimeControl::FixedDepth(depth))
        }
        "FixedNodes" => {
            let n: u64 = rest
                .parse()
                .map_err(|_| TimeControlParseError::InvalidNumber(rest.to_string()))?;
            if n == 0 {
                return Err(TimeControlParseError::ZeroValue);
            }
            Ok(TimeControl::FixedNodes(n))
        }
        other => Err(TimeControlParseError::UnknownKind(other.to_string())),
    }
}

/// Render an engine `TimeControl` back to its on-disk form. Inverse of
/// [`parse_time_control_str`] for the four canonical shapes.
pub fn time_control_to_str(tc: TimeControl) -> String {
    let mut out = String::new();
    match tc {
        TimeControl::PerMove(d) => {
            let _ = write!(out, "PerMove:{}", humantime::format_duration(d));
        }
        TimeControl::FixedDepth(d) => {
            let _ = write!(out, "FixedDepth:{d}");
        }
        TimeControl::FixedNodes(n) => {
            let _ = write!(out, "FixedNodes:{n}");
        }
        TimeControl::Infinite => {
            // Not part of the on-disk grammar; persisted as the
            // canonical fallback. The UI must explicitly opt in to
            // analysis-mode infinite searches at runtime.
            out.push_str(DEFAULT_TIME_CONTROL_STR);
        }
    }
    out
}

/// Parser error type for [`parse_time_control_str`]. Carries enough
/// detail for the UI to surface a useful toast message.
#[derive(Debug)]
pub enum TimeControlParseError {
    /// Missing `:` separator between kind and value.
    MissingSeparator,
    /// `<duration>` failed to parse via `humantime`.
    InvalidDuration(humantime::DurationError),
    /// `<n>` failed to parse as a `u64`.
    InvalidNumber(String),
    /// Numeric value was zero, which is meaningless for depth/nodes.
    ZeroValue,
    /// Depth exceeded `u8::MAX`.
    DepthTooLarge(u32),
    /// Kind tag was none of `PerMove`, `Total`, `FixedDepth`,
    /// `FixedNodes`.
    UnknownKind(String),
}

impl std::fmt::Display for TimeControlParseError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::MissingSeparator => f.write_str("missing ':' separator"),
            Self::InvalidDuration(e) => write!(f, "invalid duration: {e}"),
            Self::InvalidNumber(s) => write!(f, "invalid number: {s:?}"),
            Self::ZeroValue => f.write_str("value must be > 0"),
            Self::DepthTooLarge(n) => write!(f, "depth {n} exceeds 255"),
            Self::UnknownKind(k) => write!(f, "unknown time-control kind: {k:?}"),
        }
    }
}

impl std::error::Error for TimeControlParseError {}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    fn tmp_path(name: &str) -> PathBuf {
        let mut p = std::env::temp_dir();
        let nanos = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        p.push(format!("chess-ai-test-{nanos}-{name}.toml"));
        p
    }

    #[test]
    fn defaults_round_trip() {
        let s = UserSettings::default();
        let toml_text = toml::to_string_pretty(&s).unwrap();
        let back: UserSettings = toml::from_str(&toml_text).unwrap();
        assert_eq!(s, back);
    }

    #[test]
    fn unknown_top_level_field_rejected() {
        let bad = "schema_version = 1\nunknown_key = 7\n[engine]\n[ui]\n[diagnostics]\n";
        let result: Result<UserSettings, _> = toml::from_str(bad);
        assert!(result.is_err(), "deny_unknown_fields must reject");
    }

    #[test]
    fn unknown_section_field_rejected() {
        let mut text = toml::to_string_pretty(&UserSettings::default()).unwrap();
        text.push_str("\n[engine.weird]\nfoo = 1\n");
        let result: Result<UserSettings, _> = toml::from_str(&text);
        assert!(result.is_err());
    }

    #[test]
    fn save_then_load_round_trip() {
        let path = tmp_path("rt");
        let s = UserSettings::default();
        s.save(&path).expect("save");
        let back = UserSettings::try_load(&path).expect("load");
        assert_eq!(s, back);
        let _ = fs::remove_file(&path);
    }

    #[test]
    fn missing_file_creates_defaults() {
        let path = tmp_path("missing");
        assert!(!path.exists());
        let loaded = UserSettings::load(&path);
        assert_eq!(loaded, UserSettings::default());
        assert!(path.exists(), "load() must persist defaults to disk");
        let _ = fs::remove_file(&path);
    }

    #[test]
    fn corrupt_file_is_quarantined_and_replaced_with_defaults() {
        let path = tmp_path("corrupt");
        fs::write(&path, b"this is not toml\x00\xff").unwrap();
        let loaded = UserSettings::load(&path);
        assert_eq!(loaded, UserSettings::default());
        assert!(path.exists(), "fresh defaults must be written");

        // The original file should have been preserved as `.bad-<ts>`.
        let parent = path.parent().unwrap();
        let stem = path.file_name().unwrap().to_string_lossy().to_string();
        let mut found_bad = false;
        for entry in fs::read_dir(parent).unwrap().flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with(&stem) && name.contains(".bad-") {
                found_bad = true;
                let _ = fs::remove_file(entry.path());
            }
        }
        assert!(found_bad, "corrupt file must be quarantined");
        let _ = fs::remove_file(&path);
    }

    #[test]
    fn out_of_range_clamped_and_rewritten() {
        let path = tmp_path("clamp");
        let raw = "schema_version = 1\n\
            [engine]\n\
            default_strength = \"Maximum\"\n\
            default_time_control = \"PerMove:5s\"\n\
            max_threads = 250\n\
            reproducible_mode_default = false\n\
            hint_time_ms = 100000\n\
            [ui]\n\
            board_orientation = \"Auto\"\n\
            theme = \"Standard\"\n\
            last_human_color = \"White\"\n\
            animate_moves = true\n\
            [diagnostics]\n\
            debug_logging = false\n";
        fs::write(&path, raw).unwrap();
        let loaded = UserSettings::try_load(&path).expect("load");
        let cap = MAX_THREAD_CAP.min(num_cpus_estimate());
        assert!(loaded.engine.max_threads <= cap);
        assert!(loaded.engine.hint_time_ms <= 60_000);
        let _ = fs::remove_file(&path);
    }

    #[test]
    fn invalid_time_control_string_falls_back() {
        let path = tmp_path("badtc");
        let raw = "schema_version = 1\n\
            [engine]\n\
            default_strength = \"Maximum\"\n\
            default_time_control = \"This is not a time control\"\n\
            max_threads = 1\n\
            reproducible_mode_default = false\n\
            hint_time_ms = 3000\n\
            [ui]\n\
            board_orientation = \"Auto\"\n\
            theme = \"Standard\"\n\
            last_human_color = \"White\"\n\
            animate_moves = true\n\
            [diagnostics]\n\
            debug_logging = false\n";
        fs::write(&path, raw).unwrap();
        let loaded = UserSettings::try_load(&path).expect("load");
        assert_eq!(loaded.engine.default_time_control, DEFAULT_TIME_CONTROL_STR);
        let _ = fs::remove_file(&path);
    }

    // ---- TimeControl grammar (T026) -------------------------------------

    #[test]
    fn parses_per_move_5s() {
        let tc = parse_time_control_str("PerMove:5s").unwrap();
        assert_eq!(tc, TimeControl::PerMove(Duration::from_secs(5)));
    }

    #[test]
    fn parses_per_move_500ms() {
        let tc = parse_time_control_str("PerMove:500ms").unwrap();
        assert_eq!(tc, TimeControl::PerMove(Duration::from_millis(500)));
    }

    #[test]
    fn parses_per_move_1m() {
        let tc = parse_time_control_str("PerMove:1m").unwrap();
        assert_eq!(tc, TimeControl::PerMove(Duration::from_secs(60)));
    }

    #[test]
    fn parses_total_5m_3s() {
        let tc = parse_time_control_str("Total:5m:3s").unwrap();
        // 5min/40 = 7.5s + 3s = 10.5s
        assert_eq!(tc, TimeControl::PerMove(Duration::from_millis(10_500)));
    }

    #[test]
    fn parses_fixed_depth() {
        let tc = parse_time_control_str("FixedDepth:10").unwrap();
        assert_eq!(tc, TimeControl::FixedDepth(10));
    }

    #[test]
    fn parses_fixed_nodes() {
        let tc = parse_time_control_str("FixedNodes:1000000").unwrap();
        assert_eq!(tc, TimeControl::FixedNodes(1_000_000));
    }

    #[test]
    fn rejects_missing_separator() {
        assert!(parse_time_control_str("PerMove5s").is_err());
    }

    #[test]
    fn rejects_unknown_kind() {
        assert!(parse_time_control_str("Foo:5s").is_err());
    }

    #[test]
    fn rejects_zero_depth() {
        assert!(parse_time_control_str("FixedDepth:0").is_err());
    }

    #[test]
    fn rejects_depth_overflow() {
        assert!(parse_time_control_str("FixedDepth:9999").is_err());
    }

    #[test]
    fn round_trip_per_move() {
        let tc = TimeControl::PerMove(Duration::from_millis(2_500));
        let s = time_control_to_str(tc);
        let back = parse_time_control_str(&s).unwrap();
        assert_eq!(tc, back);
    }
}
