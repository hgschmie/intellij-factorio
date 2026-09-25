# Factorio Modding Tool Kit for IntelliJ

Java integration under `de.softwareforge.factorio`. Development release 0.1.0.
Targets IntelliJ IDEA 2026.2 (build 262), EmmyLua2 and LSP4IJ 0.21.0.
LuaLS and profiling are not supported.

## Build

The local build uses the installed IDEA SDK, the source-built patched EmmyLua2,
and LSP4IJ from the spike workspace. Install the toolkit's locked Node dependencies
before the first build. Run:

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

Open the mod or multi-mod workspace. Under **Tools → Factorio → Configure
Factorio Project**, enable services and select the active mod directory containing
`info.json`. Set executable paths and additional dependency directories or ZIPs.
Then run **Regenerate Factorio API Definitions**. Generation uses matching local
API JSON, keeps generated data in IDE caches, and merges libraries into
`.emmyrc.json` without replacing unrelated settings.

Locale keys and changelog support run alongside EmmyLua2. A **Restart Factorio
Language Service** action is available; process logs also appear in LSP4IJ.

Create a **Factorio** run configuration. Set the Factorio executable, mod
directory, save ZIP, config.ini and working directory. For testing, configure a
separate Factorio write-data directory. Use **Debug** and set Factorio Lua
breakpoints in the gutter. The debugger talks directly to `factorio --dap`.

## Mod release actions

The Factorio menu exposes package, version increment, changelog datestamp,
package scripts, ZIP upload, portal details and publish. These use FMTK's CLI
semantics, including `info.json` package options and hook scripts. Select the
active mod before invoking them. Commands run in the background and can be
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
