# Missing nullable connector argument diagnostic

Investigation and implementation: 2026-10-17. Fix **UP-086**,
`fix/nullable-required-arguments`, integrated into `work/intellij-modules`.
The reproduction below describes the initial build175 baseline.

## Reproduction

Current input is `~/factorio/mods_21/LogisticTrainNetwork/script/stop-events.lua`.
Line 158 asserts the input connector exists; line 160 passes a separate lookup
from `lampctrl_wire_connectors` into `connect_to`. The module enables
`strict.tableIndex`; only `unnecessary-assert` is disabled. The generated API
correctly declares `target: LuaWireConnector` (required).

Packaged EmmyLua2 0.30.0-175 (analyzer work 9f1b230a) emits no diagnostic at
that call. Capturing the exact argument lookup in a temporary local in a copied
fixture confirms its inferred type is `LuaWireConnector?`. The same call with
literal nil or a string correctly emits param-type-mismatch. The call signature
is therefore known; this is not missing API data or the hover/self heuristic.
Original mod files were not edited.

A minimal fixture also reproduces the gap with an ordinary function accepting a
required named class, and an explicitly annotated nullable class argument:

```lua
---@class Connector
---@param target Connector
local function required(target) end
---@param target Connector?
local function example(target)
    required(target) -- missing warning
    required(nil)   -- correctly warns
end
```

The explicit-nullable case reproduces both on build175 and freshly fetched
upstream/main d823745fa70646964bb06e1e0108499cc2a0174e. Upstream advanced from
ef6c06a7 by one commit, `refactor(type): optimize base types`; no local branches
were rebased. Upstream lacks UP-016's dictionary-read nullability, so explicit
nullable arguments are the independent upstream reproduction.

## Cause and proposed repair

`check/checker/param_type_check.rs` uses `param_generic_base_match` as an
acceptance path. Its fallback calls general `is_compatible`. In
`semantic_model/type_check/mod.rs`, `fast_eq_check` explicitly treats
`Nullable(Ref(T))` as equal to `Ref(T)`. That succeeds before stricter union
checking, even in assignment mode. General compatibility also accepts a source
union when only one alternative matches.

Required argument checks must validate every possible source alternative against
the parameter contract. Simply switching a helper to assignment mode is not
sufficient while the nullable-reference shortcut remains ahead of those checks.
The repair should preserve cast semantics where intentional and cover required
classes, optional contracts, aliases, generics, overloads and guarded values.
UP-079 fixed full optional parameter contracts; UP-041 handles named union
parameters. Neither covers this required named-class case. UP-016 correctly makes
the dictionary read nullable and exposes the gap; UP-084/085 are presentation-only
changes and do not modify argument checking.

This establishes a current-upstream defect, not its historical origin. The three
historical checkpoints were not run in this investigation.

## Evidence

Analyzer `build/investigations/nullable-connector-arguments-20261017/`:
- `probe.py`, `minimal/probe.lua`: minimized cases and protocol runner.
- `build175.json`, `upstream-d823745f.json`: independent comparison.
- `real_probe.py`, `source/`: copied LTN input with actual module configuration.
- `real175.json`: actual-call signature, diagnostics and controlled argument variants.
- `evidence/*.jsonl`: protocol captures; `upstream-build.log`: upstream build.

An additional upstream-only coverage gap appears for a wrong string argument
through the computed receiver `left[1].connect_to`; the combined work branch
already diagnoses it. It does not explain the reported work-build failure,
which reproduces with a plain function and a named receiver.

## Implementation scope: UP-086

`fix/nullable-required-arguments` is based directly on upstream/main d823745f.
Signed initial fix 38d309c6 is applied to the internal work branch with signed pick
31d29cf8 (`-x` provenance). The use-site flow follow-up `76e288ed` is applied
as signed pick `30e8aa46`; final combined tests/handoff commit `09a9f266`.
This avoids importing the unrelated base-type
refactor into the established ef6c06a7 work baseline. Integration retains UP-079
alongside the new required-parameter check; no analyzer scheduling change.

The patch rejects nil alternatives in nullable **class** arguments before the
legacy overlap shortcuts. It covers class aliases, instantiated generic classes,
ordinary and method calls, callable-union overloads, forwarded pcall arguments,
variadic slots and explicit generic arguments. Optional parameters and legitimate
nullable generic inference remain accepted. General compatibility/cast semantics
are unchanged. UP-016 supplies the strict dictionary-read nullability in the
original example; it is complementary, not a standalone prerequisite. The work
branch adds a test combining both patches and checking independent connector guards.

The parameter checker consults use-site flow before rejecting a nullable cached
member type. This preserves `assert(table[key])`, branch guards and inline casts;
invalidating writes are covered by a negative test. An initial package replay
caught the missing guard consultation before delivery; build176 was refreshed.

A broader prototype that checked nil for every argument type exposed unrelated
upstream flow gaps in primitive/callback cases. It was not retained: callback
initialization, discriminant sibling-field flow and legacy call-signature collection
need separate evaluation before changing general union acceptance. UP-086 is
intentionally a class-contract fix; it does not claim comprehensive nil-safety for
all primitive, function or structural-table arguments.

Before this task, all active worktrees were clean. Existing unpushed fixes,
comparison branches and analyzer/EmmyLua2/FDE work commits were pushed to origin
and verified (86 checked active/fix/work/comparison branches matched). Local
backup/archive refs were preserved. No upstream PR was opened.

## Delivery and validation

Installable package: `plugins/IntelliJ-EmmyLua2-0.30.0-176.zip`, with its original
under `upstream/Intellij-EmmyLua2/build/distributions/`. The custom analyzer is
macOS arm64 only. Analyzer work/pin: `09a9f266920f2279193841aef7bf6955db8ac59e`;
EmmyLua2 work: `d92ee69`. Keep FDE 0.8.6-dev and LSP4IJ 0.21.1-SNAPSHOT-7.
FDE's development analyzer pin is updated; it does not bundle this server and
needs no new distribution for an analyzer-only correction. Normal UP-045/UP-082
remain enabled; the UP-008 comparison archives are unchanged.

Validation evidence lives under analyzer
`build/investigations/nullable-connector-arguments-20261017/` and each worktree's
`build/logs/`. The standalone fix has 11 focused tests and passes all 1,531
analysis tests with all features, Clippy with warnings denied, and rustfmt.
The combined focused suite passes 27 tests. All **2,873 combined workspace tests**
pass: analysis 1,849, formatter 490, server 282, parser 191, parser descriptions 51
and miscellaneous 10. Empty test binaries/doctest targets are not counted.
Workspace Clippy (all targets/features, warnings denied) and rustfmt pass.
The final runs force source recompilation when switching worktrees: the shared
Cargo target briefly reused a standalone test binary in an intermediate work
run. Final logs explicitly name the work checkout and include its extra tests;
the stale intermediate run is not counted as validation.
Both final native IntelliJ tests passed (2 executed, 0 skipped); the package
identity/permission checks and packaged replay also pass. ZIP hashes are below.

The release replay uses a frozen copy of the mod with its actual API libraries
and configuration. It verifies the following:

- The original call at stop-events.lua160 produces `expected LuaWireConnector
  but found LuaWireConnector?` on the lamp connector argument.
- Capturing that lookup in a local preserves the nullable type and warning.
- Asserting the actual lamp lookup removes the warning. Asserting only the
  input connector does not prove that the lamp connector exists.
- Literal nil and wrong-string controls still warn.
- The connect_to hover retains documentation, dot notation and all three
  arguments from UP-084/085.

Frozen-module scan: **64 → 79 diagnostics**, no existing diagnostics removed.
The 15 additions are required-parameter nil checks that were previously missing:

| File | Lines | Contract exposed |
| --- | --- | --- |
| prototypes/entities.lua | 35, 56, 97 | Nullable prototype lookup passed to required table |
| prototypes/items.lua | 9, 14, 21, 30, 48 | Nullable item prototype lookup passed to required table |
| prototypes/recipes.lua | 9, 23 | Nullable recipe prototype lookup passed to required table |
| script/alert.lua | 28 | Nullable icon lookup passed to required SignalID |
| script/init.lua | 190 | Nullable wire connector passed to required connector |
| script/schedule.lua | 62, 67 | Optional train/platform owner passed to required LuaTrain |
| script/stop-events.lua | 160 | Reported nullable connector argument |

These are consistent with the declared contracts: dictionary types do not
promise that these particular keys exist, and schedule.owner permits nil and
LuaSpacePlatform. Some built-in prototype names may be guaranteed by game/mod
knowledge beyond those types; an assertion or suitable refinement expresses
that guarantee. No mod code was changed. The already-guarded parked train call
at stop-update.lua273 and schedule record at schedule.lua440 remain clear.

All task source/package/pin changes are committed with signatures and DCO.
The newly created UP-086 branch and resulting work commits remain local.
No upstream PR was created and no plugin was installed into the user's IDE.

Final archive validation checks the descriptor version, embedded analyzer pin,
release/prepared/archive server identity and Unix executable modes (`0755`).
The packaged replay (`final176.json`, `final176-diagnostics.json`) matches the
validated release replay exactly.

- ZIP SHA-256: `00c5e445f578175e21bc902e194bf14658dd211b2e938b0afc0eb100a65cba30`
- macOS arm64 server SHA-256: `a6a236008c9a2a0f067f9f40fb9e221f83658f4781982f127f48707b5e5edd6a`

The final native IntelliJ checks cover generated API isolation in separate mod
workspaces and asymmetric API attributes. Their forced-run XML receipt is saved
in FDE `build/investigations/up086-nullable-arguments/`. A forced rerun is needed
when replacing the external prepared server, which Gradle otherwise treats as
unrelated to the already up-to-date test output.
