//! Engine link layer (T029) — the UI's view of the worker thread.
//!
//! `chess-engine` exposes a low-level channel API ([`EngineHandle`]).
//! The UI does not want to deal with bare commands and events on every
//! frame, so this module wraps it in a small facade:
//!
//! - [`EngineLink::tick`] is called once per egui frame; it drains the
//!   event queue, updates [`EngineLink::status`] live, and returns the
//!   freshly-popped events to the UI.
//! - [`EngineLink::start_search`] / [`EngineLink::stop`] /
//!   [`EngineLink::set_position`] / [`EngineLink::shutdown`] are typed
//!   helpers around `Command::*` — keeps the UI free of `unwrap`s on
//!   `EngineDead`.
//!
//! See:
//! - [contracts/engine-api.md §2](../../specs/001-chess-ai-rewrite/contracts/engine-api.md)
//! - [contracts/ui-interactions.md §2.5](../../specs/001-chess-ai-rewrite/contracts/ui-interactions.md)

#![allow(dead_code)] // surface lands in CP-E (Game UI) onwards

use chess_core::{Move, Position};
#[cfg(test)]
use chess_engine::SearchInfo;
use chess_engine::{Command, Engine, EngineConfig, EngineHandle, Eval, Event, Score, SearchResult};
use tracing::{debug, warn};

/// Live status indicator surfaced in the right-panel HUD per
/// [contracts/ui-interactions.md §2.5](../../specs/001-chess-ai-rewrite/contracts/ui-interactions.md).
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub enum EngineStatus {
    /// Worker is parked; no search in progress.
    #[default]
    Idle,
    /// A search is running. `depth` and `eval_cp` are the most recent
    /// `SearchProgress` snapshot. `eval_cp` may be `None` until the
    /// engine has reported a score (typically within ~50 ms).
    Thinking {
        /// Last completed iterative-deepening depth.
        depth: u8,
        /// Centipawn evaluation from the side-to-move perspective.
        /// `None` until the first `SearchProgress` arrives.
        eval_cp: Option<Eval>,
    },
    /// The engine emitted `Stopped` after a clean shutdown — the
    /// handle should be dropped after this state is observed.
    Stopped,
}

impl EngineStatus {
    /// Return `true` while the worker is mid-search.
    pub fn is_thinking(&self) -> bool {
        matches!(self, EngineStatus::Thinking { .. })
    }
}

/// UI-side facade around an engine [`EngineHandle`].
pub struct EngineLink {
    handle: EngineHandle,
    status: EngineStatus,
    last_result: Option<SearchResult>,
}

impl EngineLink {
    /// Spawn a fresh engine and return a link wrapping its handle.
    /// Cost: ~50–150 ms (one-time NNUE init) per
    /// [contracts/engine-api.md §1](../../specs/001-chess-ai-rewrite/contracts/engine-api.md).
    pub fn spawn() -> Self {
        let engine = Engine::new();
        Self::from_handle(engine.spawn())
    }

    /// Construct a link from an externally-spawned handle. Used by
    /// tests that want a custom-configured engine.
    pub fn from_handle(handle: EngineHandle) -> Self {
        Self {
            handle,
            status: EngineStatus::Idle,
            last_result: None,
        }
    }

    /// Latest engine status. Updated by [`EngineLink::tick`].
    pub fn status(&self) -> &EngineStatus {
        &self.status
    }

    /// The most recent `SearchResult` the engine emitted, if any.
    pub fn last_result(&self) -> Option<&SearchResult> {
        self.last_result.as_ref()
    }

    /// Drain the engine's event queue, update [`EngineLink::status`]
    /// from any `SearchProgress` / `SearchComplete` / `SearchAborted`
    /// frames, and return the events for the UI to render.
    ///
    /// Intended to be called once per egui frame.
    pub fn tick(&mut self) -> Vec<Event> {
        let events = self.handle.drain();
        for event in &events {
            self.absorb(event);
        }
        events
    }

    /// Send `Command::SetPosition`. Logs a warning if the engine has
    /// exited — the UI should treat this as a "must reboot" signal.
    pub fn set_position(&self, position: Position, history: Vec<Move>) {
        if self
            .handle
            .send(Command::SetPosition { position, history })
            .is_err()
        {
            warn!("engine_link: SetPosition rejected — engine thread dead");
        }
    }

    /// Send `Command::StartSearch`. Marks the link `Thinking { depth: 0,
    /// eval_cp: None }` until the first `SearchProgress` event lands.
    pub fn start_search(&mut self, config: EngineConfig) {
        if self.handle.send(Command::StartSearch { config }).is_err() {
            warn!("engine_link: StartSearch rejected — engine thread dead");
            self.status = EngineStatus::Stopped;
            return;
        }
        self.status = EngineStatus::Thinking {
            depth: 0,
            eval_cp: None,
        };
        self.last_result = None;
    }

    /// Send `Command::Stop`. Idempotent if the engine is already idle.
    pub fn stop(&self) {
        if self.handle.send(Command::Stop).is_err() {
            warn!("engine_link: Stop rejected — engine thread dead");
        }
    }

    /// Send `Command::SetConfig` (live thread / logging tweak).
    pub fn set_config(&self, max_threads: u8, debug_logging: bool) {
        if self
            .handle
            .send(Command::SetConfig {
                max_threads,
                debug_logging,
            })
            .is_err()
        {
            warn!("engine_link: SetConfig rejected — engine thread dead");
        }
    }

    /// Send `Command::Shutdown`. After this, the worker thread will
    /// emit `Event::Stopped` and exit; subsequent commands fail.
    pub fn shutdown(&self) {
        if self.handle.send(Command::Shutdown).is_err() {
            debug!("engine_link: Shutdown ignored — engine thread already dead");
        }
    }

    /// Update [`EngineLink::status`] and [`EngineLink::last_result`]
    /// from a single inbound event.
    fn absorb(&mut self, event: &Event) {
        match event {
            Event::SearchProgress(info) => {
                self.status = EngineStatus::Thinking {
                    depth: info.depth,
                    eval_cp: Some(score_to_cp(&info.score)),
                };
            }
            Event::SearchComplete(res) => {
                self.last_result = Some(res.clone());
                self.status = EngineStatus::Idle;
            }
            Event::SearchAborted => {
                self.status = EngineStatus::Idle;
            }
            Event::Warning(msg) => {
                warn!("engine_link: warning from worker: {msg}");
            }
            Event::Stopped => {
                self.status = EngineStatus::Stopped;
            }
        }
    }
}

impl Drop for EngineLink {
    fn drop(&mut self) {
        // Best effort: ask the worker to stop. Ignore failures because
        // the channel may already be closed in the panic case.
        let _ = self.handle.send(Command::Shutdown);
    }
}

/// Map a `chess_engine::Score` to a centipawn integer for the HUD.
/// Mate scores are projected to a high-magnitude centipawn value so
/// the UI can display them in the same eval bar.
fn score_to_cp(score: &Score) -> Eval {
    match *score {
        Score::Cp(cp) => cp,
        Score::Mate(plies) => {
            // 100 cp per mate ply, capped well below `i32::MAX` to leave
            // room for downstream arithmetic.
            let signed = i32::from(plies) * 100;
            (32_000 - signed.abs()).max(0) * plies.signum() as i32
        }
    }
}

/// Convenience: short string for `EngineStatus` suitable for the HUD.
pub fn render_status(status: &EngineStatus) -> String {
    match status {
        EngineStatus::Idle => "Idle".to_string(),
        EngineStatus::Stopped => "Stopped".to_string(),
        EngineStatus::Thinking { depth, eval_cp } => match eval_cp {
            Some(cp) => format!("Thinking… depth {depth}, eval {:+.2}", *cp as f32 / 100.0),
            None => format!("Thinking… depth {depth}"),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    fn dummy_info(depth: u8, cp: i32) -> SearchInfo {
        SearchInfo {
            depth,
            seldepth: depth,
            score: Score::Cp(cp),
            pv: vec![],
            nodes: 0,
            nps: 0,
            elapsed: Duration::default(),
        }
    }

    fn dummy_result(mv_to: chess_core::Square) -> SearchResult {
        SearchResult {
            mv: chess_core::Move::new_quiet(chess_core::Square::new(0), mv_to),
            info: dummy_info(0, 0),
            completed: true,
        }
    }

    #[test]
    fn render_status_idle() {
        assert_eq!(render_status(&EngineStatus::Idle), "Idle");
    }

    #[test]
    fn render_status_thinking_with_cp() {
        let s = EngineStatus::Thinking {
            depth: 12,
            eval_cp: Some(42),
        };
        assert_eq!(render_status(&s), "Thinking… depth 12, eval +0.42");
    }

    #[test]
    fn render_status_thinking_no_cp_yet() {
        let s = EngineStatus::Thinking {
            depth: 1,
            eval_cp: None,
        };
        assert_eq!(render_status(&s), "Thinking… depth 1");
    }

    #[test]
    fn engine_link_round_trip_via_real_engine() {
        // End-to-end: spawn -> shutdown -> drain Stopped event.
        let mut link = EngineLink::spawn();
        assert!(matches!(link.status(), EngineStatus::Idle));
        link.shutdown();
        std::thread::sleep(Duration::from_millis(50));
        let events = link.tick();
        assert!(
            events.iter().any(|e| matches!(e, Event::Stopped)),
            "expected Stopped event after shutdown, got {events:?}"
        );
        assert_eq!(link.status(), &EngineStatus::Stopped);
    }

    #[test]
    fn absorb_search_progress_updates_status() {
        let mut link = EngineLink::spawn();
        // We can't construct events from outside, but we can absorb
        // synthetic ones via the private method (visible in cfg(test)).
        link.absorb(&Event::SearchProgress(dummy_info(7, -55)));
        assert_eq!(
            link.status(),
            &EngineStatus::Thinking {
                depth: 7,
                eval_cp: Some(-55),
            }
        );

        let res = dummy_result(chess_core::Square::new(16));
        link.absorb(&Event::SearchComplete(res.clone()));
        assert_eq!(link.status(), &EngineStatus::Idle);
        assert_eq!(link.last_result().map(|r| r.mv), Some(res.mv));

        link.shutdown();
    }
}
