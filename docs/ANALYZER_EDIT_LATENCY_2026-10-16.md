# LTN edit latency — UP-082 (2026-10-16)

## Cause and observed behavior

Read-only source: `~/factorio/mods_21/LogisticTrainNetwork/script/stop-update.lua`.
The installed IDE had EmmyLua2 0.30.0-172-UP-008, FDE 0.8.6-dev and
LSP4IJ 0.21.1-SNAPSHOT-7. A saved copy of all 37 Lua files and the actual managed
Factorio configuration/API libraries reproduces the issue without IntelliJ.

Changing `---@param count number` at 482 to `integer` clears the `int32` mismatch
at 496, but one diagnostic pass previously took about 15 seconds. In the intermediate
`---@param count i` state, the analyzer reports 19 diagnostics, including the
`expected i` warnings from the screenshot. These describe the incomplete edit;
the expensive pass prevents newer results from replacing them promptly.
The original `number`→`int32` mismatch is valid: `number` includes fractions.
Original mod files and the running IDE were not modified.

CPU sampling isolates recursive `walk_member`/`trace_member` calls. Nested branch
merges revisit the same predecessor states exponentially. The pure-linear-branch
shortcut cannot collapse nested branches. Existing declaration memoization does
not cover member tracing. Diagnostic analysis holds a shared analysis lock, so
long passes can also postpone edits and later hover/completion requests.

This is an analyzer bottleneck, independent of UP-008 versus UP-045. The exact
15-second delay reproduces with only document diagnostic requests, without a
workspace pull, FDE or LSP4IJ. No LSP4IJ code change is needed for this fix.

## Historical provenance

Same small diagnostic probe, 16 nested branches with an acyclic numeric-loop
summary; debug-build wall times are illustrative, not a cross-machine benchmark:

| Position | Commit | Time |
| --- | --- | --- |
| Before refactor | aaaca684 | 3.6ms |
| Immediately after refactor | 3474c99d | 8,835ms |
| Before our first merged analyzer patch | 1c8589bb | 8,933ms |

All three named tests executed. Without a loop, later upstream's member-position
shortcut masks this tiny example; the loop-inclusive probe exercises the path
used in the real file. Current upstream also fails the member-trace regression.
This defect predates our merged contributions.

## Patch and integration

- UP-082: `fix/member-flow-trace-memoization`, signed `54aaa7b8`, directly on
  upstream/main `ef6c06a7`.
- Cache member trace results by member identity and flow node, only when the
  incoming guard/cast path is empty and the flow graph is acyclic. Keep original
  traversal for nonempty paths and real back edges. Cache lifetime is one semantic
  model; edits rebuild it.
- Reuse the exact iterative cycle detector already in UP-072. Standalone UP-082
  includes this helper, without depending on UP-072's declaration/guard changes.
  Work keeps the helper once. The two optimizations are complementary.
- Normal work: `ec216e69`, including pick `95e1c923`, handoff `bbc815f7` and
  final focused-test alignment `ec216e69`.
- Normal EmmyLua2 work: `f02ef54`, version 0.30.0-173.
- Comparison analyzer: `experiment/up008-build173`, `da77e58e`; code-analysis,
  parser and formatter sources exactly match normal work. Only scheduler/server
  code is replayed from build172's `899bb485`.
- Comparison EmmyLua2: `experiment/up008-build173`, `c18ed9d`, version 173-UP-008.
- All new commits are local; no push, upstream PR, or PR-status change.

## Validation

- Standalone: 1,521 analysis tests; final focused tests include 4/8/32/64 nested
  branches, with and without loop summaries, plus distinct members/casts and
  reversed repeated queries. Disabling the new cache makes the regression fail
  at 8 branches / 2,556 trace steps. The test isolates member tracing from the
  separate declaration cache rather than silently depending on UP-072.
- Combined: 2,846 workspace/all-feature tests (1,833 analysis and 271 server),
  Clippy with warnings denied, rustfmt. Comparison: 273 server tests.
- Native IntelliJ/FDE: 3 tests pass, covering unsaved cross-file diagnostic
  refresh, immediate semantic tokens, and Lua/FMTK Problems aggregation.
- Exact five-state edit replay compares complete diagnostic sets and hovers;
  all match before/after. Warm normal packaged document passes take 347–374ms,
  versus 14,982–15,050ms before. Latest hover is 15–16 ms when no diagnostic is
  already holding the lock. Initial library loading is a separate ~3 s cost.
- With a background workspace pull, fixed document reports took 341–2,022ms in
  this replay. UP-045 still has contention; this is not a promise of instantaneous
  operations. Rapid i→integer edits during an active pass return the correct
  four final diagnostics within 669 ms (hover 323 ms) in the packaged normal replay.
- ZIP validation checks descriptor version, embedded analyzer pin, identical
  release/extracted binary hashes, and 0755 Unix server modes. Only macOS arm64
  contains our custom analyzer. Normal prepared dependencies stay UP-045.

Evidence under analyzer `build/investigations/edit-latency-20261016/` includes
frozen source/configuration, protocol traces/results, CPU sample, historical
probes and validation logs. Full test logs also remain in each producing
checkout's `build/logs`. Installation archives are retained in repository
`build/distributions` and copied flat into workspace `plugins/`.

## Installation

Install either normal `IntelliJ-EmmyLua2-0.30.0-173.zip` or the matching
`IntelliJ-EmmyLua2-0.30.0-173-UP-008.zip`, then restart IntelliJ. Keep
FDE 0.8.6-dev and LSP4IJ 0.21.1-SNAPSHOT-7. FDE's development analyzer pin is
updated without a version bump or new FDE ZIP.

Both archives are now validated in plugins/. The packaged UP-008 replay takes
326–329ms per warm diagnostic pass (hover14–15ms); rapid edits settle within596ms.
All five diagnostic sets and hover responses match the corresponding old build.
All source/packaging signatures were verified. FDE's three native tests passed;
no interactive measurement or automatic installation was performed.

| Archive | SHA-256 |
| --- | --- |
| IntelliJ-EmmyLua2-0.30.0-173.zip | `462b1ebf8c1855e32959af424b336c69a08404b62d5e7f6a71583d1b77a9b2d0` |
| IntelliJ-EmmyLua2-0.30.0-173-UP-008.zip | `6b8f33c33ab911cf80fdc317ba83f2a1f0fdea3ad9d667875ad5789451f03377` |

