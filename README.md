# Factorio Modding Tool Kit for IntelliJ

Java integration under `de.softwareforge.factorio`. Development release 0.1.0.
Targets IntelliJ IDEA 2026.2 (build 262), EmmyLua2 and LSP4IJ 0.21.0.
LuaLS and profiling are not supported.

## Build

The local build uses the installed IDEA SDK, the source-built patched EmmyLua2,
and LSP4IJ from the spike workspace. Install the toolkit's locked Node dependencies
before the first build. The exact toolkit commit is pinned in `toolkit.lock`;
review and update that pin when deliberately adopting toolkit changes. Run:

```sh
bash scripts/build.sh test buildPlugin
```

`FMTK_IDEA_PATH` and `FMTK_JAVA_HOME` override the local SDK/JDK paths.
The sibling `vscode-factoriomod-debug` repository must be on the accepted
`work/intellij-toolkit` baseline. The ZIP includes the built CLI, JavaScript chunks,
toolkit license and a `fmtk/BUILD.txt` identifying its source commit. Node itself
is not bundled. The tested runtime is Node 26.9.0.

## Install and configure

Install EmmyLua2 and LSP4IJ, then use **Install Plugin from Disk** for the ZIP in
`build/distributions`. On Apple Silicon use an EmmyLua2 release containing the
`aarch64` fix (the local `0.24.0-115-IDEA262-patched` build is tested).

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
Dependency paths are one per line, including in the editable module table.
API generation uses matching local JSON, keeps generated data in IDE caches,
and updates managed entries in `.emmyrc.json` while preserving unrelated settings.
Use **Regenerate Factorio API Definitions** after correcting toolchain settings.
Legacy active-mod settings remain usable until that root is attached as a module.

The development build now uses EmmyLua2
`0.24.0-115-IDEA262-patched-modules`, whose macOS arm64 server fixes colliding
short imports and named cross-mod imports across attached roots. Build it with
`../Intellij-EmmyLua2/scripts/build-module-patch.sh` first. `emmy-analyzer.lock`
pins the server source revision; build and isolated-profile preparation check
that pin. Other platform binaries in this local package remain unpatched.
See [acceptance status](docs/ACCEPTANCE.md) for client completion limitations.

Locale keys and changelog support run alongside EmmyLua2. A **Restart Factorio
Language Service** action is available; process logs also appear in LSP4IJ.

Create a **Factorio** run configuration and select its module (a sole detected
module is selected automatically for new configurations). Set the Factorio executable, mod
directory, save ZIP, config.ini and working directory. For testing, configure a
separate Factorio write-data directory. Use **Debug** and set Factorio Lua
breakpoints in the gutter. The debugger talks directly to `factorio --dap`.

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
Git repositories beneath `../spike/plugin-publishing-tests`. Its preload rejects
external network destinations. No real mod is published. See
`docs/IMPLEMENTATION.md` for outstanding checks and acceptance evidence.

## Isolated acceptance environment

With the test IDE closed, `python3 scripts/prepare-test-ide.py` prepares the
workspace-only profile from the existing spike fixture and dependencies. Run
`bash scripts/launch-test-ide.sh` to open it. This does not install into your
everyday IDE.

The existing copied protocol fixture at `../spike/plugin-protocol` can be tested
with `scripts/test-language-services.py` and `scripts/test-debug-protocol.py`.
`FACTORIO_TEST_ROOT` can override that fixture/evidence root. See
[acceptance status](docs/ACCEPTANCE.md) before treating this development build as
ready for regular use.
