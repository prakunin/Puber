# Test design: trailer-preview-load-control

Base `8c3117ea`. ACs: `brief.md#acceptance-criteria`; decisions and risks: `design-architect.md`.
Test file: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewLoadControlTest.kt`, JUnit 5, style of `TrailerSurfaceRedrawTest.kt` in the same folder (pure helper, no extension needed: no coroutines, no strings).

## Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Mis-ordered durations throw at player build, crashing the panel on focus (design R5) | 2 | 3 | 6 | `buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` |
| R2 | A value drifts from AC-1; `bufferForPlaybackMs` above 1_000 delays first frame and poster fade (brief Risks, design R2) | 2 | 2 | 4 | `trailerPreviewBuffer_matchesAcceptedValues`; manual 1 |
| R3 | Trailer above ~10 Mbit/s hits the 12 MiB cap and stalls (brief Risks, design R1) | 2 | 2 | 4 | manual 1 |
| R4 | Load control built but not passed to `ExoPlayer.Builder`, or built outside `remember` (design R4) | 1 | 2 | 2 | review check A |
| R5 | Stop paths (card change, `ON_STOP`) regress (brief AC-5) | 1 | 3 | 3 | manual 2, 3 |
| R6 | Builder hits an `android.*` stub on the JVM ("not mocked") (design R3) | 1 | 2 | 2 | Accepted: AC-2 test shows it at once; implementer reports, adds no test options |
| R7 | Diff spreads to `TrailerOverlay.kt` or `PlaybackController.kt` (AC-3) | 1 | 2 | 2 | review check B |
| R8 | Helper missing from the baseline profile (design R6) | 1 | 1 | 1 | Accepted: one call per preview |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `TrailerPreviewLoadControlTest.trailerPreviewBuffer_matchesAcceptedValues` | Asserts each of the six fields of `TRAILER_PREVIEW_BUFFER` against literals (10_000, 15_000, 1_000, 2_000, 12 * 1024 * 1024, false); fails if any value changes or two durations are swapped. Red: the symbol does not exist at base |
| AC-2 | unit | P0 red | `TrailerPreviewLoadControlTest.buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` | `assertDoesNotThrow` on `buildTrailerPreviewLoadControl()` with the default params, returning a non-null `DefaultLoadControl`; fails if min < playback/after-rebuffer or max < min. Red: the function does not exist at base |
| AC-3 | manual (review) | P1 | review checks A, B | Fails if `.setLoadControl(buildTrailerPreviewLoadControl())` is missing or outside the `remember` lambda, or the two other files change |
| AC-4 | device | P1 | manual 1 | Stall, black frame or late fade from the new buffer values |
| AC-5 | device | P1 | manual 2, 3 | Audio left after card change or Home; next preview not starting |

## Bugs

N/A: improvement, no defect on the base commit.

## Manual checks

Review (no device):

| # | Step | Expected | Must not happen |
|---|---|---|---|
| A | Read `TrailerPreviewPlayer.kt` in the worktree diff | `.setLoadControl(buildTrailerPreviewLoadControl())` between `ExoPlayer.Builder(context)` and `.build()`, inside `remember { }`; allocator is `DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)` built inside the helper | Load control held in a field, a `val` outside `remember`, or shared across players |
| B | `git -C <wt> diff 8c3117ea --stat -- app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` | Empty output | Any line |

Device: TV box `192.168.1.110:5555`, package `com.kino.puber.stage`, worktree dev APK via `tools/android-apk-install-preserve`, under a lease; every key is `adb -s 192.168.1.110:5555 shell input keyevent <KEY>`.

| # | Step | Expected | Must not happen |
|---|---|---|---|
| 1 | Open a title's details with a trailer; on a catalogue grid, `KEYCODE_DPAD_RIGHT` to a card with a trailer and leave focus for the preview delay; watch to the end | Trailer starts within the same delay as on master; still fades out on the first frame; plays to its end and the still returns | Black rectangle before the fade; visible stall or spinner mid-trailer; preview ending early with an error (`onFinished` from `onPlayerError`) |
| 2 | While a preview plays, `KEYCODE_DPAD_RIGHT` to the next card with a trailer | First preview stops at once with no audio; second card's preview starts after the delay | Audio of the first trailer continuing; second preview never starting; focus leaving the grid |
| 3 | While a preview plays, `KEYCODE_HOME`; then return to the app | Audio stops on Home; on return the still shows and no trailer resumes by itself | Trailer audio over the launcher; crash on return |

## Perf

N/A: track `perf` off (`brief.md#open-questions` Q3).

## UNTESTABLE

None. AC-3 has no JVM-visible effect (`ExoPlayer` exposes no load-control getter and needs a `Context`), so it is a review check, not a test.
