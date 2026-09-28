# Per-mod language servers

The 0.2.1 development build registers an independent EmmyLua analyzer and FMTK
locale/changelog server for each detected Factorio mod. This isolates globals
such as `This` in unrelated modules, in addition to the existing analyzer patch
that fixes short and named module imports.

## Dependencies and installation

Requires LSP4IJ 0.21.0 and the patched EmmyLua2 client containing
`EmmyLuaServerProvider` and `EmmyLuaServerRouting`. In
`../upstream/Intellij-EmmyLua2`, the contribution branch is
`fix/file-language-server-routing` (`547e03e`), merged into
`work/patched-build` (`54818f2`; subsequent local validation `a924dd9`).
The distribution retains version `0.25.1-115-IDEA262-patched-modules`, so an older
ZIP with that same version must be replaced by the newly built ZIP.

Install that ZIP and `build/distributions/intellij-factorio-0.2.4-dev.zip`, then
restart IDEA. No LSP4IJ or additional analyzer source changes are required.
The analyzer remains pinned by `emmy-analyzer.lock`.

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

Each analyzer initializes in a managed workspace under the IDE system cache:
`softwareforge-factorio/<project>/language-servers/<identity>/workspace`.
Its `.emmyrc.json` names only that mod, its explicit dependencies and API/user
libraries. Sibling directories hold analyzer logs and standard-library resources.
The child environment redirects home/config/data lookup to avoid inheriting
project-wide roots from global analyzer configuration. The generated config is
automatic; edit project settings or the project's `.emmyrc.json` instead.

Previously managed entries in the project's `.emmyrc.json` are removed using
the existing ownership record. Unrelated options are preserved and copied into
the per-instance config. Relative user library paths are resolved against the
project. User `workspaceRoots`/`packages`, structured library entries, or libraries
containing attached mod source trees produce an actionable configuration error.
Move source roots into module/dependency settings. Global home configs and
per-mod `.emmyrc.lua` are not inputs to this configuration path.

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
bash scripts/build.sh test buildPlugin
bash scripts/build.sh -PplatformTests -PrealMods=/Users/henning/factorio/mods test
bash scripts/build.sh verifyPlugin
```

`-PapiDocs=/path/to/doc-html` overrides the platform API test's local Factorio
installation. The real-mod test copies Lua/JSON/locale files, following symlinks,
into workspace-owned temporary directories; it never edits the originals.
Without `-PrealMods` that test is skipped.

Platform tests use actual LSP4IJ definitions and native analyzer/Node processes.
They cover isolated globals, editor routing to exactly the owning pair of servers,
locale navigation and VFS create/delete, restart, module removal, unsaved shared
dependency delivery/save/disk replacement, generated API hover/configuration,
and the reported `This:storage()` navigation in both real mods. These are IDE
platform tests, not interactive UI acceptance. The test runner disables LSP work
progress because synchronous IntelliJ test progress otherwise blocks LSP4IJ's
notification queue; normal IDE progress is unchanged.

### Known analyzer refresh limitation

Analyzer 0.25.1 reindexes the changed file without necessarily invalidating cached
exported table types in importers. Changing a dependency's returned table can
leave completion in an unchanged consumer stale until that consumer is
reanalyzed (for example, edited or reopened). The regression test separately
verifies that both servers receive the unsaved buffer, then reopens the importers
to verify their new fields. No full-workspace reindex or analyzer patch is hidden
in this manager. Save changes and restart the language services if stale types
persist. The earlier rapid-open/edit ordering limitation also remains.

The validated runtime is macOS arm64, IDEA 262, LSP4IJ 0.21.0 and the pinned
EmmyLua 0.25.1 development build. Other operating systems need runtime validation.

Plugin Verifier reports **Compatible** against the exact patched dependencies
and IDEA IU-262.10968.63. Its strict Gradle task remains nonzero for internal API
usages; deprecated and experimental APIs are also reported. Editor refresh uses
LSP4IJ's internal `LSPFileSupport`, as does the existing locale completion cache
integration. Do not interpret binary compatibility as an API stability guarantee.
