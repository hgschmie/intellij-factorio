**Code review: Lua profiler — 2026-10-20**

**Current status: all seven findings independently verified resolved; no new
findings in the shutdown guard.** The final pre-commit review covered the
working-tree changes against `eff1b8f`. Independent verification is complete;
the user authorized committing the reviewed changes on 2026-10-20. The earlier commit review and its failing
reproduction are retained below as historical evidence.

Reviewed the working-tree changes against `HEAD` (`05e6a9d`), including the
untracked profiler implementation, tests, fixtures, scripts, and plugin
registration. The review focuses on the new code. The initial review recorded
four P2 findings and made no production changes. Independent follow-up review
added two further P2 findings.
**Initial profiler review status: findings 1–6 resolved.**
This report accompanies the completed FDE **0.9.0-dev** profiling checkpoint.
The review and validation entries preserve their status at the time of each run.
The validation below distinguishes the fix author's checks, independent
follow-up verification, and the original review's historical evidence.

**1. [P2] Resolved — Preserve the native top-level component required by snapshot comparison**

Location: [FactorioProfileImporter.java](../src/main/java/de/softwareforge/factorio/profiler/FactorioProfileImporter.java),
lines 90–91; [FactorioProfileModulePanel.java](../src/main/java/de/softwareforge/factorio/profiler/FactorioProfileModulePanel.java),
lines 101–111.

`doCreateTopLevelComponent()` returns a `FactorioProfileModulePanel`, with the
native `MainCallTreeDataComponent` nested inside it. In the pinned IDEA 262
implementation, `CompareSnapshotsActionGroup` recognizes a dump only when
`ProfilerDumpPanel.getTopLevelComponent()` is itself a
`MainCallTreeDataComponent`. Consequently, the native **Compare With Baseline…**
action cannot find the current Factorio snapshot and throws
`IllegalArgumentException: Couldn't find tab related to passed component`.
Factorio snapshots are also omitted when looking for comparison baselines.
This affects ordinary imports, before any module selection is applied.

Confirmed with a review probe that registers an actual `ProfilerDumpPanel` in
the Profiler tool window and invokes its comparison action. The wrapped view
throws; registering the same data with the native top-level component succeeds.
The existing viewer tests also emit this exception while reporting passing
JUnit results, so their green status does not establish correct action behavior.

Keep the native component as the top-level component and integrate the module
controls without breaking that contract, or provide an explicitly supported
comparison integration. Add a regression test through the real Profiler tab
that checks comparison actions before and after changing module filters.

Evidence: [comparison probe log](../build/logs/profiling-review-compare-console.log)
and [installed comparison API bytecode](../build/logs/profiling-review-compare-api.txt).

Resolution (2026-10-20): the module picker is now a toolbar inside the native
`MainCallTreeDataComponent`. Applying a filter replaces the registered native
`ProfilerDumpPanel` at its existing tab position, preserving its name, pinned
state and selected view. Comparison actions therefore see the current filtered
model, and disposal releases the previous panel. A registered-tab regression
queries baselines in both directions before and after filtering, invokes the
comparison action, and verifies disposal of the old component. The existing
native-view test now registers a real Profiler tab as well.

**2. [P2] Resolved — Do not automatically claim every `.cpuprofile` as a Factorio recording**

Location: [FactorioProfileImporter.java](../src/main/java/de/softwareforge/factorio/profiler/FactorioProfileImporter.java),
lines 15–25; [factorio-profiler.xml](../src/main/resources/META-INF/factorio-profiler.xml),
line 4.

The importer is registered in the global `ProfilerDumpParserProvider` extension
point and advertises `cpuprofile` as its required extension. IntelliJ's generic
snapshot importer selects extension-matching providers automatically. Passing
an explicit provider in FDE's own `open()` method does not prevent this global
selection, despite the comment at line 31.

A review probe supplied a JavaScript CPU profile containing `scriptId`, an
`app.js` URL, and no Factorio metadata. The installed provider finder selected
`factorio.lua`, and its parser returned `Success`. Thus opening an unrelated
Chrome/Node snapshot through the common Profiler can silently present it as
**Lua execution**, with Factorio's source handling and interpretation of the
root. If another provider advertises the same extension, routing also depends
on provider ordering.

Retain explicit Factorio imports, but avoid unconditional global extension
ownership. Use producer-aware identification where possible, or require an
explicit provider choice for this shared format. Cover both Factorio and
non-Factorio snapshots through the generic provider finder.

Evidence: `testGenericCpuProfileIsAutomaticallyClaimed` in the
[platform probe log](../build/logs/profiling-review-platform-console.log).

Resolution (2026-10-20): the provider returns no required extension. IntelliJ's
generic finder offers Factorio only as an explicit provider choice when it
cannot identify the producer; it never automatically selects FDE by extension.
FDE's Open action and completed captures continue to select the parser explicitly.
Regression coverage checks both the native fixture and an unrelated JavaScript
snapshot through the actual provider finder, plus explicit native parsing.

**3. [P2] Resolved — Restrict the Factorio profiler executor to Factorio configurations**

Location: [FactorioProfilerExecutor.java](../src/main/java/de/softwareforge/factorio/profiler/FactorioProfilerExecutor.java),
lines 9–14; [factorio-profiler.xml](../src/main/resources/META-INF/factorio-profiler.xml),
line 3.

The executor is added to the global Profile group, but nothing excludes it from
unrelated DAP configurations. The new `FactorioDebug.DescriptorFactory.canRun()`
restricts which executors a *Factorio* configuration accepts; it does not
restrict which configurations this executor can run. In the pinned LSP4IJ
dependency, the generic DAP configuration/factory accepts arbitrary executor
IDs.

Confirmed by creating a generic `DAPConfiguration` for `python3 -m
debugpy.adapter`: `canRun("FactorioLuaProfiler")` returns true, and
`ProgramRunner.getRunner()` returns `DAPDebugRunner`. This exposes a runnable
**Profile with Factorio Lua** choice for a non-Factorio adapter, although
recording is installed and started only by `FactorioDebug.Descriptor`.
Selecting it can launch a session without the promised recording.

Add an applicability/routing restriction that checks the selected configuration,
coordinating with the generic DAP runner if necessary. Extend the executor test
with a negative case for an unrelated DAP configuration; the current test only
checks the inverse restriction on a Factorio configuration.

Evidence: `testFactorioExecutorOfferedForGenericDap` in the
[platform probe log](../build/logs/profiling-review-platform-console.log).

Resolution (2026-10-20): UP-098 (`fix/dap-executor-routing`, local commit
`b744f357`) changes LSP4IJ's generic DAP configuration and default descriptor
factory to accept only Run and Debug. Custom adapters opt in through their
existing `canRun` override; FDE already explicitly accepts its Lua profiler.
This fixes routing itself rather than relying on the currently selected
configuration to hide a menu entry. Regression tests verify that the generic
Python DAP configuration rejects the Factorio executor and has no matching
runner, while Run/Debug and explicit custom-adapter opt-in still work.

LSP4IJ build **0.21.1-SNAPSHOT-13** contains the fix and is required for this FDE
build. All 55 DAP tests pass; the contribution's local CodeRabbit review against
`upstream/main` completed with zero findings. The branch and work integration
are local and unpushed; no PR was filed.

**4. [P2] Resolved — Make the native module-filter test independent of the author's filesystem**

Location: [FactorioProfileModulePanelTest.java](../src/ideTest/java/de/softwareforge/factorio/profiler/FactorioProfileModulePanelTest.java),
lines 60–69; fixture:
[factorio-2.1.23.cpuprofile](../src/test/resources/profiling/factorio-2.1.23.cpuprofile).

The default fixture contains absolute Lua URLs below
`/Users/henning/ai/fmtk/intellij-factorio/build/test-work/protocol/fixture/mods/`.
The test asserts that import discovers `LogisticTrainNetwork`, but it creates
neither that mod's `info.json` nor a registered mod root. For absolute URLs,
`FactorioProfileModules` needs one of those sources of metadata to identify the
module. The fixture's filename and directory name intentionally do not suffice.

The test passes on this populated workspace because previous native probes left
those files behind. On a clean checkout, another machine, or after deleting the
ignored protocol fixture, these frames become **Unidentified Lua sources** and
the assertion at line 69 fails. A review probe relocated the recorded URLs to
an empty temporary directory and confirmed that only the unidentified choice
remained.

Create disposable mod directories and manifests within the test and rewrite
the fixture URLs to them, or use logical `__mod__/` locations in a portable
fixture. Verify the default test with no previously generated native fixtures.

Evidence: `testSnapshotModuleAssertionDependsOnOriginalAbsolutePaths` in the
[platform probe log](../build/logs/profiling-review-platform-console.log).

Resolution (2026-10-20): the retained native fixture now uses portable logical
`__LogisticTrainNetwork__/…` and `__fmtk-probe__/…` URLs instead of the author's
absolute paths. The default module-filter test needs neither manifests nor old
native-probe output. All three module-panel tests passed using that default
fixture, including the real snapshot-comparison regression.

**Validation and limits (initial review)**

- Focused unit tests: **17 passed**, covering the parser, recording state
  machine, and module resolution/filtering. Results:
  [profiling-review-unit](../build/test-results/profiling-review-unit/).
- Existing profiler platform tests: **12 passed**. Three additional review
  probes confirmed findings 2–4. The run logged the comparison-action exception
  described in finding 1 despite having no JUnit failures. Results:
  [profiling-review-platform](../build/test-results/profiling-review-platform/).
- A separate registered-tab comparison probe confirmed finding 1 and checked
  the unwrapped native component as a control. Its assertions passed.
- Targeted launch/stop platform tests: **9 passed**, covering automatic and
  manual recording, Lua-initiated recording, disabled protocol tracing, a slow
  save, in-game termination, normal Run, and the existing paused Stop path.
  Results: [profiling-review-lifecycle](../build/test-results/profiling-review-lifecycle/).
- Review-only probes and their Gradle init script are under the ignored
  [build/test-work/profiling-review](../build/test-work/profiling-review/)
  directory. The versioned test and production sources were left unchanged.
- Native Factorio capture was not rerun. The documented native adapter stall
  and loss of an active recording on in-game Quit are not reported as new FDE
  defects. The dependency refresh was checked in context; this was not a
  separate review of upstream LSP4IJ changes.

**Independent follow-up review — 2026-10-20**

Verified the resolutions of findings 1–4 against the updated implementation,
portable fixture, and prepared LSP4IJ **0.21.1-SNAPSHOT-13**. All **14 profiler
platform regression tests passed** in an independent run, including the new
registered-tab comparison, explicit import routing, and executor rejection
tests. [Verification log](../build/logs/profiling-rereview-verification-console.log)
and [results](../build/test-results/profiling-rereview-verification/).

At that point, two additional review-only probes failed reproducibly for the
cases below, separately from the passing regression suite. Production code and
versioned tests were not edited by this follow-up review; native Factorio
capture was not rerun.

**5. [P2] Resolved — Keep module filtering independent of the loaded snapshot file**

Location: [FactorioProfileModulePanel.java](../src/main/java/de/softwareforge/factorio/profiler/FactorioProfileModulePanel.java),
lines 115–120 (`replaceSnapshot`). Introduced by the resolution of finding 1.

Every filter change now creates a new `ProfilerDumpDescriptor` and passes it to
a new `ProfilerDumpPanel`. That platform constructor calls
`Files.readAttributes(descriptor.getFile())` to obtain the snapshot timestamp.
If the user moves or deletes a snapshot after importing it, applying a module
filter throws `NoSuchFileException` instead of displaying the already-computed
filtered data. The previous implementation filtered entirely in memory and did
not require the original file to remain on disk.

The failure also escapes the callback's `catch (RuntimeException)`: the Kotlin
constructor propagates an `IOException`, which is not a `RuntimeException`.
Consequently the normal error handler does not clear the `Filtering…` state.
This can occur when a user removes a large recording to reclaim disk space or
renames it while keeping its Profiler tab open.

Confirmed with an isolated platform probe: register a normal snapshot panel,
prepare the selected-module call trees, delete only the probe's temporary
snapshot, and invoke the same final EDT replacement method. The probe fails
with `NoSuchFileException` from `ProfilerDumpPanel` through
`FactorioProfileModulePanel.replaceSnapshot`. No user recording was modified.

Keep the display update independent of the source file's continued existence,
and ensure reconstruction failures reach the UI's error/cleanup path. Add a
regression that imports a snapshot, moves/deletes its backing file, and changes
the module selection successfully.

Evidence: [follow-up probe log](../build/logs/profiling-rereview-probes-console.log),
[JUnit result](../build/test-results/profiling-rereview-probes/TEST-de.softwareforge.factorio.profiler.ProfilingFollowupProbeTest.xml),
and [review-only probe source](../build/test-work/profiling-rereview/src/de/softwareforge/factorio/profiler/ProfilingFollowupProbeTest.java).

Resolution (2026-10-20): replacement panels use the native in-memory constructor
path without a dump descriptor, retaining the original filename as metadata.
The constructor therefore no longer reads the source file's attributes. The
EDT reconstruction handler catches checked exceptions propagated by Kotlin as
well as runtime failures, clears the pending state, and keeps cancellation
separate. The regression imports a disposable copy of the native fixture,
moves it, selects LTN, deletes the moved copy, selects None and restores All.
It checks totals, tab name/pinning, source filename, disposal and comparison
availability. It reproduced `NoSuchFileException` before the fix and now passes.

**6. [P2] Resolved — Render frames from both sides of a snapshot comparison**

Location: [FactorioProfileImporter.java](../src/main/java/de/softwareforge/factorio/profiler/FactorioProfileImporter.java),
lines 49–57 (`getCommentInfo` and `getColorScheme`). Exposed by the resolution
of finding 1.

The renderer closes over the `styles` map for one imported profile and assumes
every rendered frame exists in that map. IntelliJ's `ProfilerSnapshotComparator`
uses the first snapshot's renderer for the combined comparison tree, which also
contains calls unique to the second snapshot. For such a call,
`styles.get(frame.frame)` returns null and `getCommentInfo()` throws
`NullPointerException`; `getColorScheme()` has the same unchecked lookup.

This occurs in ordinary comparisons where a function ran in only one capture,
or where code changes introduced a new function/location. The comparison tab
can now open, but requesting the tooltip/category information for that frame
fails. The new comparison regression uses the same underlying `Data` for both
tabs and filters a subset of it, so its renderer still knows every frame and
does not expose this case.

Confirmed using two independently imported profiles containing `onlyInFirst`
and `onlyInSecond`, respectively. The probe uses the same native
`MultipleCallTreesProfilerData` wrappers as the viewer, builds the actual
comparison model with an installed progress indicator, and requests the
renderer metadata for its nodes. It fails on the second-only frame at
`FactorioProfileImporter.java:53` with a null `Style`.

Make a frame's presentation usable independently of its originating snapshot's
map—for example, carry the precomputed style with `LuaFrame`, preserving raw
frame equality—or provide safe comparison-aware lookup. Keep filesystem/PSI
work out of rendering. Add a regression using different recordings with
disjoint calls, and render their comparison nodes rather than checking only
that a comparison tab was created.

Evidence: `testComparisonRendersCallsUniqueToEitherSnapshot` in the
[follow-up probe log](../build/logs/profiling-rereview-probes-console.log) and
[JUnit result](../build/test-results/profiling-rereview-probes/TEST-de.softwareforge.factorio.profiler.ProfilingFollowupProbeTest.xml).

Resolution (2026-10-20): immutable `LuaFrame` instances now carry their
precomputed style and display name. Renderers use the supplied frame's own
presentation, with no lookup in another recording's map and no filesystem/PSI
work while rendering. Raw frame equality and hashing remain unchanged. A native
comparator regression uses independently imported recordings with disjoint calls
and modules. It renders text, category comments and colors in both comparison
directions, for full and module-filtered trees, and checks that each frame keeps
its original presentation and raw identity. It reproduced the null-style
failure before the fix and now passes.

**Fix author's initial resolution validation, findings 1–4 — 2026-10-20**

- **17 profiler unit tests passed**: parser, recording state machine and module
  identity/filtering. [Results](../build/test-results/profiling-review-fixed-unit/).
- **24 FDE platform tests passed**, including registered-tab comparison,
  explicit import selection, negative/positive executor routing, native views,
  source navigation, settings, and nine launch/stop scenarios.
  [Results](../build/test-results/profiling-review-fixed-platform/).
- **55 LSP4IJ DAP tests passed**, including both new routing regressions.
  [Results](../../upstream/lsp4ij/build/checkouts/patched/build/test-results/dap-routing-build13/).
- The default native fixture is portable and was used without the optional
  external-profile environment variables. Its URLs contain no absolute paths.
- Checked the platform test output for the former comparison-action exception;
  none remains. The corrected module-filter screenshot was visually inspected.
- Local CodeRabbit review of the LSP4IJ contribution completed with zero
  findings. [Review log](../../upstream/lsp4ij/build/checkouts/dap-executor-routing/build/logs/coderabbit-dap-routing.jsonl).
- Native game capture was not rerun; the existing early-startup adapter issue
  and explicit-stop-before-quit requirement remain unchanged.

Rebuilt and validated installable archives:
[**FDE 0.9.0-dev**](../../plugins/intellij-factorio-0.9.0-dev.zip) and
[**LSP4IJ 0.21.1-SNAPSHOT-13**](../../plugins/lsp4ij-0.21.1-SNAPSHOT-13.zip).
Install both. [Artifact receipt](../build/logs/profiling-review-fixed-artifact.txt).
FDE changes are uncommitted; the dependency fix/integration are committed
locally. No push or upstream PR. These results precede follow-up findings 5–6.

**Fix author's follow-up resolution validation, findings 5–6 — 2026-10-20**

- Both new regressions failed against the previous implementation, reproducing
  the missing-file and null-style failures.
  [Before-fix results](../build/test-results/profiling-followup-before-fix/).
- **16 profiler platform tests passed**, including both regressions, existing
  registered-tab comparison, native views, source navigation, import routing,
  executor routing and colors. No former comparison exception, missing-file
  exception, null-style exception or filtering error appears in their output.
  [Results](../build/test-results/profiling-followup-platform/).
- **17 profiler unit tests passed** for parsing, recording state and module
  resolution/filtering. [Results](../build/test-results/profiling-followup-unit/).
- Rebuilt **FDE 0.9.0-dev**, checked ZIP/JAR integrity and plugin metadata, and
  copied the archive into flat `plugins/`.
  [Install ZIP](../../plugins/intellij-factorio-0.9.0-dev.zip) ·
  [Artifact receipt](../build/logs/profiling-followup-artifact.txt).
- LSP4IJ build 13 and other dependency pins are unchanged. Native Factorio
  capture was not rerun. These refinements remain uncommitted; no push or PR.

**Independent verification of findings 5–6 — 2026-10-20**

Observed both findings being marked resolved and reviewed the corresponding
production and test changes. Finding 5 now constructs the replacement from
in-memory data without a descriptor, preserves the snapshot filename in the
correct constructor argument, and handles checked reconstruction failures.
Finding 6 uses immutable presentation carried by each frame, while retaining
raw-frame equality and hashing. No new findings were identified in these fixes.

Independently reran **16 profiler platform tests and both original review-only
reproduction probes: 18 passed, zero failures, errors or skips**. The original
missing-file and disjoint-recording probes now pass without modification.
The added regressions also cover moved/deleted files through asynchronous
filtering, None/All selection, tab metadata and comparison availability, plus
rendering disjoint recordings in both comparison directions and filtered views.
The test output contains none of the previously reported comparison,
missing-file, null-style or filtering errors. Source hashes were unchanged
during this verification run.

Evidence: [independent verification log](../build/logs/profiling-watch-verification-console.log),
[JUnit results](../build/test-results/profiling-watch-verification/), and
[verified source hashes](../build/test-work/profiling-watch/verification-source-hashes.json).
The earlier failing probe results remain preserved separately. This check made
no production or versioned test changes and did not rerun native Factorio capture.

**Initial profiler review complete: findings 1–6 independently verified resolved.**

**Latest commit review — `eff1b8f` — 2026-10-20**

Reviewed `eff1b8f5b9ba2b6a054e93f6de10ff899d3f74f0` (`fix: restore
pre-profiler shutdown behavior`) against its parent `a92cd21`, including all
11 changed files and the surrounding launch/shutdown lifecycle. The working
tree was clean at review start. Removing automatic saving on IDE Stop and
restoring the 15-second cleanup timeout are intentional, documented changes;
the loss of an explicitly unsaved recording is not a finding. One new P2
regression was reproduced.

**7. [P2] Resolved — Block automatic profiling once shutdown has begun**

Location: [FactorioDebug.java](../src/main/java/de/softwareforge/factorio/FactorioDebug.java),
lines 283–285 (`terminate`), together with
[FactorioProfileSession.java](../src/main/java/de/softwareforge/factorio/FactorioProfileSession.java),
lines 31–35 (`canStart` / `start`). Introduced by `eff1b8f`.

Stopping a Profile launch before its handshake completes no longer prevents
the launch-completion callback from starting the recorder. Previously,
`terminate()` called `profiling.finish()`, which set `closing` before sending
`terminate`, so a later `profiling.start()` was rejected. The new shutdown path
does not mark the recording session as closing, and `canStart()` now checks
only `IDLE`. The session remains eligible to start until `releaseClient()`
eventually calls `close()` after process/output cleanup.

If the user presses Stop while `configurationDone` is pending, and that response
arrives during shutdown, the existing `connectToServer().whenComplete` callback
at line 259 sends `startProfile` after `terminate`. This starts recording for a
launch the user already cancelled, and a subsequent `profileRunning` event makes
normal cleanup report an unsaved recording created during shutdown. The new
pending-start regression does not cover this ordering: it starts recording
before Stop and leaves the `startProfile` request unanswered.

Confirmed with the real IntelliJ Profile executor, LSP4IJ client and Factorio
process handler against a disposable adapter. The adapter delays only the
`configurationDone` response until it receives `terminate`; all subsequent
shutdown messages follow the existing fixture behavior. The request traces are:

```text
eff1b8f: initialize, launch, configurationDone, terminate, startProfile, threads, disconnect
parent:  initialize, launch, configurationDone, terminate,               threads, disconnect
```

The same probe passes with the three changed lifecycle classes copied verbatim
from parent `a92cd21`, and fails again after rebuilding the current classes.
No versioned source was replaced for this control; the Gradle init script
selects isolated copies under `build/test-work/`.

Retain a shutdown guard that blocks new recorder commands and the automatic
launch callback as soon as Stop/disposal begins, without requesting or waiting
for a save. Preserve consumption of already queued completion events during
output cleanup. Add a regression for Stop before the launch acknowledgement,
asserting that no `startProfile` follows `terminate`.

Evidence: [current-commit confirmation log](../build/logs/review-eff1b8f-confirm-console.log),
[failing result](../build/test-results/review-eff1b8f-confirm/TEST-de.softwareforge.factorio.FactorioStartupStopReviewTest.xml),
[parent control log](../build/logs/review-eff1b8f-parent-control-console.log),
[passing parent result](../build/test-results/review-eff1b8f-parent-control/TEST-de.softwareforge.factorio.FactorioStartupStopReviewTest.xml),
and [review-only probe and fixture](../build/test-work/review-eff1b8f/).

**Independent validation of `eff1b8f`**

- **11 focused unit tests passed**: recording lifecycle, process-handler
  cleanup and restart state. [Results](../build/test-results/review-eff1b8f-unit/)
  and [log](../build/logs/review-eff1b8f-unit-console.log).
- **20 existing platform tests passed**: all 19 Stop scenarios and the real
  runner restart test. The additional startup/Stop probe failed on its explicit
  unexpected-`startProfile` assertion. These ran together:
  [results](../build/test-results/review-eff1b8f-probe/) and
  [log](../build/logs/review-eff1b8f-platform-console.log).
- The isolated parent control passed; a second current-commit run reproduced
  finding 7. The final probe waits for recording-state cleanup before teardown.
  Earlier harness teardown diagnostics are not reported as product findings.
- Confirmed the documented FDE **0.9.1-dev** installation archive checksum,
  plugin version and ZIP/nested-JAR integrity. No archive was installed or rebuilt
  for distribution by this review.
- Checked that reviewed source hashes remained unchanged and that the current
  implementation was rebuilt after the parent control. Production code and
  versioned tests were not edited. Native Factorio capture was not rerun; this
  review establishes the protocol ordering with a controlled adapter, not a
  native-game reproduction. [Source hashes](../build/test-work/review-eff1b8f/reviewed-source-hashes.json).

**Historical `eff1b8f` review result: one open P2 finding (7); findings 1–6 remained resolved.**

**Finding 7 resolution — author validation, 2026-10-20**

Added a synchronized shutdown guard to the recording session. The process-first
Stop callback, DAP terminate/terminated and disposal set it before shutdown work.
Start/Stop actions and the automatic launch callback cannot send recorder
commands after that point, including when the transport connects late. Pending
commands and queued recorder events remain intact until normal client cleanup;
already saved snapshots still open. No automatic save or additional shutdown
wait has been restored, and the 15-second cleanup timeout is unchanged.

The versioned adapter fixture now delays `configurationDone` until `terminate`.
Its new real-runner regression failed before the fix with `startProfile` after
`terminate`, and passes after the fix. Two unit regressions cover shutdown before
and after connection, command rejection throughout recording states, and queued
completion delivery without a false unsaved-recording report.

**25 unit tests + 37 IntelliJ platform tests passed**, zero failures, errors or
skips. Coverage includes all 20 Stop scenarios, restart and the profiler views.
Built and validated **FDE 0.9.2-dev**; the installation ZIP is in flat `plugins/`.
No native game capture was rerun. Changes remain **uncommitted and unpushed**;
these author checks do not constitute independent review completion.

[Failing regression](../build/test-results/profiling-shutdown-guard-before/) ·
[Unit results](../build/test-results/profiling-shutdown-guard-unit/) ·
[Platform results](../build/test-results/profiling-shutdown-guard-platform/) ·
[Build/test log](../build/logs/profiling-shutdown-guard-platform-console.log) ·
[Artifact receipt](../build/logs/profiling-shutdown-guard-artifact.txt) ·
[Validated source hashes](../build/logs/profiling-shutdown-guard-source-hashes.json).

**Independent review of the uncommitted shutdown guard — 2026-10-20**

Reviewed all working-tree changes against `eff1b8f`, including the shutdown
guard, callback registration, regression fixture/tests and release documentation.
Finding 7 is independently verified resolved. No new findings were identified.

`beginShutdown()` and recorder commands share the session lock. The guard is
set synchronously in the process-first Stop callback, as well as DAP termination
and disposal. Registration also handles a process already stopping or stopped.
A late transport connection does not clear the guard. Start/Stop actions and
the automatic launch callback therefore cannot issue new recorder commands
after it is set. Queued running/completion events remain consumable, and the
guard does not resolve pending operations, initiate a save or extend cleanup.

Independent validation:

- **13 focused unit tests passed**: seven recording-session tests, two process
  handler tests and four restart-state tests. This includes command rejection
  before/after connection and completion delivery during shutdown.
  [Results](../build/test-results/review-shutdown-guard-unit/) ·
  [Log](../build/logs/review-shutdown-guard-unit-console.log).
- **22 platform tests passed**: all 20 Stop scenarios, the real-runner restart
  test and the original review-only startup/Stop reproduction, reused without
  modification. Zero failures, errors or skips in either suite.
  [Results](../build/test-results/review-shutdown-guard-platform/) ·
  [Log](../build/logs/review-shutdown-guard-platform-console.log).
- The original probe now records `initialize, launch, configurationDone,
  terminate, threads, disconnect`, with no `startProfile` after `terminate`.
  [Original probe's passing result](../build/test-results/review-shutdown-guard-platform/TEST-de.softwareforge.factorio.FactorioStartupStopReviewTest.xml).
  The earlier failing result remains preserved in the finding's evidence.
- Verified both FDE **0.9.2-dev** archives against the documented SHA-256,
  checked ZIP/nested-JAR integrity, plugin version and the packaged shutdown
  guard. [Artifact verification](../build/test-work/review-shutdown-guard/artifact-verification.txt).
- Reviewed sources, dependency pins and the original probe remained unchanged
  throughout validation. [Source hashes](../build/test-work/review-shutdown-guard/reviewed-source-hashes.json)
  and [reviewed implementation diff](../build/test-work/review-shutdown-guard/reviewed-diff.patch).

This review changed only this report and ignored validation artifacts. No
production code or versioned tests were edited, no distribution was installed,
and no commit or push was made. Native Factorio capture was not rerun; lifecycle
verification used controlled adapters.

**Independent review complete: all seven findings resolved; zero open findings.**
