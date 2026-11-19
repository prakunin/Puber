# Readiness 2: trailer-video-hidden-regression

Verdict: **NOT READY**

Spec and plan at worktree HEAD 84eced53, base 3ec85287. Round 1 (`readiness-1.md`) still holds for AC coverage, citations, build order and scope; this round re-checks the two fixes and the new citations.

## Round 1 fixes

| Fix | Where checked | Status |
|---|---|---|
| 1. P0 rows AC-4/D3, AC-3/T1, AC-4/T2 marked | `test-design.md` and spec lines 149, 151, 152: `P0 red` | ok in the revised copy |
| 2. D4 adds source-run M2 first-frame fade | spec line 178 and the headless note at line 180 | ok in the revised copy |

## New citations (worktree, all checked)

| Citation | Resolves to | Status |
|---|---|---|
| `TrailerPreviewPlayer.kt:3-27` | first import `android.view.Gravity` to last `Media3UiR` | ok |
| `TrailerPreviewPlayer.kt:73` | `DisposableEffect(exoPlayer)` | ok |
| `SkeletonAsyncImageVisibilityTest` (spec line 145) | `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImageVisibilityTest.kt` | ok |
| `.flow/runs/detail-poster-trailer-flicker/test-design.md` M2 | rows AC-1, AC-2 at lines 58-59 | ok |

## Must fix

1. Spec `docs/superpowers/specs/2026-11-19-trailer-video-hidden-regression.md` holds two `## Test design` sections and two `## Open questions` sections. Lines 116-203 are the revised copy (match `test-design.md`); lines 208-292 are the stale round-1 copy, whose rows AC-4/D3 (line 241), AC-3/T1 (243) and AC-4/T2 (244) are `P0` with no `red`/`guard` mark and whose D4 (line 270) lacks M2. Delete lines 204-293 (the `Open questions: None.` stub and the stale Test design), keeping the revised Test design and the Q1/Q2 Open questions table.
