# Give the trailer preview player a small LoadControl - plan

**Spec:** `docs/superpowers/specs/2026-11-28-trailer-preview-load-control.md`
**Goal:** The panel trailer preview `ExoPlayer` buffers 10-15 s capped at 12 MiB instead of the media3 streaming defaults.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh && bash tools/tests/test-itest-tooling.sh`.
- Both run in the worktree `/home/dev/orca/workspaces/Puber/trailer-preview-load-control`, never `./gradlew`; set `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Only `TrailerPreviewPlayer.kt` and the new test file change. `app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt` and `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` get no diff (AC-3). No back buffer, no shared factory, no test options in `app/build.gradle.kts`.
- AC-4 and AC-5 are device observations for `qa-device` after this task (spec, Test design: Manual checks 1-3); the implementer does not touch a device.

---

## Tasks

### Task 1: Build the preview player with its own small DefaultLoadControl

**Covers:** AC-1, AC-2, AC-3
**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt` - add params class, constant and builder at top level after `surfaceNeedsRedraw` (`:151`); attach in the `remember` block (`:62-75`, builder chain `:63-64`). Existing imports reused: `androidx.annotation.OptIn` (`:6`), `androidx.media3.common.C` (`:20`), `androidx.media3.common.util.UnstableApi` (`:24`). Shape to copy: warm-up load control `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:710-720`. Field names follow `DeviceBufferConfig.BufferParams` (`app/src/main/java/com/kino/puber/ui/feature/player/vm/DeviceBufferConfig.kt:77-88`).
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewLoadControlTest.kt` (new; style of `TrailerSurfaceRedrawTest.kt` in the same folder: `internal class`, JUnit 5 `org.junit.jupiter.api.Test`, no extensions)

**Consumes:** `DefaultLoadControl.Builder` (`setAllocator`, `setBufferDurationsMs`, `setTargetBufferBytes`, `setPrioritizeTimeOverSizeThresholds`, `build`), `DefaultAllocator(boolean, int)`, `ExoPlayer.Builder.setLoadControl`.
**Produces:**

```kotlin
internal data class TrailerPreviewBufferParams(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
    val prioritizeTimeOverSize: Boolean,
)

internal val TRAILER_PREVIEW_BUFFER = TrailerPreviewBufferParams(
    minBufferMs = 10_000,
    maxBufferMs = 15_000,
    bufferForPlaybackMs = 1_000,
    bufferForPlaybackAfterRebufferMs = 2_000,
    targetBufferBytes = 12 * 1024 * 1024,
    prioritizeTimeOverSize = false,
)

@OptIn(UnstableApi::class)
internal fun buildTrailerPreviewLoadControl(
    params: TrailerPreviewBufferParams = TRAILER_PREVIEW_BUFFER,
): DefaultLoadControl
```

- [x] Write `TrailerPreviewLoadControlTest` with the two tests of the spec's Test design (Traceability AC-1, AC-2):
  - `trailerPreviewBuffer_matchesAcceptedValues`: `assertEquals` on each of the six fields of `TRAILER_PREVIEW_BUFFER` against the literals `10_000`, `15_000`, `1_000`, `2_000`, `12 * 1024 * 1024`, `false` (AC-1).
  - `buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer`: `assertDoesNotThrow` on `buildTrailerPreviewLoadControl()` and `assertNotNull` on the result (AC-2).
- [x] Red run: `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.uikit.component.details.TrailerPreviewLoadControlTest'` fails to compile on unresolved `TRAILER_PREVIEW_BUFFER` / `buildTrailerPreviewLoadControl`; record it in the Implementation record.
- [x] Add the class, constant and function above to `TrailerPreviewPlayer.kt` after `surfaceNeedsRedraw` (`:151-152`). Body: `DefaultLoadControl.Builder().setAllocator(DefaultAllocator(/* trimOnReset = */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE)).setBufferDurationsMs(params.minBufferMs, params.maxBufferMs, params.bufferForPlaybackMs, params.bufferForPlaybackAfterRebufferMs).setTargetBufferBytes(params.targetBufferBytes).setPrioritizeTimeOverSizeThresholds(params.prioritizeTimeOverSize).build()`. A new allocator per call; nothing cached at top level. Imports: `androidx.media3.exoplayer.DefaultLoadControl`, `androidx.media3.exoplayer.upstream.DefaultAllocator`.
- [x] Insert `.setLoadControl(buildTrailerPreviewLoadControl())` between `ExoPlayer.Builder(context)` (`:63`) and `.build()` (`:64`), inside the `remember { }` lambda (`:62`). Nothing else in the composable changes.
- [x] If the AC-2 test fails with a "Method ... not mocked" error, stop and report it under Blocked; do not add `unitTests.isReturnDefaultValues` or other test options.
- [x] Run the fast gate and the test command above (green); commit, e.g. `Give the trailer preview player a small load control`.

**Done when:** the test command reports both tests passed; the fast gate is green; `git -C /home/dev/orca/workspaces/Puber/trailer-preview-load-control diff 8c3117ea --stat -- app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` prints nothing; `git diff 8c3117ea --stat -- app/src/main` lists only `TrailerPreviewPlayer.kt`.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** `94339eaf` Give the trailer preview player a small load control

**Red run** (P0 `red` tests on the unchanged code):

- red: `trailerPreviewBuffer_matchesAcceptedValues` - AssertionFailedError at line 12 (stub with zero values, `minBufferMs` 0 != 10000)
- green on base: `buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` - the zero-valued stub returns a default `DefaultLoadControl`, which cannot throw; the test guards only once the durations are real (it passes with the final values, so no "not mocked" error)
- red (round 1 fix): same test with the builder's min and max durations swapped on the final code - IllegalArgumentException at TrailerPreviewLoadControlTest.kt:22 (max < min); change reverted, so the test discriminates mis-ordered durations

**Deviations from the plan:**

None. Red run used a zero-valued stub instead of a compile error, per the implementer rule.

Round 1 fix: the AC-2 test was green on the zero-valued stub (recorded above, now with a swapped-durations red run). A behaviour check on the built `DefaultLoadControl` (`shouldContinueLoading` / `shouldStartPlayback`) was tried and not kept: on the JVM, `LoadControl.Parameters` with `Timeline.EMPTY` throws IndexOutOfBoundsException / NullPointerException inside `DefaultLoadControl`, and a real timeline needs more media3 fixtures than the plan allows. The `setLoadControl` wiring stays with AC-3 review and the AC-4/AC-5 device checks.

**Files touched outside the plan:**

**For the reviewers:**

**Blocked:**

## Readiness

Verdict: **READY**

### Coverage

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | Task 1 | `TrailerPreviewLoadControlTest.trailerPreviewBuffer_matchesAcceptedValues` (P0 red) |
| AC-2 | Task 1 | `TrailerPreviewLoadControlTest.buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` (P0 red) |
| AC-3 | Task 1 | Review checks A, B (P1) |
| AC-4 | qa-device after Task 1 | Device manual 1 (P1) |
| AC-5 | qa-device after Task 1 | Device manual 2, 3 (P1) |

### Citations sampled (worktree at `d4a155a1`)

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

### Checks

| Check | Result |
|-------|--------|
| Build green per task | One task: test, helper and call site land in one commit with fast gate and test run |
| Dependency order | N/A: single task |
| P0 rows name defect, mark, test name | AC-1: value drift or swapped durations, red; AC-2: mis-ordered durations throw, red |
| Out of scope | Plan limits `app/src/main` diff to `TrailerPreviewPlayer.kt`; no back buffer, no shared factory, no test options |
| Blocking open questions | None; Q1-Q3 answered |
