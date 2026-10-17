# LTN nested receiver field contracts — UP-083 (2026-10-17)

## Result

EmmyLua2 **0.30.0-174** removes both reported diagnostics at
`script/stop-update.lua:601` and `:608`. Both the destination and RHS `.count`
hover as `ItemCountType` (an alias of `uint32`, ultimately `integer`). `amount`
remains `uint32`. The copied file has no diagnostics after the fix.

The frozen 37-file LTN scan changes **71 → 69 diagnostics**. Only these two
warnings disappear; no diagnostics are added. `script/request_processor.lua`
remains clear. Original mod files were read-only throughout.

## Cause and correction

Member resolution can select an inferred runtime assignment when the receiver
is initially nullable. Flow inference subsequently knows its declared class,
but the rule preferring that class's field contract was restricted to a plain
name receiver. It skipped nested receivers such as `records[key]` and
`parent.item`. The broad `number` inferred for a self-update could therefore
feed back into its own RHS instead of the declared integer alias.

The correction removes that name-only restriction. It reuses the existing
named/generic receiver rule, without changing arithmetic, nullable collection
semantics or server scheduling. An explicit receiver cast reproduces the lost
field type on current upstream, so this fix is independently reviewable.
The standalone regression fails before the correction and passes afterward.
Historical attribution before our first merged contribution was not requested
or established in this task.

UP-016 exposes the original dictionary entry's nullability; computed-entry
guards establish its non-nil receiver, and UP-040 preserves integer arithmetic
once operand types are correct. Those are complementary to UP-083, not
prerequisites for its standalone branch. UP-082 remains unchanged. Work-only
regressions cover strict.tableIndex off/on, addition/subtraction, rejected
fractional/string assignments, and missing-entry warnings. Standalone tests
cover aliases, generic/optional fields and nullable receivers.

## Branches and validation

- Fresh upstream/main: `ef6c06a79f635cc014028539519fffd74e6f24da`.
- Standalone `fix/nested-member-field-contracts`: signed `7daee751`.
- Work merge: signed `4cddf0ea`; no unrelated upstream changes imported.
- Work integration tests/handoff and analyzer pin: signed
  `d4f2b723b0bc8b58fe74391e93190b9de257a13d`.
- EmmyLua2 `work/patched-build`: signed `9075eb1`.
- Standalone: 1,519 final analysis tests pass; the separately completed full-run
  large-file stress test also passes. Clippy/all targets/features and rustfmt pass.
- Combined: **2,850 workspace/all-feature tests pass**, including 1,837 analysis
  and 271 server tests, with no skipped/filtered tests. Clippy and rustfmt pass.
- Native IntelliJ/FDE: **2 tests pass, none skipped**: generated API across
  separate workspaces and asymmetric read/write API attributes.
- Packaged-server replay validates the actual diagnostic and hover changes.

The first attempted standalone diagnostic negatives exposed an existing upstream
nested-write checker limitation; they failed before and after the source change.
The final independent tests assert the actual field inference and nullable
receiver behavior. Combined tests verify invalid writes through our existing
field-assignment checks rather than weakening those expectations.

Evidence: analyzer `build/investigations/nested-member-contracts-20261017/`,
standalone `build/checkouts/loop-member-numeric/build/logs/`, work
`build/checkouts/intellij-work/build/logs/up083-*`, and FDE
`build/investigations/up083-nested-fields/`.

## Installable artifact

`plugins/IntelliJ-EmmyLua2-0.30.0-174.zip` (also retained in the producing
checkout and `upstream/Intellij-EmmyLua2/build/distributions/`).

ZIP SHA256: `3b74aac9b94f42f2e5894b97ed86cfb9888854fd020d739d970b00076d8a22e2`.
macOS arm64 server SHA256:
`a214b298a3cec6b204d6582110e675c5731fee2d26aa0f19cd1cff722b9b8d91`.
Version, embedded analyzer pin, binary identity, ZIP integrity and Unix0755
executable permissions are verified. Only macOS arm64 contains the custom server.

Normal UP-045 scheduling is retained. FDE's development pin is updated;
keep **FDE 0.8.6-dev** and its existing ZIP, and **LSP4IJ 0.21.1-SNAPSHOT-7**.
Neither needs reinstalling for this analyzer change. No comparison variant,
upstream PR, branch push or user IDE installation was performed. All task-owned
source/package/pin/documentation changes are committed locally with signatures.
