# Analyzer table completion — 2026-10-19

Development analyzer pin: `17a76f41f43ac1d8c7fc2b3c13e120ef11f62500`, packaged
in EmmyLua2 **0.30.0-184**. UP-096 removes string-library methods from record
constructor field suggestions when the expected parameter is a string/table
union such as ItemFilter. The three intended fields appear immediately, without
a cast. API and client behavior are unchanged.

The isolated fix `89b80eb8` is based on upstream/main `51f2aa7b`; signed work
pick `a5929fe7` retains all existing patches. Standalone and combined server
suites pass **233** and **313** tests, including seven regressions; Clippy and
rustfmt pass. Fourteen packaged before/after protocol checks cover actual API
and frozen LTN source, including in-memory edits and comparator-value completion.

Both forced native tests pass with fresh XML: generated API/separate workspaces
and asymmetric API attributes, **two executed, zero failures/errors/skips**.
Evidence: `build/investigations/union-table-completion184-20261019/`.

Only macOS arm64 receives the patched analyzer. Verified archive:
`../plugins/IntelliJ-EmmyLua2-0.30.0-184.zip`, SHA-256:
`6a9707ec3cb460d191fa7d8291c808513e444ac7ef10d2d48d62eb42353a9d7c`.
Original archives remain under EmmyLua2 build/distributions. Descriptor version,
embedded source pin, release/prepared binary identity and Unix0755 modes pass.
FDE remains **0.8.6-dev** with its existing ZIP; LSP4IJ build10 is unchanged.
This development pin does not require rebuilding FDE for installation.

No push, new PR or regular IDE installation. Unrelated pending FDE documentation
and LSP4IJ lock changes are preserved. Full receipt and inventory:
[upstream report](../../upstream/memory/LTN_TABLE_COMPLETION_2026-10-19.md),
[UP-096](../../upstream/FIX_INVENTORY.md).
