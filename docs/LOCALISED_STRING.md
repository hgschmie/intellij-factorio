# LocalisedString arrays

FDE 0.8.5-dev bundles FMTK definitions matching Factorio's published API:

```lua
---@alias LocalisedString string|number|boolean|LuaObject|nil|LocalisedString[]
```

The prototype/data-stage type has the smaller documented domain:

```lua
---@alias data.LocalisedString string|data.LocalisedString[]
```

With EmmyLua2 build167, an array explicitly typed `LocalisedString[]` can be
passed or returned wherever a `LocalisedString` is expected. A cast also works,
but is unnecessary for that conversion. Factorio still expects the first array
element to be the localisation key (including the special empty-string key for
concatenation); the annotation follows the API's array type instead of enforcing
the key with a tuple.

The old nil omission was a workaround for EmmyLua issue #1195. Its original
false optional-field diagnostic no longer reproduces on the current analyzer;
empty optional-field objects pass and invalid values remain rejected. The
undocumented coordinate-object alternative was removed to match the API union.

Two independent fixes are included:

- UP-069: the analyzer checks array values against each union alternative,
  including named and recursive aliases, while preserving element-type checks.
- UP-070: FMTK's handwritten runtime/data aliases match the API's recursive arrays.

UP-041 already handles the related named-union function-argument path. UP-069
also fixes casts and returns and is independently based on upstream/main.

Install both **FDE 0.8.5-dev** and **EmmyLua2 0.30.0-167-IDEA262-patched-modules**.
The custom analyzer is currently built for macOS arm64. Keep LSP4IJ patched4.
Regenerate Factorio API definitions after upgrading; the changed bundled CLI
selects a new generated-API cache. If an external FMTK CLI is configured, update
that CLI too or select the bundled one.

Validation includes analyzer positive/negative regressions, a native IntelliJ
test using generated runtime and data definitions, and raw-LSP checks against a
fixed copy of LogisticTrainNetwork. Original mod files remain unchanged.
