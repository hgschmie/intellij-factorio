# Exclusion path correction — 2026-09-28

Version 0.2.3 removes the erroneous `base/scripts/` entries from IntelliJ defaults.
Only `base/script/` is valid. The seven typo entries saved by 0.2.2 are also removed
when settings load; unrelated custom exclusions are preserved. The upstream
VS Code correction remains on its separate PR branch.

# Filtered Factorio data library — 2026-09-28

Version 0.2.2 applies the VS Code toolkit's data-library `ignoreDir`/`ignoreGlobs`
defaults, plus the stateful story helper and current `base/script` scenario
helpers (the upstream defaults use `base/scripts`). Exclusions can be edited
and restored in Configure Factorio Project → Factorio data library → Edit
exclusions. They apply to the installed game library only; FMTK's helper stubs
and the mod's own files remain indexed.

All five real-process platform tests pass. The generated-API regression verifies
that storage contains the mod's `sensor_data` without scenario fields, and that
`util.table.deepcopy` navigates to FMTK's stub. The actual-mod regression now
loads the complete generated API and filtered game library into copies of both
mods, confirms local `This:storage()` navigation, and checks storage hover in
`lib/this.lua`, including `sensor_data` in logistics-sensor. No original mod
files were changed. The new exclusions dialog has not been exercised interactively.

Install `build/distributions/intellij-factorio-0.2.2-dev.zip` and restart IDEA.
The previous patched EmmyLua2 build remains suitable.

# EDT lifecycle fix — 2026-09-28

Version 0.2.1 fixes the reported `LanguageServerExplorer.handleAdded` EDT assertion.
Background module reconciliation previously registered server definitions directly;
LSP4IJ then updated its Swing tree on that same background thread. Registration,
replacement, removal and restart now run on the EDT. Background disposal deactivates
servers immediately and schedules registry cleanup; pending configuration cannot
recreate disposed definitions. Generation and dependency buffer work stay in the
background.

All five platform tests pass. The new regression observes real registry callbacks during background
configuration, replacement, module removal and disposal, asserting that every
notification arrives on the EDT. It also tests configuration queued around disposal.
The generated-API background configuration test and existing real-mod navigation,
locale, dependency update and restart tests are retained. This is automated platform
validation; the user's running IDE was not modified or exercised interactively.
Install `build/distributions/intellij-factorio-0.2.1-dev.zip`; the patched EmmyLua2
build from the previous installation remains suitable.

# Per-mod manager update — 2026-09-27

The 0.2.0 development build uses one EmmyLua and one FMTK instance per mod.
See [PER_MOD_SERVERS.md](PER_MOD_SERVERS.md) for installation prerequisites,
configuration, automated coverage and the remaining importer-type refresh
limitation. All 28 unit tests and four real-process IDE platform tests pass, including generated API
hover and `This:storage()` navigation in copies of the two reported mods.
No everyday IDE installation or interactive UI acceptance was performed for this
change. Earlier UI results below describe earlier builds.

Plugin Verifier now runs offline against the exact patched client dependencies.
It reports **Compatible** with IU-262.10968.63: no unresolved classes or binary
compatibility problems. Its strict Gradle task still exits unsuccessfully for
seven internal API usages (including existing completion-cache/Toolkit access
and the manager's editor refresh), with eight deprecated and 26 experimental API
usages also reported. Keep LSP4IJ at the validated 0.21.0 until revalidated.

# Development acceptance — 2026-09-25

The original single-mod core workflows pass in the isolated IDEA 262 environment. This remains a
development build with the limitations and untested edge cases listed below.

## Verified

- Java package, Gradle group and plugin ID: `de.softwareforge.factorio`.
- Java compilation, plugin ZIP construction, and 22 unit tests pass.
- Unit tests cover preserving user EmmyLua settings, idempotent generation,
  ZIP traversal rejection, versioned dependency extraction, credential redaction, per-directory operation
  locking, failed process reporting, owned-child cancellation, staged settings,
  graceful debugger stop and its bounded force-kill fallback.
- JetBrains Plugin Verifier reports **Compatible** with IU-262.10968.63.
  It reports experimental LSP4IJ DAP APIs and internal completion-cache access;
  upgrades of those dependencies require revalidation. The verifier resolves the
  marketplace EmmyLua2 dependency; the isolated runtime uses our patched build.
- IntelliJ's isolated startup log confirms the plugin loads alongside EmmyLua2
  and LSP4IJ. No plugin startup exception was recorded in that run.
- Toolkit typechecking and all 35 existing LSP tests pass. Lint of publish.ts
  still reports two pre-existing `prefer-promise-reject-errors` findings unrelated
  to the one-line publish-precondition fix.
- The bundled CLI passes local package/publish tests: archive contents,
  compile hooks, dirty-tree/wrong-branch rejection, release commits/tag/local push,
  version/changelog updates, mock upload/details, invalid credentials, hook
  failure, network failure and process-group cancellation. No live portal request.
- Native Factorio DAP protocol passes three launch/stop cycles with variables,
  evaluation, stepping, probe/dependency breakpoints and clean exit codes.
- API/prototype/event assistance, imports, locale close/reopen behavior, watcher
  registration and changelog protocol checks pass. Ordinary dependency edits
  propagate after the dependency's didOpen has completed.

## New isolated IDE evidence

After the desktop was unlocked, actual interaction with this plugin established:

- The regeneration action produced 1,168 Factorio 2.1.20 API files and updated
  EmmyLua libraries. Quick Documentation identifies `event.tick` as `MapTick`
  in `EventData.on_tick` without an explicit callback annotation.
- A standard Toggle Line Breakpoint on control.lua:13 selected the plugin's
  breakpoint. Debug launched Factorio and stopped at that source line.
- Variables showed `count = 41`, the nested table and `LuaSurface: nauvis`.
  Expanding the nested table displayed its inner table containing `value = 41`.
  The evaluation editor returned `42` for `count + 1` and `"nauvis"` for
  `game.surfaces[1].name`.
- Step Into entered helper.lua:6; Step Over reached line 7 with `sum = 42`;
  Step Out returned to control.lua:14 with `result = 42`. Another Step Into
  entered the copied LTN metrics.lua:66 with `key = "probe"`.
- Three launches hit the persisted probe breakpoint. The third session also
  hit a breakpoint in LTN metrics.lua:66 after its registration settled.
  Stop ended each process without an orphan. Those initial UI stops used
  SIGKILL; the September 25 fixes and two later clean UI stops supersede that
  shutdown limitation (see below).
- Build Mod ZIP completed and produced the probe archive. The publishing
  preview displayed the mod/version, destination and release effects; Cancel
  exited without supplying credentials or contacting the portal.
- Go to Declaration on the Lua locale key navigated to probe.cfg:2.
- The final build completed `game.get_sur` to `game.get_surface`,
  `item.stack_si` to `item.stack_size`, and `fmtk-probe.re` to
  `fmtk-probe.ready`. Prototype Quick Documentation showed the generated
  `data.ItemPrototype` field type and documentation.
- Local helper and cross-mod LTN calls navigated to their definitions. Helper
  documentation showed the integer parameters/return type. Adding `ui_added`
  to the copied dependency propagated to its consumer's completion after refresh.
- External locale creation, change, rename and deletion passed after IDE
  Refresh. With an unchanged Lua document/caret, replacing `fresh` with
  `fresher` in the locale file changed the next completion to `fresher`.
  Navigation followed the renamed file and reported no declaration after deletion.
- A duplicate locale key produced `Duplicate Key`; editing its name cleared
  that diagnostic before the explicit save. A malformed changelog separator
  produced `Category line prefix incorrect. Missing separator.`
- The settings page displayed the persisted executable paths, active mod and
  dependency. Increment Mod Version changed the copied mod from 0.1.0 to 0.1.1.
  Run Package Script executed a harmless fixture script and wrote its expected
  result beneath the copied mod.
- Reopening the project started one EmmyLua server and one FMTK server, using
  the persisted project settings. The previous servers exited with their IDE.

Temporary completion diagnostics were removed, and insertion checks were
repeated on the final build. The automation did not capture completion popups;
the evidence records the inserted text. The cache-reset contributor explicitly
precedes `LSPCompletionContributor`, and the same-caret locale update passed.

## Remaining acceptance and limitations

UI access intermittently returned `cgWindowNotFound` but recovered for the
checks above. The service checkbox and locale discard workflow subsequently
passed the September 25 checks below.
Real publishing and credential storage against a live portal are intentionally
outside acceptance; publishing execution uses the loopback harness.

The plugin suppresses raw stdio protocol output in its console, avoiding
LSP4IJ 0.21.0's fragmented-message filtering issue. Decoded DAP output events,
stderr and IDE status remain visible. Stop explicitly requests disconnect with
`terminateDebuggee: true`, waits up to five seconds off the UI thread, and
falls back to process-tree termination if the adapter does not exit.

EmmyLua 0.24 dispatches didOpen asynchronously while didChange is synchronous.
Sending an edit immediately after opening a dependency can let the open handler
overwrite the new buffer contents. The original rapid-open/edit probe failed;
waiting one second for opening to settle passed. A diagnostic-barrier variant
was also attempted, but library diagnostics are not reliably emitted. No upstream Rust server patch is bundled.

The IDE logged a recovered VFS `FileDeletedException` during the external
deletion test; the subsequent navigation result correctly reflected deletion.

## September 25 edge-case validation

- Disabled services through the settings checkbox: FMTK exited while the same
  EmmyLua process stayed alive. Reopening Configure now leaves the checkbox
  disabled; previously the action silently enabled services before Apply.
  Re-enabled services and restarted the IDE: one FMTK and one EmmyLua server
  ran using the persisted settings. Suggested mod roots are staged in the form
  rather than written when opening settings; the unit test covers cancellation.
- Added a locale key without saving; verified the disk file was unchanged and
  Lua completion inserted the unsaved key. Undid the locale edit, closed and
  reopened the locale file: the removed key yielded `No suggestions`, while the
  persisted key still completed. This UI check uses Undo to discard; the protocol
  harness separately covers didClose with an outstanding unsaved buffer.
- Two successive debug launches hit control.lua:13, displayed variables, and
  stopped with exit code 0. Factorio logged `Goodbye`; no Factorio process was
  left behind. Game output remained visible with no raw DAP fragments.
- Unit tests cover Stop initiating disconnect with stdin still open and an
  unresponsive adapter being killed within the bounded fallback.
- Plugin Verifier remains Compatible (8 experimental and 4 internal API uses).

## Evidence locations

Under `../spike/`:

- `plugin-final-build.log`, `plugin-verification.log`, `plugin-toolkit-tests.log`
- `plugin-final-publishing.log`, `plugin-publishing-tests/run-*/results.json`
- `plugin-protocol/evidence/` and its named run logs (retain failed runs)
- `plugin-ide/log/idea.log`
- `plugin-ui-evidence/` (individual accessibility captures, including failed
  completion attempts; only the checks described above are accepted)
- `plugin-ui-final-build.log`
- `plugin-edge-build.log`, `plugin-edge-tests.log`, `plugin-edge-verifier.log`
- `plugin-ui-evidence/edge-*` (settings, unsaved locale and clean DAP stop captures)

Gradle test and verifier reports are under `build/reports/`.

After acceptance, the isolated IDE and both language servers were stopped; no
Factorio process remained. Disposable probes and changed mod metadata were
preserved under `plugin-ui-evidence/fixture-final/`, then the copied mod's
metadata and LTN source were restored. The everyday IDE profile was not changed.

## Project/module follow-up

Tested with IDEA 2026.2.3 (IU-262.10968.63), macOS 27 on Apple M2 Max,
Factorio 2.1.20, patched EmmyLua2 0.24.0-115 and LSP4IJ 0.21.0.

- New Project and New Module both expose **Factorio Mod** and create the seven
  requested files plus prototypes/scripts/graphics directories. The generated
  mod loaded successfully in Factorio while creating a disposable save.
- Existing-sources import attached an external mod through the native importer.
  Hash comparison confirmed every imported mod file was unchanged. Its `.iml`
  was placed under the IDE project's `.idea/modules` directory.
- Fresh-project API generation and startup of EmmyLua/FMTK services were
  automatic. Reopening the multi-module project retained its modules.
- Settings listed imported/generated mods and the legacy configured root.
- Packaging from each external module's editor produced that mod's ZIP.
  A regression test verifies default metadata exclusions, user ignores, and
  explicit package extras against the updated toolkit CLI.
- A module-associated Factorio launch stopped at control.lua:13 with count=41;
  Stop exited with code 0 and disconnected successfully.
- Unit coverage includes invalid/duplicate identities, ambiguous roots,
  canonical symlink deduplication, module removal and metadata repair,
  nonempty destinations, metadata preservation, and legacy service migration.

Evidence: `../spike/module-ui/evidence`, `../spike/module-ui/load-test/output.log`,
`../spike/package-metadata-test/results.json` (paths relative to the repository).

**Original failure, resolved by the follow-up below:** `test-module-services.py` reproduced wrong short-import
resolution and failed named cross-mod imports with two attached external roots.
EmmyLua indexes a single global module path; identical helper.lua names collide,
and root stripping prevents the generated named module maps from matching.
FMTK locale completion and watched-file creation/deletion work in both roots. This result limits the new
multi-module Lua workflow and does not invalidate the earlier single-mod tests.
At that checkpoint no additional EmmyLua server patch was bundled. The previously documented
rapid-open/edit race also remains.

Removal acceptance also passed in the final isolated build: with all modules
removed, FMTK stopped and plugin-owned API libraries, roots and maps disappeared.
Pre-existing user roots/maps/libraries and diagnostics remained unchanged.
Evidence: `../spike/module-ui/evidence/removed-module-config.json`. The original
test modules were restored after closing the IDE. Final build/test/verifier log:
`../spike/plugin-modules-final-build.log` (22 tests, Compatible; four deprecated,
20 experimental and four internal API usages).

## EmmyLua multi-root fix follow-up

Configuration experiments (roots, libraries, packages, strict roots) could not
make both local and named imports correct. Evidence:
`../spike/emmy-config-tests/results.json`. Upstream main retained the same index.

The generic Rust fix is signed commit `966a16d` on
`fix/workspace-relative-module-resolution`, based on upstream `aaaca684`.
Development uses its signed backport `7562964` on `work/intellij-modules`, based
on 0.24.0, and EmmyLua2 `0.24.0-115-IDEA262-patched-modules`. Only the macOS arm64
server in that development ZIP is patched. Both repositories remain separate;
the upstream PR branch contains no local packaging or Factorio-specific logic.

- Upstream: 1,096 analysis tests and 213 server tests passed, one ignored server
  test. Clippy completed with warnings only in unchanged files.
- 0.24.0 development backport: 1,082 analysis tests and 213 server tests passed,
  one ignored server test. Release build and EmmyLua2 packaging passed.
- Multi-module definition, inferred member completion, named cross-mod imports,
  and locale completion/create/delete checks all pass. Existing single-mod API,
  event, dependency-edit and locale lifecycle protocol checks also pass.
- Actual IDE: wizard-probe imported its own `wizard_marker`; import-probe imported
  its own `import_marker`; the named cross-mod import completed `import_marker`
  and navigated to import-probe/helper.lua. Local import navigation selected the
  correct helper in both modules.
- Empty-prefix completion did not consistently display in the client even when
  the captured server response was correct. Typing a member prefix and invoking
  completion worked. The earlier rapid-open/edit race remains outside this fix.
- Development preparation now replaces owned dependency directories instead of
  leaving old versioned JARs beside the new ones. Startup confirms the modules
  build; the temporary protocol wrapper was removed after validation.
- Factorio plugin: 22 tests pass and Plugin Verifier reports Compatible against
  the new development dependency (same dependency API warnings).

Evidence: `../spike/emmy-{upstream,development}-protocol.log`,
`../spike/emmy-development-single-mod.log`, `../spike/emmy-module-ui-evidence`,
`../spike/emmy-{patch-test,upstream-ls-tests,development-tests,upstream-clippy}.log`,
`../spike/emmylua2-module-build.log`, `../spike/factorio-emmy-module-build.log`.


## September 27: main-based analyzer and relocated repositories

`emmy-analyzer.lock` now pins `34b9c1071b5bc9b075c851deaf259657de775acf`,
the main-based analyzer development merge (0.25.1). EmmyLua2 packaging commit
`47bfa05` on `work/patched-build` produces
`IntelliJ-EmmyLua2-0.25.1-115-IDEA262-patched-modules.zip`. Both upstream
repositories, plus `vscode-factoriomod-debug`, now live under `../upstream/`.
Build, license collection, and packaging test paths have been updated. The FMTK
source pin remains `6dc3400fafbeb5f2c21702e06b7003e3027d4226`.

Validation against the new dependency:

- `scripts/build.sh test buildPlugin verifyPlugin` passed: 22 tests, no failures
  or skipped tests. Plugin Verifier reports Compatible with IDEA
  IU-262.10968.63; the existing four deprecated, 20 experimental, and four
  internal API usages remain.
- All 10 single-module and 12 multi-module language-service checks passed with
  the packaged arm64 analyzer and rebuilt FMTK bundle. Single-module tests used
  a fresh copy of the existing fixture, with absolute library paths updated.
- Packaging metadata and simulated publishing regressions passed after their
  toolkit paths were updated. Publishing used fake credentials, temporary Git
  repositories, and a loopback-only portal; no real publication occurred.
- The Factorio ZIP's toolkit source stamp matches `toolkit.lock`, and its CLI
  matches the tested bundle. The EmmyLua2 package's embedded analyzer pin and
  native binary hash were checked during its build.

Evidence is in `build/evidence/emmy-0.25.1/` (build, protocol, packaging and
simulated-publishing logs; single/modules JSON results). The new artifact is
`build/distributions/intellij-factorio-0.1.0-dev.zip`.

`test-language-services.py` now accepts `EMMY_LS` and resolves tool paths from
this repository, independently of `FACTORIO_TEST_ROOT`. This permits testing a
new analyzer with fresh fixtures without updating the isolated IDE installation.
Neither IDE installation was changed, and actual IDE UI/debug acceptance was
not rerun. The earlier completion-display and rapid-open/edit limitations remain.
Only macOS arm64 includes the local analyzer patch; other servers in the
EmmyLua2 package are upstream 0.25.1 binaries.
