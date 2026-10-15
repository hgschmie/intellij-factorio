# Class identity in member values — UP-057

Completed 2026-10-15. Original mod sources and the running IDE installation were not changed.

## Problem and correction

`LogisticTrainNetwork/script/metrics.lua:44` assigns `Metrics.__index = Metrics`.
Current upstream/main and our previous packaged server reported an assignment
mismatch between a recursive anonymous table and `ltn.Metrics`. A three-line
class reproduces it without dictionary inheritance; `M.self = M` does too.

The semantic model already associates class/enum annotations with a runtime
declaration. The low-level `decl_type` query used by member values instead inferred
the `{}` initializer, losing that identity. The 12-line correction adds the same
association there, preserving the qualified type name. Explicit `@type` and module
annotations still take precedence. No field-name exception, diagnostic suppression
or relaxed compatibility rule is involved.

Six regressions cover self/other member references, parenthesized values,
dictionary inheritance, enum identity, namespaces, private/internal scope,
cross-file queries, explicit type priority, incompatible reassignments and
contradictory declared field types. Three initial tests fail before the fix.
An existing inheritance test expected any assignment mismatch and passed solely
because `a.__index = a` warned; it now expects the valid inheritance idiom already
explicitly accepted by the checker. Its neighboring invalid-class test still passes.

## Branches and publication

### Historical origin verified on 2026-10-15

This regression was introduced by upstream commit `3474c99d` (`refactor project`,
CppCXY, 2026-10-08), before any of our analyzer contributions were merged.
The same minimal `---@class Example; local M = {}; M.__index = M` regression
test was compiled and run in a separate detached historical checkout:

| Upstream revision | Result |
| --- | --- |
| `aaaca684`, immediate parent of the refactor | No assignment mismatch; test passes (including dictionary inheritance) |
| `3474c99d`, initial refactor | False assignment mismatch; test fails even without dictionary inheritance |
| `1c8589bb`, immediately before our first merged analyzer PR, #1266 | Same false assignment mismatch |

`git blame` traces the missing class/enum association and initializer fallback
in `semantic_db/query.rs::decl_type` to `3474c99d`. The only subsequent upstream
change in that function before our correction is PR #1266's module lookup;
the reproducer uses no imports. This establishes historical origin rather than
merely reproducing on today's upstream, which already includes our other fixes.
No fix branches, work pins or plugin packages changed during this investigation.
Evidence: `build/investigations/metrics-self-index-20261015/check-history.py`,
`historical-results.json`, and the three `history-*.log` files in the analyzer repo.

### Current integration

| Repository | Branch | Commit/head | Purpose |
| --- | --- | --- | --- |
| Analyzer | `fix/class-member-value-identity` | `d28acce3` | Independent contribution on upstream/main `96545f45` |
| Analyzer | `work/intellij-modules` | `d0097b01` | Signed cherry-pick with provenance; avoids unrelated upstream HashMap refactor |
| Analyzer | `work/intellij-modules` | `cb881b4d` | Integration documentation and final analyzer pin |
| EmmyLua2 | `work/patched-build` | `839a1cd` | Build160 package/pin/documentation |
| FDE | `main` | `b16e7b2` | Development analyzer pin only |

All new commits are signed and have DCO sign-offs. This fix has no pending local
prerequisite; it works with the existing dictionary/read-write patches on the work
branch. These commits were initially local, with no PR. Publication status is maintained
in the workspace inventory at `upstream/FIX_INVENTORY.md`; the earlier publication
report is `upstream/OUTSTANDING_PUSH_2026-10-15.md`.

## Validation

- Standalone analyzer: 1,475 analysis tests with all features (including the stress
  test), Clippy across all targets/features with warnings denied, and formatting pass.
  Final six focused tests also pass after expanding the member-reference coverage.
- Integrated analyzer: 1,679 analysis tests and 257 language-server tests pass.
  Tests regenerate Chinese standard-library resources; those generated changes
  were restored before packaging.
- Packaged server on a fixed copy of the real LTN mod: metrics.lua has one warning
  before and none after. Control, data and request_processor remain free of
  diagnostics. Hover on `__index` now shows `ltn.Metrics`.
- Packaged minimal examples, with/without dictionary inheritance and the explicit
  annotation workaround, report no diagnostics. Unpatched upstream separately
  reproduces the issue. Other upstream metrics warnings at line114 persist on
  the standalone branch; existing work patches already resolve them.
- Two native FDE platform tests pass: unopened-module service startup/closed-file
  diagnostics and native module configuration/VFS reload.
- Archive descriptor, integrity, analyzer pin, binary equality and Unix0755 mode
  verified. The archive copy in plugins/ is byte-identical to the original.

This validates the four stated LTN files, not every diagnostic in the workspace.

## Installation

Install **IntelliJ-EmmyLua2-0.30.0-160-IDEA262-patched-modules.zip** from the flat
workspace `plugins/` folder and restart IntelliJ. Leave the custom server override
empty to use the bundled analyzer. Only macOS arm64 contains this local server fix.

The original archive remains under upstream/Intellij-EmmyLua2/build/distributions.
FDE remains 0.8.3-dev; its existing ZIP is unchanged (SHA-256
`a89ff5ac750b8482e2613605a7b3a2e0fede21a00c95ce1dc5ec39132eb835f7`).
LSP4IJ patched4 is unchanged. The one-off UP-008 comparison remains at build159.

Analyzer pin: `cb881b4d10b9d3f71850b22e0c4c5d32b4f6fd40`.
Archive SHA-256: `e5d58a5aefa4f0dfa5cebcc62878498562306602c9aa90cc6123fa4997075286`.
Analyzer SHA-256: `517e39dd8991abfbbb4f8bd821662457c9ad84ffbc94d1a33e0488488f9d72e7`.

## Evidence

- Analyzer `build/investigations/metrics-self-index-20261015/`: frozen source,
  before/after raw-LSP probes, hover, package.json and preserved platform XML.
- Standalone checkout `build/logs/`: before/after tests, analysis-final.log,
  focused-final.log, clippy.log, fmt.log and server-build.log.
- Analyzer work `build/logs/class-member-identity-combined.log`.
- EmmyLua2 work `build/logs/class-member-identity-package.log`.
- FDE `build/logs/class-member-identity-platform.log`.
