# LTN negative literal hover — 2026-10-17

**UP-090**: `fix/negative-literal-hover`, signed `95d498871b8969f099b3bd473602ab43bb7e1225`,
based on freshly fetched upstream/main `d823745f`. Signed work pick `a0931520`
and handoff commit **72e366218c4a1d6508d8ba97866413cebf1b5300** are integrated
on work/intellij-modules. EmmyLua2 work **c66fc00** packages **0.30.0-179**.
All new commits remain local; no push, PR or IDE installation requested.

## Cause and correction

The actual file is `~/factorio/mods_21/LogisticTrainNetwork/script/init.lua`,
line143: `stop.error_code = stop.error_code or stop.errorCode or -1`.
Both the minus and digit hover returned `1` in packaged build178.
Two independent paths in current upstream cause this:

- `semantic_model/infer/vm.rs::unary_type` returns the operand unchanged when no
  operator overload exists, so numeric constant `1` remains `1` after negation.
- Hover normally selects the digit's literal node, excluding its unary minus.

The fix folds integer, documented-integer and floating constants under unary
minus. Operator overloads keep precedence. Integer overflow widens to number,
consistent with existing conservative binary constant arithmetic. Hovering
any part of a direct negative numeral uses the unary expression and its full
range. Binary subtraction, exponent operands and other expression boundaries
remain separate. Numeric table keys keep their member information.

This reproduces on unmodified current upstream as well as our build178; no
claim is made about which historical ancestor first introduced it. Five new
regression tests cover inference, overflow, hover contents/ranges and context.
Before the fix both inference tests and two hover tests fail; the boundary
control passes. The constructor-key hover expectation was corrected to retain
its existing useful member presentation, not replace it with a bare number.

UP-090 has no pending-patch prerequisite. Its signed work cherry-pick avoids
importing the unrelated upstream base-type refactor and preserves UP-036's
length result plus directional-field and metatable hover hooks.

## Validation and delivery

- Standalone: **1,522 analysis tests and 229 server tests** pass; Clippy for both
  crates, all targets/features, warnings denied; rustfmt passes.
- Combined: **2,902 workspace tests**, zero failures/ignored: 1,859 analysis,
  301 server, 490 formatter, 191 parser, 51 parser-description, 10 doc CLI.
  Empty test targets are not counted. Workspace Clippy and rustfmt pass.
- Packaged LSP replay returns `-1` for both character67 and68 at zero-based
  line142 of the frozen real init.lua. Both responses cover range67..69.
- Frozen full LTN diagnostics remain identical: **104→104**, including zero
  diagnostics in script/request_processor.lua. Original mod files are untouched.
- Both forced native IntelliJ checks pass: generated API/separate workspaces
  and asymmetric API attributes. XML timestamp `2026-10-17T23:52:18.513Z`,
  2 tests, zero failures/errors/skips.
- Descriptor version, embedded pin, prepared/release binary identity and all
  Unix server executable modes 0755 verified. Archive copied to flat plugins/.

Install `plugins/IntelliJ-EmmyLua2-0.30.0-179.zip`. Original retained under
upstream/Intellij-EmmyLua2/build/distributions/ and in the packaging worktree.
Only macOS arm64 contains the custom server. Keep FDE **0.8.6-dev** and its
existing ZIP; only its development analyzer pin/receipt changes. LSP4IJ remains
**0.21.1-SNAPSHOT-7**. Normal UP-045/UP-082 remain in the analyzer.

ZIP SHA-256: `0e587f82b57b42bbe7d84d9f5f68b4ac71547d9ced678474c2d7ea4d19b39576`.
Server SHA-256: `d180df6c06ba0f72325717cda49317eb9a76158d6cce717708c11e63740edab4`.

Evidence: analyzer build/investigations/negative-literal-hover-20261017/ contains
frozen source, raw before/after LSP captures, package.json and diagnostic-delta.json.
Full scans: build/investigations/nullable-connector-arguments-20261017/
build178-real-diagnostics.json and build179-real-diagnostics.json.
Standalone/work logs: their build/logs/negative-literal-* files; failing baseline
logs are negative-hover-before.log and negative-inference-before.log.
Packaging: Emmy worktree build/logs/negative-literal-build179.log.
Native checks: FDE build/logs/negative-literal-build179-platform.log and test XML.
