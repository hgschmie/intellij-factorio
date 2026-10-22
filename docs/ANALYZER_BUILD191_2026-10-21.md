# Analyzer build191 — 2026-10-21

Development analyzer pin: `2486fc957e29a44e56b239493900dc7068d6e87a`.
EmmyLua2 **0.30.0-191** includes UP-108 follow-up `069441cf` on
`feat/unused-self-diagnostic`, integrated with UP-052's unfiltered constructor
checks preserved.

`unused` now suppresses both ordinary unused and implicit-self diagnostics.
`unused-self` suppresses only implicit-self diagnostics. Configuration and file/
current-line/next-line annotations share this behavior, preserving existing
`unused` suppressions. A child enable does not override a disabled parent.
Diagnostic names, per-code severities, tags and suppression actions are unchanged.
This corrects build190's fully independent suppression behavior.

Validation: **725 standalone / 918 combined diagnostic/type-check tests**, all
**227 standalone / 319 combined server tests**, full-workspace Clippy/rustfmt,
nine packaged protocol scenarios and two native FDE tests pass. The native cases
are default-server ownership/release and generated API with separate workspaces;
no failures/errors/skips. Evidence: `build/logs/emmy191-platform.log` and
`emmy191-native-results.json`. Full flow suites are retained from build190 rather
than repeated for the scoped diagnostic-filter amendment. The unchanged frozen
LTN fixture remains at zero diagnostics across all 37 Lua files.

Installable archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-191.zip`.
SHA-256: `9bd23f589deaa7611005b812576b1892a4c19e0160d419f177555af11272f81f`.
Descriptor, pin, binary identity, executable0755 and bundled schema verified.
Only macOS arm64 carries the custom analyzer. FDE remains 0.9.3-dev and LSP4IJ
remains SNAPSHOT-15, with no rebuilt archive for either. Only FDE's development
pin and receipt change. No push, PR, settings change or regular IDE installation.

Full evidence: workspace `upstream/memory/UNUSED_SELF_DIAGNOSTIC_2026-10-21.md`.
