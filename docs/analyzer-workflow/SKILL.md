---
name: find-lua-analyzer-bug
description: Investigate incorrect EmmyLua Lua diagnostics, inference, hover or navigation in this Factorio workspace; prepare an isolated analyzer fix, integrate it locally, and rebuild the affected plugin when requested. Use for analyzer bug reports rather than ordinary Lua application fixes.
---

# Find Lua analyzer bug

Use the user's source and observed IDE behavior to identify the faulty layer,
produce a reviewable regression fix, and validate the packaged analyzer against
the reported case. Respect a request for investigation only; otherwise carry the
requested fix, local integration and packaging through completion.

Read the workspace HANDOFF.md and current repository instructions first. Inspect
actual branch/checkouts and dirty state; warn about pre-existing uncommitted
changes and preserve them. Historical branch names and pins in old documentation
are not instructions to reset the current work branches.

## Investigation and isolated fix

- Re-read the real Lua file: edits can move line numbers or change the example.
  Trace declarations, imports, API definitions and relevant server configuration.
  A highlight on an index key does not necessarily mean the key expression is
  undefined; inspect the diagnostic's full target and its read/write context.
- Compare a minimized reproduction on freshly fetched upstream with the internal
  patched baseline. Establish whether the bug is upstream, a local regression,
  a patch interaction, configuration, or a stale installed dependency.
- When asked whether our patches introduced the problem, start historical
  reproductions at the three fixed revisions in
  [historical provenance checkpoints](references/historical-provenance.md):
  before the refactor, immediately after it, and before our first merged analyzer
  contribution. Current upstream may already contain our fixes; reproducing there
  alone does not establish historical origin.
- Use original mods as read-only inputs. Put fixtures, protocol captures and logs
  under the relevant repository's build directory. Preserve a fixed input copy
  for before/after comparisons.
- Prepare a separate fix branch/worktree on current upstream/main (or the actual
  upstream default branch). If a pending local patch is a genuine prerequisite,
  document that dependency and submission order rather than forcing independence.
- Add regressions that fail before the fix and check the claimed behavior, plus
  relevant negative cases. Preserve declared contracts, nil behavior, scope and
  asymmetric read/write semantics as applicable. Check that selected tests
  actually ran. Keep packaging pins and internal handoff notes off PR branches.
- Commit with signed Conventional Commits. Keep new fixes local: pushing branches,
  opening PRs or messaging reviewers requires an explicit request.

## Integration and delivery

Integrate the reviewed fix into the internal work branch, preserving existing
patches. Use a merge when appropriate; use a signed cherry-pick with provenance
when merging would import unrelated upstream refactors. Describe which was used.
Validate both the standalone fix and combined behavior with relevant local patches.
Run appropriate broader checks once focused regressions pass.

For **analyzer-only changes**, rebuild the analyzer and the **EmmyLua2 package**.
Update FDE's development analyzer pin and relevant integration evidence, but do
not bump FDE's version or create another FDE ZIP solely for a new analyzer.
FDE launches the installed EmmyLua2 server; its analyzer pin is a build/test
check, not a runtime dependency pin. Rebuild/release FDE when its own behavior,
configuration or client API integration changes. Follow user version directions.

Use [the workspace build reference](references/workspace-builds.md) for concrete
paths, validation and archive handling. Record the root cause, branch/base,
signed commits, tests, package path and limitations in current handoff/release
notes. Verify package provenance/executable permissions and keep task-owned
tracked changes committed. Do not install into the user's running IDE unless
requested. Report unresolved blockers candidly rather than calling partial work
complete.

## Background work and concurrent reports

When the user asks for background work, delegate investigation/fixing to an agent
with an exclusive fix worktree and the full task constraints, then keep the parent
available for other reports. The parent coordinates a single owner for shared
work-branch integration, dependency pins, prepared plugins and packaging; ask the
parent for that ownership before mutating shared state. Independent fixes can be
investigated concurrently, but shared integration/builds must be serialized or
batched deliberately. Use separate Cargo targets for concurrent builds, or
serialize use of a shared target. Update the parent on findings and completion.
