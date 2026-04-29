//! Reproducible-mode normalization helpers.
//!
//! This module centralizes how `Mode::Reproducible` alters runtime
//! configuration before a search starts.

use crate::{EngineConfig, Mode, TimeControl};

/// Default deterministic depth used when reproducible mode receives
/// an open-ended/infinite time control.
const REPRO_DEFAULT_DEPTH: u8 = 12;

/// Deterministic node budget scale used to convert `PerMove` into
/// a node-limited search in reproducible mode.
const REPRO_NODES_PER_MS: u64 = 20_000;

/// Return a search config normalized for reproducible-mode guarantees.
///
/// In reproducible mode we:
/// - force single-thread search (`max_threads = 1`),
/// - convert time-based controls into deterministic depth/nodes controls.
pub(crate) fn normalize_config(cfg: &EngineConfig) -> EngineConfig {
    let mut normalized = cfg.clone();
    if let Mode::Reproducible { seed } = cfg.mode {
        normalized.max_threads = 1;
        normalized.time_control = normalize_time_control(cfg.time_control, seed);
    }
    normalized
}

fn normalize_time_control(tc: TimeControl, seed: u64) -> TimeControl {
    match tc {
        // Replace wall-clock control with deterministic node budget.
        TimeControl::PerMove(dur) => {
            let ms = dur.as_millis().min(u64::MAX as u128) as u64;
            let base = ms.saturating_mul(REPRO_NODES_PER_MS).max(REPRO_NODES_PER_MS);
            let seed_salt = splitmix64(seed) % (REPRO_NODES_PER_MS / 4);
            TimeControl::FixedNodes(base.saturating_add(seed_salt))
        }
        // Avoid open-ended searches in reproducible mode.
        TimeControl::Infinite => TimeControl::FixedDepth(REPRO_DEFAULT_DEPTH),
        other => other,
    }
}

// Small deterministic mixer to derive a stable seed-dependent salt.
fn splitmix64(mut x: u64) -> u64 {
    x = x.wrapping_add(0x9E37_79B9_7F4A_7C15);
    let mut z = x;
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^ (z >> 31)
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use crate::{EngineConfig, Mode, TimeControl};

    use super::normalize_config;

    #[test]
    fn reproducible_forces_single_thread() {
        let cfg = EngineConfig {
            mode: Mode::Reproducible { seed: 7 },
            max_threads: 8,
            ..EngineConfig::default()
        };

        let got = normalize_config(&cfg);
        assert_eq!(got.max_threads, 1);
    }

    #[test]
    fn reproducible_rewrites_per_move_to_fixed_nodes() {
        let cfg = EngineConfig {
            mode: Mode::Reproducible { seed: 0xDEAD_BEEF },
            time_control: TimeControl::PerMove(Duration::from_secs(2)),
            ..EngineConfig::default()
        };

        let got = normalize_config(&cfg);
        assert!(matches!(got.time_control, TimeControl::FixedNodes(_)));
    }

    #[test]
    fn reproducible_rewrites_infinite_to_fixed_depth() {
        let cfg = EngineConfig {
            mode: Mode::Reproducible { seed: 123 },
            time_control: TimeControl::Infinite,
            ..EngineConfig::default()
        };

        let got = normalize_config(&cfg);
        assert!(matches!(got.time_control, TimeControl::FixedDepth(12)));
    }
}