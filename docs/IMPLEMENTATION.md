# Implementation progress

Target: Java integration for IDEA 262, EmmyLua2 only, LSP4IJ 0.21.0, bundled
FMTK CLI, installed Node and native Factorio DAP. Profiling is deferred.

- [ ] Reproducible plugin distribution and settings
- [ ] API generation, safe EmmyLua configuration, dependency imports
- [ ] Locale/changelog LSP integration and lifecycle
- [ ] Factorio DAP configuration and dedicated breakpoints
- [ ] Package/version/scripts and publishing UI
- [ ] Mock-portal/local-Git publishing regression tests
- [ ] Automated checks and isolated IDE acceptance
- [ ] Installation documentation, evidence, signed commits

All development outputs stay under ~/ai/fmtk. Publishing validation uses copied
mods, fake credentials, a local portal mock and disposable local Git remotes.
No production release, upstream push, or everyday IDE install is required.
