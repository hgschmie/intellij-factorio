# Same-version standard-library cache upgrade — UP-093

Completed 2026-10-18. This follows UP-091/092 and EmmyLua2 build180.

## Cause and reproduction

The installed macOS arm64 analyzer matches build180 exactly (pin 278036c3).
Its current inference code is running, but the per-module resource cache for LTN
contains the old pairs annotation, including the unbound I generic and no array
overload. The cache marker contains only `0.30.0`, unchanged across local builds.
Consequently the server loads old declarations with the new inference code,
producing the reported concrete value type and unknown key type.

The previous fresh-resource probes missed upgrades. Their helper also set a
different default data directory, causing the old extraction guard to refresh
explicit resource paths. The corrected upgrade probe uses a read-only copy of
the live LTN cache and sets XDG_DATA_HOME to match the explicit resource path,
just as FDE does. Build180 then reproduces the screenshot exactly. No live
cache or original mod file was modified.

## Patch and integration

Independent branch `fix/std-resource-cache-fingerprint`, signed `ef3674de`, is
based on freshly fetched upstream/main `dd80bd92`. Signed pick `80eedd82` and
handoff commit yield work/pin `6a6d08d5bb443d4ad97b213c47241e36daba0bbf`.

- Include bundled file paths/content in resource cache identity, alongside the
  upstream version. The same source version with changed declarations refreshes.
- Check the selected resource directory rather than an unrelated default path.
- Extract base resources before generating translations; locale cache keys include
  bundled resources, locale and translation metadata. Base refresh invalidates
  translation markers. Failed writes leave no current marker.
- Reuse unchanged caches and respect disabled filesystem resource output.
- Generate translation-test output in disposable test directories.

UP-093 has no dependency on UP-092, but enables UP-092's declarations in existing
same-version installations. It changes resource loading, not type inference.
All existing work fixes and normal UP-045/UP-082 remain. No push or PR requested.

## Validation and delivery

Standalone: all three resource regressions and all 228 server tests pass;
analysis/server all-target/all-feature Clippy and rustfmt pass. Work: the same
three resource regressions and all 306 server tests pass, with Clippy/rustfmt.
The tests cover same-version upgrades, fingerprints, explicit directory isolation,
cache reuse, failed extraction and translated-cache refresh/order.

Initial standalone completion failures were traced to an unrelated upstream
heuristic: EnvProvider treats any file path containing `std` as standard library.
The initial checkout name `std-resource-cache` triggered it. Moving the unchanged
checkout to `build/checkouts/resource-cache` makes all 228 server tests pass.
The unchanged upstream baseline completion suite also passes. No unrelated
completion change is included.

The packaged release server is tested against the copied old IntelliJ cache:
- Before: pairs hover contains unknown key/control types; loop inference works.
- After upgrade: pairs and ipairs hover/loop variables give integer/ltn.FreeTrain.
- Second launch: the corrected hover persists and cached-file mtimes are unchanged.
- Full fixed-source LTN diagnostics remain identical: six in stop-update.lua,
  zero in request_processor.lua, including the temporary ipairs variant.

Evidence is under analyzer `build/investigations/iterator-hover-20261018/`:
`upgrade_probe.py`, `upgrade180.json`, `upgrade181-first.json`, `upgrade181.json`,
corresponding diagnostic scans/logs and `cache-upgrade-validation.json`.
The new marker is `0.30.0:2f91bf621f19de4c`. Two forced native IntelliJ tests pass:
PerModServersTest asymmetric API attributes and separate module workspaces;
fresh XML records two tests, zero failures/errors/skips (2026-10-19T02:13:46Z).

Install `plugins/IntelliJ-EmmyLua2-0.30.0-181.zip`. The original remains under
`upstream/Intellij-EmmyLua2/build/distributions/`, and the prepared dependency is
refreshed. Archive version/pin, server identity and Unix0755 modes are verified.
SHA-256: `df7d0ffd0153ed4f73ad7d61a465dae1c9abb968e31848c659ab4f3cae7296df`.
Only macOS arm64 contains the custom analyzer. Keep FDE 0.8.6-dev and LSP4IJ
0.21.1-SNAPSHOT-7; FDE's development pin/documentation changes only, with no new
FDE ZIP. Installing build181 and restarting the server refreshes old caches;
manual IntelliJ cache deletion is unnecessary. No IDE installation was performed.

Signed EmmyLua2 packaging commit: `f3cdfb7`. New fix/work/package commits remain
local. PR status was not rechecked; previous verification dates remain unchanged.
