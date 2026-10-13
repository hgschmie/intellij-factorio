# Toolchain check correction — 2026-09-28

Version 0.2.4 checks FMTK with `--help`, which includes its version and exits
successfully. FMTK has no `--version` option; its `version` subcommand increments
a mod's version and is not a diagnostic command. Node/Factorio `--version` and
Factorio `--help`/`--dap` validation are unchanged. The four diagnostic commands
were verified directly with the installed tools; the Tools menu was not exercised
interactively for this fix.

# Development tree relocation — 2026-09-28

Active scripts now use `dev/` and repository-local `build/` paths. Historical
paths in the older results below remain unchanged. See
[DEVELOPMENT_LAYOUT.md](DEVELOPMENT_LAYOUT.md) for the directory map.

Validated after relocation: analyzer release build and EmmyLua2 package/hash/pin
checks, 30 Factorio unit tests, five Factorio platform tests using real-mod copies,
five EmmyLua2 routing tests, language/module protocol probes, package metadata
and mock-portal publishing checks. The relocated debugger fixture also passed
three launch/breakpoint/inspect/evaluate/step/dependency-breakpoint/disconnect
cycles, with exit code zero each time. Build logs are now automatically captured under
each repository's `build/logs/`. The isolated IDE profile was prepared at
`dev/ide`; it was not opened for interactive UI acceptance. Preparation replaces
the Factorio plugin directory before extracting its ZIP so obsolete versioned
JARs cannot remain alongside the current JAR.

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

## Separate runtime attribute read/write types — 2026-09-30

Implemented and validated on macOS arm64 with Factorio 2.1.20. FMTK's optional
`docs --target emmylua` emits complementary `@field (read)` / `@field (write)`
definitions. The plugin requests that format and includes its target/revision in
the definition cache key. Existing FMTK output remains the default.

The analyzer keeps getter inference separate from setter checking, including
nilability, inherited/generic fields, assigned callback parameters, table input
context, completion, hover, and definition navigation. Writes invalidate prior
getter narrowing without replacing the getter type with the assigned value.
Read-only/write-only restrictions and directional index operators are deferred.

Source revisions and review branches:

- Toolkit development `63181fb`, review `fix/asymmetric-api-attributes` at
  `fe2636b` (based on `upstream/next`). The review worktree is
  `upstream/vscode-factoriomod-debug-worktrees/asymmetric-api-attributes`.
- Analyzer development `b7b7045a`, review `fix/asymmetric-field-types` at
  `384d9d35`, stacked on `fix/index-metamethod-presentation`. The review worktree
  is `upstream/emmylua-analyzer-rust-worktrees/asymmetric-field-types`.
- EmmyLua2 build `0.25.1-127-IDEA262-patched-modules`, packaging commit `1ca4499`.
  Shared build paths now also work from the linked patched-build worktree.

Validation:

- FMTK typecheck, changed-file lint, and two CLI generation tests passed.
- Analyzer development: 1,127 analysis tests, 239 LSP tests (one pre-existing
  ignored test), and 191 parser tests passed. Nine focused tests also passed on
  the independent analyzer review branch.
- The packaged analyzer passed `python3 scripts/test-asymmetric-api.py` against
  all 17 asymmetric attributes in the installed API: getter assertions before
  and after writes, valid setter input, deliberately invalid assignments, burner
  hover, and getter-member completion after assigning a string identifier.
- Factorio plugin: 35 unit tests and the complete 17-test IntelliJ platform suite
  passed, including `testAsymmetricApiAttributes`, regeneration, per-mod services,
  and navigation using copies of real mods. EmmyLua2's 22 plugin tests passed.
- Platform command: `bash scripts/build.sh
  -Porg.jetbrains.intellij.platform.useCacheRedirector=false -PplatformTests
  -PrealMods=/Users/henning/factorio/mods_21 test buildPlugin`.
- An initial platform run lacked the existing real-mod test input; a later run
  caught a hover assertion that assumed one alias rendering. The final full run
  passed after supplying the fixture and accepting equivalent named/expanded
  write types.

Evidence is in `build/logs/asymmetric-*.log` and
`build/test-work/asymmetric-api/results.json`; analyzer suite logs are in its
active development worktree's `build/` directory. The platform test uses isolated
IDE processes. No interactive desktop acceptance or regular-profile installation
was performed. Only the macOS arm64 bundled server includes these patches.

The Factorio plugin version is `0.4.0-dev` for this feature, which requires the
matching patched EmmyLua2 build below. The version-only change passed
`buildPlugin`, and the packaged plugin descriptor was verified as `0.4.0-dev`.
The build log is `build/logs/version-0.4.0-dev-package.log`.

Installable archives:

- `intellij-factorio/build/distributions/intellij-factorio-0.4.0-dev.zip`
- `upstream/Intellij-EmmyLua2-worktrees/patched-build/build/distributions/IntelliJ-EmmyLua2-0.25.1-127-IDEA262-patched-modules.zip`

Install both matching archives and regenerate the Factorio API definitions to
use the feature in an existing IDE installation.

## DAP tracing controls (2026-09-30)

Factorio run configurations now persist verbose console tracing, optional
DAP-only file logging, and an output folder. A bounded background writer creates
a unique UTF-8 `.jsonl` file for every session. Each line is a compact DAP
envelope with sequence IDs, command/event, and payload, serialized with LSP4J's
DAP serializer. Console-only tracing remains human-readable and verbose.
Logging failures disable only file logging.
When saving to file, protocol traces are excluded from the console; normal game
output and debugger errors remain visible. File failures do not enable console
tracing as a fallback.
The default folder is `factorio/dap` beneath IntelliJ's log directory; the debug
console reports the actual filename. No LSP4IJ patch or dependency update is needed.

Validation:

- 42 unit tests passed; the opt-in real-game test is skipped in the normal suite.
  Coverage includes both message directions, console-only/file-only trace routing,
  continued protocol output/error delivery, numeric envelope IDs, successful and
  failed responses, escaped multiline strings, UTF-8 payloads, concurrent sessions,
  ordered draining, startup disposal, file
  creation/write failures and queue overflow.
- Three IntelliJ UI tests and the existing breakpoint regression test passed.
  Logging controls were checked for enabled states, staging, XML persistence,
  cloning and legacy defaults. The rendered form was visually inspected at
  `build/reports/ui/run-configuration.png`.
- The opt-in Java/LSP4J protocol test launched Factorio twice using the disposable
  fixture, hit a breakpoint, read scopes/variables, evaluated `count + 1`, stepped
  in/out/over, and disconnected. Both sessions exited successfully and produced
  distinct files containing bidirectional requests/responses and stopped events.
  Every line parsed as compact JSON, response `request_seq` values matched request
  `seq` values, and output/evaluation payloads were preserved;
  no protocol traces reached the console, and DAP output delivery remained active.
- `test buildPlugin` passed. An initial sandboxed run failed three existing
  process-management tests because macOS process inspection was denied; the suite
  passed with that access enabled.

Preserved results are in `build/reports/dap-jsonl-results/` and
`build/reports/dap-platform-results/`; actual game traces are under
`build/test-work/dap-protocol/`. Build invocation logs are in `build/logs/`.
To repeat the opt-in protocol test, prepare the fixture through `scripts/protocol.py`,
then run:

```sh
FACTORIO_DAP_FIXTURE="$PWD/build/test-work/protocol/fixture" \
  bash scripts/build.sh -Porg.jetbrains.intellij.platform.useCacheRedirector=false \
  test --tests 'de.softwareforge.factorio.FactorioDap*'
```

`FACTORIO_EXECUTABLE` optionally overrides the macOS Factorio executable. The
fixture must use a separate write-data directory. These checks did not install
into or alter the regular IDE profile, and did not exercise an interactive IDE
debug session. This feature bumps the plugin to `0.4.1-dev`; its archive is
`build/distributions/intellij-factorio-0.4.1-dev.zip`.

## Game-requested debugger restart (2026-09-30)

Factorio 2.1.20 sends `terminated.body.restart.relaunchArgs` after applying a
changed mod selection, then exits. LSP4IJ 0.21.1's default `DAPClient.terminated`
ignores the restart field. The Factorio descriptor now handles that event,
waits up to 15 seconds for the old process handler to finish, and asks IntelliJ
to restart the run profile on the EDT. The next launch receives the original
restart value as `__restart`, following the
[DAP terminated-event contract](https://github.com/microsoft/debug-adapter-protocol/blob/main/debugAdapterProtocol.json).
This is session-only data, consumed from the execution environment without
editing or persisting the run configuration. No upstream patch is required.

Duplicate events cannot launch multiple replacements. Stop cancels a pending
restart synchronously; normal termination, project disposal, closed debug tabs,
and a process that fails to exit do not launch a replacement. The new session
performs initialization and breakpoint registration again, and gets its own DAP
log when file tracing is enabled.

Validation:

- The real game's restart event was captured after the user toggled the test
  mod in an isolated Factorio instance. Its unmodified payload was replayed in
  a new native `factorio --dap` connection: initialization/launch completed and
  disconnect exited with code 0. Evidence: `build/test-work/restart-probe/evidence/`
  and `build/logs/restart-relaunch.log`. A first replay under the restricted
  process sandbox crashed in macOS graphics initialization; the replay succeeded
  with normal app runtime access.
- The IntelliJ platform regression runs the real run-profile runner, LSP4IJ
  clients and breakpoint registration against a controllable adapter. It checks
  two successive automatic restarts (including immediate process exit without a
  disconnect response), opaque payload transfer, duplicate events, restored
  breakpoints, normal quit, a clean manual rerun, and Stop during pending restart.
  It and the existing three configuration UI tests and breakpoint regression
  passed: five tests total. Results: `build/reports/restart-platform-results/`.
  The test keeps background progress tasks asynchronous, as in a running IDE;
  IntelliJ's default synchronous headless behavior deadlocks LSP4IJ startup.
- Unit coverage exercises restart values, single-use payload consumption,
  duplicate suppression, and cancellation before/after the request.
- `test buildPlugin` passed all 47 unit/protocol tests, including the opt-in
  real-game test's two launches, breakpoint hits, variable inspection, evaluation,
  stepping and clean disconnects. Preserved XML: `build/reports/restart-unit-results/`.

Repeat the platform checks with:

```sh
bash scripts/build.sh -Porg.jetbrains.intellij.platform.useCacheRedirector=false \
  -PplatformTests test --tests de.softwareforge.factorio.FactorioRestartTest \
  --tests de.softwareforge.factorio.FactorioBreakpointTest \
  --tests de.softwareforge.factorio.FactorioUiTest
```

The user installed `0.4.2-dev` and confirmed that changing the mod configuration
in the game now restarts Factorio with the debugger in their running IDE.
This completes interactive acceptance alongside the protocol and platform tests.
The build is `build/distributions/intellij-factorio-0.4.2-dev.zip`.


## Nullable dictionary lookups, 0.5.0-dev (2026-10-01)

This minor development release pins analyzer `772ec76d` and uses patched EmmyLua2
`0.25.1-128-IDEA262-patched-modules`. The independent analyzer change is
`fix/nullable-table-index` (`3548b7f5`), merged into `work/intellij-modules`.
Set `"strict": {"tableIndex": true}` in the mod's `.luarc.json` or `.emmyrc.json`
to treat missing dictionary entries as `nil`. The option is off by default.
No managed configuration override or annotation change is required. Required
named fields, explicit `__index` function returns, and iteration value types
remain unchanged. No production glue with the read/write patch was needed;
combined regression coverage lives on the analyzer work branch.

Validation:

- Independent analyzer: 1,101 analysis, 213 LSP, 191 parser tests passed.
  Combined analyzer: 1,139 analysis, 239 LSP, 191 parser tests passed. Each LSP
  suite has one existing ignored test. These source checks preceded packaging.
- EmmyLua2 release packaging and all 22 plugin tests passed. The ZIP's descriptor,
  analyzer source pin, executable hash, and Unix server modes (`0755`) were
  verified. System unzip preserved executable permission without correction.
- Factorio `test buildPlugin` passed: 46 unit tests passed, with the opt-in
  real-game DAP test skipped (47 total). Existing native module configuration/VFS
  reload, asymmetric API attributes, and Lua language-level platform tests all
  passed: three platform tests against the newly prepared dependency.
- A real stdio LSP probe ran the extracted release analyzer with FMTK's actual
  `mods.lua` annotation and a module-local `.luarc.json`. With `tableIndex` off,
  `mods["nullius"]` retained `string` and the original `unnecessary-if` warning.
  With it on, hover reported `string?` and that warning disappeared. The required
  field positive control still warned in both cases.

Evidence is under `build/logs/nullable-index-*`,
`build/reports/nullable-index-unit-results/`,
`build/reports/nullable-index-platform-results/`,
`build/reports/nullable-index-artifacts.json`, and
`build/test-work/table-index-package/`. EmmyLua2 build/test logs are in its worktree's
`build/logs/`. Automated checks did not modify the regular IDE profile or user
mod files. On 2026-10-01, the user confirmed that the rebuilt patch works in
their running IntelliJ instance, completing live IDE acceptance.

Install both archives, retained beside previous releases:

- `build/distributions/intellij-factorio-0.5.0-dev.zip`
- `../upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.25.1-128-IDEA262-patched-modules.zip`

The EmmyLua2 packaging script now also copies and verifies worktree-built ZIPs in
that main-repository distribution folder. Only the macOS arm64 analyzer carries
the local patches; other platform binaries remain upstream 0.25.1.

## Optional dictionary keys, 0.5.1-dev (2026-10-01)

This patch release pins analyzer `e7ffd3b5` and uses patched EmmyLua2
`0.25.1-129-IDEA262-patched-modules`. The independent branch
`fix/optional-dictionary-keys` contains signed commits `e3d5b79e` and `8dcd6c95`;
the latter addresses the review findings. Signed merge `e7ffd3b5` integrates
them into `work/intellij-modules` with strict/read-write compatibility coverage.

Indexing `table<uint64, ProcInfo>` with `uint64?` now yields `ProcInfo?`, avoiding
the false always-falsy warning on the following guard. The fix works with
`strict.tableIndex` off or on. Literal and instantiated generic aliases retain
their key types, and exact matching key aliases reuse a lookup while preserving
nil branches. Required named-field overrides remain intact. The original bug
predates `strict.tableIndex`; this release does not change that option's default.

Validation:

- Independent analyzer: 1,106 analysis, 213 LSP and 191 parser tests passed.
  Combined work branch: 1,157 analysis, 239 LSP and 191 parser tests passed.
  Each LSP suite retains one ignored test. Fifteen focused regression tests
  cover alias forms, required fields, recursive aliases and large key unions.
  The 8,192-member matching-alias cases take about 6 ms in debug tests instead
  of seconds; these are not release-server latency measurements.
- EmmyLua2 release packaging and all 22 plugin tests passed. Archive versions,
  source pins, binary hashes and executable modes (`0755`) were verified;
  system extraction preserves the executable bit without a repair step.
- Factorio `test buildPlugin` passed: 46 unit tests passed and one opt-in game
  test was skipped. The native module configuration/VFS reload, asymmetric API
  attributes and Lua language-level platform tests all passed (three tests).
- A stdio LSP probe exercised the packaged release server with both strict flag
  values. The original optional-key hover is `ProcInfo?`, its guard has no false
  warning, and guarded hover retains `ProcInfo`. Asserted keys retain the existing
  strict-option behavior. Optional literal and generic alias cases both yield
  `string?` without false warnings. The required-field positive control still
  warns in both cases.

Evidence: `build/logs/optional-key-*`,
`build/reports/optional-key-unit-results/`,
`build/reports/optional-key-platform-results/`,
`build/reports/optional-key-artifacts.json`, and
`build/test-work/optional-key-package/`. EmmyLua2 build/test logs are in its
worktree's `build/logs/`; analyzer logs are in each analyzer worktree's `build/`.

Install both archives from the usual distribution folders:

- `build/distributions/intellij-factorio-0.5.1-dev.zip`
- `../upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.25.1-129-IDEA262-patched-modules.zip`

Only the macOS arm64 analyzer contains local patches. No regular IDE profile or
user mod files were modified. Manual IDE acceptance remains pending.

## Imported function calls in assignment targets, 0.5.2-dev (2026-10-02)

This patch release pins analyzer `a7ea9de6` and uses EmmyLua2
`0.25.1-130-IDEA262-patched-modules`. The independent analyzer fix is signed
`3ac513d3` on `fix/duplicate-field-index-calls`, based on upstream main `aaaca684`;
it was merged into `work/intellij-modules` without conflicts or compatibility glue.

The upstream duplicate-field checker mistook calls in assignment targets, such as
`inventory[tools.createItemIdentifier(item)] = item`, for overwrites of the imported
function. The check now compares the reference with the complete assignment
target. References in its key or prefix are reads, and direct writes still warn.
This reproduces in both official analyzer 0.25.1 and our previous build 129.

Validation:

- Two new analyzer tests cover eight read-only expressions and five genuine
  redefinitions. The read-only test fails before the fix. Cases include dot and
  bracket syntax, function-valued keys/right-hand sides, multiple assignments,
  calls returning assignment prefixes, and function declarations.
- Independent analyzer: 1,093 analysis, 213 LSP, 191 parser tests passed.
  Combined work branch: 1,159 analysis, 239 LSP, 191 parser tests passed.
  Each LSP suite retains one existing ignored test. Formatting and whitespace
  checks passed.
- EmmyLua2: all 22 plugin tests and release packaging passed.
- Factorio: `test buildPlugin` passed, with 46 unit tests passing and one opt-in
  game test skipped. The three native module configuration/VFS reload,
  asymmetric API attributes and Lua language-level platform tests passed.
- The packaged release server passed a stdio LSP check: the inline call and
  temporary-key variant produce no duplicate diagnostic; direct reassignment
  still reports `duplicate-set-field` on the assigned member.
- Archive plugin IDs/versions, analyzer pin, binary hashes and `0755` Unix server
  permissions were verified. The main-repository and worktree Emmy ZIPs match.

Evidence is in `build/logs/duplicate-field-*`,
`build/reports/duplicate-field-unit-results/`,
`build/reports/duplicate-field-platform-results/`,
`build/reports/duplicate-field-artifacts.json`, and
`build/test-work/duplicate-index-call-package/`. The before-fix and official-server
comparisons are in `build/test-work/duplicate-index-call/` and
`build/test-work/duplicate-index-call-upstream/`, with matching protocol logs under
`build/logs/`. Analyzer and EmmyLua2 build/test logs remain in their worktrees.

Install both archives, retained beside previous releases:

- `build/distributions/intellij-factorio-0.5.2-dev.zip`
- `../upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.25.1-130-IDEA262-patched-modules.zip`

Only macOS arm64 includes the analyzer patch. The official LSP4IJ dependency is
unchanged. No regular IDE profile or user mod files were modified; manual IDE
acceptance remains pending.

## Formatting with multiple module servers, 0.5.3-dev (2026-10-07)

Reformat could select another mod's EmmyLua server because LSP4IJ's started-server
selection checked the shared Lua mapping without checking file enablement. That
server did not index the requested file and returned no edits. This is independent
of our upstream DAP breakpoint contribution; the formatter-selection code predates
it. Module-local `.luafmt.toml` works when the owning server receives the request.

Factorio now enforces module ownership in its formatting feature as well as its
client features. LSP4IJ's separate `fix/file-scoped-formatting` branch contains
signed commit `a0b68b10`, based directly on upstream main `4796cf99`. It adds the
file-enablement check to `hasAny` and `processLanguageServers` and includes four
regression tests. Signed merge `9191439a` integrates it into `work/factorio-build`;
work-only build commit `bce37200` packages the IDEA 262 version.

The release pins `0.21.1-SNAPSHOT-factorio-formatting-patched` by source revision
and ZIP checksum in `lsp4ij.lock`. `scripts/build-lsp4ij.sh` prepares that local
build and retains support for official archives pinned by URL/checksum. EmmyLua2
build 131 is rebuilt against it; analyzer pin `a7ea9de6` is unchanged.

Validation:

- All four independent LSP4IJ regression tests fail before the fix and pass
  after it: both shared helpers, whole-file formatting, and selected-range
  formatting. Tests include two servers with identical mappings but different
  file owners, plus changes to file enablement.
- The broader LSP4IJ comparison ran 66 tests: 29 failures without the fix,
  25 with it. All 25 remaining failures are the same pre-existing typing and
  automatic indentation failures on the local IDEA 262 SDK; no new failures.
  The release's 18 selected tests pass, covering the fix, existing formatting,
  server lifecycle, DAP breakpoints and upstream read/write usage handling.
- Factorio's new real-server test passes with the previous official LSP4IJ
  nightly, proving the local guard independently. Both mods honor their
  `.luafmt.toml` for whole-file and selection formatting; selection formatting
  leaves the unselected line unchanged.
- EmmyLua2's 22 plugin tests pass. Factorio's 46 unit tests pass, with one
  opt-in game test skipped. Ten self-contained IntelliJ platform tests pass:
  formatting, registry thread/lifecycle handling, independent Lua/locale
  servers, unsaved dependencies, native configuration/VFS reload, API isolation,
  asymmetric API attributes, Lua language level, breakpoint removal and restart.
  An initial broader run also selected the optional real-mod navigation test
  without `-PrealMods`; its missing-prerequisite assumption was reported as a
  failure by the platform harness. The release selection excludes that test.
- All three archive plugin IDs and versions were verified. LSP4IJ's SHA-256
  matches its source pin; the toolkit metadata and analyzer pin match the lock
  files. EmmyLua2's worktree/shared archives and prepared server bytes match,
  with Unix executable permissions preserved as `0755`.

Evidence: `build/logs/file-scoped-*`, `build/reports/file-scoped-unit-results/`,
`build/reports/file-scoped-platform-results/`,
`build/reports/file-scoped-official-factorio/`, and
`build/reports/file-scoped-artifacts.json`. Independent LSP4IJ comparison results
are in its fix worktree's `build/reports/file-scoped-comparison.json`; release
tests/build logs are in its build worktree. EmmyLua2 logs are
`build/logs/file-scoped-131-{tests,build}.log` in its worktree.

Install all three archives from their usual distribution directories:

- `build/distributions/intellij-factorio-0.5.3-dev.zip`
- `../upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.25.1-131-IDEA262-patched-modules.zip`
- `../upstream/lsp4ij/build/distributions/lsp4ij-0.21.1-SNAPSHOT-factorio-formatting-patched.zip`

Automated checks did not modify user mod files or the regular IDE profile.
On 2026-10-07, the user confirmed that formatting works in their running IDE,
completing live acceptance of this release.

## Reviewed formatting selection, 0.5.4-dev (2026-10-08)

This patch release updates LSP4IJ to
`0.21.1-SNAPSHOT-factorio-formatting-patched2` and rebuilds EmmyLua2 as
`0.25.1-132-IDEA262-patched-modules`. Factorio's ownership guard and the analyzer
and toolkit source pins are unchanged.

The review of [LSP4IJ PR #1688](https://github.com/redhat-developer/lsp4ij/pull/1688)
requested a shared matching predicate and an explicit null check for the PSI
file's virtual file. Signed follow-up `4ef6aa5c` implements both; its filter is
non-null, and the processor path supplies an accepting predicate. It adds a
nonphysical-file test and checks that an explicit rejecting filter stays effective.
The follow-up was pushed to `fix/file-scoped-formatting`, merged with signed
`8b9b8abb` into `work/factorio-build`, and packaged by `d96484ea`.

All 19 selected LSP4IJ tests pass on both the independent and development
branches, including five file-enablement regressions, existing formatting,
server lifecycle, breakpoint handling and read/write usage tests. These checks
use the installed IDEA 262 SDK. The broader suite's previously documented
baseline comparison was not repeated for this refactoring.

EmmyLua2's 22 plugin tests passed. Factorio's unit suite passed 46 tests with
one opt-in real-game test skipped; all ten selected platform tests passed,
including two-mod formatting with `.luafmt.toml`, module/service isolation and
reload, API typing, breakpoint removal and debugger restart. All three archives'
IDs, versions and pins were verified. The analyzer bytes are unchanged from
build 131; Unix server modes remain `0755`, and the shared/worktree EmmyLua2
archives match.

Logs and reports use the `file-scoped-review-*` prefix under each repository's
`build/`. Factorio's release evidence includes
`build/reports/file-scoped-review-unit-results/`,
`build/reports/file-scoped-review-platform-results/`, and
`build/reports/file-scoped-review-artifacts.json`. The updated PR's Windows CI
and archive build passed; Linux CI still failed before compilation while trying
to provision JetBrains Runtime 21. Its log is retained in the LSP4IJ fix worktree
at `build/logs/pr-1688/review-ubuntu.log`.

Rebuilt archives, retained beside the previous release:

- `build/distributions/intellij-factorio-0.5.4-dev.zip`
- `../upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.25.1-132-IDEA262-patched-modules.zip`
- `../upstream/lsp4ij/build/distributions/lsp4ij-0.21.1-SNAPSHOT-factorio-formatting-patched2.zip`

No regular IDE profile or user mod files were modified. The user accepted the
original formatting feature in 0.5.3-dev; these rebuilt packages have automated
validation only so far.

## Graceful debugger shutdown, 0.5.5-dev (2026-10-08)

Stop previously sent `disconnect` immediately and disposed the client when that
request completed. Disposal closed Factorio's stdin and the DAP reader while the
game could still be shutting down. The IDE log contained repeated `Pipe closed`
errors from late stdout. The user reported game crashes when stopping at a
breakpoint; the precise native crash mechanism was not established.

The Factorio client now follows the sequence used by VS Code: send `terminate`
when supported, wait for `terminated`, then send `disconnect`. A successful
terminate response is only an acknowledgement; it does not close the transport.
Factorio does not advertise `supportTerminateDebuggee`, so that optional
disconnect argument is omitted, as it is in VS Code. Game-requested restarts
still use `disconnect(restart=true)` and retain the existing relaunch payload.
Stop cancels pending restarts, including a restart event arriving during Stop.

Client disposal waits for process termination, IntelliJ's output readers, and
the DAP parser to finish, preserving final protocol events and file logs. On
macOS/Linux, adapter cleanup sends SIGTERM through `ProcessHandle.destroy()`;
unlike `Process.destroy()`, this does not close the Java stdio streams. Windows
retains process-tree termination, like VS Code. Recovery remains bounded: five
seconds for termination, two for disconnect, and five after SIGTERM before a
forced process-tree kill. These automatic timeouts are our recovery policy;
VS Code's first terminate request can wait for another user Stop instead.

Sources compared against the installed VS Code 1.141.0 revision:

- [Stop request selection](https://github.com/microsoft/vscode/blob/2a59476c9bfcb90b3ddc372c36762471b7dfad1c/src/vs/workbench/contrib/debug/browser/debugSession.ts#L426)
- [Disconnect and adapter shutdown](https://github.com/microsoft/vscode/blob/2a59476c9bfcb90b3ddc372c36762471b7dfad1c/src/vs/workbench/contrib/debug/browser/rawDebugSession.ts#L592)
- [Executable adapter signals](https://github.com/microsoft/vscode/blob/2a59476c9bfcb90b3ddc372c36762471b7dfad1c/src/vs/workbench/contrib/debug/node/debugAdapter.ts#L344)

Validation uses the actual IntelliJ runner and LSP4IJ client with a controllable
paused adapter. It covers delayed output after both acknowledgements, stdin
remaining open, exact terminate/disconnect ordering, unsupported and rejected
terminate requests, timeouts, SIGTERM recovery, forced fallback, and process exit
without a terminated event. The existing restart test also covers duplicate
events, immediate exit, breakpoint restoration and Stop cancelling a restart.
The unit process test independently checks final stdout and open stdin while
handling SIGTERM.

Final results: 46 unit tests passed (the opt-in native test skipped in that run),
all seven new shutdown platform tests passed, and the existing restart platform
test passed. The new paused-shutdown regression was also run against HEAD's old
implementation and failed specifically because stdin closed before cleanup;
the fixed sources were restored before the final passing run and archive build.
The final platform log contains no `Pipe closed` errors.

The opt-in native protocol test ran two full Factorio 2.1.21 sessions against
the isolated fixture: breakpoint, variables, evaluation, step into/out/over,
then the production shutdown coordinator while paused. Both sessions exited
with code 0, emitted `Goodbye`, and preserved the final `terminated` event in
their JSON Lines logs. This is protocol and platform-test validation, not a
manual test of the installed plugin in the user's project. Windows was not run.

Evidence uses `build/logs/dap-shutdown-*`, `build/reports/dap-shutdown-*`,
`build/test-work/shutdown-baseline/`, and native session
`build/test-work/dap-protocol/session-5095530084702815587/`.
The first sandboxed platform JVM aborted before tests; the runtime-access retry
worked. A unit fixture initially mistook the echoed command for child stdout;
its readiness check was corrected to accept only stdout events.

Installable archive: `build/distributions/intellij-factorio-0.5.5-dev.zip`.
LSP4IJ, EmmyLua2, analyzer and toolkit pins are unchanged. No regular IDE profile
or user mod files were modified.

## Factorio Development Environment branding, 0.5.6-dev (2026-10-08)

The IntelliJ plugin is now **Factorio Development Environment (FDE)**. Its plugin
descriptor, Gradle metadata and settings page use the full name. Toolkit task
titles and plugin-generated operation/error messages use FDE. The toolkit field
is labelled **Toolkit CLI**, with a tooltip identifying the bundled engine.
The plugin description and README credit and link **Factorio Modding Tool Kit
(FMTK)** for API definitions, language services and packaging/publishing.
References to the actual FMTK CLI, schemas, configuration formats, environment
variables and dependency paths retain the upstream name. The plugin ID and
settings identifiers remain stable so this archive updates the existing plugin.

Added `META-INF/pluginIcon.svg` for the Plugins page. Its explicit 40×40 viewport
embeds the original 144×144 Factorio thumbnail from
`/Applications/factorio.app/Contents/data/base/thumbnail.png`, retaining the
supplied artwork and transparent padding. The image bytes are unchanged; the
SVG viewport controls display size. This preserves the original raster artwork
rather than tracing it into vector shapes. The existing 16×16 and 32×32 PNGs
remain the in-product file/action icons. The logo is credited to Wube Software.

The [JetBrains plugin-logo specification](https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html)
requires `META-INF/pluginIcon.svg` and a 40-pixel nominal size; plugin lists and
details use different display scales. Rendered the new resource with the
installed IDEA 262 SVG renderer at 40×40, 80×80 and 160×160 and visually inspected
the results. The 40-pixel rendering has visible bounds x=2..37, y=3..36, preserving
the required transparent perimeter. The same artwork works on light and dark
backgrounds, so no duplicate dark-theme resource is needed. The embedded source
keeps the SVG around 34 KB, larger than JetBrains' ideal 2–3 KB vector logo size.

`test buildPlugin` passed: 46 unit tests passed, one opt-in native game test was
skipped. Inspected the built JAR inside the distribution ZIP: the plugin name,
version, settings display name and FMTK acknowledgement match; the plugin logo
resource is packaged and embeds the original image bytes. The small PNGs and
toolkit source pin also match the existing assets and lock file. Light/dark
render previews are in `build/test-work/branding/logo-themes.png`. Manual
verification in the user's Plugins page has not been performed.

Evidence: `build/logs/fde-branding-build.log`, `build/test-work/branding/`, and
`build/reports/fde-branding-*`. Installable archive:
`build/distributions/intellij-factorio-0.5.6-dev.zip`.
No runtime dependency pins or user IDE profiles were changed.

## Lua debug REPL and evaluation errors, 0.6.0-dev (2026-10-09)

The user's red, bare `error` lines were failed watch evaluations, not shutdown
failures. Factorio returns `message: "error"` with the useful Lua diagnostic in
`body.error.format`. FDE now decodes that detail (including format variables) for
watch, hover and interactive evaluation failures. Evaluation errors stay next to
the expression; other failed DAP requests and genuine game stderr still appear in
the game console. File traces preserve the original DAP envelopes and error body.
Console-only traces retain the existing human-readable format.

Nested evaluation exception stops were caused by FDE forcing
`hookDebugConsole: true`. Factorio documents that flag as enabling error hooks
inside REPL evaluation; both Factorio and VS Code default it to false. FDE now
sends false, including for previously saved launch configurations. No synthetic
continue or step-out requests are needed, and evaluation context remains `repl`.
Normal game exception filters are unchanged.

Added a **Lua REPL** debugger tab using IntelliJ's expression history editor and
value tree. Enter/Evaluate sends an expression or statement with the currently
selected DAP frame. Results are expandable, including nested tables and Factorio
objects. The game console is read-only so input cannot corrupt protocol stdin.
One REPL evaluation runs at a time; up to 100 results are retained while paused.
Resuming/stepping/stopping expires the results and ignores late responses;
command history is retained. This release requires a suspended Lua frame and has
no REPL completion: Factorio 2.1.21 does not advertise `supportsCompletionsRequest`.
No upstream dependency changes were needed.

Validation:

- 50 unit/native tests passed, including two isolated Factorio 2.1.21 sessions.
  With all three exception filters enabled, Lua errors, an invalid native API
  argument, a nil member and a syntax error returned failed evaluation responses
  without a nested stop or changed stack/frame/line. Assignment persisted, table
  results expanded, stepping worked, and both sessions terminated with Goodbye
  and exit code 0.
- 11 IntelliJ platform tests passed: three new REPL tests plus seven shutdown
  cases and the existing restart test. REPL tests cover file/console/no tracing,
  selection of the correct frame and `repl` context, command history, nested
  expansion, detailed watch errors, old saved launch options, preserved stderr,
  duplicate submission prevention and late responses after resuming.
- The first sandbox-only unit run hit the existing macOS `sysctl` restriction in
  process tests; the runtime-enabled run passed. The first native run exposed a
  test assertion expecting unquoted member names; Factorio returns `"inner"`.
  Corrected that assertion and reran successfully.

Evidence: `build/logs/dap-repl-*`, `build/reports/dap-repl-native/`,
`build/reports/dap-repl-platform/`, and native session protocol traces under
`build/test-work/dap-protocol/session-767838598632461332/`.
Archive: `build/distributions/intellij-factorio-0.6.0-dev.zip`.

Live UI validation also passed in the isolated IntelliJ IDEA 2026.2.3 profile.
Enter evaluated `nested`; expanding its table and `inner` child displayed `value`
as 41. `surface.get_tile(nil)` showed the full red diagnostic beside that REPL
entry while the game stayed at control.lua:13, without extra exception frames.
Keyboard history selected prior commands and Enter ran the recalled expression.
The final layout has a Lua REPL tab beside Console. Stop logged Goodbye, successful
disconnect and exit code 0; the isolated IDE was then closed. No new IDE ERROR
records were logged. Screenshots are in `build/reports/dap-repl-ui/`. The three
REPL platform tests were rerun successfully after the tab placement refinement.
The regular IDE profile and user mod sources were not changed.

## Runtime REPL completion, 0.6.1-dev (2026-10-09)

The 0.6.0-dev REPL/evaluation work was committed first as signed `43f3748`.
This feature builds on that clean baseline; no dependency pins or upstream
repositories changed.

The Lua REPL uses IntelliJ's completion popup on a private plain-text PSI document.
Invoke Basic Completion (Ctrl+Space in the tested macOS keymap). Suggestions read
DAP scopes and variables from the selected paused frame, without `evaluate`
requests. Locals shadow upvalues/globals; later local slots win. Mod storage is
not flattened into the lexical scope. Simple dot/colon members and literal
string/integer index chains are supported, including Factorio's `[1] nauvis`
labels for LuaCustomTable entries. Quoted keys receive closing delimiters, and
completion in the middle of a name replaces its remaining suffix.

Caches are scoped to one paused frame and discarded on REPL evaluation, frame
change, resume/step/stop or disposal. Late replies cannot refill a stale popup.
Requests have a three-second deadline; waiting is cancellable. Failed requests
can be retried. Enter accepts a popup item before a subsequent Enter evaluates it.
The existing command history and expandable result tree remain in place.

Limits: up to 1,000 entries per scope/table and 16 path components; no computed
indices, call results, metatable lookup, virtual debugger nodes or lazy values.
Factorio 2.1.21 does not advertise DAP completion. Its variable responses expose
API attributes but omit most API methods; those methods and annotation-only/nil
fields are not suggested. Adding API/LSP completion remains separate work.

Validation:

- 55 unit/native tests passed, none skipped. New cases cover safe path parsing,
  escaping, shadowing, storage isolation, cached reads, stale replies, size limits,
  failed-request retry and lazy/virtual exclusion. Native acceptance launched two
  real Factorio sessions and completed local names, nested/quoted members, API
  attributes and `game.surfaces[1].na`; evaluation error, stepping and shutdown
  checks continued to pass. Initial native probes exposed omitted API methods
  and labeled numeric keys; expectations/handling were corrected and rerun.
- Four REPL platform tests passed. The completion test exercises the actual
  IntelliJ popup and insertion, including middle-of-name and quoted-key edits,
  while asserting that completion sends no evaluation requests. A follow-up
  focused test passed after changing it to edit the initialized input document,
  matching normal typing. The final four-test platform run and build also passed
  after removing temporary diagnostics (`repl-completion-final-build.log`).
- Live isolated IDEA 2026.2.3 / Factorio 2.1.21 check passed: `nes` became `nested`
  on the first Enter with no result added; the next Enter evaluated the table.
  `nested.in` became `nested.inner`; `nested['in` became `nested['inner']`.
  After evaluating `nested.added = 42`, fresh completion found `nested.added`.
  Stop reported Goodbye and exit 0. The app capture did not show the floating
  lookup window, but actual keyboard selection and resulting text were verified.
  Temporary diagnostic logging confirmed the returned candidates and was removed.
  No new IDE ERROR records appeared. The isolated IDE was closed afterward.

Evidence: `build/logs/repl-completion-*`, `build/reports/repl-completion-native/`,
`build/reports/repl-completion-platform/`, `build/reports/repl-completion-ui/`,
and native `build/test-work/dap-protocol/session-4033524401562006198/`.
Archive: `build/distributions/intellij-factorio-0.6.1-dev.zip`.

Final ZIP SHA-256: `7d9f743c192eb5e488f907823c2010560c55879973e183691213c48144de0b16`. Archive metadata reports `0.6.1-dev`;
the packaged completion classes contain no temporary diagnostic logging.

## Optional debugger address hiding, 0.6.2-dev (2026-10-09)

Added the project setting **Factorio Development Environment → Debugger → Hide
memory addresses in debugger values**, default off. It is staged until Apply and
persisted in project workspace settings. Changes affect newly rendered values,
including after stepping or re-evaluating; existing tree nodes are not forcibly
refreshed by the Settings dialog.

`FactorioDebugVariables` uses LSP4IJ's public variable-presentation hook for
Variables, watches, debug hovers and the Lua REPL. The same formatter supplies
REPL completion previews. Native reference suffixes such as `[table *0x1234]`,
`[C function *0x1234]` and `[Lua helper.lua:5 *0x1234]` are shortened while retaining
the type and function location. Native virtual `[address]` rows are omitted from
expanded tables. An FDE wrapper filters `variables` responses after protocol
logging, returning a new response without mutating the original. This applies to
all debugger trees and leaves other requests delegated to the original server.
Quoted string keys/values and values typed as strings are preserved, as are
ordinary hex numbers, variable names and unfamiliar formats. The underlying DAP
objects, expansion references, expressions and raw trace messages are unchanged.
No dependency pins or upstream sources changed.

Validation:

- `test buildPlugin` passed: 59 tests passed, one optional native Factorio test
  skipped. New tests cover native table/function previews, escaped quotes,
  literal strings, truncated/unrecognized values, virtual address identification,
  toggling and unchanged protocol data. Response-filter tests also cover empty
  tables, ordinary/virtual string fields named `[address]`, preserved child
  references, unchanged request arguments, other requests and error propagation.
- Eight IntelliJ platform tests passed: five REPL tests and three UI tests.
  The new enabled-setting case verifies actual REPL/completion presentation,
  nested expansion with address rows absent at both levels and original address
  rows remaining in the raw JSONL log. Existing cases verify address rows remain
  visible when hiding is disabled. The settings test verifies Apply/Reset
  behavior and XML persistence. The rendered settings page was inspected.
- The archive reports version `0.6.2-dev` and includes the formatter class.
  No agent-run live game/UI session was run for this presentation-only feature.
  The user confirmed the refined behavior in their IDE on 2026-10-10.

Evidence: `build/logs/debug-values-unit.log`,
`build/logs/debug-values-platform.log`, `build/reports/debug-values-unit/`,
`build/reports/debug-values-platform/`, and `build/reports/ui/settings.png`.
The final row-suppression refinement is verified in
`build/logs/debug-address-rows-unit.log`,
`build/logs/debug-address-rows-platform.log`, and
`build/reports/debug-address-rows-{unit,platform}/`.
Archive: `build/distributions/intellij-factorio-0.6.2-dev.zip`.
SHA-256: `86af354fcc0dbd086cc35ace3a85bac8fffa08b7d15b20c6ca492cbd4112ae2f`.

## Analyzer 0.30.0 integration, 0.7.0-dev (2026-10-12)

FDE now pins analyzer `78c3ea62b1b3ace4e011310dd66fa1179ffc45fb`, packaged in
EmmyLua2 `0.30.0-133-IDEA262-patched-modules`. The internal analyzer branch was
rebased onto upstream main `1c8589bb` and the squashed module-resolution PR
commit `ef3cf3c8`, which has been pushed to the user's fork. Independent patch
branches and backups remain available; internal compatibility fixes are not
part of PR #1266.

The upstream semantic-engine rewrite required porting the existing diagnostic,
navigation, metatable, asymmetric-field and dictionary-index patches. Integration
tests also identified lost inline API members/overloads, event-type union matching,
imported factory signatures and return-type hover rendering. Internal fixes retain
Factorio event inference, `This, Framework = require('lib.init')()` typing and
navigation, and API calls with aliased parameters such as `uint32|string`.

This release updates dependency pins and package versions; no FDE Java behavior
changed. LSP4IJ and the toolkit remain on their existing pins. Install both:

- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-133-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.0-dev.zip`

The rebuilt patched analyzer is macOS arm64 only. Other binaries in the EmmyLua2
archive remain upstream 0.25.1. The regular IDE and original mods were not changed.

Validation:

- All 2,455 analyzer workspace tests passed with all features, including the
  upstream slow test. Workspace Clippy (all targets/features, warnings denied)
  and rustfmt passed. All 21 internal commits above upstream main have verified
  signatures.
- All 22 EmmyLua2 plugin tests passed. FDE's 59 unit tests passed; one optional
  native game test was skipped.
- All nine IntelliJ platform tests passed with real language-server processes.
  They cover formatting/routing, EDT lifecycle, independent Lua/locale services,
  unsaved dependencies, native module config/VFS reload, generated API and built-in
  mod imports, asymmetric attributes, copied real-mod navigation/storage isolation,
  and the Factorio Lua language level.
- Real LSP probes passed runtime/prototype/event completion, API hover, module-local
  and cross-mod imports, dependency edits, locale close/reopen and file watchers,
  changelog symbols, and cross-file diagnostic refresh. All 17 asymmetric
  attributes in the installed Factorio 2.1.21 API passed read/write checks.
- Packaging verified versions, analyzer source pin and binary hash, and Unix
  executable permissions after ZIP extraction. No interactive IDE/game test or
  Plugin Verifier run was performed for this upgrade.

Evidence: `build/logs/analyzer-030-{plugin-build,platform-final,language-services-verified,module-services-verified,asymmetric-api-verified}.log`,
`build/reports/analyzer-030-{unit-tests,platform-final}/`, EmmyLua2's
`build/logs/analyzer-030-{build133,plugin-tests}.log`, and the analyzer's
`build/logs/internal-rebase-{workspace-final,clippy-final,refresh-protocol-final}.log`.
Earlier platform runs recorded the regressions fixed above; the final run passed.

ZIP SHA-256:

- EmmyLua2: `fa0f8a8fcdb8fbdce1ab7a96b6a4652ca7e7e71e6e23670c88918bd1625eb6e0`.
- FDE: `437603e5f88914e1788ac5b55513c59b557490bcb85ac5fa355909dd77d39c5d`.

## Merged upstream module resolution, 0.7.1-dev (2026-10-12)

FDE pins analyzer `f6e67847612be9ae8822e7fad0e865849a78510c`, packaged in EmmyLua2
`0.30.0-134-IDEA262-patched-modules`. The internal branch is rebased onto
upstream main `dbb68f47`, which includes our merged PR #1266 and the newer
callable-table and tuple/dictionary compatibility fixes. The remaining local
patches are preserved. The only rebase conflict combined upstream member
initializers with our standalone-call metatable indexing; no additional
compatibility code was needed.

This release updates the analyzer pin and package versions. LSP4IJ and toolkit
pins are unchanged. The patched analyzer is rebuilt for macOS arm64; other
bundled platforms remain upstream 0.25.1. Install both new archives:

- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-134-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.1-dev.zip`

Rust validation passed all 2,471 workspace tests with all features, rustfmt and
workspace Clippy with warnings denied. All 22 EmmyLua2 plugin tests passed.

FDE validation passed 59 unit tests (one optional native-game test skipped) and
all nine IntelliJ platform tests. These cover per-module routing/formatting,
EDT registry lifecycle, independent Lua/locale services and removal, unsaved
dependency edits, native module configuration/VFS reload, generated API/built-in
imports, asymmetric fields, copied real-mod navigation/storage and Lua version.
Real protocol tests also passed runtime/prototype/event completion, API hover,
local/cross-mod imports, locale file lifecycle/changelog symbols, cross-file
diagnostic refresh and all 17 asymmetric Factorio 2.1.21 attributes.

Packaging verified descriptor versions, analyzer source pin and binary hash,
and Unix executable permissions after extraction. No interactive IDE/game test
or Plugin Verifier run was performed. The regular IDE and original mods were
not modified.

Evidence: `build/logs/merged-module-{plugin-build,platform,language-services,module-services,asymmetric-api}.log`,
`build/reports/merged-module-{unit,platform}/`, EmmyLua2's
`build/logs/merged-module-{build134,plugin-tests}.log`, and analyzer
`build/logs/merged-module-{workspace,clippy,refresh-protocol}.log`.

ZIP SHA-256:

- EmmyLua2: `b5ca4464946776de98ddaad05e24ce8292e1aed0a123ed05c3ec0ebe4488a3af`.
- FDE: `4d8bd0346c472982dfbc7959c926135d0bed010c5eed638a92dbda6fa6d23b3c`.

## Diagnostic-refresh review integration, 0.7.2-dev (2026-10-12)

FDE pins analyzer `f5349db8ee7c970d654720fcb8b64b0736bc788b`, packaged in EmmyLua2
`0.30.0-135-IDEA262-patched-modules`. Signed merge `49a9ed3f` integrates the
reviewed PR #1283 follow-up `1c47a240` into `work/intellij-modules`; its change is
exactly the reviewed ten-file patch. The upstream baseline and other internal
patches are unchanged. The contribution branch remains separate.

The follow-up stops pending diagnostic refreshes before shutdown acknowledgement
and on context close, prevents queued updates from scheduling after shutdown,
and uses weak client references in timer tasks. Tracked removals on close,
rename and configuration reload also use the capability-gated scheduler.

This release updates dependency pins and package metadata; FDE Java behavior,
LSP4IJ and toolkit pins are unchanged. Install both new archives:

- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-135-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.2-dev.zip`

The rebuilt patched analyzer is macOS arm64 only; other bundled platforms remain
upstream 0.25.1. The regular IDE and original mods were not changed.

All 2,473 analyzer workspace tests with all features, rustfmt and workspace
Clippy (warnings denied) passed. All 22 EmmyLua2 tests passed on retry. The first
run encountered an existing LSP4IJ `LanguageServerWrapper.isActive()` null
`launcherFuture` startup race; the failing report is retained in EmmyLua2's
`build/reports/refresh-review-initial/`.

FDE passed 59 unit tests (one optional native-game test skipped). Real protocol
checks passed runtime/prototype/event completion, API hover, local/cross-mod
imports, dependency edits, locale file lifecycle/changelog symbols, and all 17
asymmetric Factorio 2.1.21 attributes. The expanded diagnostic-refresh regression
passed against the packaged binary for edits, close/deletion, watcher events,
rename, configuration reload, capability gating and shutdown. Its bounded
cleanup does not claim to fix general stdio-worker teardown behavior.

All nine IntelliJ platform tests passed with real language-server processes:
module routing/formatting, EDT lifecycle, Lua/locale isolation/removal, unsaved
dependency edits, native module config/VFS reload, generated API/built-in imports,
asymmetric attributes, copied real-mod navigation/storage and Lua language level.
Packaging verified versions, source pins, binary hash and executable permissions.
No interactive IDE/game test or Plugin Verifier run was performed.

Evidence: `build/logs/refresh-review-{plugin-build,platform,language-services,module-services,asymmetric-api}.log`,
`build/reports/refresh-review-{unit,platform}/`, and
`build/logs/refresh-review-packages.json`. Analyzer evidence is in its active
worktree's `build/logs/refresh-review-*`; EmmyLua2 evidence is in its worktree's
`build/logs/refresh-review-{build135,plugin-tests-retry}.log`.

ZIP SHA-256:

- EmmyLua2: `927a0bae786ddfa0a23aca03c27df1ba7aaec4c455ab7cdd0203fa8bc31e6795`.
- FDE: `fda5b401cb98105ba131673a996fc692e9eb9489d20073a96c2d31397e3a9bf7`.

## Named cast expression scope, 0.7.3-dev (2026-10-12)

FDE pins analyzer `8548f5d19da5c965f95886d6103b0cba6dbe5bd1`, packaged in
EmmyLua2 `0.30.0-136-IDEA262-patched-modules`. This includes the named-cast fix
from [draft PR #1285](https://github.com/EmmyLuaLs/emmylua-analyzer-rust/pull/1285),
merged into the analyzer work branch as `51288958`. The cast on an array key no
longer replaces the element type. The standalone patch passed 2,389 Rust workspace
tests and the combined work branch passed 2,475, plus Clippy and rustfmt, during
the preceding implementation task.

The rebuild passed 22 EmmyLua2 tests and 59 FDE unit tests (one optional native-game
test skipped). Eight selected platform tests passed initially; the asymmetric
API test failed only during teardown on an IntelliJ DiscoveryService thread
leak in macOS filesystem discovery. Its isolated retry passed without code or
configuration changes. Both reports are preserved. These nine tests cover real
Lua/FMTK processes, routing, formatting, dependency edits, generated API and
asymmetric fields, copied real-mod navigation/storage and Lua language level.

The packaged analyzer passes the original cast/index/assert LSP reproduction.
Archive descriptors, source pins, binary hashes and Unix executable permissions
were verified. FDE Java code, LSP4IJ and toolkit pins are unchanged. Custom
analyzer patches remain macOS arm64 only. No interactive IDE/game testing,
Plugin Verifier run or everyday IDE installation was performed.

Artifacts:

- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-136-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.3-dev.zip`

Evidence: `build/logs/named-cast-*`, `build/reports/named-cast-unit/`,
`build/reports/named-cast-platform-initial/`, `build/reports/named-cast-platform-retry/`,
and analyzer `build/investigations/cast-index/packaged136/`.

ZIP SHA-256:

- EmmyLua2: `46f8b212a1fe01c92fdd025a35f1ff47bfa3e36e44fd8a413024c048baebdb55`.
- FDE: `49941eb121ef0bddcd3d0a1310b77947d3a4292f854e21aebbaec88cd4720a90`.

## Named cast review follow-up, 0.7.4-dev (2026-10-12)

FDE pins analyzer `4b1a0c2cef5f358bef03ba356038286cded26f64`, packaged in
EmmyLua2 `0.30.0-137-IDEA262-patched-modules`. Draft PR #1285 now includes signed
follow-up `3b0d6831`, merged internally as `697f751d`. The review adds typed index
operands, debug stack checks and regression coverage for owner/statement casts,
nil/never keys, nested computed keys and recursive dependencies. The original
cast/index/assert behavior is preserved.

Validation passed all 2,392 standalone and 2,478 combined Rust workspace tests,
Clippy with warnings denied, and rustfmt; 22 EmmyLua2 tests and 59 FDE unit tests
passed (one optional native-game test skipped). The packaged LSP reproduction
returns `ltn.FreeTrain?` for the raw lookup and `ltn.FreeTrain` after `assert`.
All nine platform tests passed on the first run, exercising real Lua/FMTK
processes, module routing/formatting, lifecycle/EDT handling, dependency edits,
module configuration reload, generated API and asymmetric fields, copied real-mod
navigation/storage and Lua language level. Evidence is in
`build/logs/cast-review-platform.log`.

Archive versions, source pins, binary hashes and executable permissions were
verified. No product Java/Kotlin implementation, LSP4IJ or toolkit pin changed.
Custom analyzer patches remain macOS arm64 only. No interactive IDE/game test,
Plugin Verifier run or ordinary IDE installation was performed.

Artifacts:

- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-137-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.4-dev.zip`

Evidence: `build/logs/cast-review-*`, `build/reports/cast-review-{unit,platform}/`,
EmmyLua2 `build/reports/cast-review-plugin-tests/`, analyzer checkout
`build/logs/cast-review-*` and `build/investigations/cast-index/packaged137/`.

ZIP SHA-256:

- EmmyLua2: `2366a953236e1fc28fc0671ce52dd11f25a23fab282b011182ac407057900ea8`.
- FDE: `21fc46d8c5937cc680d76f4e5a1ca7edf857aeeabb7ec7241454f1828c8fb540`.


## Flow-aware union member arguments, 0.7.5-dev (2026-10-12)

FDE pins analyzer `f653fa59ba576db2c2330b41e14b2702ddde62b9`, packaged in
EmmyLua2 `0.30.0-138-IDEA262-patched-modules`. [PR #1286](https://github.com/EmmyLuaLs/emmylua-analyzer-rust/pull/1286)
uses the owner's flow-sensitive type in missing-union-member argument checks,
so casts, guards and assignments remove false nil diagnostics while remaining
missing members and incompatible field types still diagnose. Original `430fe1de`
and review follow-up `272d3096` were merged internally as `b62f2f41` and `3862d97d`.

Combined validation also exposed an interaction with PR #1285 for ambiguous
union members without a unique ID. Its follow-up `4dbecfe5`, merged as `525a6f0f`,
restores an immediate named cast only on its exact static path and resolved root
declaration. It preserves owner/path isolation, projected field types, scope and
reassignment behavior. General ambiguous-member cast persistence after an
intervening statement was already absent and remains outside this correction.
Both upstream contributions remain independent and are ready for review.

All 2,492 combined analyzer workspace tests with all features, workspace/all-target
Clippy with warnings denied and rustfmt passed. The PR #1285 compatibility change
also passed all 2,395 standalone workspace tests. All 22 EmmyLua2 tests and 59 FDE
unit tests passed (one optional native-game test skipped). All nine selected
platform tests passed on the first run, using real Lua/FMTK processes and copied
mod fixtures. They cover routing/formatting, lifecycle/EDT handling, dependency
edits, module configuration reload, generated API and asymmetric fields, copied
real-mod navigation/storage, and Lua language level.

The packaged server was tested with copies of all 37 LogisticTrainNetwork Lua
files. The reported request_processor.lua lines 365/366 have no target parameter
diagnostics. Comparing all diagnostics for that file with build 137 gives 69 to
58: eleven synthetic nil mismatches removed, no added diagnostics. The prior
cast/index probe still returns `ltn.FreeTrain?` for the raw lookup and
`ltn.FreeTrain` after `assert`.

Archive IDs/versions, analyzer pins, binary hashes and Unix executable modes
were verified. Release, prepared and ZIP arm64 server bytes are identical.
FDE Java code, LSP4IJ and toolkit pins are unchanged. Custom analyzer patches
remain macOS arm64 only; other bundled servers are upstream 0.25.1. No ordinary
IDE installation, interactive IDE/game test or Plugin Verifier run was performed.
The original mod files were not changed.

Artifacts:

- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-138-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.5-dev.zip`

Evidence: `build/logs/union-argument-*`,
`build/reports/union-argument-{unit,platform}/`, EmmyLua2's
`build/reports/union-argument-plugin-tests/`, analyzer worktree
`build/logs/union-argument-*` and main analyzer repository
`build/investigations/union-member-argument/real-mod-packaged138/` and
`build/investigations/cast-index/packaged138/`. The real-mod diagnostic comparison
is `real-mod-packaged138/diagnostic-diff.json`; the package audit is
`build/logs/union-argument-packages.json`.

ZIP SHA-256:

- EmmyLua2: `b53caf8025cdae10f84067fccffc83172fbffb1de751c146c0d07b2849cb35ae`.
- FDE: `17e3743cd09d2fdd0839adddf9088f474647983b76aff3530a1fc730f82b117b`.

Arm64 analyzer SHA-256: `21e0c03fca8997fa2a195e16599296283d27a02374e2d5aba8ccc5750269c6dc`.

## Table rendering and inline assertions, 0.7.6-dev (2026-10-13)

The analyzer pin is `8b32942e911f4ed49351f8383b41d09aa140d557` on
`work/intellij-modules`. Two independent upstream fixes address the
LogisticTrainNetwork table annotation reports:

- [PR #1289](https://github.com/EmmyLuaLs/emmylua-analyzer-rust/pull/1289)
  preserves explicit generic arguments in rendering. Built-in `table` declarations
  have no declared generic parameters; the renderer previously dropped their
  supplied key/value types and displayed `table<>`.
- [PR #1290](https://github.com/EmmyLuaLs/emmylua-analyzer-rust/pull/1290)
  confines inline `@as` assertions to their attached expression. A constructor's
  `@as ltn.FreeTrain` no longer casts its individual fields or unrelated later reads.

Both branches start at upstream main `cb05b0c0`, were submitted as drafts, and
received review follow-ups before integration. Signed backports `71c30d1c`,
`6c341ed2`, `f5ee2ad1`, and `18ea33d9` preserve our existing Arc baseline without
importing unrelated upstream changes. Work-only commit `581ba186` applies the
same expression-local rule to our directional-field read walker and adds a
compatibility regression. Both PRs have 15 successful CI checks and the expected
skipped release job; no additional feedback appeared at the final check.

All 2,510 combined Rust workspace tests passed with all features, plus
workspace/all-target Clippy with warnings denied and rustfmt. All 22 EmmyLua2
tests, 59 FDE unit tests (one optional native-game skip), and nine selected
IntelliJ platform tests passed. No platform retry was needed. Platform coverage
includes real Lua/FMTK processes, per-module routing/formatting, lifecycle/EDT,
dependency edits, configuration reload, generated API/asymmetric fields, copied
real-mod navigation/storage and Lua language level.

The packaged server was tested with copies of all 37 LogisticTrainNetwork Lua
files. Hover now displays `table<integer,ltn.FreeTrain>` and the constructor
fields retain LuaTrain, LuaSurface and integer types. File diagnostics decreased
from 57 to 53: four false constructor-field warnings removed, with no new warning
locations. An existing LocalisedString/boolean warning at line 889 now names the
literal string instead of the enclosing assertion type. The earlier line 365/366
warnings remain absent, and every cast/index probe case matches packaged build
138, including FreeTrain? lookup and FreeTrain after assert. The standalone
minimal constructor probe also passes with and without the inline assertion.

Archive IDs/versions, analyzer pins, hashes and Unix 0755 executable modes were
verified. The release, prepared and ZIP arm64 server bytes match. Only macOS arm64
carries the custom analyzer patches; other bundled servers remain upstream
0.25.1. LSP4IJ/toolkit pins are unchanged. No ordinary IDE installation,
interactive IDE/game test or Plugin Verifier run was performed. Original mods
were not changed. Internal branches remain local; only PR branches were pushed.

Evidence: `build/logs/table-annotations-*`, including `packages.json` and
`packaged-probes.json` with that prefix; `build/reports/table-annotations-{unit,platform}/`;
EmmyLua2 `build/reports/table-annotations-plugin-tests/`; analyzer work
`build/logs/table-annotations-*`; main analyzer
`build/investigations/table-annotations/{packaged139,real-mod-packaged139}/`
(including `diagnostic-diff.json`), `build/investigations/cast-index/packaged139/`
and `build/investigations/union-member-argument/real-mod-packaged139/`.

Artifacts:

- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-139-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.6-dev.zip`

ZIP SHA-256:

- EmmyLua2: `d1021bdd5068aab43bd5349cf8138b8c1a7f1e6e115dbd99787ffa14aadd13bf`.
- FDE: `cebe773a4f01dee4faba385a085e587972bdb3a3d79fcaa9df72016805d0d63c`.

Arm64 analyzer SHA-256: `d9a1a4a2f0c118bd6e3e50ac21d24e20aa1f3f0568c570f1e54d1fd132fed867`.

## Dictionary constructors and enum tables, 0.7.7-dev (2026-10-13)

Analyzer pin `5a064469ebcbfdd7faca3bfcbd24ee4ba716ebb8` retains the previous
work branch and adds two independent diagnostic fixes based on upstream main
`cb05b0c0`. Both failures originate in the upstream semantic rewrite, not our
local patches. Neither branch was pushed and no PR was created, at the user's
request; future PR descriptions are saved in each checkout's ignored
`build/PR_DESCRIPTION.md`.

- `fix/dictionary-constructor-fields`, signed `cdf32d10`: the field checker
  treated `table<K,V>` as the standard library table class's closed record
  surface. A narrow guard matches the existing assignment checker's treatment
  of dictionaries. Class missing/extra field checks remain enabled. This does
  not add dictionary key/value validation.
- `fix/cross-file-enum-members`, signed `7727a58f`: the checker looked for an enum
  runtime declaration in the current file and checked its enum type rather than
  its constructor's fields. It now uses the owning file and runtime table surface.
  Enum-typed values and missing fields remain errors, including in conditions.

Signed backports `5c39ca41` and `14ac6b9e` applied cleanly to the existing work
branch; no compatibility glue or unrelated upstream changes were needed.
Standalone checker/type-check suites passed 639 and 638 tests, respectively,
with Clippy and formatting checks. The new positive regressions fail before
fixing. All 2,515 combined workspace tests with all features, workspace/all-target
Clippy with warnings denied, and rustfmt passed.

All 22 EmmyLua2 tests, 59 FDE unit tests (one optional native-game test skipped),
and nine selected IntelliJ platform tests passed on the first run. Platform tests
exercise real Lua/FMTK processes, routing/formatting, lifecycle/EDT, dependency
and configuration updates, generated APIs/asymmetric fields, copied real-mod
navigation/storage and Lua language level.

The packaged server was tested against copies of all 37 LogisticTrainNetwork
Lua files. Comparing build 139 with 140 gives constants.lua 17→0 diagnostics,
request_processor.lua 53→46, and cache.lua 21→8. No diagnostics were added. All
reported dictionary constructor and DISTANCE_RESULT member warnings disappeared.
Original mod files and the ordinary IDE profile were not changed.

Archive IDs/versions, pins, hashes and Unix 0755 executable permissions were
verified. Release, prepared and archived arm64 server binaries are identical.
Custom analyzer changes remain macOS arm64 only; other bundled servers remain
upstream 0.25.1. LSP4IJ/toolkit pins are unchanged. No interactive IDE/game test
or Plugin Verifier run was performed. All branches remain local for this task.

Evidence: plugin repositories `build/logs/constructor-enum-*`,
`build/reports/constructor-enum-{unit,platform,plugin-tests}/` as applicable;
analyzer work `build/logs/constructor-enum-*`; standalone checkout `build/logs/`;
main analyzer `build/investigations/constructor-enum/{packaged139,packaged140}/`
(including `packaged140/diagnostic-diff.json`). Archive audit is FDE
`build/logs/constructor-enum-packages.json`.

Artifacts:

- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-140-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.7-dev.zip`

ZIP SHA-256:

- EmmyLua2: `8bd8744807452933a16b190b788aba9e49fca2dccd3aca5be4f338c0d16c7257`.
- FDE: `4929d27ccc8d9a76d04806a1edfb677405128af3cb7fb67fce7fc240f1f382dc`.

Arm64 analyzer SHA-256: `7256ebb3e2de790a227cbd4af0ea89197ffd76e6214ef92d81b9b6618f249f7e`.

## Computed-index assignment flow, 0.7.8-dev (2026-10-13)

The analyzer pin is `c29ce116c7eebeb46a54b0f1b42d862b8af3271c` on
`work/intellij-modules`. This release narrows reads such as `groups[key]` after
`groups[key] = groups[key] or {...}`. It preserves the declared element type while
removing nil when the same local table/key definitely received a nonnil value.
Key/table reassignment, alias-capable writes, calls, control-flow joins, loop and
closure boundaries conservatively stop the new trace. Nominal classes retain
their existing read/write semantics.

The independent local branch is `fix/computed-index-assignment-flow`, signed
`9fdb3dde`, on current upstream main `cb05b0c0`; signed work backport `3ee94cbc`.
Its reproduction uses upstream's strict array indexing and does not depend on
the pending strict dictionary-index feature. Work-only integration tests verify
all combinations of strict.tableIndex and strict.arrayIndex, precise element
type and retained unsafe-lookup warnings. No new branch or PR was pushed.

This build also includes the reviewed follow-ups to dictionary constructors
(PR1291, work `e538efd3`) and cross-file enum tables (PR1292, work `6ad16119`).
Existing module, metatable, read/write, optional-key, cast and type-display fixes
remain. Toolkit and LSP4IJ dependencies are unchanged.

Validation completed:
- Standalone upstream workspace: 2,407 tests, all features; workspace/all-target
  Clippy with warnings denied and rustfmt pass.
- Combined work workspace: 2,526 tests, all features; the same Clippy/fmt checks.
- EmmyLua2: 22 tests; FDE: 59 unit tests passed, one optional native-game skip.
- Packaged server protocol replay uses identical copies of all 37 Lua files from
  LogisticTrainNetwork. Only the reported line432 NeedCheckNil disappears:
  request_processor.lua 46→45, constants.lua 0→0, cache.lua 8→8. No diagnostics
  added in the three checked files. Original mod sources were not modified.
- Archive audit verifies plugin IDs/versions, source pin, matching release/
  prepared/ZIP server bytes and Unix0755 executable modes.

IntelliJ platform verification: all 9 selected tests passed (8 PerModServersTest
and 1 FactorioLuaLanguageLevelTest).
No interactive IDE/game test or Plugin Verifier run. The custom analyzer remains
macOS arm64 only; other bundled server binaries remain upstream0.25.1.

Evidence:
- Analyzer `build/checkouts/computed-index-flow/build/logs/{workspace-tests,clippy,fmt}.log`.
- Work analyzer `build/logs/computed-index-{workspace-tests,clippy,fmt,integration}.log`.
- Main analyzer `build/investigations/computed-index-flow/{packaged140,packaged141}/`,
  including `packaged141/diagnostic-diff.json`.
- Plugin worktree `build/logs/computed-index-*` and saved
  `build/reports/computed-index-*` XML/HTML reports.
- FDE `build/logs/computed-index-packages.json` archive audit.

Install both archives from their usual directories, then restart IntelliJ:
- `upstream/Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-141-IDEA262-patched-modules.zip`
- `intellij-factorio/build/distributions/intellij-factorio-0.7.8-dev.zip`
