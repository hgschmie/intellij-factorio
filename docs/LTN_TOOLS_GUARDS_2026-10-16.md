# LTN tools.lua guard fixes — 2026-10-16

The current `script/tools.lua` is clear: the packaged build170 server reports
zero diagnostics, compared with three warnings from build169 on identical input.
The original mod was read only. Its recent `LocalisedString[]` annotation edit
was retained in the new fixture; it was not part of this patch.

## Why the earlier fixes did not cover these cases

| Location | Cause | Resolution |
| --- | --- | --- |
| tools.lua:284 | UP-029/071's definite-write fact was discarded at `cap_function()`. The analyzer required every intervening call to be absent, even after the parent table had been initialized. | UP-078 shares effect handling between assignments and guards. Opaque calls preserve facts, as explicitly chosen by the user. |
| tools.lua:522 | UP-033/060/072 stopped at calls, indexed writes and loop boundaries. Deleting a child entry was treated as potentially deleting its guarded parent. Recursive array inference could also lose the element contract at the loop/write site. | UP-078 checks the relevant effects, preserves parent facts across child writes and safe loops, and uses the array contract when recursive inference is unknown. |
| tools.lua:577 | Truthiness checked the entire multiple-return pack from `next()`, rather than its first result. | UP-077 backports the small already-upstream correction from 0bc11af2. |

The first two are extensions of the existing computed-access patches, not three
unrelated application workarounds. UP-078 is a separate follow-up branch so that
existing contribution branches are not rewritten. The `next()` issue is separate;
its upstream fix had not reached our older work baseline.

## Branches, integration and dependencies

- UP-077: `fix/condition-first-result-backport`, signed `b78a0b61`.
  Based on work d6a76394, work pick `db7e8d02`. Backports only the condition
  predicates from upstream `0bc11af2328fec936282a9da853a898bcee0d713`, plus two
  regressions. Already upstream; do not submit another PR. Drop the backport
  when incorporating the original upstream implementation.
- UP-078: `fix/computed-guard-effect-boundaries`, signed `ca4dec98`.
  Fresh upstream/main `ef6c06a79f635cc014028539519fffd74e6f24da`, plus signed
  prerequisite picks UP-033 (45138d6b), UP-059 (06ee63d4), UP-060 (ba1e22f1),
  UP-029 (12e9e0c4), UP-071 (4ca66d98), UP-072 (cf226503), UP-061 (7a449aba).
  Direct prerequisites are UP-071/072/061; the others are transitive groundwork.
  Preserve the existing submission order: UP-059 before the combined guard series.
- Work pick `bcee9563` preserves UP-073's optional-field handling. Signed
  follow-up `0f6001bb` updates strict-dictionary expectations and the handoff.
  Final analyzer pin: `0f6001bb5bdb7c64abd7e918e1dff1d676805df9`.

Integration uses signed provenance cherry-picks rather than importing the whole
new upstream union/variadic rewrite. No other fix branches were rewritten.
All new commits remain local: no push, new PR or IDE installation.

## Effect policy and limits

Opaque calls retain guards by explicit user direction. This is a pragmatic flow
policy, not a purity guarantee. Visible local-closure writes, known mutators
(`rawset`, `setmetatable`, table mutations), root/key/parent replacement and
uncertain alias writes still invalidate facts. Closure inspection is bounded;
merely defining a closure does not execute its writes. Guards do not cross
unproven joins or function boundaries.

A child write preserves its parent container. Direct writes to a fresh unescaped
local output table are independent; tables passed elsewhere or aliased are not
assumed independent. Loop checks include later writes that could have executed
on an earlier iteration. Static member accesses retain their native
assignment-aware precision rather than reverting to the declared collection
contract. Full interprocedural effect/alias analysis is outside this patch.

## Validation

- Four new effect-boundary test groups cover the callback pattern, nested loops,
  child writes, globals, independent output tables and conflicting mutations.
  Initial positive cases failed before the patch; mutation controls pass after.
- Two first-result regressions cover optional/boolean results and constant first
  results. The optional-result case failed before the backport.
- Standalone UP-078: **1,547 analysis tests** pass with all features, Clippy
  all-targets/all-features with `-D warnings`, and rustfmt.
- Combined work: **1,770 analysis tests and 267 server tests** pass with all
  features, Clippy and rustfmt. Existing strict-dictionary tests deliberately
  change their opaque-call expectation; mutation/alias rejection remains tested.
- FDE: **64 unit tests pass**, with one existing skipped test. Both native
  generated API/separate-workspace and LocalisedString checks pass (2/2).
- Current LTN source copy: build169 reports exactly tools.lua284/522/577;
  the packaged build170 reports **zero diagnostics**. Capacity hover remains
  `number`. The two release runs took about 19 seconds including initialization;
  this is a smoke comparison, not a benchmark.
- Identical older frozen full-mod snapshot: **86 → 82 diagnostics**,
  **72 → 68 warnings**, unchanged **14 hints**, **zero additions**. The fourth
  removed warning is stop-update.lua89: clearing one child field no longer loses
  the parent guard before clearing another. `request_processor.lua` stays clear.
  Full-scan time is about43 seconds in both release builds.

The frozen snapshot still has its older `LocalisedString` annotation in tools.lua;
its unrelated warning there remains. The current copy has the user's corrected
`LocalisedString[]` annotation and is entirely clear. An exploratory unoptimized
server probe exceeded its180-second deadline; accepted protocol validation uses
the optimized server that is actually packaged.

Evidence (workspace-relative):

- Standalone `upstream/emmylua-analyzer-rust/build/checkouts/guard-effect-boundaries/build/logs/`:
  before-effects.log, all-analysis-final.log, clippy-final.log.
- Work `upstream/emmylua-analyzer-rust/build/checkouts/intellij-work/build/logs/`:
  build170-combined-final.log, build170-table-index.log, build170-clippy.log.
- Current-input protocol/diagnostics/hover captures:
  `upstream/emmylua-analyzer-rust/build/investigations/tools-guards-20261016/`
  before-build169 and after-packaged170.
- Frozen full scan: `upstream/emmylua-analyzer-rust/build/investigations/ltn-flow-20261015/`
  after-build169 and after-build170. Comparisons include full ranges/messages,
  so repeated diagnostics on a line are not collapsed.
- FDE build/logs/build170-unit.log and build170-native.log; preserved unit XML
  under build/test-results/build170-unit/.

## Installation artifact

`plugins/IntelliJ-EmmyLua2-0.30.0-170-IDEA262-patched-modules.zip`

SHA256: `3bc8c043cd24f575cce4996e2bfdfdcdee6c9785736f48c8a6f1bdbb3875d7b1`.
EmmyLua2 packaging commit: signed `aa7e4b6` on `work/patched-build`.
Original ZIP retained in upstream/Intellij-EmmyLua2/build/distributions/ and
its development worktree build/distributions/. Descriptor version, analyzer pin,
release/prepared/archive binary identity and Unix0755 permissions were verified.
Only macOS arm64 contains the locally patched analyzer.

Install **only EmmyLua2 build170**. Keep FDE **0.8.6-dev**, LSP4IJ patched4 and
normal UP-045. FDE's development pin was updated; its version and ZIP are unchanged.
