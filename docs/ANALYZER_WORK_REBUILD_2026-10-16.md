# Analyzer work-branch rebuild — 2026-10-16

Rebuilt `work/intellij-modules` from upstream/main `ef6c06a79f635cc014028539519fffd74e6f24da`.
Current signed work head: `ea1f7fbb2134f50522f4178ade5dcc0732d8725e`.

All 54 retained contributions are present: 51 without PRs and three open PRs.
There are 63 unique contribution commits after deduplicating prerequisite copies,
14 preserved work-only picks, and one final integration/test/documentation commit.
All 78 commits above upstream have valid signatures. Contribution branches are
unchanged from the completed [refresh assessment](../../upstream/memory/ANALYZER_PATCH_REFRESH_2026-10-16.md).

UP-008 remains an inactive rollback. UP-009 is obsolete and is omitted. UP-077's
production backport is omitted because upstream supplies it; its tests are retained.
UP-019/020/021 remain work-only API table, factory/signature and navigation adaptations.
Directional/dictionary contracts, generic remote methods, constructor assertions,
loop casts and optional-field guards retain their integration coverage.

The old work head `0f6001bb` is preserved at
`refs/backup/work-rebuild-20261016/work-intellij-modules`. Original fix branch tips
retain their prior `refs/backup/stack-refresh-20261016/` backups. The rebuilt work
branch and the 51 rebased non-PR branches remain local; this task does not push
or modify PRs. GitHub verified on 2026-10-16: #1286, #1292 and #1296 remain open,
ready for review, at their existing refreshed heads. No new PRs.

## Validation

- Full `cargo test --locked --workspace --all-features`: **2,832 passed**, zero
  failed/ignored/filtered; includes **1,823 analysis** and **267 server** tests.
  The long linear-assignment stress test is included.
- Workspace/all-target/all-feature Clippy with `-D warnings`, and rustfmt pass.
- EmmyLua2 routing/client suite: **30 passed**.
- FDE unit suite: **64 passed**, one existing skip (65 discovered).
- Native IntelliJ integration: **8 passed**, covering Lua 5.2 selection, isolated
  Lua/locale servers and removal, cross-file workspace diagnostics, immediate
  semantic tokens, generated APIs, typed remote calls, LocalisedString and
  asymmetric API fields.

The first combined run exposed missing work-only constructor-assertion glue and
the existing class-index negative-control limitation. The final signed integration
restores the previous behavior; all standalone upstream regressions remain on their
contribution branches. The initial native test also assumed synchronous restart
although FDE queues lifecycle work. Its test-only correction waits for a different
server identity before asserting restart and removal behavior. Production FDE code
is unchanged; LSP4IJ source/builds are untouched. Generated translation output was
archived and restored after tests.

## Real-mod comparison

The packaged server and pre-package release server produce identical results on
the frozen LTN fixture: **82 → 81 diagnostics**, **68 → 67 warnings**, and **14 hints**.
There are no new diagnostic locations. The old always-truthy warning at
`script/stop-update.lua:414` disappears. The existing settings.lua:54 mismatch now
identifies the incompatible string union member more precisely.

`script/request_processor.lua` remains clear. The newer tools.lua snapshot used
for UP-078 also remains clear, including the wagon-capacity guard, loop guard and
next() check. The older frozen fixture still has its pre-existing tools.lua:207
LocalisedString assignment warning; it is identical to build170. Original mods
were not edited, and no interactive IDE installation was performed.

## Distribution

- EmmyLua2: `0.30.0-171-IDEA262-patched-modules`.
- Install archive: `../plugins/IntelliJ-EmmyLua2-0.30.0-171-IDEA262-patched-modules.zip`.
- Original archive: `Intellij-EmmyLua2/build/distributions/IntelliJ-EmmyLua2-0.30.0-171-IDEA262-patched-modules.zip`.
- ZIP SHA256: `73124b9fe3be4eb737c085fb1848951f326707092fa06edb31fc883d414bfc4b`.
- Packaged arm64 binary SHA256: `b0ded55ea5a5418d1812c73f919d1376fc3bf1ceb6617a1e72c833cc3ff10745`.
- Analyzer lock and binary identity match; all Unix server ZIP entries retain 0755.
- Only macOS arm64 contains the custom analyzer. Other bundled platform binaries
  retain the existing upstream downloads.
- FDE remains **0.8.6-dev**, with its development pin updated; its ZIP is unchanged.
  Expected existing ZIP SHA256:
  `febdaf00d42fdc593808e34675b1ba25111bbcfc1c0ecc22e7b282b29747a73a`.
- LSP4IJ is maintained by the other agent. These tests consume the separately
  prepared patched5 dependency without changing it.

## Contribution integration map

Original creation dates and dependency order remain in FIX_INVENTORY.md.
Submit UP-059 before the agreed combined UP-033/UP-060 contribution.
All other prerequisite chains are unchanged.

| ID | Contribution branch | Refreshed source commit(s) | Work commit(s) | PR |
| --- | --- | --- | --- | --- |
| UP-010 | `fix/local-table-metatable-association` | `66e13fb5` | `298d9141` | — |
| UP-011 | `fix/key-aware-index-metamethods` | `82184a47` | `57ff7f6d` | — |
| UP-012 | `fix/index-metamethod-definition` | `68c6295e` | `a5160dba` | — |
| UP-013 | `fix/index-metamethod-presentation` | `1b915f1f`, `69c4e5d3` | `d26bc3dd`, `54d9a31e` | — |
| UP-015 | `fix/asymmetric-field-types` | `b9521b0d` | `6347b89c` | — |
| UP-016 | `fix/nullable-table-index` | `70476dae`, `3d75b6de` | `6f628f79`, `3191d2ea` | — |
| UP-017 | `fix/optional-dictionary-keys` | `a1241cc0`, `360210d0` | `ca749153`, `53c64ddc` | — |
| UP-018 | `fix/duplicate-field-index-calls` | `9b50083b` | `427dd69d` | — |
| UP-023 | `fix/flow-aware-union-member-arguments` | `88cfc64f`, `3b742971` | `4f9a6ce3`, `0e30ab33` | #1286 |
| UP-028 | `fix/cross-file-enum-members` | `83985e97`, `81d89c9c` | `8748120b`, `1b6de1d1` | #1292 |
| UP-029 | `fix/computed-index-assignment-flow` | `b92f4cc1` | `55322d68` | — |
| UP-030 | `fix/unbound-table-field-diagnostics` | `ae460ee0` | `0cded870` | — |
| UP-031 | `fix/nominal-enum-member-compatibility` | `08d9bf04` | `b8caa7db` | — |
| UP-032 | `fix/callback-return-type` | `7d019953` | `020487d9` | — |
| UP-033 | `fix/computed-index-guards` | `00a3712c` | `85d0e544` | — |
| UP-034 | `fix/aliased-member-discriminants` | `2d64a18a` | `86bebd7d` | — |
| UP-035 | `fix/union-table-constructor-checks` | `a46b3a88` | `ca9258c7` | — |
| UP-036 | `fix/builtin-length-result` | `37df22b9`, `62c41818` | `d599bfe7`, `bd3cce28` | — |
| UP-037 | `fix/class-index-array-compatibility` | `58353d00` | `62f8b20c` | — |
| UP-038 | `fix/inline-assertion-arguments` | `c281e18e` | `295b1f6d` | — |
| UP-039 | `fix/aliased-call-arguments` | `fc5b966c` | `637577bb` | — |
| UP-040 | `fix/numeric-alias-arithmetic` | `9726a164` | `001942cf` | — |
| UP-041 | `fix/union-alias-parameters` | `4e11a130` | `9f46a918` | — |
| UP-042 | `fix/aliased-dynamic-member-keys` | `23c829e6` | `4401a79b` | — |
| UP-043 | `fix/integer-alias-return-types` | `f2f10efc` | `75f11ad6` | — |
| UP-045 | `fix/workspace-diagnostic-scheduling` | `447b0c78` | `298d2c4a` | — |
| UP-046 | `fix/unique-workspace-id-aggregation` | `3a4b730c`, `824bb04e` | `64944df7`, `7cc2e406` | #1296 |
| UP-048 | `fix/mixed-config-key-merging` | `4919592c` | `beac913a` | — |
| UP-049 | `fix/bare-table-member-inference` | `f010a264`, `81d71d52` | `65bdb945`, `5ec44097` | — |
| UP-050 | `fix/namespaced-constructor-inheritance` | `f10597f1` | `aa24be89` | — |
| UP-051 | `fix/callable-class-overloads` | `236633b1` | `b682cd45` | — |
| UP-052 | `fix/constructor-call-argument-alignment` | `acb6cafb` | `2302a80f` | — |
| UP-053 | `fix/doc-type-completion` | `65f556b6` | `9343c1d9` | — |
| UP-054 | `fix/request-update-ordering` | `8c52ada5` | `ab5458ee` | — |
| UP-056 | `fix/side-effect-require-resolution` | `89fb5db6` | `893cd154` | — |
| UP-057 | `fix/class-member-value-identity` | `d7057807` | `e3861009` | — |
| UP-058 | `fix/enum-cast-compatibility` | `4966e6cd` | `6c01e372` | — |
| UP-059 | `fix/inherited-dictionary-index` | `846d2df2` | `7cb4c534` | — |
| UP-060 | `fix/stable-global-index-guards` | `3bca221c` | `cf2c314e` | — |
| UP-061 | `fix/loop-carried-literal-types` | `0e2418a8` | `f27dede3` | — |
| UP-062 | `fix/enum-literal-compatibility` | `6f41ab49` | `a7d5d8ca` | — |
| UP-063 | `fix/declared-field-hover` | `7718998f` | `b9989fd6` | — |
| UP-064 | `fix/member-self-assignment-types` | `676a8da7` | `24db4414` | — |
| UP-065 | `fix/unknown-logical-and` | `ceb3ce9e` | `2252cd0c` | — |
| UP-066 | `fix/dependent-generic-call-constraints` | `7edc7ecd`, `a2802a11` | `cf9cd7c0`, `9bb6c28a` | — |
| UP-067 | `fix/variadic-named-type-compatibility` | `318e553d` | `5215de72` | — |
| UP-069 | `fix/array-union-alias-compatibility` | `472f6149` | `41eb6f81` | — |
| UP-071 | `fix/stable-index-assignment-flow` | `1b461c82` | `ca440ce1` | — |
| UP-072 | `fix/computed-class-field-guards` | `2f697aa3` | `54882f00` | — |
| UP-073 | `fix/optional-field-condition-diagnostics` | `d0f82375` | `75a6f26d` | — |
| UP-074 | `fix/loop-exit-assignment-flow` | `ca21763d` | `3a29d938` | — |
| UP-075 | `fix/string-alias-index-keys` | `e88548d0` | `fa76cac5` | — |
| UP-076 | `fix/inferred-literal-assignment-contracts` | `2e57512f` | `70915424` | — |
| UP-078 | `fix/computed-guard-effect-boundaries` | `a7d69dd7` | `5e428964` | — |

## Commit receipt

- Analyzer `work/intellij-modules`: `ea1f7fbb` (clean).
- EmmyLua2 `work/patched-build`: `0df4734` (build171 pin/metadata, clean).
- FDE test-only restart wait: `6d4df8c`, on top of the other agent’s
  LSP4IJ dependency receipt `775cea6`. The development pin/report follow-up is
  committed separately. No runtime changes or version bump.
- All task commits are signed and remain local. No pushes or PR actions.

## Evidence

Analyzer `build/investigations/work-rebuild-20261016/` contains the contribution
and work-only source→integration mappings, signature audit, package receipt,
diagnostic comparison and replay logs. Full Cargo logs live in
`build/checkouts/rebuild-20261016/build/logs/`. The checkout is retained for these
logs; the normal active checkout is still `build/checkouts/intellij-work`.

EmmyLua2 logs: `Intellij-EmmyLua2-worktrees/patched-build/build/logs/`
(`routing-build171.log`, `package-build171.log`). FDE logs and saved unit/native
XML reports: `../intellij-factorio/build/logs/analyzer-build171-*` and
`../intellij-factorio/build/analyzer-build171/`.

No pending PR feedback or unrelated repository changes were folded into this task.
