# Analyzer/client build 199 — 2026-10-22

EmmyLua2 version 0.30.0-199 bundles analyzer
`1c952b9a9aa0fa047afc8d028e044972550a5a39` and asynchronous gutter refresh.
Amends UP-017/035/061/088; adds UP-113/114/115/116. Upstream ce61189c supplies
merged UP-028/#1292. UP-045 remains active.

All 3,154 Rust tests, 35 client tests, Clippy/rustfmt and the three focused native
checks (module root ownership, generated APIs in separate workspaces, Lua 5.2
selection) pass. Packaged reproductions verify the eight fixes; LTN remains
37 files, zero warnings/errors and two existing local-alias hints.

Broader native checks: 14/18 passed; two require -PrealMods and two lifecycle tests
time out. Both timeouts also reproduce on unchanged build 198 at the same
assertions (saved_marker disk reload and initial closed-consumer diagnostics).
They are recorded separately, not suppressed. The final test setup is restored
to 199. Evidence: build/logs/emmy199-native-{final.log,results.json},
build/test-results/emmy199-broad/ and build/test-results/emmy198-lifecycle-control/.

Archive: workspace plugins/IntelliJ-EmmyLua2-0.30.0-199.zip.
ZIP SHA-256:e09cb0c37f35714091d78f9780c304bbcd20853585c32997fc496b0e146dafd2.
Analyzer SHA-256:2418e0a0856face9f941e452437936c0bfc6b6c79009a1a325de290aeea51b2f.
Descriptor, lock, executable0755 and archive/prepared/release identity verified.
Only macOS arm64 receives the custom analyzer. This updates the development pin;
FDE 0.9.3-dev and LSP4IJ SNAPSHOT-15 archives are unchanged. Initial outstanding
changes were published before implementation; new task commits remain local.
No new PR or original-mod edits. Full receipt:
workspace upstream/memory/EIGHT_ISSUE_FIXES_2026-10-22.md.
