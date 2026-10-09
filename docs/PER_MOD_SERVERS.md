# Per-mod language servers

The 0.2.1 development build registers an independent EmmyLua analyzer and FMTK
locale/changelog server for each detected Factorio mod. This isolates globals
such as `This` in unrelated modules, in addition to the existing analyzer patch
that fixes short and named module imports.

## Dependencies and installation

The current release uses LSP4IJ `0.21.1-SNAPSHOT-factorio-formatting-patched2`
and EmmyLua2 `0.25.1-132-IDEA262-patched-modules`, which provides
`EmmyLuaServerProvider` and `EmmyLuaServerRouting`.
The active EmmyLua2 branch is `work/patched-build`; see its `PATCHED_BUILD.md`.

Install those ZIPs from each upstream repository's `build/distributions/` and
`build/distributions/intellij-factorio-0.5.5-dev.zip`, then restart IDEA.
The LSP4IJ breakpoint-removal fix is documented in
[BREAKPOINT_REMOVAL.md](BREAKPOINT_REMOVAL.md). The formatting fix requires no
analyzer changes; the analyzer remains pinned by `emmy-analyzer.lock`.

## Ownership and lifecycle

`FactorioServerManager` registers project-specific LSP4IJ definitions with stable
IDs derived from canonical project/mod paths. Servers start on demand. Settings
and module changes replace affected definitions; removal and project disposal
unregister them. Registry mutations and restart operations run on the EDT because
LSP4IJ calls its Swing explorer listeners synchronously. Background callers enqueue
immutable configuration snapshots without waiting for the EDT; disposal immediately
deactivates definitions and then schedules registry cleanup. Queued configuration
is ignored after disposal. **Restart Factorio Language Services** restarts the registered
instances. The old static FMTK definition is removed.

An attached mod owns its editor files. An external dependency shared by several
mods has one deterministic editor owner; every consumer still indexes that
explicit dependency. Unsaved dependency text is forwarded to other running
consumers, and closed there after save. Each instance receives filesystem
watches bounded to its own roots. Diagnostics are displayed only by the editor
owner. EmmyLua custom gutter requests use the same file-routing hook.

Whole-file and selection formatting also respect editor ownership. Factorio's
formatting feature rejects files belonging to another scope, including with the
previous official LSP4IJ nightly. The patched LSP4IJ additionally checks client
file enablement in `hasAny` and `processLanguageServers`, so these shared helpers
cannot select a server merely because its filename/language mapping matches.

Lua files outside managed scopes retain the default EmmyLua server. On scope
changes its existing document connections are dropped so it cannot keep indexing
newly claimed files. Open editors are refreshed to select their new owners.

## Configuration

Generated API libraries and the filtered Factorio data library are shared read-only. Mod source trees are not
shared implicitly. Per-module dependency settings must contain individual mod
directories or mod ZIPs; a collection parent is rejected to prevent indexing
unrelated sibling mods. Explicit dependency globals remain visible in that
consumer, so declaring another attached mod as a dependency intentionally widens
its scope.

Each analyzer initializes with its mod directory as its root and working directory.
The analyzer loads native `.luarc.json`, `.emmyrc.json`, and `.emmyrc.lua` files
from that directory. Prefer one file; when several exist, the analyzer loads them
in that order. Module-relative paths and `${workspaceFolder}` refer to the mod.
Project-level configuration is not inherited by sibling modules. No migration or
rewriting of user configuration files is performed.

Keep diagnostics, severity overrides, completion, hints, and other analysis
preferences in the module configuration. For example, `.emmyrc.json`:

```json
{
  "diagnostics": {
    "disable": ["unnecessary-assert"]
  }
}
```

The plugin answers each server's `workspace/configuration` request with only
Factorio integration settings: Lua 5.2, the `?.lua` require pattern, generated API
and filtered game libraries, explicit dependency packages, source roots and
`__mod-name__` mappings. Those values are not user preferences. Configure
Factorio libraries/dependencies through the plugin settings instead. No generic
project-wide LSP4IJ settings are forwarded to these servers.

The native EmmyLua2 parser also receives Lua 5.2 for attached Factorio mod files
through its independent `languageLevelProvider` extension (patched build 121+).
This enables legacy `global` member names only for the known Factorio runtime;
unrelated files with no supplied language level retain strict Lua 5.5 parsing.

**Current limitation:** native configuration merges objects, replaces scalars
with later values, and appends arrays. The plugin overlay is applied last, but
cannot replace user arrays. Do not set `workspace.workspaceRoots`,
`workspace.packages`, `workspace.library`, `workspace.moduleMap`, or
`runtime.requirePattern` in module configuration. Conflicting entries are not
automatically rejected and can bring unrelated mods into the analyzer. Additional
libraries outside managed roots also have no plugin-provided watches or editor
routing. Strict enforcement would require separate analyzer work; this version
uses the existing server protocol without upstream patches.

Saving, creating, deleting, moving or renaming any of the three configuration
files triggers a debounced restart of only that module's EmmyLua server. Analyzer
0.25.1 ignores deletion notifications; the plugin uses a fresh process consistently
for all config changes so deletion and editor atomic saves behave the same way.
Other module servers and FMTK locale services remain running. Invalid
configuration is handled by the analyzer (logged and skipped); retaining the last
valid configuration is not
guaranteed. Configuration changes must be saved to take effect.

Managed overlay snapshots (`workspace/managed-emmy-config.json`), logs and
standard-library resources remain under the IDE system cache:
`softwareforge-factorio/<project>/language-servers/<identity>/`.
The snapshot is for inspection, not editing. Child home/config/data lookup stays
isolated from global analyzer configuration. This also means `~` refers to the
isolated home; use module-relative paths instead.

### Factorio data exclusions (0.2.2)

The data library is a structured EmmyLua entry with `ignoreDir` and `ignoreGlobs`,
matching `src/vscode/VersionSelector.ts` in the toolkit. Core implementations
covered by FMTK stubs are skipped; the generated stubs remain indexed. Scenario,
campaign, migration, tutorial and menu-simulation globs are confined to this
library entry and do not exclude the mod's own files.

Defaults use the correct `base/script/` paths and additionally exclude
`core/lualib/story.lua` and the rocket-rush, supply and team-production script
directories. The erroneous `base/scripts/` entries from 0.2.2 are removed from
saved settings on load; other user entries are preserved.
These scripts mutate scenario-owned `storage` and otherwise pollute mod globals.

**Configure Factorio Project → Factorio data library → Edit exclusions…** edits
the complete project-wide lists, one relative path or glob per line. Restore
defaults resets the dialog fields; OK stages them, and applying the parent settings
persists them and regenerates server configurations. Cancel leaves the prior
values intact. Empty lists are allowed as an explicit opt-in to indexing all
files. Existing projects acquire the defaults without editing their source files.

## Validation

Run from the plugin repository:

```sh
./gradlew test buildPlugin
./gradlew -PplatformTests -PrealMods=/Users/henning/factorio/mods test
./gradlew verifyPlugin
```

`-PapiDocs=/path/to/doc-html` overrides the platform API test's local Factorio
installation. The real-mod test copies Lua/JSON/locale files, following symlinks,
into workspace-owned temporary directories; it never edits the originals.
Without `-PrealMods` that test is skipped.

Platform tests use actual LSP4IJ definitions and native analyzer/Node processes.
They cover isolated globals, editor routing to exactly the owning pair of servers,
locale navigation and VFS create/delete, restart, module removal, unsaved shared
dependency delivery/save/disk replacement, generated API hover/configuration,
native module configuration in all three formats, per-module diagnostics, relative
exclusions, config create/edit/delete/rename, neighboring Lua/locale server
stability, and the reported `This:storage()` navigation in both real mods. These are IDE
platform tests, not interactive UI acceptance. The test runner disables LSP work
progress because synchronous IntelliJ test progress otherwise blocks LSP4IJ's
notification queue; normal IDE progress is unchanged.

### Known analyzer refresh limitation

Analyzer 0.25.1 reindexes the changed file without necessarily invalidating cached
exported table types in importers. Changing a dependency's returned table can
leave completion in an unchanged consumer stale until that consumer is
reanalyzed (for example, edited or reopened). The regression test separately
verifies that both servers receive the unsaved buffer, then reopens the importers
to verify their new fields. Ordinary dependency edits do not trigger a full
reindex or a server restart. Save changes and restart the language services if stale types
persist. The earlier rapid-open/edit ordering limitation also remains.

The validated runtime is macOS arm64, IDEA 262, official LSP4IJ nightly 0.21.1-20260930-013028 and the pinned
EmmyLua 0.25.1 development build. Other operating systems need runtime validation.

The previous Plugin Verifier run (0.2.8-dev) reported **Compatible** against the exact patched dependencies
and IDEA IU-262.10968.63. Its strict Gradle task remains nonzero for internal API
usages; deprecated and experimental APIs are also reported. Editor refresh uses
LSP4IJ's internal `LSPFileSupport`, as does the existing locale completion cache
integration. Do not interpret binary compatibility as an API stability guarantee.
