# Factorio Problems — 0.8.0-dev

Open **View → Tool Windows → Problems**, then choose **Factorio**.
The tab combines the diagnostics received by FDE's per-module EmmyLua and FMTK
locale/changelog services. It does not collect unrelated default-server reports,
Factorio API library diagnostics or other projects' results.

Choose **All modules** or one module, select a severity filter, and optionally
filter by text (message, file, module, source or diagnostic code). Results are
grouped by module and relative file path. Each message shows its source, code,
line and column. Double-click or press Enter/F4 to open its exact location.
Expand/collapse controls operate on the whole tree.

Opening the Factorio tab starts Lua and locale/changelog services for **all
configured modules**, including modules without open files. **Start services**
retries stopped services; already-running servers are reused. These services stay
alive for this project session, including after the last editor closes. Modules
added later start automatically; removed or disabled services are still stopped.
Server startup runs in the background, while registry and UI updates stay on EDT.

Ordinary file opening remains lazy until project-wide diagnostics are requested.
Filtering only reads cached results; the tab does not introduce another diagnostic
request loop. UI updates are coalesced, and hidden/inactive views defer tree
rebuilding until shown.

## What “project-wide” covers

The tab displays the latest diagnostics supplied by the servers:

- EmmyLua workspace reports can include closed Lua files. Open documents use the
  normal document-diagnostic path. Both UP-045 and the UP-008 comparison can feed
  this tab; their scheduling/freshness behavior is unchanged.
- FMTK currently publishes locale/changelog diagnostics when those documents are
  opened or edited and clears them on close. Closed locale/changelog files are
  indexed for language features, but that does not produce project-wide diagnostic
  reports. This release does not change that server behavior.
- Startup and the first workspace analysis take time. Disabled or failed services
  have no results. An empty view means
  no problems have been reported, not that every project file passed an analysis.

Each server's results are replaced independently, so clearing Lua warnings does
not erase locale results for the same file. Empty reports clear their messages.
Module removal, service shutdown/restart, and file deletion/rename clear retained
results. Late reports from retired sessions cannot restore removed entries.
Generated/dependency files outside the owning module are filtered out.

## Dependencies and implementation

Install FDE `intellij-factorio-0.8.0-dev.zip` and LSP4IJ
`lsp4ij-0.21.1-SNAPSHOT-workspace-diagnostics-patched4.zip`, then restart IntelliJ.
Keep the installed EmmyLua2 build158 variant; no analyzer rebuild is needed.

LSP4IJ's independent `feat/diagnostic-update-hook` branch provides a small generic
`LSPDiagnosticFeature.diagnosticsUpdated` hook after diagnostic cache updates.
FDE converts these snapshots into immutable presentation data, tracks each
server session, and registers a native Problems-view tab. No polling, duplicate
workspace diagnostic request loop, or analyzer patch is involved. Changes remain
local; no upstream PR was opened for the hook.
