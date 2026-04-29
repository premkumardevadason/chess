//! T091 - CLI contract integration tests.

use std::fs;
use std::time::Instant;

use assert_cmd::Command;
use predicates::str::contains;

fn bin() -> Command {
    Command::cargo_bin("chess-ai").expect("binary chess-ai must build")
}

#[test]
fn version_exits_zero_and_prints_version_line() {
    bin()
        .arg("--version")
        .assert()
        .success()
        .stdout(contains("chess-ai"));
}

#[test]
fn help_exits_zero_and_includes_usage() {
    bin()
        .arg("--help")
        .assert()
        .success()
        .stdout(contains("Usage:"));
}

#[test]
fn self_test_exits_zero_and_completes_within_sixty_seconds() {
    let started = Instant::now();
    bin().arg("--self-test").assert().success();
    assert!(
        started.elapsed().as_secs() <= 60,
        "self-test exceeded 60 seconds"
    );
}

#[test]
fn version_and_help_together_exit_with_usage_error() {
    bin()
        .args(["--version", "--help"])
        .assert()
        .code(2)
        .stderr(contains("cannot be used"));
}

#[test]
fn bogus_argument_exits_with_usage_error() {
    bin()
        .arg("--no-such-flag")
        .assert()
        .code(2)
        .stderr(contains("unexpected argument"));
}

#[test]
fn reset_settings_renames_existing_file_with_timestamp_suffix() {
    let exe = assert_cmd::cargo::cargo_bin("chess-ai");
    let settings_path = exe
        .parent()
        .expect("binary parent")
        .join("settings.toml");
    fs::write(&settings_path, "schema_version = 1\n").expect("seed settings");

    bin()
        .args(["--self-test", "--reset-settings", "--portable"])
        .assert()
        .success();

    let settings_dir = settings_path.parent().expect("settings dir");
    let mut saw_backup = false;
    for entry in fs::read_dir(settings_dir).expect("read settings dir") {
        let entry = entry.expect("dir entry");
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if name.starts_with("settings.toml.bak-") {
            saw_backup = true;
            break;
        }
    }

    assert!(saw_backup, "expected backup settings.toml.bak-<timestamp>");

    // Best-effort cleanup for subsequent local runs.
    let _ = fs::remove_file(&settings_path);
}
