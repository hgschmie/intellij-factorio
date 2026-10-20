# Analyzer equality and alias guards — build 183 (2026-10-18)

FDE remains 0.8.6-dev and its existing ZIP is unchanged. The development analyzer
pin is f1d218d402dcc38bcb43415234073093dd644d33. EmmyLua2 0.30.0-183 packages
UP-095 and the UP-078 amendment for macOS arm64. LSP4IJ remains SNAPSHOT-7.

UP-095 preserves required parameters after inequality exit guards. UP-078 now
tracks stable local aliases and preserves computed guards across nonconflicting
branch merges, while invalidating facts after relevant writes and mutators.
The saved refactored LTN cache.lua/stop-update.lua diagnostics change 2+1 -> 0+0;
the deletion control still warns. Original mod sources and the regular IDE
installation were not changed.

Validation: 2,929 combined analyzer workspace tests, standalone suites of 1,528
and 1,559 analysis tests, workspace/standalone Clippy and rustfmt. Both native
IntelliJ integration tests pass with fresh XML: testAsymmetricApiAttributes and
testGeneratedApiWithSeparateWorkspaces (2 executed, zero failures/errors/skips).
The sandboxed JVM first aborted in macOS AppKit registration before any tests;
the isolated native-access rerun passed. Logs: build/logs/equality-alias183-*.

Archive: ../plugins/IntelliJ-EmmyLua2-0.30.0-183.zip.
SHA-256: fd511b115b55c88d01af359a399dd48f994d0a436eeb789bf6fcac14963438bc.
Descriptor, embedded analyzer pin, executable identity and Unix0755 permissions
verified. The packaged executable matches the release used for the LTN replay.
Full report: ../upstream/memory/LTN_EQUALITY_ALIAS_FIXES_2026-10-18.md.
No remote push, upstream PR or IDE installation. All task commits are signed.
