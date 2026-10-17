# Implementation follow-up (2026-10-17)

The requested fixes are now signed on independent branches based on freshly
fetched upstream/main `d823745fa70646964bb06e1e0108499cc2a0174e`:

- **UP-088**, `fix/contextual-callback-parameters`, `4cedbc3a`: infer closure
  parameters from a dictionary's declared value signature and an annotated local
  function initializer. Follow plain/generic aliases, validate the dictionary key,
  and preserve explicit parameter annotations. Six targeted regressions cover
  aliased/inline/generic callbacks, body checking, mismatched keys and alias cycles.
  All 1,525 standalone analysis tests and analysis Clippy pass.
- **UP-089**, `fix/table-field-hover-context`, `aaae6fcf`: two overly broad uses
  of a table field's enclosing range leaked context into its value/body. Match
  annotation attachment to the actual member key; use constructor hover context
  only when the cursor is on that key. Callback member assignments/reads retain
  their own field type. Two facts regressions and three hover regressions are
  included; all 1,521 standalone analysis and 226 server tests and Clippy pass.

Both reproduce independently on upstream/main. UP-089 does not depend on UP-088:
individually annotated callbacks already reproduce it. They are also independent
of UP-080/087. No historical provenance claim is made from current upstream alone.
The work branch picks are `c56755b4` and `dd91889d`; signed cherry-picks avoid
importing upstream's unrelated base-type refactor. Existing work-specific
write-context lookup and nested constructor support are preserved.

The earlier outstanding work was clean, committed and pushed first: FDE main
`6641cf0`, analyzer UP-080 `33e7abe3`, UP-086 `76e288ed`, UP-087 `a8c897fe`, work
`c31e503d`, EmmyLua2 `a6490ff`. Six origin heads were directly verified; active
LSP4IJ/toolkit branches already matched. Historical archive/backup refs remain
local. No PR status was changed or newly checked.

Completed: signed analyzer work **d69ba527115d65f2bd5ca2eabb56913223655c7a**,
signed EmmyLua2 packaging **309d248**, **IntelliJ-EmmyLua2-0.30.0-178.zip**.
New fixes/integration/package commits stay local. Pre-existing outstanding work
was pushed first. No PR or user IDE installation.

### Final validation and package

- **2,897 combined tests pass**: 1,857 analysis, 298 server, 490 formatter,
  191 parser, 51 parser-description, 10 documentation CLI. No failures/skips;
  empty binary/doc-test targets are not counted.
- Workspace Clippy (all targets/features, warnings denied) and rustfmt pass.
- Packaged LSP replay checks all six minimal callback cases, including invalid
  assignments/calls. All **29 callbacks** in the updated actual settings.lua have
  correct parameters (87 declaration/body-read hovers). Member hovers show
  message_level: integer, message_include_gps: boolean, debug_log: number,
  including individually annotated entries. No duplicate fixture diagnostics.
- Both forced native IntelliJ tests pass: generated API/separate workspaces and
  asymmetric API attributes. Fresh XML: 2 tests, 0 failures/errors/skips,
  timestamp `2026-10-17T22:16:40.036Z`.
- Descriptor version, embedded analyzer pin, identical archive/prepared/release
  server, and Unix executable modes 0755 are verified.

Install `plugins/IntelliJ-EmmyLua2-0.30.0-178.zip`; original retained in the EmmyLua2
repository's build/distributions. ZIP SHA-256:
`815661e90e75252cf1e1877d68c3ce74f46c71c95afe1af8115b9394b80b1586`.
arm64 server SHA-256:
`2193303bc4e7409b0f76801a8e96b01b532757837f42efcc845f22844e4e5178`.
Only macOS arm64 has the custom server. FDE stays **0.8.6-dev**, existing ZIP
unchanged; only its development analyzer pin/receipt changes. LSP4IJ stays
**0.21.1-SNAPSHOT-7**, with no source/build changes.

### Newly visible diagnostics

The frozen full LTN fixture changes **79→104 diagnostics**, in only two files.
request_processor.lua retains its previous zero-warning baseline.

- `script/settings.lua`: **34→58**. Known callback parameters restore assignment
  checks. ModSetting.value has the broad API union Color/double/int32/boolean/
  string; assigning it to integer/boolean fields needs more precise type
  information. tonumber returns number?, which does not satisfy required
  integer/enum fields, and debug_log is declared number but assigned nil when
  disabled. These warnings follow the declarations, not evidence of runtime
  failure. Per-setting API typing or appropriate casts/assertions is separate work.
- `script/stop-update.lua:17`: **0→1**, `if signal.count then` is always truthy for
  its now-inferred required integer count. Lua zero is truthy; nearby handlers
  explicitly check `signal.count > 0`. This newly exposed warning is valid.

The updated user file, with two per-entry annotations, independently changes
**36→58** diagnostics. Its line numbers differ from the older full-module copy.
In the full scan, 25 new assignment messages replace one incorrect inferred
boolean?/number contract message; the numeric guard yields a net increase of 25.
Original mods remain untouched.

Logs: analyzer work build/logs/contextual-callback-workspace-{tests,clippy,fmt}.log;
Emmy worktree build/logs/contextual-callback-build178.log; FDE
build/logs/contextual-callback-build178-platform.log and fresh test XML.
Callback evidence includes build178-minimal.json, build178-current.json,
build178-verification.json, current-source-receipt.json. Full-module evidence:
analyzer build/investigations/nullable-connector-arguments-20261017/
build178-real-diagnostics.json and build178-diagnostic-delta.json.
Function-keyword hover remains Lua keyword help; parameter/member inference and
hover are the fixes here. Original mod files are read-only inputs.

Evidence: analyzer `build/investigations/contextual-setting-callbacks-20261017/`.
The user added individual @type annotations to two entries during this task;
`current-source/` freezes that updated file. Use the corrected before replay
`build177-annotation-corrected.json`; the earlier annotation replay used stale
workspace/package roots and is not acceptance evidence.

---

# Settings callback parameter inference investigation

2026-10-17, investigation only. No fix branch/ID, source changes, rebuild of an
installable plugin, push, PR or installation. Existing work/fix trees were clean.

## Report and reproduction

Current file: `~/factorio/mods_21/LogisticTrainNetwork/script/settings.lua`.
Lines50/52 declare `ltn.SettingFunction` as
`fun(ltn_settings: ltn.Settings, name: string)` and the constructor as
`table<string, ltn.SettingFunction>`. Lines54/55 contain anonymous functions.

Raw LSP replay with packaged EmmyLua2 build177 (work c31e503d) confirms both
parameter declarations and reads in those bodies hover as unknown. Hovering the
function keyword returns generic Lua keyword help. Fresh upstream/main remains
d823745f; the six minimal cases below were also run against a freshly built
unmodified upstream debug server. This is not introduced by UP-080/087. No
historical refactor checkpoints were requested or tested in this investigation.

| Context | Build177 parameters | Current upstream parameters |
| --- | --- | --- |
| Dictionary with function alias value | unknown / unknown | unknown / unknown |
| Dictionary with inline function signature | unknown / unknown | unknown / unknown |
| Named class field with the same alias | Settings / string | Settings / string |
| Individual constructor entry annotated with the alias | Settings / string | Settings / string |
| Local function variable annotated with the alias | unknown / unknown | unknown / unknown |
| Later assignment to a typed dictionary entry | unknown / unknown | unknown / unknown |

The minimal callback deliberately assigns a string to Settings.count:integer
and passes name to an integer parameter. Only the correctly typed class-field
and individually annotated cases diagnose both errors. This establishes a real
inference gap rather than just incorrect hover text. A separate known upstream
inject-field diagnostic appears for the later dictionary assignment; build177's
existing patches remove that diagnostic but do not restore contextual parameters.

## Cause

`semantic_model/types.rs` asks call-context inference first, then
`expected_member_param_for_closure` in `semantic_model/signature.rs`.
`closure_param_vm` only reconstructs an enclosing call, so it has no context for
this standalone constructor. `expected_member_signatures_for_closure` can recover
the constructor's declared owner type and expand a function alias, but selects
signatures via `member_infos_with_key_all`. That enumerates declared members;
it does not retrieve the value contract for a matching dictionary key. Thus the
outer table type is known, but its callback signature never reaches the closure
parameters. Inlining the alias produces the same failure. A directly annotated
local function exposes a related missing closure-assignment context path.

Separately, `handlers/hover/mod.rs` handles keywords before semantic hover.
Hovering the literal `function` therefore returns fixed language help, even in
the class-field cases where parameter inference is correct. Showing the callback
signature or its alias there would be an additional hover enhancement; recovering
parameter types alone will not change that keyword behavior.

## Repair direction / workaround

Supply closure signatures from their expected assignment/initializer type:
for dictionary entries, match the key and extract the declared value signature;
for directly annotated function variables, use the variable annotation.
Resolve aliases through the existing signature pipeline and respect explicit
parameter annotations, generics, overloads and conflicting dictionary keys.
Use the initialization/write contract rather than a nullable dictionary read.
The resulting type should feed body analysis, hover and completion consistently.
This belongs to contextual function inference, separate from UP-080/087's hover
presentation fixes. No implementation scope or new patch ID is assigned yet.

A tested temporary workaround is `---@type ltn.SettingFunction` immediately
above each constructor entry (or explicit `---@param` annotations). The original
mod was not edited.

## Evidence and state

Evidence/probe script: analyzer
`build/investigations/contextual-setting-callbacks-20261017/`.
The real input and isolated variant files, complete LSP responses, logs and
verification.json are retained. Case definitions are shared in one types.lua to
avoid duplicate definitions contaminating the comparison.

Analyzer work c31e503d, EmmyLua2 a6490ff, FDE6641cf0, both development pins,
prepared binary and ZIP177 remain unchanged. The shared **debug** executable is
now built from upstream d823745f; rebuild before treating it as a work binary.
The release/prepared/installable analyzer is still build177. Inventory counts
and PR status verification dates are unchanged.
