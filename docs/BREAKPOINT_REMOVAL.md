# Removing the last breakpoint in a source (0.2.6-dev)

LSP4IJ 0.21.0's BreakpointHandlerBase.unregisterBreakpoint only supplies the
removed source to the request builder when the entire session's breakpoint list
is empty. DAP setBreakpoints replaces the breakpoint list for one source file.
If A and B each have a breakpoint, removing A's last breakpoint sends only B's
remaining list. The adapter keeps A's stale breakpoint and can stop there again.

FactorioDebug.Handler overrides unregisterBreakpoint: remove the tracked
breakpoint and use LSP4IJ's unregisterTemporaryBreakpoint(sourcePosition) path
to send the replacement lists, including an empty list for the removed source.
No LSP4IJ binary change is needed for users. This is a compatibility workaround
for 0.21.0 and can be removed once the upstream fix is released and required.

Upstream PR branch: `upstream/lsp4ij`, `fix/dap-clear-last-breakpoint-per-source`,
commit `c44ae7c2`, based on the existing 0.21.0 checkout (`b30f69d`). The upstream fix removes the
session-wide emptiness check and always passes the removed source. Its regression
test exercises the actual DAP handler with real IntelliJ line breakpoints and a
recording protocol endpoint.

Validation:
- FactorioBreakpointTest reproduced a missing setBreakpoints(A, []) request on
  the released dependency, then passed with the Factorio workaround.
- DAPBreakpointHandlerBaseTest failed against the released dependency and passed
  after compiling the patched upstream class into an isolated copy of that
  dependency. No other upstream production class was replaced.
- Covered remaining breakpoints in the same file, the last breakpoint in one of
  multiple files, the final breakpoint in the session, re-registration, and the
  temporary-unregister flag.

Upstream test execution used the Factorio plugin's existing IDEA 262 platform
harness with the upstream test added using an init script. It did not run the
entire upstream suite or its default IDEA 2024.2 matrix. Evidence and the isolated
dependency are in `upstream/lsp4ij/build/breakpoint-verification/`.
This verifies emitted protocol requests; a live Factorio gutter-removal session
has not been repeated for this fix.

## Updated dependency (0.2.7-dev)

The upstream patch was rebased onto `upstream/main` at `2dbaa593` (0.21.1-SNAPSHOT).
Its new PR commit is `b7adb84e`; it retains Java 17-compatible test APIs. The
local `work/factorio-build` branch adds IDEA 262 build tooling separately.
`lsp4ij.lock` pins that build branch, and `scripts/build-lsp4ij.sh` produces
`lsp4ij-0.21.1-SNAPSHOT-factorio-patched.zip`. Both Factorio and EmmyLua2 builds
consume the updated shared dependency at `dev/plugins/lsp4ij`.

The Factorio-specific unregister workaround from 0.2.6 is removed: the actual
LSP4IJ handler now implements the fix. FactorioBreakpointTest remains as an
integration regression test to prevent adopting a dependency without the fix.
Install the patched LSP4IJ ZIP together with Factorio 0.2.7-dev.

The complete LSP4IJ source now builds successfully in its own Gradle project;
its breakpoint regression passes there. The build is pinned at `4745d824` and
uses Gradle 9.6.1, Kotlin 2.3.0, IntelliJ Platform Gradle plugin 2.18.1 and the
local IDEA 262/JBR 25 SDK. The custom ZIP targets IDEA build 262 and later.
The local build also supplies the newly separated Structure View SDK dependency,
uses standard Gradle test reporting, and restricts test plugin loading to LSP4IJ.
These build adaptations are not included in the PR.

Factorio's nine platform tests (including the breakpoint test with no local
workaround and navigation against copies of the real mods) and EmmyLua2 routing
tests pass against this full build. Logs are in each repository's `build/logs/`.
The full upstream test suite and live Factorio debugging were not rerun.

## Review against upstream issue #1316

Reviewed https://github.com/redhat-developer/lsp4ij/issues/1316 and its fix,
https://github.com/redhat-developer/lsp4ij/pull/1318, commit `89d94eac`.
That issue concerns removing the session's final breakpoint. The old
`sendBreakpoints` guard returned whenever `breakpoints.isEmpty()`, even if a
removed source was supplied. The essential correction was to return only when
`breakpoints.isEmpty() && temporaryBreakpoint == null`. Our patch preserves
that guard, as well as the no-initialized-server guard.

The same upstream commit also restricted the removed-source argument to cases
where the session-wide list was empty. Our patch removes that restriction only.
When the session is empty, both versions send exactly the same removal marker;
when it is not, our version additionally keeps the removed source in the request.
`computeIfAbsent` in the DAP request builder preserves remaining breakpoints in
that source. Disassembly handling ignores this marker and continues replacing
its entire instruction-breakpoint list as before (source review only).

The existing regression test covers the final breakpoint, same-file retention,
cross-file removal, re-registration and temporary unregistration. To check its
sensitivity to #1316, an isolated mutation test restored the old unconditional
empty-list guard. It failed on the final `b.lua` removal because no replacement
request was sent. Restoring our unchanged patch passes. Test execution was forced
to avoid reusing cached test results. Logs, XML results and the mutation driver
are in `upstream/lsp4ij/build/checkouts/patched/build/issue-1316-review/`.
No production change or new regression test was needed; the existing test detects
both reported failure modes. Published ZIPs and dependency pins are unchanged.

## Official upstream dependency (September 29, 2026)

Upstream accepted the fix as `6ed15ae6` and its portability test as `4887361f`.
The local development worktree was rebased onto upstream, dropping the separate
production patch. Only build tooling differs from upstream in that fallback tree.
Active integration builds now use the unmodified Marketplace nightly
`0.21.1-20260930-013028`, pinned by archive SHA-256 in `lsp4ij.lock`.
The earlier source-built ZIP instructions above are historical.

Validation against that official binary: all 13 Factorio platform tests and all
12 EmmyLua2 tests pass. The 32 Factorio unit tests also pass. The Factorio breakpoint regression exercises same-file
retention, cross-file removal, final-breakpoint removal, re-registration and
temporary unregistration through the actual LSP4IJ handler. `buildPlugin` also
succeeds. This verifies automated integration; live IDE debugging was not
repeated for this dependency update.
