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
