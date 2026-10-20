# Analyzer historical provenance checkpoints

Recorded at the user's request on 2026-10-15. When asked whether an analyzer bug
was introduced by our patches, start historical reproductions at these fixed
upstream revisions. They are comparison anchors, not development branch bases.

Repository: `upstream/emmylua-analyzer-rust` relative to the workspace.
Dates below retain the upstream commit timezone (+08:00).

| Position | Immutable commit | Date | Meaning |
| --- | --- | --- | --- |
| Before the refactor | `aaaca68425d9362876228649b0b8d92f07654daa` | 2026-08-15 | Immediate parent of the large analyzer refactor |
| Immediately after the refactor | `3474c99db1fe02213606232870bfba49a5c72581` | 2026-10-08 | CppCXY's `refactor project` commit |
| Before our first merged analyzer patch | `1c8589bbc8e60eb6f40bd89000082a53a10803a7` | 2026-10-09 | Upstream main immediately before PR #1266 merged |

Our first analyzer contribution, PR #1266, merged on 2026-10-12 as
`c6b728223325eeb28acb9665fc588acf14892260`; its first parent is the third anchor.
These hashes remain fixed as upstream and work branches advance.

## Reproduction and attribution

- Run the same minimized Lua input at all three anchors when historical origin
  is requested. Compare with current upstream and the relevant internal revision
  as needed. An occurrence on current upstream alone cannot establish independence
  from our changes, because upstream already includes some of our contributions.
- Keep Lua version, annotations and relevant configuration equivalent. Adapt only
  the test harness for historical APIs, not the semantics of the reproduction.
  Record incompatibilities or unsupported features rather than treating a build
  failure or an unexecuted test as a clean result.
- Use detached checkouts below the analyzer's `build/checkouts/`; preserve active
  fix/work branches. Save input, exact commits, commands and diagnostic results
  under `build/investigations/BUG/`. Follow workspace build/cache guidance and
  verify the selected test actually runs; avoid stale shared-target results.
- Passing on the first anchor and failing on its immediate successor isolates
  the regression to the refactor. Reproduction before PR #1266 establishes that
  it predates our merged analyzer contributions. If the behavior first appears
  later, narrow the interval using history/bisection and inspect the introducing
  change before attributing it to an upstream author, our patch or an interaction.
- Update the fix inventory and investigation report with the evidence and any
  limits. Historical investigation alone does not require rebuilding plugins.

## Existing example

UP-057 (`Metrics.__index = Metrics`): passes before the refactor, fails immediately
after it and before PR #1266. Reproduction is a plain annotated class with no
imports or dictionary dependency.

The reusable example harness and results are in the analyzer repository at
`build/investigations/metrics-self-index-20261015/check-history.py` and
`historical-results.json`, with `history-*.log` outputs. The script is specific to
that bug; adapt its fixture for new reports. The versioned narrative is in
[the UP-057 report](../CLASS_MEMBER_IDENTITY_2026-10-15.md); its workspace source is
`upstream/memory/CLASS_MEMBER_IDENTITY_2026-10-15.md`.
