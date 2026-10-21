# Analyzer dependency update — 2026-10-21

The development pin now selects analyzer work
`1e45b2e1c611a6ec83d4e2d39902efff3b328af8`, packaged as EmmyLua2
`0.30.0-187`. It integrates the UP-040 numeric-for amendment and new
UP-101 table-argument matching, UP-102 non-nil standard iterator values,
and UP-103 guarded member casts on upstream/main `bb7c7d4c`.

Seven of the eight original LTN warnings disappear. The remaining optional
fuel-signal argument and two further prototype-lookup diagnostics reflect Lua
annotation gaps. More precise contracts clear all diagnostics in the five
checked files of an isolated copy; the actual mod is untouched. The optional
patch, detailed classification and complete delivery receipt are in workspace
`upstream/memory/LTN_POST_REFRESH_WARNINGS_2026-10-21.md`.

The combined analyzer passes 3,039 tests, Clippy and rustfmt. Package validation
checks the descriptor, pin, binary identity and Unix executable permissions.
All five selected native FDE tests pass, with zero failures/errors/skips.
Their results are recorded in that receipt and `build/logs/emmy187-platform-driver.log`.

This is an analyzer-only dependency update. FDE remains **0.9.3-dev** with the
already validated archive containing FMTK's UP-100 changelog fix. LSP4IJ remains
**0.21.1-SNAPSHOT-13**. No FDE or LSP4IJ distribution is rebuilt for this pin.
