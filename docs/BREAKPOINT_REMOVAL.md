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
