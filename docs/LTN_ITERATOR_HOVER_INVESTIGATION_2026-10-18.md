# Iterator hover and ipairs loop-variable inference

Investigated and implemented 2026-10-18. The initial investigation was read-only;
the user subsequently authorized fixes, work integration and packaging.

The actual source is `~/factorio/mods_21/LogisticTrainNetwork/script/request_processor.lua:583`.
`select_train` accepts `ltn.FreeTrain[]?`; the early return at line565 removes
nil before `pairs(free_trains)` at583. The reported iterator variables should
therefore have types `integer` and `ltn.FreeTrain`.

## Reproduction

Raw LSP requests against packaged EmmyLua2 **0.30.0-179** / analyzer **72e36621**
and freshly fetched/built upstream/main **dd80bd92** reproduce both symptoms:

| Input | Loop variables | Function hover |
| --- | --- | --- |
| `pairs(array)` with `array: Foo[]` | `integer`, `Foo` | Generic types rendered as `unknown` |
| `pairs(dictionary)` with `dictionary: table<string,Foo>` | `string`, `Foo` | Generic types rendered as `unknown` |
| `ipairs(array)` | `unknown`, `unknown` | Value type rendered as `unknown` |

No IDE is involved in these requests. Current-upstream reproduction establishes
that the symptoms exist there too; this investigation did not perform historical
provenance checks or attribute their introduction to a particular contribution.

## Two distinct defects

1. **Call-site hover generic bindings.** `inferred_call_bindings` uses
   `infer_call_with_bindings`, which reruns parameter matching independently
   of ordinary call-result inference. The library parameter is a union containing
   a broad `table` alternative (the projected structural index annotation).
   In our work build, this branch succeeds before a typed branch is tried,
   without binding K/V; the hover layer then replaces unbound generics with
   `unknown`. Upstream's corresponding union matcher rejects this union shape
   outright, also leaving the bindings empty. This is broader than the builtin
   iterator names: a local `T[] | table` generic function reproduces the bad
   hover on build179, whereas `T[] | boolean`, plain `T[]`, and plain
   `table<K,V>` produce correctly specialized hovers. Normal call-result
   inference still obtains `Foo` in those controls.

2. **ipairs loop inference.** `infer_standard_iter_slot` requires at least two
   generic parameters and treats them as K/V. Standard `ipairs` declares only
   `V` because the key is already integer. That helper returns None, and both
   loop variables become unknown. Standard `pairs` declares K/V/I and reaches
   its separate collection-based inference, explaining why its loop variables
   work despite its broken hover. Existing ipairs tests for named classes with
   integer index fields use an earlier shortcut and do not cover plain arrays.

The standard-library `ipairs` stub also describes only the returned iterator
function, omitting the state and initial control value. Its iterator annotation
omits the control argument. That explains its abbreviated hover return signature,
but it is not the cause of the early exit above.

Any implementation should reuse consistent call-site generic specialization for
hover, preserve useful typed matches before broad fallback matches, and remove
the assumption that every standard iterator has two generic parameters. These
are separate regression cases; fixing hover alone will not repair ipairs loops.
Iterator documentation must distinguish the iterator's yielded key/value from
the iterator/state/control triple returned by pairs/ipairs themselves.

## Initial investigation evidence and state

Evidence is under analyzer `build/investigations/iterator-hover-20261018/`:
`source/iterators.lua`, `probe.py`, `build179.json`, expanded/control JSON and
text outputs, `upstream-dd80bd92.json`, `actual-source-excerpt.txt`, and complete
protocol captures in `evidence/`.

A temporary upstream instrumentation test confirmed empty hover bindings
(one test executed successfully). Its patch is archived as `instrumentation.patch`,
and its output is in `build/checkouts/iterator-hover-upstream/build/logs/hover-binding-trace.log`.
The instrumentation was removed; the detached upstream checkout and integration
checkout are clean. The shared debug binary now contains upstream dd80bd92.
The prepared/release analyzer, work branches, development pins and distributed
ZIPs were not changed. No fix ID, commit, push, PR or IDE installation.


## Implementation — UP-091 and UP-092

Both independent branches are based on freshly fetched upstream/main
`dd80bd928f53ec42cc20cb8842020b22327a62a9`:

- **UP-091**, `fix/ipairs-array-inference`, signed `e41571d3`: recognize the
  standard library's one-generic ipairs signature. The key stays integer and
  the value generic is inferred from arrays/integer dictionaries. This treatment
  is restricted to the actual standard-library declaration, preserving custom
  functions named ipairs and their generic semantics.
- **UP-092**, `fix/call-site-collection-generics`, signed `df55badc`: use the
  same full callable candidates for call-site presentation as expression
  inference. Generic collection alternatives are tried transactionally, retaining
  the useful typed match ahead of broad table fallbacks. Failed alternatives
  cannot leak partial bindings. The pairs array overload and both English/Chinese
  iterator annotations describe the iterator/state/control triple accurately.

UP-091 passes 1,524 standalone analysis tests. UP-092 passes 1,525 analysis and
226 server tests. Both pass all-target/all-feature Clippy and rustfmt.
Tests cover guarded optional arrays, integer/string dictionaries, shadowed
iterators, generic-union rollback, rejected incompatible arguments, concrete
hover signatures/documentation and all three iterator-factory return values.

Signed work picks `ba4d368c` and `fd4e6a0d`, plus final return-triple
amendment `dee9b41e`, retain the existing baseline without importing unrelated upstream
refactors. UP-092 overlaps UP-041's union matching code; integration preserves
UP-041's ordinary-union compatibility. Neither new fix requires the other or
UP-041 on its standalone branch. Coordinate the shared matcher if submitting
UP-041/092 upstream. No push or PR is requested.

A fresh read-only copy of the current LTN source was used for delivery testing.
Its build179 baseline has **6 diagnostics, zero in request_processor.lua**.
This differs from the older frozen 104-diagnostic fixture because the user has
since edited the mod. The captured source/configuration are held fixed between
before/after runs. Original mod files remain unchanged.


## Delivery validation

Final analyzer work/pin: `278036c34fe0b274ab54dd1ef0ad3025d9b2dd0b`.
Combined suites pass **2,908 tests**: 1,862 analysis, 304 server, 490 formatter,
191 parser, 51 parser-description and 10 doc-CLI tests. Clippy with all targets
and features and `-D warnings`, plus rustfmt, pass. One formatter timing test
measured 10.38 ms against its 10 ms threshold under parallel execution; the full
304-test server suite passed on a serial rerun without a source change or a
skipped test. The successful other-crate suite completed the workspace coverage.

EmmyLua2 **0.30.0-180** is built and copied into
`plugins/IntelliJ-EmmyLua2-0.30.0-180.zip`, with originals under the Emmy worktree
and shared repository `build/distributions/`. Descriptor version, embedded pin,
release/prepared/archive server identity and Unix0755 modes are verified.
ZIP SHA-256: `9ac2a206914c0742309b7b88869726dd1703905c18cf8d8e1d6a607b73b3ba25`.
Only macOS arm64 includes the custom analyzer. FDE remains **0.8.6-dev**, with
only its development analyzer pin/validation documentation updated; its ZIP and
LSP4IJ **0.21.1-SNAPSHOT-7** are unchanged.

Raw LSP replay of the packaged binary against the fixed actual-source copy
confirms at request_processor.lua583:

| Expression | Function hover iterator yields | Loop key | Loop value |
| --- | --- | --- | --- |
| `pairs(free_trains)` | `integer, ltn.FreeTrain` | `integer` | `ltn.FreeTrain` |
| `ipairs(free_trains)` | `integer, ltn.FreeTrain` | `integer` | `ltn.FreeTrain` |

The minimal string-keyed dictionary hover likewise yields `string, Foo`.
The full current-mod scan is **identical before/after: six diagnostics**, all in
stop-update.lua; **request_processor.lua remains at zero**, including the temporary
ipairs variant. Assertions and full JSON/protocol evidence are in
`build/investigations/iterator-hover-20261018/`: `delivery-validation.json`,
`actual179/180.json`, `actual179/180-diagnostics.json`, `build180.json`, their
text logs and per-run resources/log directories. The temporary ipairs edit exists
only in LSP document memory for the disposable copy; original Lua files are not
modified. No IDE installation, upstream PR or push was requested.


Both forced native IntelliJ integration tests pass against the prepared package:
`PerModServersTest.testAsymmetricApiAttributes` and
`PerModServersTest.testGeneratedApiWithSeparateWorkspaces`. Fresh JUnit XML
records two tests with zero failures, errors or skips (2026-10-19T01:56:33Z,
2026-10-18 local time). Signed EmmyLua2 packaging commit: `7c9a343`.
The source, work and packaging commits remain local; prior remote status/PR
verification dates are unchanged.
