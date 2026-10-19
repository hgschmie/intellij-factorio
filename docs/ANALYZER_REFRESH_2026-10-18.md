# Analyzer refresh — build 182 (2026-10-18)

FDE remains 0.8.6-dev; its existing distribution is unchanged. The development
analyzer pin is `1c82cf231e824f279efec1f73bd32bd61415db16` on work/intellij-modules, freshly based on
upstream/main 3d4d1b73. EmmyLua2 0.30.0-182 packages this analyzer for macOS arm64.
LSP4IJ remains 0.21.1-SNAPSHOT-7.

UP-050/056 are replaced by upstream implementations. All 64 retained non-PR
branches were rebased; three already-current PR branches are included. New
UP-094 corrects declaration-cache path identity; UP-074 depends on it and keeps
acyclic loop summaries memoized. All previous work-only integration is retained.

Validation: 2,921 combined analyzer workspace tests, workspace Clippy/rustfmt,
standalone branch checks, and three forced native platform tests passed:

- PerModServersTest.testAsymmetricApiAttributes
- PerModServersTest.testGeneratedApiWithSeparateWorkspaces
- PerModServersTest.testWorkspaceDiagnosticsFollowUnsavedCrossFileEdits

Final XML reports three executed tests, zero failures/errors/skips. Log:
`build/logs/upstream-refresh182-final-platform.log`.
Packaged raw-LSP replay against the unchanged saved LTN fixture and copied cache
matches build181 exactly: six existing diagnostics, zero in request_processor.
Pairs/ipairs hover and loop inference retain integer/ltn.FreeTrain.

ZIP: `../plugins/IntelliJ-EmmyLua2-0.30.0-182.zip`.
SHA-256: `bc35a134108a9e791353efadccc9fba67ab17ff566464b286ebbc4417372d97e`.
Plugin version, embedded pin, binary identity and Unix0755 permissions verified.
Full branch/dependency report: `../upstream/ANALYZER_REFRESH_2026-10-18.md`;
canonical inventory: `../upstream/FIX_INVENTORY.md`.
Task changes are signed local commits. No remote push, PR action or IDE installation.
