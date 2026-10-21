# Implementation progress

Target: Java integration for IDEA 262, EmmyLua2 only, LSP4IJ 0.21.0, bundled
FMTK CLI, installed Node and native Factorio DAP. Native Lua profiling was added
in 0.9.0-dev; see [profiling](PROFILING.md). The milestones below are historical.

## Profiling checkpoint — 2026-10-20

Follow-up **0.9.1-dev** restores the shutdown path and 15-second cleanup budget
from before profiling. Neither IntelliJ Stop nor in-game Quit initiates or waits
for a profile save; use **Stop Lua Profiling** and wait for completion first.

FDE **0.9.0-dev** implements native Lua capture, the Factorio Profile executor,
manual start/stop, streamed snapshot import, native Flame Graph/Call Tree/Method
List, source navigation, stable mod/file colors, Lua execution timing, module
selection and snapshot comparison. All six [review findings](PROFILING_REVIEW.md)
are independently verified resolved. The validated installation set is FDE
0.9.0-dev, EmmyLua2 0.30.0-185 and LSP4IJ 0.21.1-SNAPSHOT-13 (UP-098).

Profiling must be explicitly stopped before in-game Quit. The early-startup
native debug-adapter stall is deferred; the user confirmed manual profiling
works at later breakpoints. Ignoring test folders remains planned follow-up work
and is not part of this implementation. See [the profiling record](PROFILING.md)
for validation, artifacts and the preserved investigation history.

## Earlier milestones

- [x] Reproducible plugin distribution and settings implementation
- [x] API generation, safe EmmyLua configuration, dependency import implementation
- [x] Locale/changelog LSP integration and lifecycle implementation
- [x] Factorio DAP configuration and dedicated breakpoint implementation
- [x] Package/version/scripts and publishing UI implementation
- [x] Mock-portal/local-Git publishing regression tests
- [x] Automated checks and core isolated IDE acceptance
- [x] UI edge cases: service checkbox transition and unsaved locale Undo/close/reopen
- [x] Clean DAP console and graceful Stop with bounded fallback
- [x] Installation documentation, evidence, signed implementation checkpoint

All development outputs stay under ~/ai/fmtk. Active resources use the
[development layout](DEVELOPMENT_LAYOUT.md): shared resources in `dev/`,
repository-local logs/results in `build/`, and historical evidence in `spike/`. Publishing validation uses copied
mods, fake credentials, a local portal mock and disposable local Git remotes.
No production release, upstream push, or everyday IDE install is required.

See [ACCEPTANCE.md](ACCEPTANCE.md) for verified results, remaining UI checks,
and the upstream rapid-open/edit ordering limitation. Checked implementation
items do not imply their IDE UI acceptance has passed.

## Project and module support

- [x] Standalone Factorio Mod project/module wizard and safe skeleton generation
- [x] Existing-sources import with module metadata stored in the IDE project
- [x] Automatic discovery, legacy migration and explicit service policy
- [x] Shared toolchain and staged per-module dependency/package overrides
- [x] Context-selected mod commands and module-associated debug configurations
- [x] Separate EmmyLua and FMTK instances per mod (see [server manager](PER_MOD_SERVERS.md))
- [x] Isolated New Project/New Module/import/package/debug UI checks
- [x] Correct short and named cross-mod Lua imports with multiple attached mods (patched macOS arm64 analyzer)

The analyzer is pinned to `34b9c107` on `work/intellij-modules` in
`../upstream/emmylua-analyzer-rust`, now based on upstream main (0.25.1). It
includes the reviewed changes through `07cbc7ce` from upstream PR #1266.
Development EmmyLua2 packaging is on `../upstream/Intellij-EmmyLua2`'s
`work/patched-build` branch, commit `a924dd9`. The package version is
`0.25.1-115-IDEA262-patched-modules`; the local resolver patch applies to macOS
arm64 only. See `ACCEPTANCE.md` for current validation and historical IDE checks.
The user authorized this patch after configuration alternatives failed;
additional patches are a last resort, not prohibited.
