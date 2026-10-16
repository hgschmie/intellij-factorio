# LTN follow-up priorities and fixes — 2026-10-16

This revisits the “Analyzer or API-model follow-ups” in
[the original triage](LTN_FLOW_TRIAGE_2026-10-15.md). Original mods were read only.
The same frozen LTN source and Factorio 2.1.21 library/configuration are retained
for the before/after package comparison.

## Priority order

1. **Inferred assignment contracts — fixed as UP-076.** `stop-update.lua:415`
   assigns a computed number to `stack_count`, initialized with `0`. Checking a
   later write against the exact inferred zero is wrong. This is a general
   inference defect, not an enum/API annotation problem. Mutable unannotated
   locals now accept new values of their primitive type; numeric literals allow
   fractional numeric values. Explicit annotations/aliases, inline `@as`, named
   `@cast` restrictions, const/iterator declarations and literal read precision
   are preserved. The fix runs before path casts, so it cannot widen away a
   later explicit cast.
2. **Aliased dictionary keys — fixed as UP-075.** `schedule.lua:246` keeps the
   correct `ltn.ItemIdentifier` key type. The checker failed to expand this string
   alias when validating `table<string, number>`. This is not lost loop/branch
   flow as initially suspected. Aliases/chains/unions now work for dictionary
   keys and string index signatures; incompatible dictionary keys and cyclic
   aliases remain checked. It benefits any domain-specific string ID alias.
3. **Callback/contextual assignments and built-in calls — next investigation.**
   The `settings.lua` warnings that expect a callback return type on a field
   assignment, or reject nil as `any`, deserve independent fixes if isolated.
   Simple `tonumber(nil)` and a simple callback field write both pass on current
   upstream and work. The actual settings lookup/union context must be retained
   in a smaller reproduction. Do not suppress the separate legitimate optional
   lookup warnings or the nonnullable `debug_log` declaration.
4. **Recursive array insertion — next investigation.** `tools.lua:207` should be
   checked with the complete `LuaObject`/`Any`/`LocalisedString` definitions.
   A smaller recursive string/number/boolean/nil/array alias already works on both
   trees, so this is more specific than generic recursive-array incompatibility.
   Capital `Any` is a Factorio alias, not the built-in analyzer `any` type.
5. **Precision of guard invalidation — correctness work, with caution.** A write
   to an unrelated child should not automatically erase a safe parent lookup.
   Conversely, arbitrary calls and writes can invalidate dictionary membership.
   Isolate `stop-update.lua:89` first; the call/alias cases at 378 and
   `tools.lua:522` require proof that the guarded lookup is still the same value.
6. **API result-shape and structural compatibility modeling.** Minimize
   `schedule.lua:222` separately. Pathfinder one-goal/all-goals output types
   correlated with input options are better expressed by API overloads; bounds
   correlations between result indices and goal arrays need richer analysis.
7. **Language/analysis enhancements, lower priority.** Dynamic casts such as
   `records[index] -?`, correlated `pairs` key membership and loop bounds are
   useful features, but not reasons to remove sound conservative diagnostics.
   The dynamic-cast parser error can also be improved independently.

**Already fixed upstream in the minimal reproduction:** `if next(stops) then`
no longer incorrectly appears always truthy on fetched main `ca9bec3b`; it still
fails on our older work baseline. The fetched commits contain substantial
union/variadic inference changes. Do not create another patch for this without
first refreshing and testing that upstream stack. These two targeted fixes do
not import that larger rewrite, so build169 does not claim to fix `next()`.

## Branches, provenance and integration

| ID | Standalone branch | Signed commit | Work pick |
| --- | --- | --- | --- |
| UP-075 | `fix/string-alias-index-keys` | `a0d5b4db` | `2f1174f0` |
| UP-076 | `fix/inferred-literal-assignment-contracts` | `f360eb96` | `9a0696aa` |

Both standalone branches use freshly fetched upstream/main
`ca9bec3b30f44f29554a90ec33e73a7479e65315` and require no other pending patch.
Both reported defects reproduce there and on the previous work baseline; this
establishes they are not unique to the internal stack, not their historical
origin. No historical contribution bisect was requested/performed.

UP-075 complements UP-042's dynamic class-member aliases. The work integration
consolidates their helper and preserves its existing recursion-depth bound.
UP-076 complements UP-061 (loop-carried read types), but does not depend on it.
Integration uses signed provenance cherry-picks to avoid importing the unrelated
new upstream rewrite. Work pin: `d6a763941c80d17046b4356d7e7caa243ba48073`.

A negative regression exposed an older work-only limitation: an integer alias
used on a string-indexed class can be accepted by the member resolver before
UndefinedField runs. This reproduced with the existing packaged build168. The
standalone negative test checks both class and generic dictionary cases; the
work test keeps the dictionary control and records the class limitation. It was
not introduced or hidden by the new key-expansion code. Recheck it during the
next upstream refresh; the current standalone branch correctly rejects it.

## Validation and delivery

Each standalone branch passes all **1,521 analysis tests** with all features,
Clippy with all targets/features and `-D warnings`, and rustfmt. Three tests per
fix cover the changed behavior and controls. The positive regressions fail
before the changes. Read-only packaged baseline probes also captured the
pre-existing class-key limitation.

Combined validation passes **1,764 analysis +267 server tests**, with all
features, Clippy and rustfmt. FDE unit tests pass (64 passed, one existing skip);
both native IntelliJ API/LocalisedString integration tests pass.

Packaged full-mod comparison: **89 → 86 diagnostics**, **75 → 72 warnings**,
unchanged 14 hints, zero errors/information. No new diagnostics. Two UndefinedField
warnings on the read/write of `fuel[key]` at `schedule.lua:246` disappear; the
incorrect literal-zero assignment warning at `stop-update.lua:415` disappears.
`request_processor.lua`, `train-events.lua` and `surface-interface.lua` remain
clear. The scan took 43.450 seconds including startup/library loading (one local
measurement). Remaining settings, recursive-array and next warnings are unchanged.

Install **IntelliJ-EmmyLua2-0.30.0-169-IDEA262-patched-modules.zip** directly from
workspace `plugins/`; the archive also remains in EmmyLua2 build/distributions.
Only macOS arm64 receives the patched analyzer. The descriptor, pinned binary
identity and executable0755 modes passed package validation. Archive SHA-256:
`b9e02e528d792b5bcbc1e236813c5f2d09f0df2f1cd00240fa45fb7ca0031020`.

Only EmmyLua2 was rebuilt. FDE retains 0.8.6-dev and its existing ZIP, with its
development analyzer pin updated. LSP4IJ patched4/normal UP-045 remain unchanged.
EmmyLua2 packaging commit: `9c2e7d5` on work/patched-build. The two fix branches
and work integration are signed; signatures verified. No branches pushed, PRs
created, original mods edited or plugins installed.

Evidence: analyzer `build/priority-*-before.log`, `build/priority-*-after.log`,
standalone checkout `build/logs/`, integration checkout
`build/logs/priority-followups-*.log`,
`build/investigations/priority-followups-20261016/`, and the shared frozen-mod
probe under `build/investigations/ltn-flow-20261015/`.
