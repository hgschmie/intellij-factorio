# Analyzer build198 — 2026-10-21

Version/archive: `0.30.0-198` / `IntelliJ-EmmyLua2-0.30.0-198.zip`.
Analyzer pin: `d4cc1386c923c148d076c453cd85c43601fc315b`.
Independent UP-112 `fc482372` on `fix/mutable-captured-variable-flow` accounts
for visible writes to captured locals at closure entry. Annotations, local
assignments/guards, unchanged captures and freshly created locals retain their
contracts and precision. All prior patches remain integrated.

The packaged analyzer removes the false always-truthy warning at train-tracker
scripts/event-setup.lua:27, in both the frozen reproduction and the latest source
copy. Other frozen-file diagnostics are unchanged. The latest source has one
separate return-type-mismatch at line200. LTN exactly matches build197:37 files,
zero warnings/errors and two existing local-alias hints. Original mods unchanged.

All 1,604 standalone analysis tests, 3,116 combined workspace tests, Clippy and
rustfmt pass. Both native FDE integration tests pass. Descriptor, analyzer pin,
binary identity, executable0755 and retained unused-self schema are verified.
ZIP SHA-256: `7992c7ca71f6811ba57f53824c280bf241632efa402145e7a7fc46af0169c2e3`.
Archive: workspace `plugins/IntelliJ-EmmyLua2-0.30.0-198.zip`.
Native evidence: build/logs/emmy198-platform.log and emmy198-native-results.json.

Only macOS arm64 receives the custom analyzer. FDE's development pin follows
this package; its 0.9.3-dev archive and LSP4IJ SNAPSHOT-15 archive remain unchanged.
Local signed commits only; no push, PR or IDE installation. Full evidence:
workspace `upstream/memory/TRAIN_TRACKER_EVENT_UPVALUE_2026-10-21.md`.

