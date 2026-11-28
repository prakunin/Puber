# Give the trailer preview player a small LoadControl

Status: approved
Date: 2026-11-28
Slug: trailer-preview-load-control
Kind: improvement
Size: M
Tracks: none

## Problem / Why

Kind: improvement   Size: M   Tracks: none   Base: 6534e4e6
Device verification: yes (playback behaviour of the panel trailer preview on the TV box).
Research: `research.md` (code state, media3 1.11.0 defaults, options A-C; not repeated here).

### Scope
Option A (research.md: Options): give the preview player built at `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt:62-64` a small `DefaultLoadControl`, shaped like the warm-up player's at `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:709-719`. `TrailerOverlay.kt`, `PlaybackController.kt` and `release()` threading stay as they are.

Size M: one file plus a test, but playback is a risk area in `.flow/roles/analyst.md`, and the first-frame timing feeds the poster fade at `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:602-626`.

Root cause of the waste: `ExoPlayer.Builder(context).build()` takes media3 streaming defaults (min/max 50 s, target bytes unset, so roughly 125 MiB for video plus 12.5 MiB for audio; research.md: External findings). Confirmed by reading the code at the base commit.


### Risks
- A trailer whose bitrate puts 10 s above 12 MiB (above about 10 Mbit/s) hits the byte cap first and may rebuffer; trailer bitrates are unverified (research.md: External findings). AC-4 on the box is the check.
- Raising `bufferForPlaybackMs` above 1_000 ms would delay the first frame and the poster fade; AC-1 pins it.
- No heap number is measured; the gain follows from the cap, not from a perf baseline.

## Acceptance criteria

- **AC-1** Given the preview load-control parameters, When a JVM test reads them, Then min buffer 10_000 ms, max 15_000 ms, buffer for playback 1_000 ms, after rebuffer 2_000 ms, target buffer 12 MiB, prioritize time over size false. (unit)
- **AC-2** Given those parameters, When `DefaultLoadControl.Builder` builds them in a JVM test, Then it does not throw. (unit; catches mis-ordered durations)
- **AC-3** Given `TrailerPreviewPlayer`, When its `ExoPlayer` is built, Then it is given the load control from AC-1 with its own `DefaultAllocator(trimOnReset = true, C.DEFAULT_BUFFER_SEGMENT_SIZE)`, and `TrailerOverlay.kt` and `PlaybackController.kt` have no diff. (code review)
- **AC-4** Given a detail or catalogue card with a trailer on the TV box, When focus rests on it, Then the trailer starts, the still fades out on the first frame with no black frame, and the trailer plays to its end and returns to the still without a stall the tester sees. (device observation)
- **AC-5** Given a trailer playing in the panel, When the D-pad moves to another card and, separately, Home sends the app to the background, Then the preview stops in both cases with no audio left, and the next card's preview starts. (device observation)

## Out of scope

The brief has no separate section; the non-goals are listed under Problem / Why and in the plan's Global constraints.

## Design

Base `8c3117ea`. Option A of `research.md#6-options`; ACs in `brief.md#acceptance-criteria`.

### Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Where do the values live? | An `internal data class TrailerPreviewBufferParams` (min, max, playback, after-rebuffer ms; target bytes; prioritizeTimeOverSize) and an `internal val TRAILER_PREVIEW_BUFFER` holding the AC-1 values, top level in `TrailerPreviewPlayer.kt`, next to `surfaceNeedsRedraw` (`TrailerPreviewPlayer.kt:151`) | AC-1 needs a JVM-readable value; same file keeps the change to one production file, as the redraw helper already does |
| How is the load control built? | `internal fun buildTrailerPreviewLoadControl(params: TrailerPreviewBufferParams = TRAILER_PREVIEW_BUFFER): DefaultLoadControl`, `@OptIn(UnstableApi::class)`, body shaped like `PlaybackController.kt:710-720`: own `DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)`, `setBufferDurationsMs`, `setTargetBufferBytes`, `setPrioritizeTimeOverSizeThresholds` | AC-2 builds it in a JVM test; AC-3 asks for the warm-up shape; `@OptIn` keeps `@UnstableApi` off the test |
| Where is it attached? | `.setLoadControl(buildTrailerPreviewLoadControl())` between `ExoPlayer.Builder(context)` and `.build()` inside the existing `remember` at `TrailerPreviewPlayer.kt:62-64` | One load control and one allocator per player, created and released with it (`:94-97`) |
| Back buffer? | Not set (media3 default 0) | Preview never seeks back; not in AC-1 |
| Shared with the warm-up player? | No; `PlaybackController.kt` untouched | AC-3 forbids a diff there (Option B rejected) |
| Test file | New `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewLoadControlTest.kt`, JUnit 5, same package as the production helper | Matches `TrailerSurfaceRedrawTest.kt` in that folder |

### Changes by layer

| Layer | Change |
|-------|--------|
| API / repository | N/A: no data path changes |
| ViewModel state and actions | N/A: the preview player is owned by the composable |
| Screen and DI module | N/A: no Koin binding; the player is `remember`ed in composition |
| Compose content | `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt`: add the params class, constant and builder (Decisions rows 1-2); add `.setLoadControl(...)` at `:63-64`; imports `androidx.media3.exoplayer.DefaultLoadControl`, `androidx.media3.exoplayer.upstream.DefaultAllocator` (`C` already imported at `:20`, `UnstableApi` at `:24`, `OptIn` at `:6`). Call site `VideoItemGridDetails.kt:615-626` unchanged |
| Resources | N/A: no strings or assets |
| Tests | `TrailerPreviewLoadControlTest.kt`: one test asserting every field of `TRAILER_PREVIEW_BUFFER` against literal AC-1 values (AC-1); one test that `buildTrailerPreviewLoadControl()` returns without throwing (AC-2). AC-3 is diff review; AC-4/AC-5 are device observation |

### Alternatives rejected

- Inline literals in `.setLoadControl(DefaultLoadControl.Builder()...)`: leaves AC-1/AC-2 untestable on the JVM.
- Separate `TrailerPreviewLoadControl.kt` file: a second production file for six constants and one function.
- Reuse `DeviceBufferConfig.BufferParams` (`DeviceBufferConfig.kt:77`): carries forward-buffer and back-buffer fields the preview does not use, and couples the preview to the main player's tuning.
- Option B (shared factory with the warm-up player) and Option C (also `TrailerOverlay`): excluded by AC-3 and `brief.md#open-questions` Q2.

### Risks

| # | Risk | Area | Lives in |
|---|------|------|----------|
| 1 | A trailer above about 10 Mbit/s fills 12 MiB before 10 s and may stall mid-preview; bitrates are unverified | correctness, playback | `TRAILER_PREVIEW_BUFFER.targetBufferBytes`; checked by AC-4 on the box |
| 2 | `bufferForPlaybackMs` above 1_000 ms delays `onRenderedFirstFrame` and the poster fade | lifecycle, first frame | `TrailerPreviewPlayer.kt:89-91` -> `VideoItemGridDetails.kt:619`; pinned by AC-1 |
| 3 | `DefaultLoadControl.Builder.build()` touching an `android.*` stub method would fail AC-2 with "not mocked" (no `isReturnDefaultValues` in `app/build.gradle.kts`); expected not to, since builder, allocator and `Util` read only static fields | test harness | `TrailerPreviewLoadControlTest.kt`; if it fails, the implementer reports it rather than adding test options |
| 4 | Load control built outside the `remember` block would rebuild on recomposition and give one player a stale allocator | recomposition, memory | `TrailerPreviewPlayer.kt:62-75`; build call stays inside the lambda |
| 5 | Mis-ordered durations throw at player build, i.e. on focus, crashing the panel | correctness | `buildTrailerPreviewLoadControl`; caught by AC-2 |
| 6 | Baseline profile does not list the new helper; one call per preview, negligible | performance | N/A beyond noting it |

Focus and D-pad paths, Koin scopes, main-thread `release()` (`TrailerPreviewPlayer.kt:94-98`) and the stop paths (`:105-108`, `:118-124`) are unchanged; AC-5 confirms them on the box.

### Open questions

None.

## Test design

Base `8c3117ea`. ACs: `brief.md#acceptance-criteria`; decisions and risks: `design-architect.md`.
Test file: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewLoadControlTest.kt`, JUnit 5, style of `TrailerSurfaceRedrawTest.kt` in the same folder (pure helper, no extension needed: no coroutines, no strings).

### Risk profile

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

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `TrailerPreviewLoadControlTest.trailerPreviewBuffer_matchesAcceptedValues` | Asserts each of the six fields of `TRAILER_PREVIEW_BUFFER` against literals (10_000, 15_000, 1_000, 2_000, 12 * 1024 * 1024, false); fails if any value changes or two durations are swapped. Red: the symbol does not exist at base |
| AC-2 | unit | P0 red | `TrailerPreviewLoadControlTest.buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` | `assertDoesNotThrow` on `buildTrailerPreviewLoadControl()` with the default params, returning a non-null `DefaultLoadControl`; fails if min < playback/after-rebuffer or max < min. Red: the function does not exist at base |
| AC-3 | manual (review) | P1 | review checks A, B | Fails if `.setLoadControl(buildTrailerPreviewLoadControl())` is missing or outside the `remember` lambda, or the two other files change |
| AC-4 | device | P1 | manual 1 | Stall, black frame or late fade from the new buffer values |
| AC-5 | device | P1 | manual 2, 3 | Audio left after card change or Home; next preview not starting |

### Bugs

N/A: improvement, no defect on the base commit.

### Manual checks

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

### Perf

N/A: track `perf` off (`brief.md#open-questions` Q3).

### UNTESTABLE

None. AC-3 has no JVM-visible effect (`ExoPlayer` exposes no load-control getter and needs a `Context`), so it is a review check, not a test.

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Values? | As in AC-1 (research.md: Open questions Q1). | no |
| Q2 | Also apply to the full-screen `TrailerOverlay`? | No: it seeks in 10 s steps and shows the buffered bar; a separate run if wanted. | no |
| Q3 | Measure heap before and after (track `perf`)? | No: the request is a configuration cap; AC-4/AC-5 cover behaviour. | no |

All questions answered with the recommended answer (owner standing instruction: recommended).

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-28 | Gate 1: approved on the recommended answers | owner (standing instruction: recommended) |
