# Readiness 1: trailer-video-hidden-regression

Verdict: **NOT READY**

Spec `docs/superpowers/specs/2026-11-19-trailer-video-hidden-regression.md` (committed 9dd98f83), plan `docs/superpowers/plans/2026-11-19-trailer-video-hidden-regression.md` (untracked in the worktree), base 3ec85287.

## AC coverage

| AC | Plan task | Test design row | Status |
|---|---|---|---|
| AC-1 | Task 2 | D1 | ok |
| AC-2 | Task 2 | D2 | ok |
| AC-3 | Task 2 | T1 | P0 row has no `red`/`guard` mark (fix 1) |
| AC-4 | Task 2 | D3, T2 | P0 rows have no `red`/`guard` mark (fix 1) |
| AC-5 | Task 2 | unit guard, D4, T3 | D4 leaves out the first-frame fade check (fix 2) |
| AC-6 | Task 1 | five `surfaceNeedsRedraw_*` rows | ok |
| AC-7 | Task 2 | `check.full` | ok |

## Citations (worktree, all checked)

| Citation | Resolves to | Status |
|---|---|---|
| `TrailerPreviewPlayer.kt:125-139`, `:126-133`, `:127-132`, `:134-137` | `AndroidView`, `factory`, `PlayerView(ctx).apply`, `update` | ok |
| `TrailerPreviewPlayer.kt:36-38`, `:40`, `:85-87`, `:140`, `:142-154` | KDoc SurfaceView sentence, `@UnstableApi`, `onRenderedFirstFrame`, closing brace, `updateContentGravity` | ok |
| `VideoItemGridDetails.kt:469`, `:482-493`, `:506` | `trailerRendered`, `TrailerPreviewPlayer` call, `graphicsLayer` alpha | ok |
| `VideoDetailsPosterStateTest.kt:20-29` | `internal class` + first JUnit 5 test | ok |
| `config/detekt/detekt.yml:817-820` | `UnusedParameter`, allowed `_` | ok |
| `gradle/libs.versions.toml:7`, `:27` | `composeBom`, `media3 = "1.11.0"` | ok |
| Call sites `ContentListScreenContent.kt:166`, `FavoriteScreenContent.kt:99`, `DetailsScreenContent.kt:189` | `VideoItemGridDetails` / `VideoDetailsMedia`, both reaching the single player call at `VideoItemGridDetails.kt:483` | ok |

## Build order and scope

Task 1 adds an `internal` predicate plus its tests (green after the red compile run); Task 2 consumes it. No task leaves the build red. Production changes stay in `TrailerPreviewPlayer.kt`; Out of scope holds. Blocking Q1 is answered; Q2 is non-blocking and answered.

## Must fix

1. Test design, Traceability (spec and `test-design.md`): mark the P0 rows AC-4/D3, AC-3/T1 and AC-4/T2 as `red` (each shows the 1.12.1 symptom per `diagnosis.md#Evidence` row 2) or `guard`.
2. Test design, D4: add the source run's M2 (`.flow/runs/detail-poster-trailer-flicker/test-design.md:80`: poster at full opacity until the first frame, then a ~300 ms fade, no black shutter or skeleton frame). AC-5 names that behaviour, the fix acts at exactly that moment, and M1, M3 and M5 do not cover it; on the emulator only T3 would catch it.
