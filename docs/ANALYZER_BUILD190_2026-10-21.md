# Analyzer build190 — 2026-10-21

Development analyzer pin: `b27a6d00febe537ffaf72d47b2124c561a5bf201`.
EmmyLua2 version: **0.30.0-190**, including independent UP-108
(`feat/unused-self-diagnostic`, signed `ec1094a9`).

Implicit colon-method `self` uses the new `unused-self` diagnostic; other local
variables and explicit parameters retain `unused`. Each code can be disabled or
assigned a severity independently. Both retain hint severity and unnecessary
marking by default. Disable `unused-self` to retain other unused diagnostics;
list both codes to preserve the former effect of disabling `unused` alone.

Analyzer and bundled EmmyLua2 schemas advertise the new code. Packaged raw-LSP
validation confirms all four disable combinations, annotation suppression,
severity override, completion and all three suppression actions. No FDE runtime
source change is needed. The 37-file frozen LTN baseline retains zero diagnostics.

All **2,577 standalone / 3,058 combined workspace tests**, Clippy/rustfmt pass.
Both native tests ran and passed without failures/errors/skips:
`testDefaultServerRespectsRootOwnershipAndRelease` and
`testGeneratedApiWithSeparateWorkspaces`. Evidence: `build/logs/emmy190-platform.log`
and `emmy190-native-results.json`. EmmyLua2 client routing source is unchanged;
its routing suite was not repeated (last run:30 passing in build188).

Installable archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-190.zip`.
SHA-256: `62d7b7f816953261db0e1f7d163402ba2a6a4389dbb4f1c22caf4e4aabeaf9ec`.
Descriptor, embedded pin, binary identity, executable permissions and bundled
schema are verified. Only macOS arm64 receives the custom analyzer. FDE stays
0.9.3-dev with the same ZIP; LSP4IJ remains SNAPSHOT-15. This update changes only
FDE's development analyzer pin and receipt. No push, upstream PR, user settings
change or installation into the running IDE.

Full evidence: workspace `upstream/memory/UNUSED_SELF_DIAGNOSTIC_2026-10-21.md`.
