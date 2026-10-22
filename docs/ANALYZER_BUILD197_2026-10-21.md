# Analyzer build197 — 2026-10-21

Development analyzer pin: `e0698d2ae8179d5158d89c01459190375346654b`.
EmmyLua2 **0.30.0-197** includes independent UP-111 `76c321fd`, integrated by
`e0698d2a`. Export inference honors a returned local's explicit @type without
adding its variable name as a union alternative. Unannotated export owner lookup,
class/enum identity, and all prior patches including UP-110 remain integrated.

Packaged replay reports zero diagnostics in train-tracker lib/this.lua, including
the reported line57 require('lib.settings') assignment. Hovers retain ff2.ModSettings.
The previous Settings|ff2.ModSettings union is a current-upstream false positive,
reproduced on unpatched bb7c7d4c and packaged196. Original Lua files are unchanged.
The 37-file LTN baseline remains zero warnings/errors and two local-alias hints.

All 1,602 standalone analysis tests and 3,109 combined workspace tests pass,
as do Clippy/rustfmt. Both native FDE tests pass (2 tests, no failures/errors/skips):
generated APIs in separate workspaces and default-server ownership/release.
Evidence: build/logs/emmy197-platform.log and emmy197-native-results.json.

Archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-197.zip`.
SHA-256: `b48108dad5ce8bdface3478ee702143b53eb487ba3878615c662f4b74ae905c9`.
Descriptor, analyzer pin, binary identity, executable0755 and retained unused-self
schema verified. Only macOS arm64 receives the patched analyzer. FDE remains
0.9.3-dev and LSP4IJ remains SNAPSHOT-15; their archives were not rebuilt.
Local signed commits only; no push, PR or IDE installation. Full receipt:
workspace `upstream/memory/TRAIN_TRACKER_MODULE_SETTINGS_2026-10-21.md`.
