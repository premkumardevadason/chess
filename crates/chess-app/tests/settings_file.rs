//! T061 — settings file integration tests.
//!
//! Covers the five test obligations listed in
//! [contracts/settings-file.md §8](../../../specs/001-chess-ai-rewrite/contracts/settings-file.md):
//!
//!   1. **Round-trip** — save / load preserves every field.
//!   2. **Atomicity** — interrupting writes does not corrupt the file
//!      (we simulate by writing a temp file directly and asserting the
//!      target is replaced atomically via `fs::rename`).
//!   3. **Corruption recovery** — a malformed `settings.toml` is
//!      quarantined to `*.bad-<ts>` and replaced with defaults at load.
//!   4. **Unknown-field rejection** — `deny_unknown_fields` causes the
//!      load path to fall back to defaults rather than partially
//!      applying the file.
//!   5. **Out-of-range clamping** — values outside the documented
//!      ranges are clamped on load AND the on-disk file is rewritten.

use std::fs;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use chess_app::settings::{
    parse_time_control_str, BoardOrientation, HumanColor, Theme, UserSettings, SCHEMA_VERSION,
};
use chess_engine::{StrengthPreset, TimeControl};

fn tmp_path(tag: &str) -> PathBuf {
    let ts = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    let mut p = std::env::temp_dir();
    p.push(format!("chess-ai-it-{tag}-{ts}.toml"));
    p
}

#[test]
fn obligation_1_round_trip_preserves_every_field() {
    let path = tmp_path("rt");
    let mut s = UserSettings::default();
    s.engine.default_strength = StrengthPreset::Intermediate;
    s.engine.default_time_control = "FixedDepth:12".to_string();
    s.engine.max_threads = 1;
    s.engine.reproducible_mode_default = true;
    s.engine.hint_time_ms = 4_500;
    s.ui.board_orientation = BoardOrientation::BlackAtBottom;
    s.ui.theme = Theme::Dark;
    s.ui.last_human_color = HumanColor::Black;
    s.ui.animate_moves = false;
    s.diagnostics.debug_logging = true;

    s.save(&path).expect("save ok");
    let back = UserSettings::try_load(&path).expect("load ok");
    assert_eq!(s, back);
    assert_eq!(back.schema_version, SCHEMA_VERSION);
    let _ = fs::remove_file(&path);
}

#[test]
fn obligation_1_round_trip_time_control_grammar() {
    // Each canonical TimeControl shape must round-trip via the
    // grammar parser in settings.rs.
    for (txt, expected) in [
        (
            "PerMove:5s",
            TimeControl::PerMove(std::time::Duration::from_secs(5)),
        ),
        ("FixedDepth:10", TimeControl::FixedDepth(10)),
        ("FixedNodes:1000000", TimeControl::FixedNodes(1_000_000)),
    ] {
        let parsed = parse_time_control_str(txt).expect(txt);
        assert_eq!(parsed, expected, "round-trip {txt}");
    }
}

#[test]
fn obligation_2_atomicity_no_partial_file_on_replacement() {
    // Atomic-rename guarantee: after `save()` returns OK, no
    // intermediate `.tmp` sibling lingers and the target is fully
    // populated.
    let path = tmp_path("atomic");
    let s = UserSettings::default();
    s.save(&path).expect("save ok");

    // The tmp sibling MUST be gone post-rename.
    let tmp = path.with_extension("toml.tmp");
    assert!(!tmp.exists(), "intermediate .tmp must be cleaned up");

    // The file must contain the full schema (toml::from_str succeeds).
    let raw = fs::read_to_string(&path).expect("read");
    let _: UserSettings = toml::from_str(&raw).expect("re-parse");
    let _ = fs::remove_file(&path);
}

#[test]
fn obligation_3_corruption_recovers_with_quarantine() {
    let path = tmp_path("corrupt");
    fs::write(&path, b"this is not toml \xff\xff").unwrap();

    let loaded = UserSettings::load(&path);
    assert_eq!(loaded, UserSettings::default());

    // The original file must be preserved as a sibling with `.bad-`.
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

    // And the load path must have left a fresh defaults file behind.
    let raw = fs::read_to_string(&path).expect("fresh defaults written");
    let parsed: UserSettings = toml::from_str(&raw).expect("parse defaults");
    assert_eq!(parsed, UserSettings::default());
    let _ = fs::remove_file(&path);
}

#[test]
fn obligation_4_unknown_field_rejected() {
    // `deny_unknown_fields` MUST reject foreign top-level keys.
    let raw = "schema_version = 1\nrogue_top_level = 7\n[engine]\n[ui]\n[diagnostics]\n";
    let result: Result<UserSettings, _> = toml::from_str(raw);
    assert!(result.is_err(), "deny_unknown_fields must reject");

    // Same for nested sections.
    let mut text = toml::to_string_pretty(&UserSettings::default()).unwrap();
    text.push_str("\n[engine.subsection]\nfoo = 1\n");
    let result: Result<UserSettings, _> = toml::from_str(&text);
    assert!(result.is_err(), "nested unknown-section must be rejected");
}

#[test]
fn obligation_5_out_of_range_values_clamped_and_rewritten() {
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

    let loaded = UserSettings::try_load(&path).expect("load with clamp");
    assert!(
        loaded.engine.max_threads <= 8,
        "max_threads must be clamped to ≤ 8 (got {})",
        loaded.engine.max_threads
    );
    assert!(
        loaded.engine.max_threads >= 1,
        "max_threads must be ≥ 1 (got {})",
        loaded.engine.max_threads
    );
    assert!(
        loaded.engine.hint_time_ms <= 60_000,
        "hint_time_ms must be clamped to ≤ 60_000 (got {})",
        loaded.engine.hint_time_ms
    );

    // The on-disk file must have been rewritten with normalised values.
    let rewritten = fs::read_to_string(&path).expect("rewritten present");
    let after: UserSettings = toml::from_str(&rewritten).expect("re-parse");
    assert_eq!(after.engine.max_threads, loaded.engine.max_threads);
    assert_eq!(after.engine.hint_time_ms, loaded.engine.hint_time_ms);
    let _ = fs::remove_file(&path);
}

#[test]
fn obligation_5_invalid_time_control_falls_back_and_rewrites() {
    let path = tmp_path("badtc");
    let raw = "schema_version = 1\n\
        [engine]\n\
        default_strength = \"Maximum\"\n\
        default_time_control = \"NotARealKind:12\"\n\
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

    let loaded = UserSettings::try_load(&path).expect("load with fallback");
    assert_eq!(
        loaded.engine.default_time_control, "PerMove:5s",
        "invalid TC must fall back to PerMove:5s"
    );

    // Rewritten on disk so subsequent boots are clean.
    let rewritten = fs::read_to_string(&path).expect("rewritten present");
    let after: UserSettings = toml::from_str(&rewritten).expect("re-parse");
    assert_eq!(after.engine.default_time_control, "PerMove:5s");
    let _ = fs::remove_file(&path);
}
