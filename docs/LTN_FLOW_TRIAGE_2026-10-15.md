# LogisticTrainNetwork flow fixes and diagnostic triage

Task and local dates: 2026-10-15 (America/Los_Angeles). Validation logs use
2026-10-16 in UTC.
Original source: `~/factorio/mods_21/LogisticTrainNetwork`. Actual Lua directory is
`script/`, singular. Original mod files were read only. A fixed copy, generated
Factorio 2.1.21 definitions from FDE 0.8.5, and identical server configuration were
used for before/after comparisons under
`emmylua-analyzer-rust/build/investigations/ltn-flow-20261015/`.

## Delivery

Install both ZIPs from the workspace `plugins/` directory:

- `IntelliJ-EmmyLua2-0.30.0-168-IDEA262-patched-modules.zip`
- `intellij-factorio-0.8.6-dev.zip`

The analyzer work pin is `19f594561ee476f888b044269f0f976df67f6dd0`.
Standalone tips: UP-071 `70df73bf`, UP-072 `d47ab845`, UP-073 `dd6db84a`,
UP-074 `8d1db76d`. All are signed and integrated. Emmy packaging: `88b64bb`.
FDE's UI feature commit is `6881ebb`; its subsequent pin/report commit is listed
in FIX_INVENTORY.md. These are local new commits; the earlier requested pushes
were completed before implementation.
Normal UP-045 scheduling remains enabled; LSP4IJ patched4 is unchanged. No new PRs
were created. Existing outstanding branches were committed/pushed first. The new
fixes and packaging commits are local pending a push request.

## Fixes and dependencies

- **UP-071**, `fix/stable-index-assignment-flow`: track assignments through stable
  nested/global paths and aliased dictionary types. Preserve the declared element
  type after `map[key] = map[key] or {}`. Do not carry the fact across changed keys,
  roots, indexed writes, callbacks, joins, or loops. Depends on UP-029 and UP-060
  (which includes UP-033/059); based on upstream main `96545f45` plus those patches.
- **UP-072**, `fix/computed-class-field-guards`: allow computed guards that select
  declared class fields, such as `prototypes[type]`. Explicit `__index` handlers
  remain excluded. Depends on UP-060; based on the same upstream main.
- **UP-073**, `fix/optional-field-condition-diagnostics`: use flow-sensitive types
  and preserve optional named-field contracts when checking conditions. Unknown
  and open-table values remain uncertain. Independent branch on `96545f45`.
- **UP-074**, `fix/loop-exit-assignment-flow`: include loop-body and break paths,
  plus zero iterations when possible, after generic/numeric for and while loops.
  Previously the post-loop flow often returned the pre-loop state. Independent
  branch on `96545f45`. Cache empty-path traces through acyclic loop summaries;
  actual cycles and nonempty guard/cast paths retain conservative traversal.
  Eight sequential loops took 13,121 uncached declaration traces before the
  cache adjustment and 33 after. This measures trace work, not total runtime.
- Work integration: preserve inline casts when UP-061 widens loop-entry values;
  let computed guards use optional named-field contracts so guards in large
  functions do not disappear when the older generic traversal hits its budget.
  The new regression retains a warning when its explicit nil guard is removed.
- FDE `fix/problem-severity-summary`, integrated into main: count and filter
  **Information** and **Hints** explicitly in the Factorio Problems view.

The independent optional-field/loop bugs reproduce on current upstream. The
computed-path fixes extend existing internal guards/assignment tracking. This
turn did not bisect the historical pre-refactor anchors; it does not establish
which older contribution first introduced any underlying defect.

## Requested locations

| Location | Outcome |
| --- | --- |
| `surface-interface.lua:68–69` | Initialized nested dictionary stays nonnullable; hover is `table<ltn.EntityPairKey,ltn.SurfaceConnection>`. |
| `train-events.lua` | False always-true/falsy diagnostics removed; all diagnostics cleared in the captured file. |
| `tools.lua:142` | Computed class-field guard now protects the nested prototype lookup. |
| `tools.lua:282` | Initialization fact retained. |
| `tools.lua:284` | Callback boundary remains conservative: `cap_function()` can mutate the global table. Hold the initialized table in a local variable to retain a definite reference; do not remove callback invalidation globally. |
| `stop-update.lua:599,606` | Initialized inventory entries no longer flagged as nullable. Separate numeric value/contract warnings remain. |
| `stop-update.lua:348` | No nullable-train diagnostic reproduced. The actual warning is optional `signal_name` passed to `get_item_count(ItemFilter)`. The preceding train guard is honored. Validate/assert the signal name. |
| `request_processor.lua` | Preserve the established clean baseline. A transient new optional-field warning during integration was caught and fixed before delivery. |

## Why 105 did not equal 91

The baseline has **0 errors, 91 warnings, 0 information, 14 hints**. The 14 hints
are 11 unused implicit `self` parameters in `schedule.lua`, one unused
`duplicateName` in `stop-events.lua`, and two preferred-local-alias suggestions
in `stop-events.lua`. They were included by **All severities** but omitted from
the old summary text. FDE 0.8.6-dev exposes their counts and dedicated filters.

## Measured final result

**105 → 89 diagnostics: 91 → 75 warnings, unchanged 14 hints, zero errors and
zero information.** Seventeen prior warnings disappeared and one valid redundant
check after an assert became visible (`stop-update.lua:414`).

| File | Before | After | Warnings | Hints |
| --- | ---: | ---: | ---: | ---: |
| `script/cache.lua` | 6 | 6 | 6 | 0 |
| `script/schedule.lua` | 26 | 26 | 15 | 11 |
| `script/settings.lua` | 35 | 35 | 35 | 0 |
| `script/stop-events.lua` | 6 | 5 | 2 | 3 |
| `script/stop-update.lua` | 19 | 13 | 13 | 0 |
| `script/surface-interface.lua` | 1 | 0 | 0 | 0 |
| `script/tools.lua` | 6 | 4 | 4 | 0 |
| `script/train-events.lua` | 6 | 0 | 0 | 0 |

All other scanned Lua files have no diagnostics, including `request_processor.lua`.
These are fresh raw-LSP results from the packaged arm64 analyzer, not a claim
that the user's already-running IDE refreshed or installed automatically.

## Remaining diagnostic triage

These are source-and-contract assessments, not claims that every remaining
analyzer issue has a minimized regression yet. Line numbers refer to the saved
source snapshot. Do not indiscriminately suppress the warnings.

### Code or declared-contract issues

- `cache.lua:41–45`: `ltn.Metrics` declares integer-valued fields, while this code
  accesses an undeclared nested `.trains` dictionary. Give aggregate metrics a
  separate declared `trains` field/type and initialize it. A cast on a later
  access does not declare or initialize that field.
- `settings.lua:52–81`: most warnings are optional `settings.global/startup[name]`
  entries dereferenced directly. The analyzer has no manifest-to-settings-key
  guarantee. Assert/check existence or provide a typed access helper. Also review
  `debug_log`: its declaration is nonnullable, but the disabled state assigns nil.
- `schedule.lua:62,67,231,295`: optional schedule owner, locomotive burner and
  current schedule record are accessed without an explicit guarantee.
  `schedule.lua:136`: choosing a random filtered depot needs a nonempty-list
  guarantee. `schedule.lua:232`: `current_fuel` is explicitly annotated nonnullable,
  so its condition is always true under the written contract; annotate `?`.
- `stop-update.lua:348`: optional signal name, as above.
  `stop-update.lua:414`: a preceding `assert(prototypes.item[signal_name])` makes
  this repeat existence test redundant. This is a legitimate newly-visible warning.
- `stop-update.lua:496`: `setLamp` accepts `count: number` but passes it to API
  `int32`; tighten the helper's numeric contract or validate/convert its input.
- `stop-events.lua:158,364`: optional connector lookup / entity unit number.
  Factorio runtime invariants may guarantee these at the call site, but they are
  not expressed by the current generic API types; assert or narrow explicitly.
- `tools.lua:284`: arbitrary callback can invalidate the global lookup. Keep a
  local reference to the initialized capacity map before calling it.
- Hints: unused `self` can be intentionally retained for an OO interface; unused
  `duplicateName` and the two existing local-alias suggestions are ordinary cleanup.

### Analyzer or API-model follow-ups

- `schedule.lua:128,162`: the API returns a union of one-goal/all-goals pathfinder
  results without expressing its correlation with the input options. This causes
  inaccessible result fields/assignment mismatch. `129–130,171`: API result indices
  and supplied goal arrays are not correlated. `222`: structural locomotive-group
  class versus string-keyed dictionary compatibility deserves a minimized test.
  `246`: the branch-assigned fuel key is lost; likely flow analysis.
- `schedule.lua:438`: dynamic `---@cast records[index] -?` is not supported by this
  cast parser; `index` is interpreted as a type. Use a local alias/assertion or add
  explicit computed-cast support. The message should be clearer.
- `settings.lua:52,80,81`: `tonumber` reporting `expected any, found nil` suggests
  an additional call-inference/diagnostic problem beyond the real optional lookup.
  `54`: expected `boolean?` appears to come from the surrounding callback return
  contract, not the assigned `debug_log` field. Needs independent reproduction.
  `85`: `pairs` key/table-membership correlation is missing; iterating the callback
  value directly avoids the repeated optional dictionary lookup.
- `stop-update.lua:89`: writing a child member discards the parent lookup guard
  conservatively. `378`: use of `dispatcher.Deliveries[trainID]` instead of the
  existing `delivery` value crosses calls/writes; membership/alias effects matter.
- `stop-update.lua:415`: local initialized as `0` is treated as an assignment
  target constrained to literal zero. This is an inference defect.
  `425,427,450,599,606`: numeric widening/declared integer aliases need further
  isolation; the earlier nil-entry errors are fixed. Some inputs are declared
  `number` (including shipment amounts), so review those contracts before assuming
  every integer warning is false.
- `stop-update.lua:546`: the analyzer does not prove bounds of a forward/reversed
  carriage loop. `tools.lua:522`: guard fact lost across nested iteration/calls;
  retaining the selected subtable in a local would express the guarantee.
- `tools.lua:207`: inferred array insertion expects `Any?` but rejects
  `LocalisedString`; likely contextual/recursive-alias inference.
  `tools.lua:577`: `next(stops)` is treated as always truthy despite a potentially
  empty dictionary; generic iterator return optionality needs investigation.

## Validation and evidence

The inventory links exact standalone/work hashes. Before/after failures and
full-suite logs are in each checkout's `build/logs/`. Full protocol captures and
JSON diagnostics are under the investigation directory. No original mod edits,
IDE plugin installation, external PR comments, or new PRs were performed.

### Final checks

| Target | Tests passed | Failures |
| --- | ---: | ---: |
| UP-071 standalone, all features | 1,486 | 0 |
| UP-072 standalone, all features | 1,481 | 0 |
| UP-073 standalone, all features | 1,471 | 0 |
| UP-074 standalone, all features | 1,473 | 0 |
| Combined analyzer, all features | 1,758 | 0 |
| Combined language server, all features | 267 | 0 |
| FDE unit suite | 64 (one existing skip) | 0 |
| Native IntelliJ Problems/API/LocalisedString tests | 4 | 0 |

Clippy with all targets/features and -Dwarnings and rustfmt passed for the
standalone fixes and combined build. Native tests validate the severity summary
and filters, isolated generated APIs, and LocalisedString arrays/optional fields.
The final packaged full-mod protocol scan completed in 44.816 seconds, including
initialization and definition loading. This is a single local measurement.
The initial integration warning in `request_processor.lua` is absent from the
final package. The Problems UI was tested natively, not visually in the user's IDE.

Both descriptors and archive copies were checked. The packaged arm64 analyzer
matches the prepared release binary and pin, and its archive executable mode is
0755. Only macOS arm64 includes the custom analyzer. Original distribution ZIPs
remain in their repositories, with identical copies directly under `plugins/`.

- `IntelliJ-EmmyLua2-0.30.0-168-IDEA262-patched-modules.zip` SHA-256: `8947a88d85c5e0bd9abbb80a01b379b9cd5f065d713287c165ade2fa6f7daca3`
- `intellij-factorio-0.8.6-dev.zip` SHA-256: `febdaf00d42fdc593808e34675b1ba25111bbcfc1c0ecc22e7b282b29747a73a`
