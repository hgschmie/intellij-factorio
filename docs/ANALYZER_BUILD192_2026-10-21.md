# Analyzer build192 — 2026-10-21

Development analyzer pin: `da3b12a1af2f87d4ae5e5aa88e1e18f82005447c`.
EmmyLua2 **0.30.0-192** includes UP-088 amendment `b457f521`, extending expected
callback parameters from dictionary values to arrays and annotated member
initializers. Work merge `da3b12a1` preserves the existing assignment fallback
and directional member-write contracts.

All eight parameter hovers in train-tracker's unchanged `lib/constants.lua`
now have the expected `tt.TrainInfo`, `string?`, and `LuaPlayer?` types.
The prior return mismatch disappears; newly visible nullable parameter mismatches
and a remaining reverse-alias guard warning are documented in the investigation.
The frozen LTN baseline still reports zero diagnostics across all 37 files.

Validation: 2,582 standalone workspace tests, 3,064 distinct combined tests,
Clippy/rustfmt, packaged protocol assertions, and both native FDE tests pass.
The final combined run passes 3,063 tests; the unchanged flow stress test passed
in preceding full runs and was not repeated after a test-only adjustment.
Native cases: generated API/separate workspaces and default-server ownership/
release; two passed, no failures/errors/skips. Evidence:
`build/logs/emmy192-platform.log` and `emmy192-native-results.json`.

Installable archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-192.zip`.
SHA-256: `41bd3b0ffd7e4cf1babb22b49d044b6cbed951da67d6a965270b77520695a5a6`.
Descriptor, pin, binary identity, executable0755 and bundled schema verified.
Only macOS arm64 carries the custom analyzer. FDE stays at 0.9.3-dev and
LSP4IJ at SNAPSHOT-15; neither archive is rebuilt. Changes are local/unpushed;
no PR, settings change or regular IDE installation.

Full receipt: workspace `upstream/memory/TRAIN_TRACKER_ARRAY_CALLBACKS_2026-10-21.md`.
