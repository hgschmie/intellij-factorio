# LTN stop-update fixes and package naming — 2026-10-16

Analyzer work head: `3584adf440832a79863659e363f5d5f199ed3d08` on upstream `ef6c06a7`.
All existing contributions remain, plus UP-079/080/081. These changes are local;
no new PRs, pushes or changes to the original mod files.

## Findings and fixes

| Reported issue | Cause | Fix |
| --- | --- | --- |
| parked_train hover remains LuaTrain? after the guard | Flow inference already narrows correctly, but hover renders the declaration contract | UP-080 uses the member's read type at the cursor, preserving assignment-target contracts |
| get_item_count rejects string? at 348 | Optional-parameter checking selects a non-nil target too early; the nullable argument must be checked against the full optional union | UP-079 accepts string? for ItemFilter? while rejecting incompatible alternatives such as boolean? |
| get_item_count hover only says function | Nullable receiver has no resolved member identity for documentation | UP-080 recovers the sole non-nil receiver's member identity for presentation; flow resolution stays unchanged |
| Deliveries[trainID] may be nil at 378 | No flow fact connects a standard iterator's key to an existing entry | UP-081 tracks pairs/ipairs entry existence until a visible conflicting write/call, key change or collection replacement |

The API generator is correct here. Factorio 2.1.21 runtime-api.json defines
LuaTrain.get_item_count's ItemFilter parameter as optional. The generated
`---@param item? ItemFilter` accepts string or nil; no caller cast is required.
The earlier LTN_FLOW_TRIAGE_2026-10-15.md classification of348 as a Lua issue was
incorrect and is explicitly corrected.

The iterator patch uses the shared stable access/effect model from UP-078,
including the user-selected policy of preserving facts across opaque calls.
It rejects shadowed/custom iterators, known iteration metamethods, closure-boundary
reads and visible conflicting effects. An assignment to an entry's child does
not remove that entry. Extra integration coverage runs with strict.tableIndex
both enabled and disabled. This complements explicit condition/assignment guards.

## Provenance

Checked the three fixed historical anchors with the same minimal same-file and
cross-file reproductions:

| Symptom | Before refactor aaaca684 | Immediately after 3474c99d | Before first merged contribution 1c8589bb | Current upstream ef6c06a7 |
| --- | --- | --- | --- | --- |
| Optional nullable argument accepted | Yes | No | No | No |
| Guarded field hover narrowed | Yes | No | No | No |
| Nullable receiver method documentation | Yes | No | No | No |
| Standard iterator establishes array entry existence | No | No | No | No |

The first three are upstream refactor regressions predating our contributions.
Iterator entry tracking was missing earlier too. After the refactor, nullable
map reads sometimes lose nil even outside loops, so a quiet dictionary-only
reproducer would give a misleading result. Array controls warn inside and outside
the loop at all historical anchors. UP-016's strict dictionary reads make this
old missing flow fact visible for Deliveries; it is not a lost explicit guard.

## Branches and dependencies

| ID | Contribution branch | Source commit(s) | Work integration | Dependency |
| --- | --- | --- | --- | --- |
| UP-079 | fix/optional-alias-parameters | 581343c6 | daba333a | Independent; complements UP-041 |
| UP-080 | fix/member-use-site-hover | 52a11989, dbe5f0ec | aa399255, f230e7cc | Independent; preserves UP-063 and optional-field guards |
| UP-081 | fix/iterator-entry-guards | 96f8d38d | 10df24b4 +3584adf4 | UP-078 and its transitive prerequisites; UP-016 for strict dictionary integration |

UP-081's source branch was amended for a Clippy-only test comparison after the
work pick; work integration includes that correction. All commits are signed.
Existing PRs 1286/1292/1296 remain open and ready for review (GitHub verified today).
There are now 57 active analyzer contribution branches: three PRs and 54 without.

## Validation

- Optional-parameter standalone: 1,521 analysis tests pass, including three new
  regressions and incompatible nullable/union/generic controls.
- Hover standalone: 1,518 analysis and 230 server tests pass. The final presentation
  refinement passes all 230 server tests and combined analysis coverage.
- Iterator standalone: 1,550 analysis tests pass; the separately completed long
  assignment stress test is the sole filtered test in the final fast rerun.
- Combined: 2,844 workspace/all-feature tests pass, none failed/skipped/filtered,
  including 1,831 analysis and 271 server tests and the long stress test.
- Workspace/all-target/all-feature Clippy with warnings denied and rustfmt pass.
- The initial general-member-resolver approach caused a guard regression in the
  combined suite. It was replaced by a presentation-only identity fallback;
  both that guard test and request_processor.lua are clear again.

Real LSP replay uses the same copied current mod and generated APIs for build171
and the new release binary. stop-update.lua improves 10→8 warnings:348 and378 are
removed. At348 parked_train hovers as LuaTrain; get_item_count shows
`function LuaTrain.get_item_count(item: ItemFilter?) -> uint32`, its description,
parameter documentation and Factorio API link. Before the guard it remains LuaTrain?.
request_processor.lua has zero warnings in both the baseline and final build.

The eight remaining stop-update warnings are unchanged: number/integer aliases
at 425/427/450/496/599/606 and two carriages[i] nil warnings at 546. This task does
not claim that the whole file or mod is warning-free. Original files remain
untouched. No interactive IDE installation was performed.

Evidence: `emmylua-analyzer-rust/build/investigations/stop-update-20261016/`.
Historical logs, isolated tests, full suites and raw-LSP traces are retained there.

## Packages

EmmyLua2 is now `0.30.0-172`; build.gradle.kts and the build script no longer add
IDEA262/patched-modules suffixes. LSP4IJ is `0.21.1-6`, using separate
pluginVersion and pluginBuild properties. Count 6 continues the previous patched5
sequence. LSP4IJ also includes the other thread's completed diagnostic-routing
review fix 08475429 plus its Javadoc updates; no contribution branches were edited
by this packaging task.

FDE remains 0.8.6-dev; its development analyzer/LSP pins change, while its existing
ZIP remains unchanged. Only macOS arm64 contains the custom analyzer; the other
bundled platform servers retain the upstream binaries.

## Final installation receipt

- EmmyLua2 work `cf99251`; version `0.30.0-172`.
  ZIP SHA256: `e8488f586fd77643aa76629590532b8e26c4311f45a156d57a3195ef00e3f791`.
  Arm64 binary SHA256: `a4ee64efd5ca93c8430467c916c2e0a43f25c960d4f445e7ac7f8a83a6296350`.
- LSP4IJ work `c7e22f69`; version `0.21.1-6`.
  ZIP SHA256: `b9934a737f46f871c7aae3ed8d6ea9a7b7ec269d3f8c2164d2eccf1a9f6b99bd`.
- Both originals remain in their repository build/distributions folders;
  installation copies are directly under workspace-root plugins/.
- Embedded plugin versions, ZIP integrity, analyzer lock and binary identity
  verified; Unix server entries and extracted arm64 executable retain 0755.
  The packaged server's LSP results match the tested release exactly.
- FDE ZIP remains unchanged at 0.8.6-dev, SHA256
  `febdaf00d42fdc593808e34675b1ba25111bbcfc1c0ecc22e7b282b29747a73a`.
- Plugin validation: 30 Emmy routing/client tests, 54 LSP4IJ diagnostic/lifecycle
  tests, 64 FDE unit tests plus one existing skipped case, and all eight focused
  native IDE tests pass against the final prepared dependencies.

The broader 18-case native run passed 15 cases. Two real-mod tests require an
explicit -PrealMods fixture and failed their assumptions when it was absent.
The remaining unsaved-dependency test times out at its final disk-replacement
step (PerModServersTest.java:363). An isolated replay with the original build 171
and patched5 packages fails at the same step, confirming this is pre-existing.
Earlier unsaved-buffer delivery assertions pass. Keep this disk-refresh issue
as a separate follow-up; it was not changed or masked in this task. The final
focused eight-case suite matches the previous release's acceptance coverage.
Initial/baseline/final XML reports are archived under FDE
build/investigations/stop-update-20261016/.
