# Changelog duplicate entries — 0.9.3-dev

FMTK previously compared each physical changelog line across a category. In LTN,
two different bugfix entries share the continuation credit “Reported by
@chrisjmendoza in PR #78.”, which caused a false `entry.duplicate` error.

UP-100 compares complete entries, including continuation text and nested content.
Nested entries are compared only with siblings under the same parent. Distinct
categories and versions retain independent scopes; repeated category headings
within a version still share top-level duplicate checks. True duplicate entries
retain their error and a link to the first entry.

The source bug originates in upstream FMTK `f60b105` (2025-10-21). The isolated
fix branch is `fix/changelog-logical-entry-duplicates`, based on our required
`work/intellij-toolkit` baseline `11419a3`. No local analyzer patch caused it.

Validation: six of nine new regressions fail before the patch; all nine pass
afterwards. The combined focused and LSP suite passes 44 tests, including the
Factorio changelog fixture and locale tests. TypeScript checking and ESLint pass.
The unchanged, frozen LTN changelog now has zero diagnostics.

Build and packaged-server validation are recorded in
[the delivery receipt](../../upstream/memory/CHANGELOG_DUPLICATE_INVESTIGATION_2026-10-21.md).
Only FDE is rebuilt for this toolkit change. The analyzer refresh is packaged
separately as EmmyLua2 build186, and LSP4IJ remains 0.21.1-SNAPSHOT-13.

FDE0.9.3-dev package validation passes, including replay through the extracted
CLI: zero diagnostics for the actual changelog, one error for the true-duplicate
control. 83 FDE unit tests pass; one environment-dependent test is skipped.
ZIP SHA-256: `82eeb3355df04075667c42fcf4ebaebe1728d06f19bba55c0f8183b40393488d`.
The validated archive is copied flat into workspace plugins/.
