# Workspace builds and evidence

Resolve the workspace from the directory containing HANDOFF.md and upstream/;
this skill lives in that workspace's .agents/skills directory. All explicit
outputs stay under the workspace (currently ~/ai/fmtk). Do not modify original
mods under ~/factorio; symlinks may be followed for reading. Incidental OS/app
runtime writes were authorized, which does not authorize explicit outputs elsewhere.

## Discover current state

Read HANDOFF.md, analyzer docs/intellij-development-handoff.md, EmmyLua2
PATCHED_BUILD.md, and FDE docs/DEVELOPMENT_LAYOUT.md as relevant. Verify git status,
branches, remotes and worktrees; source paths below describe the current layout,
not immutable revision pins.

| Purpose | Workspace-relative path / branch |
| --- | --- |
| Analyzer repository | upstream/emmylua-analyzer-rust |
| Isolated fix worktrees | upstream/emmylua-analyzer-rust/build/checkouts/BUG |
| Analyzer integration | upstream/emmylua-analyzer-rust/build/checkouts/intellij-work; work/intellij-modules |
| EmmyLua2 development | upstream/Intellij-EmmyLua2-worktrees/patched-build; work/patched-build |
| EmmyLua2 prepared dependency | upstream/Intellij-EmmyLua2/build/prepared/IntelliJ-EmmyLua2 |
| FDE | intellij-factorio; inspect active branch (currently main) |
| Shared caches/temp/build home | dev/cache, dev/tmp, dev/build-home |

For analyzer packaging, both Emmy analyzer.lock and FDE emmy-analyzer.lock should
match the clean, committed analyzer work HEAD. FDE checks this during development
build/test setup; the lock does not require an FDE runtime update.

## Validation and packaging

Use CARGO_HOME under dev/cache/cargo and TMPDIR under dev/tmp. Save command output
under each checkout's build/logs. Workspace checks commonly use:

```sh
cargo test --locked --workspace --all-features
cargo clippy --locked --workspace --all-targets --all-features -- -D warnings
cargo fmt --all --check
```

Scale checks to the change. Broad type/flow changes merit full combined analyzer
coverage. The large linear-assignment stress test normally takes over a minute.
Shared Cargo targets have previously reused stale tests across worktrees: verify
named new tests execute, and refresh crate entrypoint mtimes when switching
checkouts if needed. Never run different worktree builds concurrently in the same
target. Tests may modify tracked resources/std-zh_CN; inspect and restore only
those generated translations, preserving unrelated changes.

EmmyLua2 scripts/build-module-patch.sh builds the pinned release analyzer,
packages the plugin, validates it and refreshes the prepared dependency. Read its
current version settings; increase the Emmy build number for a new shipped fix.
It places ZIPs beside earlier builds in
upstream/Intellij-EmmyLua2/build/distributions. Use scripts/test-routing.sh for
client regression coverage. Only macOS arm64 currently receives the local custom
analyzer; do not imply fixes to the other bundled platform binaries.

FDE scripts/build.sh accepts Gradle tasks. For an analyzer-only update, use test
without buildPlugin and keep the existing FDE version/archive. Relevant platform
coverage uses -PplatformTests and tests matching PerModServersTest and
FactorioLuaLanguageLevelTest. Save unit XML/HTML reports before platform tests,
which replace the test output directories. Use the actual configured Factorio
and fixture locations; original mod roots stay read-only.

For real-mod validation, existing raw-LSP probes under analyzer
build/investigations/ and FDE scripts/protocol.py can be adapted into a new
build/investigations/BUG directory. Confirm the reported behavior with the
packaged binary against the same saved inputs, including relevant diagnostics,
hover/navigation and file-update behavior. Record remaining unrelated diagnostics
rather than claiming the whole mod is clean.

Verify the archive's plugin version, analyzer.lock, binary identity with the
prepared/release server, and Unix0755 executable mode. Preserve existing archives.
Commit source fixes separately from integration glue/tests and packaging metadata;
verify configured signatures. Never disable signing merely to finish a commit.
