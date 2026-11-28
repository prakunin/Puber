# Readiness 1: trailer-preview-load-control

Verdict: **READY**

## Coverage

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | Task 1 | `TrailerPreviewLoadControlTest.trailerPreviewBuffer_matchesAcceptedValues` (P0 red) |
| AC-2 | Task 1 | `TrailerPreviewLoadControlTest.buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` (P0 red) |
| AC-3 | Task 1 | Review checks A, B (P1) |
| AC-4 | qa-device after Task 1 | Device manual 1 (P1) |
| AC-5 | qa-device after Task 1 | Device manual 2, 3 (P1) |

## Citations sampled (worktree at `d4a155a1`)

| Citation | Claimed | Found |
|----------|---------|-------|
| `TrailerPreviewPlayer.kt:62-64` | `remember`, `ExoPlayer.Builder(context)`, `.build()` | yes |
| `TrailerPreviewPlayer.kt:151` | `surfaceNeedsRedraw` | yes |
| `TrailerPreviewPlayer.kt:6`, `:20`, `:24` | imports `OptIn`, `C`, `UnstableApi` | yes |
| `TrailerPreviewPlayer.kt:89-91`, `:94-97`, `:105-108`, `:118-124` | first frame, release, url stop, `ON_STOP` stop | yes |
| `PlaybackController.kt:710-720` | warm-up `DefaultLoadControl.Builder` chain | yes |
| `PlaybackController.kt:709-719` (spec Scope) | same chain | off by one: 709 is `sourceFactory`; non-blocking, Design and plan cite 710-720 |
| `VideoItemGridDetails.kt:602-626`, `:619` | poster fade, call site, `onFirstFrameRendered` | yes |
| `DeviceBufferConfig.kt:77-88` | `BufferParams` field names | yes |
| `TrailerSurfaceRedrawTest.kt` | JUnit 5, `internal class`, no extension | yes |

## Checks

| Check | Result |
|-------|--------|
| Build green per task | One task: test, helper and call site land in one commit with fast gate and test run |
| Dependency order | N/A: single task |
| P0 rows name defect, mark, test name | AC-1: value drift or swapped durations, red; AC-2: mis-ordered durations throw, red |
| Out of scope | Plan limits `app/src/main` diff to `TrailerPreviewPlayer.kt`; no back buffer, no shared factory, no test options |
| Blocking open questions | None; Q1-Q3 answered |
