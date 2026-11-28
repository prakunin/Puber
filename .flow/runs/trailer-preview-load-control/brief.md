# Brief: trailer-preview-load-control

Kind: improvement   Size: M   Tracks: none   Base: 6534e4e6
Device verification: yes (playback behaviour of the panel trailer preview on the TV box).
Research: `research.md` (code state, media3 1.11.0 defaults, options A-C; not repeated here).

## Scope
Option A (research.md: Options): give the preview player built at `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt:62-64` a small `DefaultLoadControl`, shaped like the warm-up player's at `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:709-719`. `TrailerOverlay.kt`, `PlaybackController.kt` and `release()` threading stay as they are.

Size M: one file plus a test, but playback is a risk area in `.flow/roles/analyst.md`, and the first-frame timing feeds the poster fade at `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:602-626`.

Root cause of the waste: `ExoPlayer.Builder(context).build()` takes media3 streaming defaults (min/max 50 s, target bytes unset, so roughly 125 MiB for video plus 12.5 MiB for audio; research.md: External findings). Confirmed by reading the code at the base commit.

## Acceptance criteria
- **AC-1** Given the preview load-control parameters, When a JVM test reads them, Then min buffer 10_000 ms, max 15_000 ms, buffer for playback 1_000 ms, after rebuffer 2_000 ms, target buffer 12 MiB, prioritize time over size false. (unit)
- **AC-2** Given those parameters, When `DefaultLoadControl.Builder` builds them in a JVM test, Then it does not throw. (unit; catches mis-ordered durations)
- **AC-3** Given `TrailerPreviewPlayer`, When its `ExoPlayer` is built, Then it is given the load control from AC-1 with its own `DefaultAllocator(trimOnReset = true, C.DEFAULT_BUFFER_SEGMENT_SIZE)`, and `TrailerOverlay.kt` and `PlaybackController.kt` have no diff. (code review)
- **AC-4** Given a detail or catalogue card with a trailer on the TV box, When focus rests on it, Then the trailer starts, the still fades out on the first frame with no black frame, and the trailer plays to its end and returns to the still without a stall the tester sees. (device observation)
- **AC-5** Given a trailer playing in the panel, When the D-pad moves to another card and, separately, Home sends the app to the background, Then the preview stops in both cases with no audio left, and the next card's preview starts. (device observation)

## Risks
- A trailer whose bitrate puts 10 s above 12 MiB (above about 10 Mbit/s) hits the byte cap first and may rebuffer; trailer bitrates are unverified (research.md: External findings). AC-4 on the box is the check.
- Raising `bufferForPlaybackMs` above 1_000 ms would delay the first frame and the poster fade; AC-1 pins it.
- No heap number is measured; the gain follows from the cap, not from a perf baseline.

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Values? | As in AC-1 (research.md: Open questions Q1). | no |
| Q2 | Also apply to the full-screen `TrailerOverlay`? | No: it seeks in 10 s steps and shows the buffered bar; a separate run if wanted. | no |
| Q3 | Measure heap before and after (track `perf`)? | No: the request is a configuration cap; AC-4/AC-5 cover behaviour. | no |
