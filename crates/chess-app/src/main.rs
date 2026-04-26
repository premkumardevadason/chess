//! `chess-ai` — single-binary native Windows chess application.
//!
//! This file currently boots a placeholder. The full boot sequence
//! (CLI parse → settings load → engine spawn → eframe::run_native) is
//! implemented in T028.

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    println!(
        "chess-ai placeholder ({} on {})",
        chess_core::version(),
        env!("CARGO_PKG_NAME")
    );
    println!("engine: {}", chess_engine::placeholder());
}
