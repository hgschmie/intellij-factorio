# Analyzer build195 — 2026-10-21

Development analyzer pin: `a762be645e306dc63c6ff10b506fe8bfe02e465c`.
EmmyLua2 **0.30.0-195** includes UP-089 amendment `1febed04` and new UP-109
`ae0516d0`, integrated by `2f31f556` / `7461d7a7`. Nested constructor hovers
retain their expected types; computed keys resolve their expressions; runtime
member @type annotations now drive value inference. Existing flow, cast,
directional-field, enum and dictionary fixes remain integrated.

Packaged replay shows FrameworkSettingsGroup and FrameworkSetting at the reported
train-tracker lib/settings.lua positions, correct key/value contracts and no
diagnostics. Strict dictionary lookup results remain string?. The frozen LTN
baseline remains 37 files, zero warnings/errors and two unchanged local-alias
hints. Original mod files were not edited.

Validation: 2,600 distinct UP-089 standalone tests (full suite then the final
server suite), 2,579 UP-109 standalone tests and all 3,101 combined workspace
tests pass, along with Clippy/rustfmt. Both native FDE integration tests pass,
without failures, errors or skips, covering generated APIs with separate
workspaces and default-server ownership/release. Evidence:
`build/logs/emmy195-platform.log` and `build/logs/emmy195-native-results.json`.

Archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-195.zip`.
SHA-256: `144a2d4cfef8d8fd3a96128d4caf76c46712026455faf395fb26b73a234bc4b8`.
Descriptor, analyzer pin, binary identity, executable0755 and retained unused-self
schema verified. Only macOS arm64 receives the custom analyzer. FDE remains
0.9.3-dev and LSP4IJ remains SNAPSHOT-15; their archives were not rebuilt.
Local signed commits, no push or PR. Full receipt: workspace
`upstream/memory/TRAIN_TRACKER_SETTINGS_HOVER_2026-10-21.md`.
