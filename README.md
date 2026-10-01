# Factorio Modding Tool Kit for IntelliJ

Java integration under `de.softwareforge.factorio`. Development release 0.4.1-dev.
Targets IntelliJ IDEA 2026.2 (build 262), EmmyLua2 and official LSP4IJ nightly 0.21.1-20260930-013028.
LuaLS and profiling are not supported.

## Build

Use Gradle directly with JDK 25 on `JAVA_HOME` (or selected as your Gradle JVM):

```sh
./gradlew help
./gradlew clean
./gradlew test buildPlugin
./gradlew -PplatformTests -PrealMods=/path/to/mods test
./gradlew verifyPlugin
```

Gradle owns the toolkit source-pin check, locked `npm ci`, CLI/server compilation,
third-party notices and bundling. Node/npm must be on `PATH`; `-PnpmExecutable`
selects another npm executable. Dependency installation and toolkit compilation
are incremental. Ordinary unit tests do not build the toolkit; platform tests and
plugin packaging do. `clean` removes this repository's build outputs, leaving
shared caches, external checkouts and their build outputs intact.

The IDE SDK and patched plugins are prepared separately. Gradle defaults to
`~/Applications/IntelliJ IDEA.app` (then `/Applications/IntelliJ IDEA.app`),
`../dev/plugins/lsp4ij` and
`../upstream/Intellij-EmmyLua2/build/prepared/IntelliJ-EmmyLua2`.
Override these with `-PideaPath`, `-Plsp4ijPath` and `-PemmyPath`.
`FMTK_IDEA_PATH` is also supported. `./gradlew verifyIdeDependencies` checks
prepared plugin source pins and the EmmyLua routing hook.

To prepare those dependencies, use `bash scripts/build-lsp4ij.sh` and the
EmmyLua2 repository's `bash scripts/build-module-patch.sh`. They retain their
separate source-build workflows. `clean`, `help` and `tasks` do not need them.

The toolkit defaults to `../upstream/vscode-factoriomod-debug`; override it with
`-PtoolkitPath`. Keep the accepted `work/intellij-toolkit` baseline checked out.
Its exact commit is pinned in `toolkit.lock`; LSP4IJ and the analyzer have their
own lock files. Review pin changes before adopting new dependencies.
The ZIP includes CLI bundles, licenses and `fmtk/BUILD.txt` identifying the
source revision. Node is not bundled; the tested runtime is Node 26.9.0.

`bash scripts/build.sh ...` remains an optional wrapper that selects JDK 25,
isolates the build home/caches/temporary files under `../dev/`, and captures
console logs in `build/logs/`. It simply delegates tasks to Gradle; `FMTK_JAVA_HOME`
overrides its JDK. Native Gradle uses the normal `JAVA_HOME`/`GRADLE_USER_HOME`
settings and console output. Gradle reports remain in `build/reports/`.
See [development layout](docs/DEVELOPMENT_LAYOUT.md) for all paths.

## Install and configure

Install the patched EmmyLua2 build containing the file-routing extension (work
branch `work/patched-build`, commit `54818f2` or later) and the official LSP4IJ nightly ZIP
`../upstream/lsp4ij/build/distributions/lsp4ij-0.21.1-20260930-013028.zip`,
then use **Install Plugin from Disk** for the Factorio ZIP in
`build/distributions`. On Apple Silicon use an EmmyLua2 release containing the
`aarch64` fix (the local `0.25.1-127-IDEA262-patched-modules` build is tested).

Choose **Factorio Mod** in New Project or New Module. This is a separate
wizard entry alongside Lua. Enter the mod's name, title, author and target
Factorio major/minor version. It creates `info.json`, `changelog.txt`, the three
data-stage files, `control.lua`, `settings.lua`, and `prototypes`, `scripts`,
`graphics` directories. Existing mod files are never overwritten.

For an existing mod, use **New Module from Existing Sources**, select its folder,
and select the **Factorio Mod** import model. Each module should have one content
root containing `info.json`. External folders are supported; nested dependencies
are not automatically imported as modules. Duplicate mod identities and ambiguous
multi-root modules are reported instead of selected arbitrarily.

**Tools → Factorio → Configure Factorio Project** lists detected mods. Services
start automatically for detected mods; explicit enable/disable is retained.
Factorio, Node, API JSON and CLI settings are shared by the project. Dependencies
and the package config have project defaults and optional module overrides.
Dependency paths are one per line; **Add…** opens a file/folder chooser.
Select a module and click **Edit Module Overrides…** to customize its dependencies
and package configuration. Changes stay staged until the parent Settings dialog
is applied. Toolchain, package, and run-configuration paths have browse buttons.
Select individual mod directories or mod ZIPs, not a parent containing many mods.
API generation uses matching local JSON, keeps generated data in IDE caches,
and runs a separate EmmyLua server rooted in each mod. Put analysis preferences
in the mod’s `.emmyrc.json`, `.luarc.json`, or `.emmyrc.lua`; the plugin supplies
Factorio integration settings separately and never rewrites these files.
See [per-mod language servers](docs/PER_MOD_SERVERS.md) for routing, configuration
and known limitations.
Factorio's data library uses the VS Code toolkit's exclusions, with additional
scenario helpers excluded for current Factorio layouts. In **Configure Factorio
Project → Factorio data library → Edit exclusions…**, edit paths/globs relative
to the Factorio `data` directory, one per line, or restore defaults. Changes take
effect when you apply the project settings; generated API/helper stubs remain
available. Exclusions apply to the installed game library, not your mod files.
Bundled mods (`base`, `core`, `elevated-rails`, `quality`, `recycler`, and
`space-age`) are mapped automatically when present in that data directory.
Imports such as `require('__base__.prototypes.entity.rail-pictures')` and
`require('__core__/lualib/collision-mask-util')` resolve to installed game files;
no dependency override is needed. Library exclusions still apply.
Module-root `info.json` files use the bundled FMTK schema for JSON validation,
completion, and documentation, including packaging options. No `$schema` field
is required. Nested locale/scenario metadata is not assigned the mod schema.

EmmyLua2’s **Editor → Code Style → Lua** page configures tab size, tabs versus
spaces, and editor indentation for the selected scheme (all Lua files). LSP
formatting uses tab size as its indentation width; other formatter options remain
in `.luafmt.toml`/`luafmt.toml`. EditorConfig and detected file indents can override
the scheme defaults.

Locale `.cfg` files have native syntax highlighting for sections, keys, comments,
placeholders, rich-text tags, and newline escapes. Customize colors under
**Editor → Color Scheme → Factorio Locale**. Highlighting works without a running
language server; FMTK continues to provide diagnostics and navigation.
Runtime attributes with different getter/setter types now retain both types.
For example, assigning `"coal"` to `LuaBurner.currently_burning` is valid, while a
subsequent read is still `ItemIDAndQualityIDPair?`. Hover displays Read and Write
types separately. The plugin requests FMTK's opt-in `--target emmylua` output;
regeneration automatically uses a separate cache from older definitions. This
requires the patched macOS arm64 analyzer below. Read-only/write-only diagnostics
and directional index operators are outside this feature.

Use **Regenerate Factorio API Definitions** after correcting toolchain settings.
Legacy active-mod settings remain usable until that root is attached as a module.

The development build now uses EmmyLua2
`0.25.1-127-IDEA262-patched-modules`, whose macOS arm64 server fixes colliding
short imports and named cross-mod imports across attached roots. Build it with
`../upstream/Intellij-EmmyLua2-worktrees/patched-build/scripts/build-module-patch.sh` first. `emmy-analyzer.lock`
pins the server source revision; build and isolated-profile preparation check
that pin. Other platform binaries use upstream 0.25.1 without the local resolver patch.
See [acceptance status](docs/ACCEPTANCE.md) for client completion limitations.

Locale keys and changelog support run alongside EmmyLua2. A **Restart Factorio
Language Services** action is available; process logs also appear in LSP4IJ.

Create a **Factorio** run configuration and select its module (a sole detected
module is selected automatically for new configurations). Set the Factorio executable, mod
directory, save ZIP, config.ini and working directory. For testing, configure a
separate Factorio write-data directory. Use **Debug** and set Factorio Lua
breakpoints in the gutter. The debugger talks directly to `factorio --dap`.

Enable **DAP logging** in the Factorio run configuration for verbose protocol
traces in the debug console. **Save DAP log to file** redirects protocol traces
exclusively to a separate UTF-8 `.jsonl` for each session, leaving normal game
output and debugger errors visible in the console. Choose an **Output folder**,
or leave it blank to use `factorio/dap` under IntelliJ's log directory (shown in
the field). Relative paths use the run configuration's working directory. The console reports the
created file's full path. Changes apply to the next session.

Each line contains a compact DAP message envelope, with its sequence number,
type, command/event and full payload. Requests, responses and events include
evaluation results and game output; newlines within strings remain JSON-escaped.
Console-only tracing keeps LSP4IJ's human-readable verbose format.
Separate process stderr and IDE console messages are not included.
Earlier files are retained; delete them
when no longer needed. A logging failure is reported in the console and does not
stop debugging or switch protocol tracing back to the console. The generic
LSP4IJ Debug Adapter Protocol settings page does not control this per-run setting.

## Mod release actions

The Factorio menu exposes package, version increment, changelog datestamp,
package scripts, ZIP upload, portal details and publish. These use FMTK's CLI
semantics, including `info.json` package options and hook scripts. Select the
mod in the editor or Project view before invoking them. When context does not
identify a mod, the action asks you to choose one. Commands run in the background and can be
cancelled; cancellation does not undo changes already made by hooks or publish.

Portal credentials are stored in IntelliJ PasswordSafe. They are supplied to
child processes through the environment, never command arguments or project
settings. Publishing may create commits/tags, push and update the portal. A
failure after an upload requires inspecting the release and repository before
retrying; there is no automatic retry or rollback.

For package commit/tag configuration, select an existing FMTK JSON config file
in project settings. The CLI reads it through `FMTK_CONFIG`.

## Development tests

```sh
python3 scripts/test-publishing.py
```

The publishing harness uses fake credentials, a loopback portal, and temporary
Git repositories beneath `build/test-work/publishing`. Its preload rejects
external network destinations. No real mod is published. See
`docs/IMPLEMENTATION.md` for outstanding checks and acceptance evidence.

## Isolated acceptance environment

With the test IDE closed, `python3 scripts/prepare-test-ide.py` prepares the
profile at `../dev/ide` using `../dev/fixtures/plugin` and the prepared dependencies. Run
`bash scripts/launch-test-ide.sh` to open it. This does not install into your
everyday IDE.

The reusable protocol fixture at `../dev/fixtures/protocol` is copied into
`build/test-work/protocol/fixture` by `scripts/test-language-services.py` and
`scripts/test-debug-protocol.py`; evidence and runtime files stay under that test-work directory.
`FACTORIO_TEST_ROOT` can override that fixture/evidence root. `EMMY_LS` selects
the analyzer binary for language-service tests without replacing the isolated
IDE installation. See
[acceptance status](docs/ACCEPTANCE.md) before treating this development build as
ready for regular use.

### Command PATH

Settings → Tools → Factorio Modding Tool Kit → **Command PATH** overrides the
complete executable search path for toolkit commands, release previews, and
child processes such as Git, GPG, and package hooks. Leave it empty to use
IntelliJ's resolved shell environment. For Homebrew on Apple Silicon, for example:

```text
/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
```

Use absolute directories separated by `:` (`;` on Windows). The value is literal:
`$PATH` and `~` are not expanded. Explicit Node and Factorio executable paths still
take precedence. **Check Factorio Toolchain** reports Git and GPG lookup paths.
This setting does not change the language-server or debugger launch environment.

### Publish output and author

Toolkit actions open a **Factorio** tool window with live output, completion or
failure status, and a Stop button. Closing an active console also cancels its
operation. Log files remain available; cancellation does not undo completed
publish steps. Output is a console, not an interactive shell.

**Author name** and **Author email** under Publishing in Factorio settings override
FMTK's `package.autoCommitAuthor` for publishing. Set both or leave both empty to
retain the package configuration/default. Other package settings are preserved,
and the source config file is unchanged. Git's committer and signing settings
remain in effect. The release confirmation displays the selected author.
