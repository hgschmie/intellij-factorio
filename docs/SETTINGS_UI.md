# Settings UI (0.2.5-dev)

The settings page, Factorio run configuration and new-mod form use natural-height
controls in a top-aligned form. Labels use their preferred width, scaled spacing,
and label-for associations. Settings are grouped into Toolchain, Project Defaults
and Mod Modules. The Services selector controls language services.

File and directory paths use IntelliJ TextFieldWithBrowseButton controls with
single-file or single-directory chooser descriptors. Dependencies support direct
multiline editing and an Add chooser for multiple mod directories/ZIPs. Module
overrides use a separate dialog instead of editing paths inside table cells.
Override and exclusion dialogs stage changes; only Apply in Settings persists them.

The supplied Factorio thumbnail is displayed at 16 logical pixels for the Tools
menu group, mod module rows, module wizard/import and Factorio run configurations.
`src/main/resources/icons/factorio.png` is an unmodified copy of
`/Applications/factorio.app/Contents/data/base/thumbnail.png`, supplied by the user.
It is a Factorio/Wube Software asset, not an original artwork of this project.

Design references:
- https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html
- https://plugins.jetbrains.com/docs/intellij/layout.html
- https://plugins.jetbrains.com/docs/intellij/input-field.html
- https://plugins.jetbrains.com/docs/intellij/file-and-class-choosers.html
- https://plugins.jetbrains.com/docs/intellij/icons.html

Validation on IntelliJ IDEA IU-262.10968.63 / macOS arm64:
`FactorioUiTest` constructs real platform controls and checks staged settings,
reset/apply, compact sizing, label spacing, browse controls, run-configuration
round trips, icon dimensions and plugin action icon loading. It renders previews
under `build/reports/ui/`. These are component renders in the test look and feel,
not screenshots of the running IDE.

Live verification completed on 2026-09-28 using the isolated `dev/ide` profile
and plugin 0.2.5-dev after restarting the desktop automation host:
- Reviewed settings, module overrides and run configurations in the dark theme.
  Single-line controls have natural heights and aligned labels; path browse
  buttons are visible. Module and run-configuration gear icons render correctly.
- Node executable, API documentation folder, dependency and save ZIP controls
  open native macOS file choosers.
- Selecting `/Applications/factorio.app/Contents/doc-html` returns that directory
  to the API docs field. Canceling Settings and reopening restores the original
  blank value, with Apply disabled.
- Module override checkboxes enable the dependency Add button and package-file
  browser. The override dialog was canceled without persisting test changes.
- The Tools menu exposes the Factorio submenu and actions. The automation API
  returned no screenshot for that native menu, so its icon was not visually
  verified (action icon loading is covered by the platform test).

Live screenshots are in `build/reports/ui/live/`: `settings.png`,
`module-overrides.png`, and `run-configuration.png`. No regular-installation
settings or real mod sources were edited. The fixture still has a pre-existing
`workspace.workspaceRoots` configuration warning; language-server behavior was
not part of this UI check. Multi-file selection, saving module overrides through
Apply, and the new-mod wizard were not re-exercised in this live pass.
