# Analyzer build196 — 2026-10-21

Development analyzer pin: `6e3ab30f7a6457c687045c7d0ff89b9e31fadc28`.
EmmyLua2 **0.30.0-196** includes independent UP-110 `f89a2037`, integrated by
`6e3ab30f`. The export checker now accepts the exported class's declared fields,
including inherited contracts from another file, while retaining diagnostics
for undefined imported fields and mutations contributed by consumers.
UP-010's metatable read behavior and all prior patches remain integrated.

The reported train-tracker lib/init.lua:12 warning reproduces on build195 and
unpatched upstream/main bb7c7d4c. Packaged replay clears it; framework remains
FrameworkRoot and settings remains FrameworkSettings in hover. The separate
existing `if script` warning remains. The 37-file LTN baseline is unchanged:
zero warnings/errors and two local-alias hints. Original mods were not edited.

All 1,600 standalone analysis tests and 3,104 combined workspace tests pass,
as do Clippy/rustfmt. Both native FDE tests pass (2 tests, no failures/errors/skips):
generated APIs in separate workspaces and default-server ownership/release.
Evidence: `build/logs/emmy196-platform.log` and `emmy196-native-results.json`.

Archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-196.zip`.
SHA-256: `594a9b48d1da99ee8c5dbb4e5248c5b5d13a38e1dd94a6fd70388e66289476fa`.
Descriptor, analyzer pin, binary identity, executable0755 and retained unused-self
schema verified. Only macOS arm64 receives the patched analyzer. FDE remains
0.9.3-dev and LSP4IJ remains SNAPSHOT-15; their archives were not rebuilt.
Local signed commits only; no push, PR or IDE installation. Full receipt:
workspace `upstream/memory/TRAIN_TRACKER_MODULE_EXPORT_2026-10-21.md`.
