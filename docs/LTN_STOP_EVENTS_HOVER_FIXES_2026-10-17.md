# LTN stop-events hover fixes — UP-084/085 (2026-10-17)

## Changes

UP-084 recovers member documentation when an initially untyped local receives its
class type through subsequent assignments. It uses the receiver type at the use
site only as a fallback for presentation identity. The ordinary member resolver
is unchanged, avoiding the guard-analysis regression encountered during UP-080.
Ambiguous receiver unions do not select one class's documentation; generic
member types are projected, and nullable receivers still produce nil diagnostics.
UP-080's existing nullable declared-receiver fallback remains in the work branch.

UP-085 displays a colon only when the declaration explicitly uses a colon or the
first parameter is named `self` or `_self`. The old owner/argument type-compatibility
and inferred-self heuristics are removed. The shared renderer applies this to
hover signatures, overload blocks and completion-item details. Actual colon
methods keep their first explicit argument. Existing tests for `fun(self:string)`
intentionally change to colon notation under the user's requested rule.

## Branches and validation

Both standalone branches are based directly on freshly fetched upstream/main
`ef6c06a79f635cc014028539519fffd74e6f24da` and have no pending patch prerequisite:

| Fix | Branch | Signed commit | Signed work merge |
| --- | --- | --- | --- |
| UP-084 | fix/flow-receiver-hover | a6ec9d6a | 68085b5f |
| UP-085 | fix/explicit-method-hover | 355a672f | b9b0aaf4 |

Work handoff/pin: `9f1b230a529f61b3695dc45cb6bf71757e84f448`.
Normal UP-045 scheduling and UP-082 performance fixes are retained.
The presentation fixes are independent of each other and complementary to UP-080.
Both defects also reproduce on current upstream; no historical attribution
before our merged contributions was requested or established.

New regression sets were run before the production fixes: four tests failed in
each set. All pass after the changes. Full standalone server suites pass 228
(UP-084) and 229 (UP-085) tests. The combined all-feature workspace passes
**2,861 tests**, including 282 server tests, with no failures/ignored/filtered
tests. Clippy across the workspace/all targets/features with warnings denied and
rustfmt pass. Original mod sources remain read-only.

Raw baseline/protocol and delivery replay are in analyzer
`build/investigations/stop-events-hover-fixes-20261017/`; the copied mod uses its
actual `.luarc.json` (strict.tableIndex=true, unnecessary-assert disabled).
The 37-file build174 baseline has 69 diagnostics. Earlier investigation evidence
is in `build/investigations/stop-events-hover-20261017/`.

## Package verification

Install `plugins/IntelliJ-EmmyLua2-0.30.0-175.zip`. EmmyLua2 signed work commit
`9e0cefc` packages analyzer `9f1b230a`. Keep FDE 0.8.6-dev and LSP4IJ
0.21.1-SNAPSHOT-7; only FDE's development analyzer pin and this receipt change.
Only the macOS arm64 server receives the custom analyzer. The normal UP-045
variant is built; older UP-008 comparison ZIPs remain unchanged.

The bundled server now returns full get_wire_connectors signatures, descriptions,
parameter documentation and Factorio API links at both stop-events.lua156/157.
At159 it returns `LuaWireConnector.connect_to(target: LuaWireConnector,
reach_check: boolean?, origin: defines.wire_origin?) -> boolean`; signature help
retains the same three arguments. The entire 37-file diagnostic result is
identical to build174: **69 → 69**, with no changed/added/removed diagnostics.
request_processor.lua and stop-update.lua remain clear. Existing warnings in
other mod files are outside this presentation-only change.

Two native IntelliJ integration tests pass without skips:
`PerModServersTest.testGeneratedApiWithSeparateWorkspaces` and
`PerModServersTest.testAsymmetricApiAttributes`. XML is retained under FDE
`build/investigations/up084-up085-hover/`.

The build script verified the embedded plugin version, analyzer lock, binary
identity and Unix0755 modes. The validated archive is retained in both the
producing worktree and main repository build/distributions/, and copied flat
into plugins/. Checksums:

- ZIP: `dcb9bb97c37738e1d8040d352a89a4b8a061017c014efa63b603fcff03671e40`
- Arm64 server: `f84d258dfdc33cb5831dff2ab55a7452bd5f2d624a06fe7e911bb2d26bf15a1b`

All task-owned tracked changes are signed commits. No pushes, upstream PR actions
or PR-status checks were performed; previous verification dates still apply.
No plugin was installed into the user's running IDE.

