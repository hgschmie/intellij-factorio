# Analyzer collection-union inference — 2026-10-19

Development analyzer pin: `4bf6a91ea91b031f44d99ca673bfd8d9c74983a4`, packaged in EmmyLua2 **0.30.0-185**.
UP-097 `d2351942`, independent on upstream/main `51f2aa7b`, fixes pairs over
collection unions and inherited generic dictionaries such as LuaCustomTable.
Signed integration `2a1e0199` retains UP-091/092 and the existing work stack.

The frozen actual LTN tools.lua now infers key uint32|string|integer and value
LuaPlayer; the pairs hover agrees, player.valid/index resolve, and diagnostics
remain zero. Six additional generated-API before/after comparisons pass.
All 2,548 standalone and 2,944 combined workspace tests pass, as do Clippy and
rustfmt. Both forced native tests pass (two executed, zero failures/errors/skips).
Evidence: build/investigations/union-iterator185-20261019/.

Verified archive: `../plugins/IntelliJ-EmmyLua2-0.30.0-185.zip`.
SHA-256: `a03a5106c02f090056587a6471043721caca31ec4c3ececbc3f0090c3e7ee841`. Only macOS arm64 receives the custom analyzer.
FDE remains 0.8.6-dev with its existing ZIP; LSP4IJ build10 is unchanged. This
pin is for development validation, not an FDE runtime release requirement.
No push, PR or regular IDE installation. Unrelated FDE edits are preserved.

[Full report](../../upstream/memory/LTN_UNION_ITERATOR_2026-10-19.md),
[UP-097 inventory](../../upstream/FIX_INVENTORY.md).
