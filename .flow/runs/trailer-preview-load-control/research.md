# Research: trailer-preview-load-control

## 1. Request

> TrailerPreviewPlayer.kt (around 62-98) builds a new ExoPlayer with default settings per preview: default buffer up to 50 s (tens of MB heap for a few seconds of preview). Scope: a small DefaultLoadControl (10-15 s, 8-16 MB target buffer) for the preview player. Sharing OkHttp and reusing one player across previews are out of scope unless trivial. Also note release() on main thread in TrailerOverlay.kt:88 as context only.

## 2. Understanding

The panel trailer preview on detail/catalogue cards plays a remote MP4 through an ExoPlayer built with media3 defaults, which buffers up to 50 s and lets the byte target grow to about 131 MB for a video track. The preview is short and is torn down on every card change, so most of that buffer is wasted heap. The request is to pass a small `DefaultLoadControl` (roughly 10-15 s, 8-16 MB) to that one player and leave the full-screen trailer and the main player alone.

## 3. Current state in code

- `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt:62-75`: `remember { ExoPlayer.Builder(context).build() }` with only audio attributes and `REPEAT_MODE_OFF`; no load control, no media source factory (default `DefaultHttpDataSource`, not OkHttp).
- `TrailerPreviewPlayer.kt:77-99`: one `DisposableEffect(exoPlayer)` adds the listener and on dispose calls `stop()` then `release()` on the main thread.
- `TrailerPreviewPlayer.kt:101-109`: `DisposableEffect(url)` sets the media item and prepares; on dispose `stop()` + `clearMediaItems()`.
- `TrailerPreviewPlayer.kt:118-127`: `ON_STOP` stops the player and reports `onFinished`.
- Single call site: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:615-626`, composed only while `trailerUrl != null`, so a player is built per preview.
- Trailer URLs are signed MP4 links (`app/src/main/java/com/kino/puber/domain/interactor/trailer/TrailerLinkInteractor.kt:14-24`), https scheme, so media3 treats them as streaming, not local playback.
- Full-screen trailer: `app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt:87-89` also builds a default `ExoPlayer`; `release()` runs in `DisposableEffect(Unit)` on the main thread at `:129-133`. It has seek (10 s steps, `:54`, `:195-200`) and a buffered-position bar, so a small buffer there changes behaviour. Context only per the request.
- Pattern to reuse: `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:709-719` builds the warm-up player's `DefaultLoadControl` with its own `DefaultAllocator(trimOnReset = true, C.DEFAULT_BUFFER_SEGMENT_SIZE)`, all four durations `WARM_UP_BUFFER_MS`, `setTargetBufferBytes(WARM_UP_TARGET_BUFFER_BYTES)`, `setPrioritizeTimeOverSizeThresholds(false)`; constants at `:936-937` (15_000 ms, 8 MiB). The main player's `buildLoadControl` is at `:479-498`.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/DeviceBufferConfig.kt:7-28` documents why the project keeps `prioritizeTimeOverSize = false` (byte budget is the hard ceiling).
- Tests: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerSurfaceRedrawTest.kt` (pure redraw helper only), `app/src/test/kotlin/com/kino/puber/ui/feature/details/vm/DetailsVMTrailerPreviewTest.kt`, `app/src/androidTest/kotlin/com/kino/puber/ui/feature/details/component/DetailsScreenContentTest.kt`. Nothing asserts player configuration.
- Recent commits on the two files: `989e8826` (2026-11-19, SurfaceView redraw), `5725e2e9` (stop previews in background), `f2e2ce04`, `34652167`.

## 4. Related work

| Run / doc | Relation |
|-----------|----------|
| `focus-trailer-preview` (done) | Introduced the preview; `docs/superpowers/specs/2026-08-19-focus-trailer-preview-design.md` |
| `trailer-video-hidden-regression` (done) | Same file; SurfaceView/first-frame behaviour that a change must not regress |
| `detail-poster-trailer-flicker` (done) | Poster fade keyed on `onFirstFrameRendered`; start latency affects it |
| `memory-and-performance-fixes`, `perf-deferred` | Perf runs; no trailer or LoadControl item found in them |

DUPLICATE_OF: none. No other run mentions `LoadControl` for the trailer.

## 5. External findings

media3 version from `gradle/libs.versions.toml:27`: `1.11.0`.

| Finding | Source |
|---------|--------|
| Streaming defaults: min 50_000 ms, max 50_000 ms, bufferForPlayback 1000 ms, afterRebuffer 2000 ms, target bytes `C.LENGTH_UNSET`, prioritizeTimeOverSize false, back buffer 0 | DefaultLoadControl.java @1.11.0, lines 64-133 |
| Unset target bytes is summed per selected track: video `2000 * 64 KiB` (~125 MiB), audio `200 * 64 KiB` (~12.5 MiB); `C.DEFAULT_BUFFER_SEGMENT_SIZE = 64 * 1024` | DefaultLoadControl.java @1.11.0 :136-187, :868-935; C.java @1.11.0 :1126 |
| Builder checks `minBufferMs >= bufferForPlaybackMs`, `>= bufferForPlaybackAfterRebufferMs`, `maxBufferMs >= minBufferMs`; min buffer is floored at 500 ms | DefaultLoadControl.java @1.11.0 :770-800 and setters |
| Local-playback values (separate setters) apply only to file/content/data/asset/raw schemes; https trailers use the streaming values | DefaultLoadControl.java @1.11.0 :192-199, :952-961; release notes 1.9.0 |
| 1.9.0 added `setPlayerTargetBufferBytes(String, int)`, per-player loading state | RELEASENOTES.md @1.11.0, 1.9.0 section |
| 1.11.0: prioritizeTimeOverSize falls back to byte limits when heap is tight | RELEASENOTES.md @1.11.0, 1.11.0 section |

Trailer bitrates are unverified; at an assumed 5-10 Mbit/s, 50 s is ~30-60 MB, 15 s ~9-19 MB, so an 8-16 MB byte cap binds before the time cap at the upper end.

## 6. Options

| # | Option | Trade-offs | Size |
|---|--------|------------|------|
| A (recommended) | In `TrailerPreviewPlayer.kt` add `.setLoadControl(...)` with a private builder: own `DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)`, durations e.g. min 10_000 / max 15_000 / playback 1_000 / after-rebuffer 2_000, `setTargetBufferBytes(12 MiB)`, `setPrioritizeTimeOverSizeThresholds(false)`; constants next to it. Optional pure helper returning the params so a JVM test can pin them | One file plus a test; mirrors the warm-up pattern; start latency unchanged (bufferForPlayback stays 1 s); a high-bitrate trailer may rebuffer more often | S |
| B | Shared `PreviewLoadControl` factory used by the preview and the warm-up player | One definition for two small-buffer players; touches `PlaybackController`, a risk area, for no user gain | S/M |
| C | A + the same small load control in `TrailerOverlay` | Saves heap in full-screen too, but a user who seeks 10 s forward hits unbuffered media more often; changes a UI the owner watches | M |

## 7. Risks

- Rebuffer on high-bitrate trailers if the byte cap is below the bitrate of 10 s of media; keep min >= 10 s or cap >= 12 MiB.
- Start latency and first-frame timing feed the poster fade (`VideoItemGridDetails.kt:603-612`); `bufferForPlaybackMs` must stay at or below the default 1000 ms.
- The change is runtime player behaviour, so per AGENTS.md it needs on-device verification on the TV box (preview starts, plays to end, card change, background).
- Builder argument checks throw at build time if the four durations are mis-ordered; a JVM test on the helper catches that.

## 8. Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Exact values? | min 10_000, max 15_000, playback 1_000, after-rebuffer 2_000 ms; target 12 MiB; prioritizeTimeOverSize false |
| Q2 | Apply to `TrailerOverlay` as well? | No; keep it out of this run (Option A) |
| Q3 | Move `release()` off the main thread? | No; context only, as the request says |

## 9. Sources

- Files: `TrailerPreviewPlayer.kt`, `TrailerOverlay.kt`, `VideoItemGridDetails.kt:590-640`, `PlaybackController.kt:410-498, 660-735, 936-937`, `DeviceBufferConfig.kt:1-120`, `TrailerLinkInteractor.kt`, `gradle/libs.versions.toml`.
- Commits: `989e8826`, `5725e2e9`, `f2e2ce04`, `34652167`.
- Runs: `focus-trailer-preview`, `trailer-video-hidden-regression`, `detail-poster-trailer-flicker`, `memory-and-performance-fixes`, `perf-deferred`.
- URLs (fetched 2026-11-28):
  - https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultLoadControl.java
  - https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/common/src/main/java/androidx/media3/common/C.java
  - https://raw.githubusercontent.com/androidx/media/1.11.0/RELEASENOTES.md
