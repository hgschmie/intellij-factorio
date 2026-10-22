# Analyzer build194 — 2026-10-21

Development analyzer pin: `f8e08719b699a6ee92f3a34018a4effc3c919f9b`.
EmmyLua2 **0.30.0-194** includes UP-016 amendment `7ec0b234`; work merge
`f8e08719` retains the existing constructor integration and all prior patches.
Preserving literal constructor keys prevents false expected-nil warnings for
valid finite-enum/literal-union dictionary keys and validates their values.

Packaged replay clears the reported train-tracker lib/settings.lua warning.
Minimized constructors pass with strict dictionary reads both enabled and
disabled, while invalid keys are still rejected. The 37-file LTN baseline is
unchanged: zero warnings/errors and two preferred-local-alias hints. Original
mod files are unchanged.

All 2,587 standalone and 3,075 combined workspace tests, Clippy/rustfmt, packaged
protocol assertions and both native FDE integration tests pass. No workspace
tests were filtered or skipped. Native tests cover generated APIs with separate
workspaces and default-server ownership/release (two passed, no failures, errors
or skips). Evidence: `build/logs/emmy194-platform.log` and
`build/logs/emmy194-native-results.json`.

Installable archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-194.zip`.
SHA-256: `73e5a5fba9d206f216ab82d93f1138ad73df952533fc603f0844beca62779d3a`.
Descriptor, pin, binary identity, executable0755 and schema verified. Only macOS
arm64 carries the custom analyzer. FDE stays at 0.9.3-dev and LSP4IJ at SNAPSHOT-15;
neither archive is rebuilt. Changes are local/unpushed, without a PR or installation.
Full receipt: workspace `upstream/memory/TRAIN_TRACKER_SETTINGS_2026-10-21.md`.
