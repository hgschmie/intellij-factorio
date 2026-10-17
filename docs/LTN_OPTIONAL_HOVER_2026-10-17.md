# Optional member hovers — UP-080 and UP-087

Implementation: 2026-10-17. The user authorized integration of the previously
held UP-080 amendment and requested a separate fix where appropriate.

## Diagnosis and scope

The current LTN `script/schedule.lua` is read-only input. Line numbers have moved:
owner is cast at55 and read at63/68; goal_index is cast at176 and read at177;
locomotive.burner is read at239. These represent two presentation defects:

- UP-080: direct member hover appended an optional declaration marker even when
  a cast/guard narrowed that particular read. Pending signed amendment 33e7abe3
  already fixes this independently on its original ef6c06a7 base.
- UP-087: expanded class/object summaries used projected member types without
  the separately stored optional field flag. LuaEntity.burner and
  TrainPathFinderOneGoalResult.start_index consequently lost their question marks.
  This reproduces on freshly fetched upstream/main d823745f. It is independent
  of UP-080 and changes hover rendering only, not core member projection.

UP-087 is `fix/optional-class-hover-fields`, signed a8c897fe on d823745f.
Optionality is combined with the projected type before rendering both local
object summaries and class hovers. Existing nullable types are not doubled;
required fields, inherited fields and callable presentation are retained.
Three targeted regressions fail before the fix. All four final focused tests
and all 227 standalone server tests pass; Clippy and rustfmt pass.

## Work integration

Signed pick a70ecda2 brings in UP-080's amendment while retaining UP-063's
assignment-context precedence. Signed pick 82e42ad5 brings in UP-087's initial
57fb8cb2; the final test refinement matches a8c897fe. Cherry-picks preserve the
existing work stack without importing the unrelated d823745f base-type refactor.

Combined regression tests exposed an integration issue: UP-080's standalone
member-flow fallback can overwrite our path-aware flow result. Work integration
uses the exact expression/receiver query and leaves nullable callbacks on the
signature/documentation path. Generic optional fields whose projection already
contains nil must still pass through that receiver-specific refinement. This
integration condition is restricted to optional generic fields; ordinary
explicit nullable contracts retain their existing type-predicate/rawget flow.
Tests cover casts, nil branches, conflicting writes, early-return guards,
unrelated receivers, generic optional values and callable documentation.

An exploratory inherited-generic summary test exposed a separate existing work
limitation (`Box<T>` inherited as `Box<Burner>` can show T). The production hover
fix does not change generic substitution; the final inheritance regression
checks optional metadata with a nongeneric parent. This exploratory finding is
recorded here rather than folded into an unrelated hover fix. It is not claimed fixed
by UP-087 and does not affect the reported Factorio fields.

## Delivery and validation

Final work integration is signed `aa7fb451`, with handoff/pin commit
`c31e503d74deaf8e49eb40f0cdf570a348785ff5`. EmmyLua2 packaging is signed
`a6490ff`, version **0.30.0-177**. Install from workspace
`plugins/IntelliJ-EmmyLua2-0.30.0-177.zip`; original archives remain in both the
EmmyLua2 worktree and shared repository build/distributions directories.

Validation completed:

- **2,885 workspace tests passed**: analysis 1,849, formatter 490, server 294,
  parser 191, parser descriptions 51, miscellaneous 10. Empty binary/doctest
  targets are not counted. Workspace all-target/all-feature Clippy with warnings
  denied and rustfmt pass. An initial broader integration attempt failed
  predicate/rawget tests and was discarded; the final constrained integration
  passes those tests and the complete suite.
- Both forced native IntelliJ checks passed: generated API with separate
  workspaces and asymmetric API attributes (2 executed, no skips).
- Packaged-server replay of the copied current schedule.lua shows owner as
  LuaTrain at63/68, goal_index as uint32 at177, the locomotive summary's burner
  as LuaBurner?, and path_results.start_index as uint32?. Direct burner hover
  remains LuaBurner? and its valid need-check-nil warning remains. Removing the
  owner cast restores the nullable train/platform union and diagnostics.
- The current schedule.lua diagnostic list is identical to build176 (2 items).
  A separate frozen full LTN fixture retains exactly the same **79 diagnostics**
  in all 37 files. Connector required-argument warnings, guarded acceptance and
  method documentation remain correct.
- ZIP version/pin, embedded/prepared/release binary identity and Unix0755
  executable modes are verified. The validated ZIP is copied flat into plugins/.

ZIP SHA-256:
`d95feb2c0ead246f7979d6ebae250fbd7ba93c7c34ebd907fd6be6ab9afe988c`.
arm64 analyzer SHA-256:
`f0b986e94c35d6b17ef2af1c3d1758145293a1bbba218305d832d48d68f14762`.
Only macOS arm64 contains this custom analyzer. FDE remains 0.8.6-dev and
LSP4IJ remains 0.21.1-SNAPSHOT-7; neither ZIP is rebuilt. FDE's development
analyzer pin and this acceptance receipt are updated.

Evidence: analyzer build/investigations/schedule-hover-followups-20261017,
cast-member-hover-20261017, nullable-connector-arguments-20261017; fix/work
checkout build/hover-*.log and build/optional-hover-*.log; FDE
build/investigations/optional-hover-build177-20261017 and
build/logs/optional-hover-build177-platform.log. Source, integration and packaging
commits are signed. No push, PR action or user IDE installation. Original mods
are unchanged.
