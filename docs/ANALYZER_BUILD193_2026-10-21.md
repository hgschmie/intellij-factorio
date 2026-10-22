# Analyzer build193 — 2026-10-21

Development analyzer pin: `d4f25314a2759d589bc8f2cdf98504ebe655ff7d`.
EmmyLua2 **0.30.0-193** includes UP-089 amendment `f8bde6ce`; work merge
`d4f25314` preserves directional member access and all prior local patches.
Implicit array entries keep their inference identity, but their values no longer
mask actual function/field declarations during navigation and hover.

Packaged replay verifies all three getStationName calls resolve to line99 and
share the same 20 references as the declaration. Function/field hovers are
correct, and all eight contextual callback parameter hovers remain intact.
The frozen callback fixture retains three prior warnings plus one newly visible
local-alias style hint. The user's latest source has changed since that snapshot.
The frozen 37-file LTN baseline has zero warnings/errors and two newly visible
preferred-local-alias hints in dispatcher.lua:78/220. No mod source was edited.

All 2,582 standalone and 3,070 combined workspace tests, Clippy/rustfmt, packaged
protocol assertions and both native FDE integration tests pass. No tests were
filtered from the workspace suites. Native tests cover generated APIs with
separate workspaces and default-server ownership/release (two passed, no failures,
errors or skips). Evidence: `build/logs/emmy193-platform.log` and
`build/logs/emmy193-native-results.json`.

Installable archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-193.zip`.
SHA-256: `0233650b23ad0639892abb93ae5be22160be2a72c78127cb28a1adcef0abf55f`.
Descriptor, pin, binary identity, executable0755 and schema verified. Only macOS
arm64 carries the custom analyzer. FDE stays at 0.9.3-dev and LSP4IJ at SNAPSHOT-15;
neither archive is rebuilt. Changes are local/unpushed, without a PR or installation.
Full receipt: workspace `upstream/memory/TRAIN_TRACKER_ARRAY_NAVIGATION_2026-10-21.md`.
