# Analyzer build200 — 2026-10-22

EmmyLua2 version0.30.0-200 bundles analyzer
`ca4943a8c95f1964315fbfef16e9c24aae9907d3`. Amends UP-089 constructor navigation;
adds UP-117 numeric enum index validation, UP-118 disk reload on close,
UP-119 bounded generic-inheritance traversal and UP-120 async native watcher
reception. UP-045 and every prior active fix remain integrated.

The full workspace run passed3,161 Rust tests. After the final server-only watcher
change, all349 server tests passed, for3,162 distinct tests covered. All-target/
all-feature Clippy and rustfmt pass. Packaged real-file/protocol checks pass;
LTN remains37 files, zero warnings/errors and two existing hints.

All five native checks pass: module ownership, generated APIs in separate
workspaces, Lua5.2 selection, closed/open cross-file diagnostics and unsaved
shared dependencies through save/disk replacement. The latter two follow-ups
also needed test setup corrections: empty reports need not allocate diagnostic
cache entries; manually injected scopes were replaced by automatic reconciliation.
The dependency test now uses generated scopes and configured dependencies.
The remaining stall after stabilizing that setup was UP-120's blocking native
watcher receive; its focused regression fails before and passes after the fix.
No assertions were weakened to hide the lifecycle behavior.

Final native receipt: build/logs/emmy200-verified-native.log,
build/logs/emmy200-native-results.json, build/test-results/emmy200-verified/.
Earlier failed/control runs remain as investigation evidence.

Archive: workspace plugins/IntelliJ-EmmyLua2-0.30.0-200.zip.
ZIP SHA-256:58d01644eeee25277aafb0fcc180dfa6345f68a604127102eab7d40572f231e3.
Analyzer SHA-256:69cb7412cb59b2309084a4f99cecad3940214cbbadb7ee462ab69871d2160df8.
Descriptor, lock, executable0755 and archive/prepared/release identity verified.
Only macOS arm64 has the custom analyzer. This changes the development pin/test
coverage only; FDE0.9.3-dev and its ZIP and LSP4IJ SNAPSHOT-15 stay unchanged.
Changes remain signed and local, not pushed; no new PR, original-mod edit or
IDE installation. Full receipt: workspace
upstream/memory/NAVIGATION_ENUM_FOLLOWUPS_2026-10-22.md.
