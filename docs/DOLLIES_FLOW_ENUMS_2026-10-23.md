# Numeric enums and falsy-member flow

FDE **0.9.4-dev** updates its bundled toolkit API generator. Generated
`defines.direction` and `defines.transport_line` declarations describe integer
values while retaining named members. The matching EmmyLua2 **0.30.0-203**
update preserves integer arithmetic through these enum contracts and corrects
falsy-member receiver flow. These changes address the reported Dollies transport
line, rotation and cursor-ghost diagnostics.

The coordinated update requires both FDE and EmmyLua2. Keep LSP4IJ
**0.21.1-SNAPSHOT-15**. The custom analyzer patches apply to macOS arm64.

## Managed API upgrade

On project startup, FDE generates definitions from the selected Factorio API
documentation and bundled toolkit. Its cache identity includes the source API
documents, annotation format and CLI bytes. Generator changes update the CLI's
content-named module import, selecting a new managed library automatically.
Identical inputs reuse the existing library, and older cache directories remain
intact. No manual editing or deletion of managed API caches is required.

A user-configured CLI continues to override the bundled toolkit; update that
external CLI separately to obtain its generator fixes.

## Validation

All **20 native tests pass**, with zero failures/errors/skips, against the corrected
EmmyLua2 build203 and the new toolkit. This includes all 18 existing checks, a
generated-enum regression and the cache-upgrade regression. Numeric enum tests
require integer indices/arithmetic, named member identity/navigation and rejection
of broad-number/string arguments. The same test failed against the old generator
and build 202; its assertions are unchanged.

The cache regression covers changed generator bytes at an unchanged path/timestamp,
identical-input reuse after relocation and preservation of the previous cache.
The actual toolkit update changes the cache key from
`api-f0ddd85e50ea0e5bf03b85ca` to `api-e7d9c243f2c7f6ff1d798c67` for the same
Factorio **2.1.23** documents. No FDE cache implementation change was needed.
Only `runtime-api/defines.lua` differs among 1,171 generated files; all 218 original
mod input hashes, 20 private EmmyLua2 files and the immutable API snapshot remain
intact after testing. Live user caches were not edited.

The unit suite has **83 passed, zero failures/errors and one existing opt-in
real-game DAP test skipped**. This result remains valid for corrected 203: all 378
EmmyLua2 plugin class files and its other Java libraries match the tested dependency;
the analyzer executable is covered by the fresh native run.

Owner receipts record **3,250 Rust tests** and static checks passing, plus
**203 packaged case executions, 255 type observations and five navigation checks**.
The LTN 37-file comparison exactly matches build 202 diagnostics and sampled hovers,
including its two separately classified API-model warnings and two hints. The
initial 203 candidate's generic-assignment regression was corrected before release;
its failed comparison and intermediate results remain preserved. Two equivalent
boolean hover spellings use the separately sealed comparison supplement.

Toolkit generator/typecheck/changed-file lint checks pass. Its full suite retains
one independently reproduced baseline settings-list failure and two existing skips;
four repository-wide lint errors are in unchanged files. Those limits are recorded
in the upstream source and integration receipts.

## Exact release inputs

| Input | Identity |
| --- | --- |
| Toolkit | `1c1db13d8543714bbf4471ff9db7c63bea2238fc` |
| Bundled CLI SHA-256 | `4197640edba13b9b4d7e521a9e0d716b7e2cbf6c5f95779174a1204c58754b3a` |
| Analyzer | `633268c36d7a429a2ac097f5ffacb4ec22fa747b` |
| EmmyLua2 ZIP SHA-256 | `ad5978b48ea0c47f2f921e9be45b78950f9eb3df677127b7e18af6793786559c` |
| macOS arm64 analyzer SHA-256 | `6e9bcc1683cbd26910d7f5b1ab9edea1d2bfeaadb8c3c6bf271335bf3e48780f` |
| FDE 0.9.4-dev ZIP SHA-256 | `c4db2b9e86c7b13a44b7b7fd67c100b2c790b4207a607750af5bcbaa4ace539a` |

The FDE archive's descriptor, toolkit revision and bundled JavaScript match these
tested inputs. The private toolkit bundle differs from Integration's bundle only
in esbuild dependency-path labels and corresponding content-named chunk references;
FDE generation, native tests and packaging all use the exact CLI hash above.
The existing FDE 0.9.3-dev, EmmyLua2 build 202 and LSP4IJ SNAPSHOT-15 archives are retained.

Evidence is under `build/reports/dollies-flow-enum-20261023/`: `corrected 203-native/`
contains XML/HTML/IDE logs and integrity results; `unit-new-toolkit/` contains unit
results; API manifests, dependency equivalence and archive verification are adjacent.
Final native log: `build/logs/dollies-flow-enum-20261023-134329.log`.
Final distribution task log: `build/logs/dollies-flow-enum-20261023-134607.log`.
No push, PR or regular IDE installation was performed.

See the [shared implementation and delivery finding](../../memory/findings/F-20261023-124808-dollies-flow-enum-build/finding.md)
for source-layer ownership and final delivery gates.
