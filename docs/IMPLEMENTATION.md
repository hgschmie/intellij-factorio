# Implementation progress

Target: Java integration for IDEA 262, EmmyLua2 only, LSP4IJ 0.21.0, bundled
FMTK CLI, installed Node and native Factorio DAP. Profiling is deferred.

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

All development outputs stay under ~/ai/fmtk. Publishing validation uses copied
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
- [x] Multiple FMTK workspace folders and managed Emmy workspace roots
- [x] Isolated New Project/New Module/import/package/debug UI checks
- [ ] Correct short and named cross-mod Lua imports with multiple attached mods

The final item fails in the bundled EmmyLua server's global module index. The
protocol reproduction is `scripts/test-module-services.py`; it intentionally
returns failure until the import checks pass. A separate EmmyLua server patch
would extend the approved plan's no-additional-EmmyLua-patch boundary; the scope
question is pending. No Rust server change is included in this checkpoint.
