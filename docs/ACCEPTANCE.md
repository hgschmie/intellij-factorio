# Development acceptance — 2026-09-24

This is a development build, not completion of all planned IDE acceptance.

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
  The evaluation editor returned `42` for `count + 1`.
- Step Into entered helper.lua:6; Step Over reached line 7 with `sum = 42`;
  Step Out returned to control.lua:14 with `result = 42`. Another Step Into
  entered the copied LTN metrics.lua:66 with `key = "probe"`.
- Stop ended the process. A second launch hit the persisted breakpoint again.
  Both UI stops reported exit 137 (SIGKILL), unlike the protocol harness's
  graceful disconnect. Do not describe the UI shutdown as graceful.
- Build Mod ZIP completed and produced the probe archive. The publishing
  preview displayed the mod/version, destination and release effects; Cancel
  exited without supplying credentials or contacting the portal.
- Go to Declaration on the Lua locale key navigated to probe.cfg:2.
- Reopening the project started one EmmyLua server and one FMTK server, using
  the persisted project settings. The previous servers exited with their IDE.

A temporary diagnostic build confirmed runtime completion candidates reach
IntelliJ, and accepting `game.get_sur` inserted `game.get_surface`. The popup
was not captured by automation. This check must be repeated on the final build,
which removes that diagnostic instrumentation. The cache-reset contributor now
explicitly precedes `LSPCompletionContributor`; this ordering change alone has
not been established as the cause or solution of the popup-capture issue.

## Remaining acceptance and limitations

UI access recovered after unlocking, then again returned `cgWindowNotFound`.
Remaining checks include final-build completion insertion and prototype/import
assistance, locale/changelog diagnostics and file-update behavior through the
IDE, nested variable expansion and API evaluation, an LTN breakpoint (stepping
into LTN passed), another relaunch cycle, and configuration/version/script UI.
Earlier spike and standalone protocol successes do not replace these checks.

LSP4IJ 0.21.0's console filter leaks fragments of native DAP messages into the
debug console, including loadedSource events and variable responses. The
observed breakpoint, evaluation and stepping workflows still succeeded; the
console presentation needs improvement before calling the debugger polished.

EmmyLua 0.24 dispatches didOpen asynchronously while didChange is synchronous.
Sending an edit immediately after opening a dependency can let the open handler
overwrite the new buffer contents. The original rapid-open/edit probe failed;
waiting one second for opening to settle passed. A diagnostic-barrier variant
was also attempted, but library diagnostics are not reliably emitted. No upstream Rust server patch is bundled.

The plain-document evaluation workaround passed the UI check. Same-caret locale
completion cache invalidation remains unverified through the UI.

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
