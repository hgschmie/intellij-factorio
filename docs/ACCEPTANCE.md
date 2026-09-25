# Development acceptance — 2026-09-24

The core workflows pass in the isolated IDEA 262 environment. This remains a
development build with the limitations and untested edge cases listed below.

## Verified

- Java package, Gradle group and plugin ID: `de.softwareforge.factorio`.
- Java compilation, plugin ZIP construction, and eight unit tests pass.
- Unit tests cover preserving user EmmyLua settings, idempotent generation,
  ZIP traversal rejection, versioned dependency extraction, credential redaction, per-directory operation
  locking, failed process reporting and owned-child cancellation.
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
  Stop ended each process without an orphan. UI stops reported exit 137
  (SIGKILL), unlike the protocol harness's
  graceful disconnect. Do not describe the UI shutdown as graceful.
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
checks above. Discarding unsaved locale edits across close/reopen is covered by
the standalone protocol tests, not an accepted final-build UI discard workflow.
Service disable/re-enable through the settings checkbox was attempted, but the
automation did not reliably toggle it; that UI transition remains unverified.
Real publishing and credential storage against a live portal are intentionally
outside acceptance; publishing execution uses the loopback harness.

LSP4IJ 0.21.0's console filter leaks fragments of native DAP messages into the
debug console, including loadedSource events and variable responses. The
observed breakpoint, evaluation and stepping workflows still succeeded; the
console presentation needs improvement before calling the debugger polished.

EmmyLua 0.24 dispatches didOpen asynchronously while didChange is synchronous.
Sending an edit immediately after opening a dependency can let the open handler
overwrite the new buffer contents. The original rapid-open/edit probe failed;
waiting one second for opening to settle passed. A diagnostic-barrier variant
was also attempted, but library diagnostics are not reliably emitted. No upstream Rust server patch is bundled.

The IDE logged a recovered VFS `FileDeletedException` during the external
deletion test; the subsequent navigation result correctly reflected deletion.

## Evidence locations

Under `../spike/`:

- `plugin-final-build.log`, `plugin-verification.log`, `plugin-toolkit-tests.log`
- `plugin-final-publishing.log`, `plugin-publishing-tests/run-*/results.json`
- `plugin-protocol/evidence/` and its named run logs (retain failed runs)
- `plugin-ide/log/idea.log`
- `plugin-ui-evidence/` (individual accessibility captures, including failed
  completion attempts; only the checks described above are accepted)
- `plugin-ui-final-build.log`

Gradle test and verifier reports are under `build/reports/`.

After acceptance, the isolated IDE and both language servers were stopped; no
Factorio process remained. Disposable probes and changed mod metadata were
preserved under `plugin-ui-evidence/fixture-final/`, then the copied mod's
metadata and LTN source were restored. The everyday IDE profile was not changed.
