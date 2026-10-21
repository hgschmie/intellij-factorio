# Analyzer build188 — 2026-10-21

The development analyzer pin is `42ab2605f060015a226bf40c7449270f5a9db354`, packaged as EmmyLua2
**0.30.0-188**. UP-036 and UP-062 are amended; new UP-105/106 fix broad rawget
index contracts and truthy short-circuit flow. Existing UP-045 remains integrated.

All seven reported cache/metrics/settings warnings were analyzer issues. The
packaged analyzer reports zero diagnostics across all 37 Lua files of the frozen
actual LogisticTrainNetwork module, with no original mod edits. Provenance,
branch ownership and dependencies are recorded in workspace
`upstream/memory/LTN_SEVEN_WARNINGS_2026-10-21.md`.

All 3,049 combined analyzer workspace tests, Clippy and rustfmt pass. EmmyLua2's
30 routing tests and these five native FDE integration tests pass with the final
prepared dependency: separate generated-API workspaces, typed remote returns,
LocalisedString arrays/optional fields, asymmetric API attributes, and Lua runtime
selection. No failures, errors or skips. Evidence: `build/logs/emmy188-platform-final.log`
and `build/logs/emmy188-native-results.json`.

The ZIP is validated and copied to `plugins/IntelliJ-EmmyLua2-0.30.0-188.zip`.
SHA-256: `58030e0fb7a550cb9c7eb0638f55dc7730b420810b0c8b2b34a8cefbef6bc801`.
Only macOS arm64 carries the custom analyzer. FDE stays 0.9.3-dev, with its
existing archive and LSP4IJ SNAPSHOT-15 dependency unchanged. This commit updates
the development pin and receipt only; no FDE rebuild or feature version bump.
No push, new upstream PR or installation into the running IDE.
