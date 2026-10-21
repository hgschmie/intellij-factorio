# Lua profiling — FDE 0.9.0-dev

Implementation checkpoint **2026-10-20**: capture, import, native views,
navigation, colors, execution-time selection, module filtering and comparison
are complete. All six [review findings](PROFILING_REVIEW.md) are independently
verified resolved. Latest validation: **17 unit tests** plus an independent
**18-test platform run** (16 regressions and the two original review probes),
with no failures, errors or skips. The validated ZIP and checksum are recorded
below; dependencies are EmmyLua2 **0.30.0-185** and LSP4IJ
**0.21.1-SNAPSHOT-13**. The development version remains **0.9.0-dev**.

The early-startup native adapter stall remains deferred, and recordings must be
explicitly stopped before in-game Quit. The dated receipts below preserve the
implementation history, including their then-current commit/build status.

Select a Factorio run configuration and choose **Profile with Factorio Lua**
from IntelliJ's standard Profile menu. FDE launches the game and starts recording
automatically when the DAP launch handshake completes. This uses Factorio's Lua
recorder; JVM profiler choices such as **IntelliJ Profiler** are not applicable
to a Factorio configuration. Profiling launches do not install debug breakpoints.

For manual capture intervals, start a normal Factorio Run or Debug session, then
use **Start Lua Profiling** and **Stop Lua Profiling** under **Tools → Factorio**
(also in the Debug toolbar). Stopping only the recording leaves the game running,
so another interval can be recorded without relaunching it. **Explicitly end
profiling with Stop Lua Profiling and wait for the saved snapshot before ending
the game session.** In-game Quit does not save the recording. We do not attempt
to work around that native Factorio bug with shutdown-output hooks or periodic
captures. IntelliJ's Stop action retains a best-effort save, but the documented
workflow is to stop profiling explicitly. Rerunning a Profile launch starts a
new recording; the saved Run/Debug configuration is not changed.

IDE-started recordings always exclude profiler hook timings. FDE sends
`{"show_hook_events": false}` in every `startProfile` request, whether started
manually or with **Profile with Factorio Lua**, using the option documented for
[LuaDebugAdapter.start_profile](https://lua-api.factorio.com/latest/classes/LuaDebugAdapter.html#start_profile).
There is no FDE setting for hook events; older saved values are ignored. This
applies to new recordings and does not alter an active recording or saved file.
Existing snapshots containing `(hook: call)`, `(hook: line)` or `(hook: return)`
still open normally. Lua-initiated recordings use the options supplied by their
Lua caller.

In the verified Factorio 2.1.23 recorder, disabling hook events subtracts hook
time from the recorded timings; it does not charge that time to the surrounding
Lua function. The hooks still execute and cost real elapsed time. Debugger
pauses remain in the recording, including their contribution to enclosing
functions' inclusive time. See the controlled comparison below.

Stopping the recording opens IntelliJ's standard **Profiler** window. Recording covers the game's Lua
contexts, including other mods; it is not restricted to the selected module.
Starting after the save has loaded keeps startup work out of a runtime profile.

The native views provide Flame Graph, Call Tree and Method List, with self and
inclusive time, percentages, search, recursion folding and caller/callee
exploration. Profiles open in **Lua execution** view: the root's own unattributed
time is excluded, its children become the top-level calls, and graph widths and
percentages use the sum of those calls as the total. Choose **Including
unattributed time** in the native **Show** selector to restore the entire
recording. Switching applies to all three views without rereading the snapshot.
The full view labels the root **(root — entire recording)**: its total includes
Lua calls, while only its self time is unattributed. A recording with no timed
calls has an empty Lua execution view; the full view remains available.

This display choice does not change the saved file or individual call timings.
Native calls beneath Lua frames, recorded hooks and debugger pauses remain in
the call tree in either view. It excludes root self time only; it does not claim
to measure uninstrumented CPU time. Reopen an existing snapshot after upgrading
to use the new default; no new capture is needed.

Use **Modules: All** above the profiler tabs to choose one or several modules,
then **Apply**. **All** and **None** in the chooser select or clear its checkboxes.
The selection affects Flame Graph, Call Tree and Method List together. The
native snapshot panel is replaced in its existing tab so snapshot comparison
uses the displayed filtered data. Selecting
all entries restores the complete recording. Each open snapshot has its own
selection; changing it does not require a new capture or rereading the file.
Filtering also works after the imported snapshot file is moved or deleted.

The filter retains each selected module's calls and everything called beneath
them, including shared libraries, native calls and calls into another module.
Other ancestor frames remain as context with their own time excluded; unrelated
sibling paths disappear. Totals and percentages are recalculated, and selecting
both a caller and a callee does not count their shared time twice. Filtering runs
in the background and preserves the selected main tab. Root unattributed time
is available only with **All modules**, since it cannot be assigned to a module;
returning to All restores the previous **Show** choice.

Module identity comes from `__mod__/` locations, registered IntelliJ mod roots
(including existing symlink targets), or a source folder's nearest `info.json`.
External mods in the recording are included in the chooser. Unresolvable source
paths are grouped as **Unidentified Lua sources** instead of guessing identities
from folder names. This is a filter of recorded source call paths, not a separate
measurement of Factorio's runtime ownership: a call with no identifiable module
is included when it is beneath a selected call, or remains visible with All.

Lua frames use a stable hue family per mod, with shades by source
file. Native calls are blue; profiler hooks are slate; event/runtime frames are
purple; paused and unattributed time are neutral gray. Hover text identifies the
category and mod. Color represents identity, not cost: frame width represents
inclusive time. The finite palette can repeat, and changing the files or mods
present in a recording does not shift other colors. Unidentified external Lua
sources receive stable file colors without guessing their mod.

Colors and text contrast adapt to light/dark themes, with visible hover states
and IntelliJ's search dimming/focus treatment. Reopen an existing snapshot to
use the new palette; no new recording is needed.
Lua frames navigate to their source location. Missing sources and
native frames remain visible; FDE does not guess a source location for them.
When the recorder supplies no function name, FDE uses a named Lua declaration
starting on the recorded line in the currently available source. Local/global
functions and explicit dot/colon methods are supported. Anonymous functions,
multiple functions on the same line, or unavailable declarations use
`filename:line`. Recorded names are preserved. Source-derived labels are captured
when the profile opens and do not change the saved snapshot or frame aggregation.
For a mostly idle recording, unattributed time can dominate the full recording
view. The default Lua execution view keeps that time from squeezing the graph.

Factorio writes `.cpuprofile` snapshots into its write-data `script-output`
directory. The debug console prints the saved path. **Tools → Factorio → Open
Factorio Profile…** reopens a recording after the debug session ends. The original
file is retained and can also be used with compatible external viewers.

To reopen a saved recording after installing the updated plugin and restarting
IntelliJ:

1. Choose **Tools → Factorio → Open Factorio Profile…**.
   The chooser starts in the last saved or opened profile's folder, remembered
   per project across IDE restarts. On first use it finds the most recent
   recording in Factorio's standard `script-output` folders, including separate
   prerelease folders such as `factorio_21`.
2. Select the saved `.cpuprofile` file. On macOS, **Cmd+Shift+G** in the file
   chooser lets you enter its directory directly.
3. Click **Open**. The **Profiler** tool window opens; choose **Flame Graph**,
   **Call Tree** or **Method List** after parsing finishes.

Factorio does not need to be running. The user's recording from 2026-10-20 is
`~/Library/Application Support/factorio_21/script-output/353519140875083.cpuprofile`.
It is 1.85 GB; the validated streaming parser read it in about 20 seconds.

Profiling initiated through `debugadapter.start_profile()` and
`debugadapter.stop_profile()` uses the same UI. Large recordings may take tens of
seconds to save. FDE reports that saving is still in progress after ten seconds;
it keeps the operation pending and disables duplicate start/stop requests.
Debugger Stop waits up to two minutes for the recording before terminating
Factorio. Crashes, in-game quit, force termination, or an unresponsive recorder
may prevent a final snapshot. FDE reports an unsaved recording when the transport
closes. Previously completed recordings remain.

## Data and compatibility

FDE uses LSP4IJ **0.21.1-SNAPSHOT-13** or a later build containing UP-098 for
executor routing. Generic DAP configurations accept Run and Debug only; the
Factorio adapter explicitly opts into **Profile with Factorio Lua**. This keeps
that executor from launching unrelated adapters.

The `.cpuprofile` format is also used by Chrome and Node. FDE does not claim the
extension automatically: use **Open Factorio Profile…**, or explicitly select
**Factorio Lua** when the common Profiler asks which provider to use.

FDE sends the same `startProfile`/`stopProfile` requests as FMTK and follows the
native `profileRunning`/`profileComplete` events. It does not install a profiling
mod, instrument user Lua or run a separate profiler process.

Native Factorio 2.1.23 snapshots use the Chrome CPU profile shape: a call tree,
frame IDs, timing entries and microsecond `timeDeltas`. After a metadata scan,
FDE pairs sample IDs and time deltas through independent file cursors and
aggregates nanosecond values without retaining per-sample arrays. The UI
formats milliseconds with sub-millisecond precision. Times are the recorder's
elapsed measurements, not a separate measurement of operating-system CPU usage.
The root's own time is unattributed to a Lua frame and can include time outside
Lua or while execution is paused. Sample/hit counts are not presented as call
counts. No coroutine IDs are present in the verified format, so the view uses
one **Lua (all contexts)** group. There is no chronological timeline or memory
profile in this release.

Source resolution accepts absolute paths and Factorio `__mod__/path` locations,
using project mods, configured dependencies (including ZIPs), and the configured
Factorio data directory. Profile line numbers are zero-based. Relocated files
with an absolute path may require restoring their original path; FDE does not
guess a matching file by name.

The viewer uses platform 262's `intellij.profiler.common` module through the
optional `com.intellij.modules.profiler` dependency. Recording and FDE debugging
remain available without that module; completed captures then offer a file link.
CLion, JavaScript and native profiler modules are not required. The parser limits
call trees to 500,000 frames but does not limit the number of timing entries:
memory depends on the call tree rather than the duration of the recording.

## Validation

`scripts/test-profile-protocol.py` captures a real recording from the disposable
probe/LogisticTrainNetwork fixture. Its outputs stay in `build/test-work/protocol`.
Use `--on-launch` to validate automatic recording after the launch handshake.
Use `--while-running` to start recording after resuming a loaded save.
The developer-only probe accepts `--show-hook-events` to investigate native
recorder overhead; FDE itself always disables these events.
Use `--quit-before-stop terminated` or `--quit-before-stop quitting` to reproduce
the native shutdown limitation; Factorio 2.1.23 closes the transport without a
response or snapshot, so these probes intentionally fail with `Server exited`.
The retained small regression fixture is under `src/test/resources/profiling`.

Unit tests cover fractional timings, recursion, empty recordings, malformed
trees/timings, cancellation, command failures, duplicate clicks, events arriving
before responses and Lua-initiated recording. Platform tests exercise populated
native Profiler views, source navigation and DAP shutdown with active/unresponsive
recordings. UI evidence is saved under `build/reports/ui/`.

### Code review follow-up — 2026-10-20

All six findings in [PROFILING_REVIEW.md](PROFILING_REVIEW.md) are resolved.
The two follow-up fixes keep module filtering independent of the source file
after import and preserve frame colors/category comments when comparing
recordings containing different functions. Presentation stays with immutable
frames; rendering performs no source lookup and aggregation identity is unchanged.

Both new regressions reproduced the failures before the fixes. **16 profiler
platform tests and 17 profiler unit tests passed** afterward. Tests cover
moving/deleting a loaded snapshot, changing/restoring module selections, and
rendering both sides of real native comparisons before and after filtering.
Native game capture was not rerun.

Rebuilt and validated **0.9.0-dev**; install
[`intellij-factorio-0.9.0-dev.zip`](../../plugins/intellij-factorio-0.9.0-dev.zip).
SHA-256: `f2aba9438721678348edb6c9ac32ced65e173dbe82d86d7a5e05c86b55ae44a7`.
[Artifact receipt](../build/logs/profiling-followup-artifact.txt).
LSP4IJ **0.21.1-SNAPSHOT-13** remains required and unchanged. Same uncommitted
feature/version; no dependency changes, commit, push or PR in this follow-up.

### Initial code review fixes — 2026-10-20

The original four findings in [PROFILING_REVIEW.md](PROFILING_REVIEW.md) were resolved.
Module controls now sit inside the native component, and filtering replaces the
registered snapshot panel so comparison uses the displayed data. The shared
CPU-profile extension requires explicit provider selection. The native fixture
uses portable logical mod paths. UP-098 in LSP4IJ build13 restricts generic DAP
routing while preserving Factorio's explicit profiler support.

Validation: **17 FDE unit, 24 FDE platform and 55 LSP4IJ DAP tests passed**, with
zero failures/errors/skips. The LSP4IJ contribution passed local CodeRabbit with
zero findings. Native comparison is exercised through registered Profiler tabs;
the previous action exception no longer appears in the platform test output.

Rebuilt and validated **0.9.0-dev** with **LSP4IJ 0.21.1-SNAPSHOT-13**. Install
both ZIPs from flat `../plugins/`. FDE SHA-256:
`70642baa7e3c4ea92a76c54f0e4c0b9244f0ffc7f41f19703ce0fc42f38fd3af`.
Receipt: `build/logs/profiling-review-fixed-artifact.txt`. FDE changes remain
uncommitted; UP-098 and the LSP4IJ integration are committed locally. Nothing
was pushed and no PR was created.

### Always exclude hook timings — 2026-10-20

Removed the hook-events checkbox and persistent setting. Every IDE-initiated
`startProfile` request now sends `show_hook_events: false`, including manual
intervals and **Profile with Factorio Lua** launches. Old saved settings cannot
enable it. Existing recordings and Lua-initiated capture options are unaffected.

All **four targeted platform tests pass**, with no failures, errors or skips:
settings UI, repeated manual intervals, automatic launch, and automatic launch
after restoring the obsolete setting as `true`. The transport tests inspect
actual outgoing DAP arguments. Evidence:
`build/test-results/profiling-hook-disabled-platform/`.

Rebuilt the same uncommitted **0.9.0-dev** feature; validated ZIP/JAR integrity,
version and removal of the packaged setting/UI, retaining the module filter.
Copied the archive from `build/distributions/` to flat `../plugins/`. SHA-256:
`f052d92056d4ded0cb35aac3f06f57463ea487e9947faab9c1cf0387d2a55d24`.
Receipt: `build/logs/profiling-hook-disabled-artifact.txt`.

### Module selection — 2026-10-20

Added the per-snapshot **Modules** picker with multi-selection and All/None.
Selected call paths retain their complete callees; shared ancestors retain only
the selected descendant time. The filter computes a union of selected paths,
so nested selections do not double-count time. Other paths and ancestor self
time are excluded. The original parsed snapshot remains immutable.

Source identity is captured once from logical Factorio paths, registered module
roots, canonical paths/symlinks and nearest `info.json` metadata for external
sources. The palette uses the same identities. Module names are not inferred
from directory names; unresolved Lua sources have a separate choice. Selection
rebuilds the native view in the background without file or PSI lookup. Obsolete
results are discarded, replaced views are disposed, and selection is local to
each snapshot. The selected main tab and All-modules Show preference survive
filter changes. Unattributed root time is offered only for All modules.

All **three targeted unit tests and 12 profiling platform tests pass**, with no
failures, errors or skips. Tests cover path aliases and boundaries, nested roots,
external metadata, missing sources, shared/native callees, ancestor time,
overlapping selections, 20,000-frame depth, cancellation, empty selections,
rapid replacement, restoring full-recording mode and all three native views.
Evidence: `build/test-results/profiling-module-unit/` and
`build/test-results/profiling-module-platform/`.

The user's `376492482469083.cpuprofile` resolves to **39 module/library names**.
Selecting LogisticTrainNetwork yields **386.258481 ms**, including its callees.
Visually checked `build/reports/ui/profiling-module-LTN-Flame-Graph.png`;
Call Tree and Method List images are alongside it. The recording was only read.

Rebuilt the same uncommitted **0.9.0-dev** feature, verified ZIP/JAR integrity,
version and packaged picker/filter classes, then copied the archive from
`build/distributions/` to flat `../plugins/`. SHA-256:
`5c081d3b8740cdf5648be1daa958ad17850a6f7a648fa03ba4653a10826621f1`.
Receipt: `build/logs/profiling-module-artifact.txt`. No upstream source or
dependency changes, upstream plugin rebuild, commit or push.

### Lua execution view — 2026-10-20

Profiles now open with the recorded root's children directly beneath IntelliJ's
invisible root, excluding unattributed root self time from the graph and its
denominator. Individual call self/inclusive times and identities stay unchanged.
The standard **Show** selector switches between **Lua execution** and **Including
unattributed time** across Flame Graph, Call Tree and Method List. Both models
are prepared from the same parsed snapshot; switching does not reread the file.
The full view labels the root's total and self time distinctly. Native calls,
hooks and debugger pauses beneath the recorded root are retained in both views.

All **10 profiling platform tests pass**, with no failures, errors or skips.
Regression coverage checks changed totals/percentages, unchanged descendant
timings and identity, idle-only/empty recordings, navigation and colors. The
native-view test exercises the actual selector in both directions across all
three tabs. Test evidence: `build/test-results/profiling-execution-platform/`.

The native UI test also loaded the user's 310 MB snapshot
`376492482469083.cpuprofile`: **3,539.272081 ms** in Lua execution view versus
**59,136.703317 ms** including unattributed time. Visually checked
`build/reports/ui/profiling-execution-Flame-Graph.png` and
`build/reports/ui/profiling-entire-Flame-Graph.png`; the Lua calls fill the width
in the default view. Matching Call Tree/Method List images are saved alongside.
For another local snapshot, set `FACTORIO_TEST_PROFILE` and the path-separated
`FACTORIO_TEST_PROFILE_SOURCE_ROOTS` before running `FactorioProfileTest` so the
isolated IDE test can resolve its Lua sources.

This refines the same uncommitted **0.9.0-dev** feature. Existing snapshots need
only to be reopened after upgrading. No source recording is modified, and no
upstream source, dependency pin, branch or PR changes are involved.
Rebuilt and verified the archive's ZIP/JAR integrity, plugin version and packaged
view selector, then copied it from `build/distributions/` to flat `../plugins/`.
SHA-256: `6ccabfa731497d2ec348c2be894d5456f55313431827148fc880e6e1b45adda2`.
Artifact receipt: `build/logs/profiling-execution-artifact.txt`. No commit or push.

### Native hook-time accounting comparison — 2026-10-20

`scripts/test-profile-hook-accounting.py` isolates the accounting behavior using
the native Factorio 2.1.23 DAP adapter in a disposable fixture. The same Lua
function sums 1..40. Its line breakpoint condition is either cheap (`false`) or
deliberately expensive (one million additions on each of 40 hits, still returning
false). An unconditional breakpoint inside the function is held for 400 ms in
every recording. The expensive cases are repeated in reverse option order.

All six captures completed. Results from the two expensive-condition runs per
option, rounded to milliseconds:

| Include hook timing events | Client elapsed | Raw profile duration | Recorded hook time | Recorded pause time |
| --- | ---: | ---: | ---: | ---: |
| Off | 758–759 ms | 414–415 ms | No hook frames | 410 ms |
| On | 734–741 ms | 727–738 ms | 318–322 ms | 407–414 ms |

The cheap-condition controls recorded only 0.083 ms of hooks when enabled, with
profile durations of 407 ms (off) and 415 ms (on), dominated by the pause. With
expensive conditions and the option off, the Lua function's own recorded time
remained 0.0015–0.0023 ms. The omitted hundreds of milliseconds were not reassigned
to Lua or root time. Raw `endTime - startTime` agrees with the sum of raw
`timeDeltas` in all six captures (within timestamp floating-point precision),
so this behavior originates in Factorio, not FDE's importer.

Conclusion: disabling hook events removes hook time from the reported profile,
but not from real execution cost. It does **not** remove debugger pauses. This
test establishes accounting behavior, not uninstrumented execution performance;
the client elapsed measurement also includes DAP communication and stack lookup.

Evidence: `build/test-work/profile-hook-accounting/evidence/results.json`, six
numbered `.cpuprofile` files and `hook-accounting.jsonl`; command output is in
`build/logs/profiling-hook-accounting.log`. The first run timed out on a redundant
`disconnect` request after all six snapshots were saved and `terminated` arrived.
The probe now closes its own process directly at that point. No user game or
recording was modified. No production code change or plugin rebuild was needed.

### Flame graph palette and color-cache fix — 2026-10-20

The former three-color palette had two problems: Lua categories were similar
browns in dark mode, and every anonymous `FlameChartColorScheme` shared the same
prefix. IntelliJ 262 caches produced colors in `UIManager` under that prefix,
so the first frame could supply the background for unrelated frames as well.

FDE now prepares stable mod/file palettes once during import. Registered absolute
mod paths, file URIs and `__mod__/file` paths share their mod-relative identity;
function names, line numbers and recording order do not change colors. A bounded
15-hue, seven-shade palette uses a distinct cache key for each entry, avoiding
both color collisions between palette entries and unlimited global cache growth.
The palette can repeat between different mods/files. Root/pause, hooks, runtime
events and native calls have separate styles. Hover text identifies the mod and
category, so color is not the sole identifier.

Foreground contrast is calculated against each frame's own background, including
theme switches and hover states. Matched search results retain their palette;
IntelliJ's nonmatch dimming and focus border remain. No source or project lookup
occurs during painting, and raw frames, aggregation and saved recordings are
unchanged.

All **eight profiling platform tests pass**, with no failures, errors or skips.
The new regressions cover path aliases, moved mod roots, reordered recordings,
distinct palette entries despite IntelliJ's color cache, category separation,
at least 4.5:1 normal/hover text contrast in both themes, and search states.
Existing importer, native viewer and source-navigation tests also pass. Evidence:
`build/test-results/profiling-colors-platform/`. Visually inspected both palette
previews: `build/reports/ui/profiling-colors-light.png` and
`build/reports/ui/profiling-colors-dark.png`.

Rebuilt and checked the same uncommitted **0.9.0-dev** feature: ZIP/JAR integrity,
manifest version and packaged palette classes verified. Archive copied from
`build/distributions/` to flat `../plugins/`. SHA-256:
`b6954ab34eb3487c0ae0c83b1ea8f1e961df6e29312630354d563eaf30f18aa0`.
No upstream changes or builds, dependency-pin changes, commit or push.

### Optional hook timing events — 2026-10-20 (superseded)

The checkbox described in this historical validation receipt has been removed.
FDE now always disables hook timing events for IDE-started recordings.

Added the project-level **Include hook timing events** checkbox, defaulting to
off. Every IDE-initiated recording reads the current setting and sends
`show_hook_events` in `startProfile` arguments. `stopProfile` remains unchanged.
Lua-initiated recordings continue to use the options supplied by the Lua caller.

All **four targeted IntelliJ tests pass**, with no failures, errors or skips:
settings staging/reset/apply/XML persistence; two manual intervals with the
setting changed between them; and automatic Profile launches with the option
disabled and enabled. Tests inspect the actual outgoing DAP arguments. Evidence:
`build/test-results/profiling-hook-events-platform/`; settings screenshot:
`build/reports/ui/settings.png`.

The disposable native Factorio **2.1.23** probe using
`--while-running --show-hook-events` captured 26 frames and 884 timing entries,
including 16 hook frames with call, line and return names. The previous default
recording fixture contains no hook frames. Protocol and snapshot evidence:
`build/test-work/profile-hooks/evidence/` (snapshot
`374325581426958.cpuprofile`). The user's game and recordings were not modified.

Rebuilt the same uncommitted **0.9.0-dev** feature, verified ZIP/JAR integrity,
manifest version and packaged option/UI/request code, then copied the archive
to flat `../plugins/`. SHA-256:
`10329f23bf91d8a36266110b110b5c02ca4062586c92627563d7c35ef9aa1434`.
No upstream source or dependency changes, upstream plugin rebuild, commit or push.

### Source names for unnamed frames — 2026-10-20

The user's snapshot `368244680209291.cpuprofile` records `(unknown)` for
`dispatcher.lua:201` and `dispatcher.lua:311`, despite providing accurate source
locations. Those declarations are `DispatcherDispatchTrains` and `OnTick`.
FDE previously displayed the recorder's placeholder unchanged.

During profile import, FDE now resolves each relevant source once and collects
function declarations under a read action. The exact `function` token line is
used, so attached documentation comments and enclosing functions cannot supply
an unrelated name. Anonymous closures count toward same-line ambiguity. Only
immutable display strings reach the renderers; graph painting does no source
lookup. Source-derived names are used consistently for graph/tree/list labels,
search names and navigation labels, while raw frames retain their identity.
The lookup uses current source, not a verified copy of the source at recording
time; it cannot detect a different declaration later placed on the same line.

All **six profiling platform tests pass**, with no failures, errors or skips.
The worker-thread regression covers 18 label cases: named local/global functions,
dot/colon methods, anonymous closures, ambiguous lines, comments, strings, body
lines, out-of-range/missing sources, empty names, and preservation of recorded,
native and tail-call names. It also checks rendering, full names, navigation
labels and unchanged frame identity. Evidence:
`build/test-results/profiling-source-names-platform/`.

Rebuilt the same uncommitted **0.9.0-dev** feature, checked ZIP/JAR integrity,
version and packaged name-resolution code, and copied the archive from
`build/distributions/` to flat `../plugins/`. SHA-256:
`bed2132c5f5a400d23725de7a7fc87bb0e1e42e444401def4964d854a0c3c5f7`.
No upstream code/dependency changes, upstream plugin rebuild, commit or push.

### Background source navigation fix — 2026-10-20

The user successfully started and stopped profiling at a breakpoint outside
early-game startup. This supports keeping the earlier native adapter stall
investigation deferred; it does not establish its exact cause.

Clicking a captured Lua frame then exposed an independent FDE bug: the common
Profiler calls `calcNavigatables` on a background worker without a read action,
but FDE called `PsiManager.findFile` directly. The original navigation test ran
on the UI thread and missed that contract.

FDE now uses short read actions for PSI lookup, the later line-offset callback,
and project-model access to module dependency settings. Disk/archive discovery
stays outside the read actions; navigation remains on the UI thread. Lookup
also checks project/file validity, and the offset callback checks PSI validity.
This follows the platform's [threading model](https://plugins.jetbrains.com/docs/intellij/threading-model.html).

The new regression test reproduced the user's read-access assertion before
the fix. It resolves a mod-relative frame and reads its offset on a worker
without an ambient read lock, then verifies editor navigation to the expected
file and line on the UI thread. All **five profiling platform tests pass**,
including the native Profiler views and the viewer-unavailable path.
Evidence: `build/test-results/profiling-navigation-before/` and
`build/test-results/profiling-navigation-platform/`.

The change remains part of the uncommitted **0.9.0-dev** profiling feature.
Rebuilt and integrity-checked `build/distributions/intellij-factorio-0.9.0-dev.zip`,
verified the packaged read-action code and version, and copied it to flat
`../plugins/`. SHA-256:
`31753b0e86ce6b87096c57f1c1a1f5cdba51eda2e7e9a23300e6993a56ee8ab9`.
Existing snapshots can be reopened after installing this build; another native
capture is unnecessary. No dependency changes, upstream plugin rebuild, commit
or push.

### Deferred manual start investigation — 2026-10-20

User decision on **2026-10-20**: shelve this investigation and its separate
bug report while the author addresses other reported early-game debug-adapter
issues. A shared cause is possible but unconfirmed. Preserve the evidence below
and retest after those upstream fixes before deciding whether to resume this
investigation or submit a report. No automatic monitoring is scheduled.

The user reported both recording actions disabled with the ten-second
"Waiting for Factorio to start Lua profiling" notice after starting from Debug.
This is the session's `STARTING` state: the request was initiated, but the
session has not processed `profileRunning`. A successful request response
alone does not complete the transition. The initial probes did not reproduce
the report; the later live capture below established a native debugger stall.
No production-code change or rebuild was made for it.

- Native Factorio 2.1.23 successfully starts/stops recording after resuming the
  disposable loaded-save fixture. Evidence: `build/logs/profiling-start-running-native.log`
  and `build/test-work/protocol/evidence/profile-running.jsonl`.
- A native main-menu probe also succeeds, receiving the start response and
  `profileRunning` in approximately 20 ms. Evidence:
  `build/logs/profiling-start-menu-native.log` and
  `build/test-work/protocol/evidence/profile-menu.jsonl`.
- Corrected the mock to send `profileRunning` without a body, exactly as the
  native adapter does. The real IDE runner handles it with protocol tracing
  both enabled and disabled; both targeted tests pass. Results:
  `build/test-results/profiling-start-platform/`.
- A read-only live IDE thread dump showed its DAP reader waiting for incoming
  data, without a profiling callback blocked on a lock. The original run had
  protocol tracing disabled. The IDE later opened a new snapshot at 14:53:33;
  that alone does not establish whether the original session recovered or was
  replaced. The user ended the debug session during the investigation.

When retesting after the upstream fixes, use the existing **LTN with DAP Log** configuration
with **Enable DAP logging** and **Save DAP log to file** enabled before launch.
The console prints the JSONL path. Capture the session while the start is still
pending so `startProfile`, its response, and `profileRunning` can be correlated.
Do not treat a missing event as proof that recording started or send duplicate
commands to guess the native recorder's state.

#### Live capture: native debugger stalled before profiling

The user reproduced the pending start with protocol logging at 14:59–15:01.
The live trace and read-only thread samples establish that Factorio's native
DAP thread had stopped answering requests before `startProfile` was sent.
This is **not a lost `profileRunning` event or a slow profile save**. The exact
trigger of the native wait has not been isolated, and no fix is claimed.

Evidence, retained under `build/investigations/profiling-start/`:

- `user-session.jsonl`, copied from
  `LTN_with_DAP_Log-20261020-145931-043-12419099931321543615.jsonl`.
- `factorio-live.sample`: all 918 samples of the native `DebugAdapter` thread
  are in an atomic wait below `DebugAdapter::LuaDebugAdapter::update()` at
  `LuaDebugAdapter.cpp:160`. The main/game threads continue running.
- `ide-live-threads.txt`: the IDE's DAP reader waits for incoming data;
  no profiling callback is blocked on a Java lock.

The trace contains a breakpoint at `LogisticTrainNetwork/script/init.lua:425`
on thread 37, successful `threads`, `stackTrace` and `scopes` responses, then
a `continued` event. Requests 10–13 (`variables`) and 14–15 (`evaluate` watches)
receive no responses. Request 16 (`startProfile`) is followed by request 17
(clear breakpoints) and request 18 (`continue`); none receives a response or
profiling event. The last normal game output was emitted after the unanswered
inspection requests. The user reports using Resume in IntelliJ; the trace's
earlier `continued` event has no preceding logged `continue` request, so its
origin remains unexplained.

Control probes used a separate save/mod fixture and plain Python DAP transport:

- Inspection at an `on_configuration_changed` breakpoint followed by profiling
  succeeds. That probe subsequently reproduces the previously observed native
  shutdown hang, with the same atomic-wait stack. It is **not** a fully passing
  end-to-end run. Evidence: `build/logs/profiling-config-start.log` and
  `build/test-work/profiling-config-start/evidence/`.
- Explicit Continue followed by requests for the old variable/frame references
  produces normal `invalid variablesReference` / `Cannot evaluate here` errors,
  then profiling and shutdown succeed. Thus stale inspection alone has **not**
  reproduced the live stall. Evidence: `build/logs/profiling-resumed-inspection.log`
  and `build/test-work/profiling-resumed-inspection/evidence/`.

Suggested native bug-report title: **DAP command processing hangs after a
breakpoint resume; game continues but inspection, continue and startProfile
requests never receive responses**. Environment: Factorio **2.1.23 build 87750**,
macOS ARM64, native `--dap`, IntelliJ IDEA **2026.2.3**, FDE **0.9.0-dev** and
LSP4IJ **0.21.1-SNAPSHOT-12**. Expected: requests complete or return an error,
including inspection requests racing with resume. Actual: the native adapter
remains in the atomic wait at line 160 and stops consuming subsequent requests.
The trace and native sample are the key attachments; a minimal reproducer for
the live-game trigger is still missing. No report has been submitted.

An IDE timeout cannot unblock this native thread. Restarting the game/debug
session is needed to obtain a fresh adapter. **Profile with Factorio Lua**
avoids installing debug breakpoints and passed the separate capture tests;
this is an alternative capture path, not a fix for the debugger stall. Continue
to stop profiling explicitly before quitting. The existing ZIP remains current.

### Profile-directory chooser follow-up — 2026-10-20

The same **0.9.0-dev** feature now remembers the saved/opened snapshot directory
in project workspace settings and preselects it in the Open Profile dialog.
First-use discovery chooses the newest recording among standard Factorio data
directories; the actual user's `factorio_21/script-output` path was verified.
Missing remembered folders fall back to discovery. Filesystem discovery runs
off the UI thread. Custom output folders are remembered from actual saved or
opened snapshot paths; no game shutdown workaround was added.

All four profiling platform tests pass, including remembering the saved folder
without the optional viewer. Results: `build/test-results/profiling-directory-platform/`.
Discovery evidence: `build/logs/profiling-directory-discovery.log`.
Rebuilt and integrity-checked the ZIP, copied to flat `../plugins/` with SHA-256:
`807db5d344db1350d54a1f26cf834d18b3abd5fb90e0db69fc68f73d2cc6cc56`.
Earlier parser/shutdown validation below remains applicable. No dependency
change, commit or push.

### Large-recording and shutdown follow-up — 2026-10-20

Rebuilt the same uncommitted **0.9.0-dev** feature and replaced the installation
ZIP in flat `../plugins/`; the repository archive is retained in
`build/distributions/`. SHA-256:
`baade8140355705341b4ebc335a356b7a4fca687ad72b82b80ab5a30e717e28b`.

- **78 unit tests pass**, one opt-in native debugger test skipped. Coverage
  includes 20,000,001 timing entries, reordered/duplicate/mismatched arrays, a
  save pending beyond ten seconds, useful errors without exception messages,
  and reporting an unsaved exit once. Results:
  `build/test-results/profiling-large-unit/`.
- **24 IntelliJ tests pass**, none skipped: Profiler (four), shutdown (fourteen),
  restart (one), REPL (five). A twelve-second save completes before IDE Stop
  sends `terminate`; an unresponsive recorder reaches the two-minute fallback.
  Game-initiated termination sends no late `stopProfile` or `terminate` request.
  Results: `build/test-results/profiling-large-platform/`.
- Parsed the user's intact **1,849,910,374-byte** snapshot with a **512 MB heap**:
  42,849 frames, 96.717136148 seconds, 20.483 seconds parse time. Read-only probe:
  `build/logs/profiling-user-large-snapshot.log`. The file can be reopened after
  installing this build; it does not need to be recorded again.
- ZIP integrity, version and packaged classes checked. No new IDE API or
  dependency changes; the initial Plugin Verifier result below still describes
  the integration's compatibility limitations. No upstream plugin rebuild,
  commit, push or bug-report submission.

Automatic saving on in-game Quit is **not fixed**: the native adapter no longer
answers recorder requests during shutdown. The report below documents the
reproduction and proposed native fix. FDE's separate false timeout and entry
limit are fixed, and IDE Stop now allows an active save up to two minutes.

### Initial build validation — 2026-10-20

Built `intellij-factorio-0.9.0-dev.zip` and copied it to `../plugins/`.
SHA-256: `1f22b10c2e3169b09ea1693dec552170113fa09e16a5be5895403080d05749d3`.
Dependencies: EmmyLua2 `0.30.0-185`, LSP4IJ `0.21.1-SNAPSHOT-12`, pinned
FMTK `11419a3`. No upstream source changes or analyzer rebuild were needed.

- 74 unit tests passed; the opt-in native debugger acceptance test is skipped
  in the normal suite. Results: `build/test-results/profiling-unit/`.
- 22 IntelliJ tests passed, covering profiling (four), Stop (twelve), restart
  (one), and REPL (five). Results: `build/test-results/profiling-platform/`.
  Five final menu/executor tests also passed with the packaged executor; results
  are in `build/test-results/profiling-executor-final/`.
- Native Factorio 2.1.23 recording probe passed, saved a real snapshot and exited
  cleanly. Logs: `build/logs/profiling-native-acceptance.log` and
  `build/logs/profiling-native-on-launch.log`. The automatic-start probe captured
  10,290 startup frames; the 68.6 MB snapshot parsed in 857 ms. Its parser log is
  `build/logs/profiling-on-launch-parser.log`.
- A 70 MB startup snapshot with 9,965 frames and 1,373,212 timing entries parsed
  in 841 ms with a 512 MB heap. Log: `build/logs/profiling-large-snapshot.log`.
- Plugin Verifier reports binary compatibility with IDEA `262.10968.63`.
  Its Gradle task fails the existing internal-API policy: eight uses in
  `FactorioServerManager`, `FactorioProblemsPanel`, `FreshLocaleCompletion` and
  `Toolkit`. None involve the profiling implementation. The new executor filter
  adds one use of LSP4IJ's existing experimental DAP factory API. Reports are in
  `build/reports/pluginVerifier/IU-262.10968.63/`.

An additional run of the older opt-in native debugger acceptance test found a
shutdown hang after its breakpoint/step/evaluation sequence. It reproduced with
profiling disabled; that existing test and production shutdown behavior were
left unchanged. Factorio reaches `Goodbye`, then its main thread waits for the
native DebugAdapter thread during destruction. Evidence is in
`build/test-work/dap-protocol/session-9823902972142707478/shutdown.sample` and
`build/logs/build-20261020-140436-87050.log` (profiling-disabled reproduction).
This is separate from the passing standalone capture and IDE lifecycle tests.

## Native Factorio issue: active profile lost on normal quit

Suggested bug-report title: **Active Lua debug profile is lost when quitting Factorio normally**.

Environment: Factorio 2.1.23 (build 87750), macOS ARM64, native DAP (`--dap`).

1. Launch Factorio through a DAP client.
2. Send `startProfile` and wait for `profileRunning`.
3. Load a save and let it run.
4. Quit inside Factorio without first sending `stopProfile`.

Actual: the game exits normally, but the active recording is lost. There is no
new `.cpuprofile` and no `profileComplete` event. Expected: normal shutdown should
finish and save the active recorder, emit `profileComplete` with its path, then
emit `terminated`.

The user observed this with in-game Quit. A separate standalone DAP probe also
reproduced it by sending `terminate` while recording, causing Factorio to log
`Quitting: user-quit.`. The probe kept stdin/stdout open and did not send
`disconnect` before requesting the snapshot. This excludes IntelliJ's disconnect
or process cleanup as the cause. Attempting `stopProfile` after `terminated`
received no response before EOF. A second probe sent `stopProfile` immediately
after the earlier `Quitting: user-quit.` output; it also received no response
before EOF. Evidence: `build/logs/profiling-after-terminated.log`,
`build/logs/profiling-on-quit-output.log` and corresponding JSONL traces under
`build/test-work/protocol/evidence/`.

There is no usable client-side opportunity to save after the game initiates
shutdown. The native shutdown sequence should flush an active recorder itself.
FDE now avoids sending futile recorder requests after `terminated` and reports
unsaved recordings when the connection ends. **Automatic saving on in-game Quit
remains unresolved in Factorio.** This is separate from FDE's corrected slow-save
timeout and large-recording parser limit. No bug report has been submitted.
