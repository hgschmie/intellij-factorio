# Analyzer build189 — 2026-10-21

Development analyzer pin: `a615f09dd1e49979f29abc55cac38dda8c27d778`.
EmmyLua2 version: **0.30.0-189**, including independent UP-107
(`fix/member-navigation-identity`, signed `ae2cc4d0`).

Find Usages combines references and implementations from the analyzer. The
upstream implementation handler compared member names without receiver identity,
returning 192 implementations of `state` in the train-tracker fixture. UP-107
returns only `FrameworkGhostManager:state`, and restores its five implicit-self
references. The final reference set has seven calls plus the declaration.
Queries on both the declaration and a self call return the exact same locations.
The 37-file frozen LTN baseline still has zero diagnostics.

Installable archive: `plugins/IntelliJ-EmmyLua2-0.30.0-189.zip` in the workspace.
SHA-256: `a093fd2343c14b04a22175be8a39f10505a546b9d929f171ff7e114799cd2248`.
Descriptor, embedded pin, binary identity and executable permissions are validated.
Only macOS arm64 receives the custom analyzer. FDE stays 0.9.3-dev with the same
ZIP, and LSP4IJ remains SNAPSHOT-15. This update changes only the development
analyzer pin and receipt. No push, new PR or installation into the running IDE.

Full evidence: workspace `upstream/memory/GHOST_MANAGER_USAGES_2026-10-21.md`.

Validation: 2,574 standalone and 3,052 combined analyzer workspace tests pass,
with Clippy/rustfmt clean. Both native FDE tests ran and passed: default-server
root ownership/release and generated API with separate workspaces. No failures,
errors or skips. Evidence: `build/logs/emmy189-platform.log` and
`build/logs/emmy189-native-results.json`. EmmyLua2 client source is unchanged;
its routing suite was not repeated for this analyzer-only package.
