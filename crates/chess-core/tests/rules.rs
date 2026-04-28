//! T049 — FIDE rules-corpus test driver.
//!
//! Loads `tests/corpora/rules_v1.epd` (T048), parses every position, and
//! cross-validates the rules engine against the optional assertion tags:
//!
//!   * `lm   <N>`           expected `legal_moves(p).len()`
//!   * `chk  yes|no`        expected `is_in_check(p)`
//!   * `term <class>`       expected termination class derived from
//!                          `Game::detect_result`'s priority order:
//!                          `mate / stalemate / fifty / insufficient / none`
//!                          (`threefold` is not reachable from a single FEN
//!                          and is exercised by `chess-core::game::tests`).
//!   * `bm   <SAN> [...]`   parser sanity only — every SAN must `parse_san` OK
//!                          in the position. Tactical evaluation lives in
//!                          `chess-engine/tests/tactics.rs` (T051).
//!
//! All tags are optional. The corpus uses every assertion only where the
//! count is hand-verified; positions whose only purpose is FEN round-trip
//! supply only `cat`/`id`.
//!
//! Failure mode: each EPD entry is one assertion. The driver reports the
//! first failing case with the ID + offending field so corpus authors can
//! locate it instantly.

use std::collections::HashMap;

use chess_core::{
    is_in_check, legal_moves, parse_san,
    rules::{is_fifty_move, is_insufficient_material, is_threefold},
    Position,
};

const RULES_EPD: &str = include_str!("corpora/rules_v1.epd");

#[derive(Debug, Clone, PartialEq, Eq)]
enum Term {
    None,
    Mate,
    Stalemate,
    Fifty,
    Insufficient,
}

impl Term {
    fn parse(s: &str) -> Option<Term> {
        match s {
            "none" => Some(Term::None),
            "mate" => Some(Term::Mate),
            "stalemate" => Some(Term::Stalemate),
            "fifty" => Some(Term::Fifty),
            "insufficient" => Some(Term::Insufficient),
            _ => None,
        }
    }
}

#[derive(Debug, Default, Clone)]
struct Case {
    fen: String,
    id: String,
    cat: String,
    expect_lm: Option<usize>,
    expect_chk: Option<bool>,
    expect_term: Option<Term>,
    expect_bm: Vec<String>,
    line_no: usize,
}

/// Parse the EPD. The grammar is:
///
/// ```text
/// <line>     ::= <fen> ( ";" <tag> )*
/// <tag>      ::= "id"   <quoted>
///              | "cat"  <quoted>
///              | "lm"   <integer>
///              | "chk"  ( "yes" | "no" )
///              | "term" ( "none" | "mate" | "stalemate" | "fifty" | "insufficient" )
///              | "bm"   <san> ( <san> )*
/// ```
///
/// Lines starting with `#` and blank lines are comments.
fn parse_corpus(text: &str) -> Vec<Case> {
    let mut out = Vec::new();
    for (i, raw) in text.lines().enumerate() {
        let line_no = i + 1;
        let trimmed = raw.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        // Split on `;` but keep the FEN as-is (FENs themselves never contain `;`).
        let mut parts = trimmed.split(';').map(str::trim);
        let fen = parts.next().unwrap_or("").to_string();
        if fen.is_empty() {
            continue;
        }
        let mut case = Case {
            fen,
            line_no,
            ..Default::default()
        };
        for tag in parts {
            if tag.is_empty() {
                continue;
            }
            let (key, rest) = match tag.find(char::is_whitespace) {
                Some(p) => (&tag[..p], tag[p..].trim()),
                None => (tag, ""),
            };
            match key {
                "id" => case.id = unquote(rest),
                "cat" => case.cat = unquote(rest),
                "lm" => {
                    case.expect_lm = Some(rest.parse().unwrap_or_else(|e| {
                        panic!(
                            "rules_v1.epd:{line_no}: invalid `lm` value {:?}: {e}",
                            rest
                        )
                    }))
                }
                "chk" => {
                    case.expect_chk = Some(match rest {
                        "yes" => true,
                        "no" => false,
                        _ => panic!(
                            "rules_v1.epd:{line_no}: invalid `chk` value {:?} (want yes|no)",
                            rest
                        ),
                    })
                }
                "term" => {
                    case.expect_term = Some(Term::parse(rest).unwrap_or_else(|| {
                        panic!(
                            "rules_v1.epd:{line_no}: invalid `term` value {:?} (want none|mate|stalemate|fifty|insufficient)",
                            rest
                        )
                    }))
                }
                "bm" => {
                    case.expect_bm = rest.split_whitespace().map(str::to_string).collect();
                }
                _ => {
                    // Unknown tag — silently ignore so the corpus stays
                    // forward-compatible with future test driver tags.
                }
            }
        }
        if case.id.is_empty() {
            panic!("rules_v1.epd:{line_no}: every line MUST carry an `id` tag");
        }
        if case.cat.is_empty() {
            panic!("rules_v1.epd:{line_no}: every line MUST carry a `cat` tag");
        }
        out.push(case);
    }
    out
}

fn unquote(s: &str) -> String {
    let s = s.trim();
    if s.starts_with('"') && s.ends_with('"') && s.len() >= 2 {
        s[1..s.len() - 1].to_string()
    } else {
        s.to_string()
    }
}

/// Mirror of `Game::detect_result`'s class without instantiating a `Game`
/// (which only accepts `Position::startpos` as a starting position in v1).
///
/// Priority MUST match `chess_core::game::Game::detect_result`:
///   1. legal_moves empty + in check  → mate
///   2. legal_moves empty + not check → stalemate
///   3. (threefold) — not reachable from a bare FEN, skipped here
///   4. fifty (halfmove_clock >= 100) → fifty
///   5. insufficient material         → insufficient
///   6. otherwise                     → none
fn classify_term(pos: &Position) -> Term {
    let legal = legal_moves(pos);
    if legal.is_empty() {
        return if is_in_check(pos) {
            Term::Mate
        } else {
            Term::Stalemate
        };
    }
    // Threefold needs a repetition table; from a bare FEN the count is 1
    // for the current zobrist, so it never fires.
    let empty: HashMap<u64, u8> = HashMap::new();
    debug_assert!(!is_threefold(&empty, pos));
    if is_fifty_move(pos) {
        return Term::Fifty;
    }
    if is_insufficient_material(pos) {
        return Term::Insufficient;
    }
    Term::None
}

#[test]
fn corpus_parses_every_fen_round_trip() {
    let cases = parse_corpus(RULES_EPD);
    assert!(
        cases.len() >= 60,
        "rules_v1.epd has {} cases; v1 baseline is ≥60",
        cases.len()
    );
    for c in &cases {
        let pos = Position::from_fen(&c.fen).unwrap_or_else(|e| {
            panic!(
                "{}: line {} — FEN parse failed: {e}\n  fen = {:?}",
                c.id, c.line_no, c.fen
            )
        });
        let round = pos.to_fen();
        assert_eq!(
            round, c.fen,
            "{}: line {} — FEN round-trip mismatch:\n  in  = {:?}\n  out = {:?}",
            c.id, c.line_no, c.fen, round
        );
    }
}

#[test]
fn corpus_legal_move_counts_match() {
    let cases = parse_corpus(RULES_EPD);
    let mut checked = 0usize;
    for c in &cases {
        let Some(expected) = c.expect_lm else { continue };
        let pos = Position::from_fen(&c.fen).expect(&c.id);
        let actual = legal_moves(&pos).len();
        assert_eq!(
            actual, expected,
            "{}: line {} — legal_moves mismatch (cat={}); expected {}, got {}",
            c.id, c.line_no, c.cat, expected, actual
        );
        checked += 1;
    }
    assert!(
        checked >= 8,
        "expected ≥8 cases with `lm` assertions, only saw {checked}"
    );
}

#[test]
fn corpus_in_check_flags_match() {
    let cases = parse_corpus(RULES_EPD);
    let mut checked = 0usize;
    for c in &cases {
        let Some(expected) = c.expect_chk else { continue };
        let pos = Position::from_fen(&c.fen).expect(&c.id);
        let actual = is_in_check(&pos);
        assert_eq!(
            actual, expected,
            "{}: line {} — is_in_check mismatch (cat={}); expected {}, got {}",
            c.id, c.line_no, c.cat, expected, actual
        );
        checked += 1;
    }
    assert!(
        checked >= 30,
        "expected ≥30 cases with `chk` assertions, only saw {checked}"
    );
}

#[test]
fn corpus_termination_classes_match() {
    let cases = parse_corpus(RULES_EPD);
    let mut checked = 0usize;
    for c in &cases {
        let Some(expected) = c.expect_term.clone() else {
            continue;
        };
        let pos = Position::from_fen(&c.fen).expect(&c.id);
        let actual = classify_term(&pos);
        assert_eq!(
            actual, expected,
            "{}: line {} — termination class mismatch (cat={}); expected {:?}, got {:?}",
            c.id, c.line_no, c.cat, expected, actual
        );
        checked += 1;
    }
    assert!(
        checked >= 30,
        "expected ≥30 cases with `term` assertions, only saw {checked}"
    );
}

#[test]
fn corpus_best_moves_parse() {
    let cases = parse_corpus(RULES_EPD);
    for c in &cases {
        if c.expect_bm.is_empty() {
            continue;
        }
        let pos = Position::from_fen(&c.fen).expect(&c.id);
        for san in &c.expect_bm {
            parse_san(&pos, san).unwrap_or_else(|e| {
                panic!(
                    "{}: line {} — `bm {}` failed to parse_san: {e:?}",
                    c.id, c.line_no, san
                )
            });
        }
    }
}

#[test]
fn corpus_covers_every_rule_class() {
    // Sanity check: the v1 corpus must touch every rule class so the
    // grammar / driver is fully exercised.
    let cases = parse_corpus(RULES_EPD);
    let mut seen: HashMap<String, usize> = HashMap::new();
    for c in &cases {
        *seen.entry(c.cat.clone()).or_insert(0) += 1;
    }
    for required in [
        "movement",
        "castling",
        "en-passant",
        "promotion",
        "mate",
        "stalemate",
        "in-check",
        "fifty-move",
        "insufficient-material",
        "fen-roundtrip",
        "pinned-piece",
    ] {
        assert!(
            seen.get(required).copied().unwrap_or(0) >= 1,
            "rules_v1.epd is missing category {required:?}; seen = {seen:?}"
        );
    }
}
