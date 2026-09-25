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
