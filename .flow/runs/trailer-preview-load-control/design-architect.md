# Design (architect): trailer-preview-load-control

Base `8c3117ea`. Option A of `research.md#6-options`; ACs in `brief.md#acceptance-criteria`.

## Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Where do the values live? | An `internal data class TrailerPreviewBufferParams` (min, max, playback, after-rebuffer ms; target bytes; prioritizeTimeOverSize) and an `internal val TRAILER_PREVIEW_BUFFER` holding the AC-1 values, top level in `TrailerPreviewPlayer.kt`, next to `surfaceNeedsRedraw` (`TrailerPreviewPlayer.kt:151`) | AC-1 needs a JVM-readable value; same file keeps the change to one production file, as the redraw helper already does |
| How is the load control built? | `internal fun buildTrailerPreviewLoadControl(params: TrailerPreviewBufferParams = TRAILER_PREVIEW_BUFFER): DefaultLoadControl`, `@OptIn(UnstableApi::class)`, body shaped like `PlaybackController.kt:710-720`: own `DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)`, `setBufferDurationsMs`, `setTargetBufferBytes`, `setPrioritizeTimeOverSizeThresholds` | AC-2 builds it in a JVM test; AC-3 asks for the warm-up shape; `@OptIn` keeps `@UnstableApi` off the test |
| Where is it attached? | `.setLoadControl(buildTrailerPreviewLoadControl())` between `ExoPlayer.Builder(context)` and `.build()` inside the existing `remember` at `TrailerPreviewPlayer.kt:62-64` | One load control and one allocator per player, created and released with it (`:94-97`) |
| Back buffer? | Not set (media3 default 0) | Preview never seeks back; not in AC-1 |
| Shared with the warm-up player? | No; `PlaybackController.kt` untouched | AC-3 forbids a diff there (Option B rejected) |
| Test file | New `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewLoadControlTest.kt`, JUnit 5, same package as the production helper | Matches `TrailerSurfaceRedrawTest.kt` in that folder |

## Changes by layer

| Layer | Change |
|-------|--------|
| API / repository | N/A: no data path changes |
| ViewModel state and actions | N/A: the preview player is owned by the composable |
| Screen and DI module | N/A: no Koin binding; the player is `remember`ed in composition |
| Compose content | `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt`: add the params class, constant and builder (Decisions rows 1-2); add `.setLoadControl(...)` at `:63-64`; imports `androidx.media3.exoplayer.DefaultLoadControl`, `androidx.media3.exoplayer.upstream.DefaultAllocator` (`C` already imported at `:20`, `UnstableApi` at `:24`, `OptIn` at `:6`). Call site `VideoItemGridDetails.kt:615-626` unchanged |
| Resources | N/A: no strings or assets |
| Tests | `TrailerPreviewLoadControlTest.kt`: one test asserting every field of `TRAILER_PREVIEW_BUFFER` against literal AC-1 values (AC-1); one test that `buildTrailerPreviewLoadControl()` returns without throwing (AC-2). AC-3 is diff review; AC-4/AC-5 are device observation |

## Alternatives rejected

- Inline literals in `.setLoadControl(DefaultLoadControl.Builder()...)`: leaves AC-1/AC-2 untestable on the JVM.
- Separate `TrailerPreviewLoadControl.kt` file: a second production file for six constants and one function.
- Reuse `DeviceBufferConfig.BufferParams` (`DeviceBufferConfig.kt:77`): carries forward-buffer and back-buffer fields the preview does not use, and couples the preview to the main player's tuning.
- Option B (shared factory with the warm-up player) and Option C (also `TrailerOverlay`): excluded by AC-3 and `brief.md#open-questions` Q2.

## Risks

| # | Risk | Area | Lives in |
|---|------|------|----------|
| 1 | A trailer above about 10 Mbit/s fills 12 MiB before 10 s and may stall mid-preview; bitrates are unverified | correctness, playback | `TRAILER_PREVIEW_BUFFER.targetBufferBytes`; checked by AC-4 on the box |
| 2 | `bufferForPlaybackMs` above 1_000 ms delays `onRenderedFirstFrame` and the poster fade | lifecycle, first frame | `TrailerPreviewPlayer.kt:89-91` -> `VideoItemGridDetails.kt:619`; pinned by AC-1 |
| 3 | `DefaultLoadControl.Builder.build()` touching an `android.*` stub method would fail AC-2 with "not mocked" (no `isReturnDefaultValues` in `app/build.gradle.kts`); expected not to, since builder, allocator and `Util` read only static fields | test harness | `TrailerPreviewLoadControlTest.kt`; if it fails, the implementer reports it rather than adding test options |
| 4 | Load control built outside the `remember` block would rebuild on recomposition and give one player a stale allocator | recomposition, memory | `TrailerPreviewPlayer.kt:62-75`; build call stays inside the lambda |
| 5 | Mis-ordered durations throw at player build, i.e. on focus, crashing the panel | correctness | `buildTrailerPreviewLoadControl`; caught by AC-2 |
| 6 | Baseline profile does not list the new helper; one call per preview, negligible | performance | N/A beyond noting it |

Focus and D-pad paths, Koin scopes, main-thread `release()` (`TrailerPreviewPlayer.kt:94-98`) and the stop paths (`:105-108`, `:118-124`) are unchanged; AC-5 confirms them on the box.

## Open questions

None.
