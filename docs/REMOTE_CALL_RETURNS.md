# Remote-call return types — 0.8.4-dev

LuaRemote.InterfaceMap supplies the declared remote method signature. For example,
`fun(): defines.events` must yield one event value, not an unbounded event sequence.
FMTK now emits `---@return ReturnType<FF>` for EmmyLua. The former extra `...`
wrapped every known return shape again. Fixed multiple returns and genuine
variadic methods are preserved, including the unknown-interface `Any?...` fallback.
LuaLS output and remote-call argument annotations are unchanged.

UP-068 is the toolkit fix, based on the accepted work/intellij-toolkit baseline
and its UP-014 target selection. UP-067 is a separate analyzer correction for
actual variadic enum/class/alias results. It is not required for the corrected
single-result remote-call annotation, and does not replace that correction.

Install FDE 0.8.4-dev and EmmyLua2 0.30.0-166-IDEA262-patched-modules on macOS arm64;
retain LSP4IJ patched4. Restart the IDE after installing. The bundled CLI hash
participates in the API cache key, so definition generation uses a new cache.
If services have not regenerated yet, use Tools → Factorio → Regenerate Factorio
API Definitions. An explicitly configured external FMTK CLI must also contain UP-068.

The native integration regression checks a nested script.on_event call, a known
single result that is not repeated into a second slot, fixed multiple and genuine variadic returns,
and rejection of an event passed to a string parameter. The workspace report
upstream/memory/VARIADIC_NAMED_TYPES_2026-10-15.md records full validation and package hashes.

A separate existing analyzer limitation remains: reading an extra result from an
ordinary single-result function is shown as any instead of nil. The corrected
remote signature now behaves like that ordinary call; this release does not
change the analyzer's missing-result inference.
