# Research: player-dpad-seek-coalesce

## 1. Request

Holding left/right in the player issues a full exact seek on every key auto-repeat (PlayerVM.kt around 733-749, PlaybackController.kt around 568). Each repeat flushes the decoder and network request and produces 2-3 player state copies, mostly discarded by the next repeat. Accumulate the target and call seekTo at most every 200-300 ms and at the end of the hold, or use SeekParameters.CLOSEST_SYNC for D-pad steps. Weak TV box (API 29) is the target.

## 2. Understanding

Every Left/Right key-down, auto-repeats included, goes straight to `ExoPlayer.seekTo` with default (exact) seek parameters, restarts the seek-stall watchdog and publishes the whole `PlayerContentState` up to three times. On a held key the player throws away almost all of that work. The run should let the indicator and seek bar follow every repeat while the player gets far fewer seeks, ending on the exact target the viewer let go at, and keep the seek-burst prompt hold, the stall recovery and the skip/next-episode reset behaving as they do now.

## 3. Current state in code

Paths are under `app/src/main/java/com/kino/puber/ui/feature/player/` unless stated.

| Where | What it does now |
|-------|------------------|
| `component/PlayerVideoSurface.kt:200-210` | `handlePlayerActionKeyEvent` maps every `ACTION_DOWN` (repeats included) to an action; there is no `repeatCount` guard for arrows (OK has one, `:182-186`, with a comment that arrows repeat on purpose) |
| `component/PlayerVideoSurface.kt:212-234` | Left/Right and media FF/RW/skip keys map to `SeekForward` / `SeekBackward`; `seekActionForKeyCode` is the seek-only subset |
| `component/PlayerProgressBar.kt:293-314` | Focused seek bar: Left/Right on `ACTION_DOWN` call the same `onSeekForward` / `onSeekBackward` (`component/PlayerOverlayLayers.kt:90-91`) |
| `component/PlayerTimedActionOverlay.kt:267-269` | Skip / next-episode prompts pass seek keys through as the same actions |
| `vm/PlayerVM.kt:659-660` | `handleAction` routes both actions to `seekForward()` / `seekBackward()` |
| `vm/PlayerVM.kt:733-749` | Each call: `startSeekBurst()`, `seekHandler.nextStep()`, target = `playbackController.currentPosition` ± step (clamped), `playbackController.seekTo(target)`, `updateContent { currentPosition }`, `showSeekIndicator` (second `updateContent`, `:751-767`) |
| `vm/PlayerVM.kt:728-731` | `startSeekBurst` restarts a 1 500 ms job; while active the tick skips `checkEarlyNextEpisode` / `checkSkipSegment` (`:1597-1600`, `:1713-1714`) |
| `vm/SeekHandler.kt:3-38` | Step ladder 10, 10, 20, 30, 60, 60 s; resets after 1 500 ms without a press (`DEFAULT_SEEK_RESET_TIMEOUT_MS`), uses `System.currentTimeMillis()` |
| `vm/PlaybackController.kt:568-578` | `seekTo`: `player.seekTo(positionMs)`, `callback?.onSeekPerformed()`, then `seekStallRecovery.start` (playing) or `cancel` |
| `vm/PlayerVM.kt:1757-1764` | `onSeekPerformed`: third `updateContent` (clears skip prompt and countdown), cancels both countdown jobs, clears `countdownDismissed` |
| `vm/SeekStallRecovery.kt:18-22, 36-43, 66` | `start` clears tried URLs and re-posts an 8 s check on every seek; a check that sees no buffer progress fails over to the next URL |
| `vm/PlaybackController.kt:447-454, 722` | Both ExoPlayer builders set no `SeekParameters`, so seeks use the default (exact) |
| `vm/PlaybackController.kt:550-558, 652` | Stream switch / quality change re-seek from the player position |
| `vm/PlayerVM.kt:1476, 1485, 1868` | Resume, start-over and skip-segment call `seekTo` directly; a coalescer must not let a pending D-pad target land after them |
| `vm/PlayerVM.kt:1584-1594` | Position tick overwrites `currentPosition` from the player while controls are up |
| `vm/PlayerVM.kt:2069` | `SEEK_INDICATOR_HIDE_DELAY_MS = 1500L` |
| `domain/interactor/player/PlayerInteractor.kt:21-24` | Streams are `HLS` or `PROGRESSIVE` |
| `gradle/libs.versions.toml:27` | Media3 1.11.0 |

Tests around it: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SeekHandlerTest.kt`, `SeekStallRecoveryPolicyTest.kt`, `PlayerVMSeekBurstTest.kt` (mocks `seekTo` as synchronous and calls `onSeekPerformed` by hand per press, `:37-50`), `PlayerVMSkipCountdownFitTest.kt`, `PlayerVMPlaybackFlowTest.kt`, `component/PlayerVideoSurfaceTest.kt`; instrumented `app/src/androidTest/kotlin/com/kino/puber/ui/feature/player/component/PlayerPromptFocusAndKeysTest.kt`.

Commits: `5d91fd66` added the seek burst and the seek-only key map; `9ab05955` added seek-stall recovery; the step logic dates from `88cdf071` (Release 1.0.0).

## 4. Related work

| Run / doc | Relation |
|-----------|----------|
| `seek-blocked-by-skip-prompt` (done, spec `docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md`) | Introduced the seek burst and prompt pass-through; its Q2/Q3 decisions (prompt returns after a seek following a cancel; prompt never takes focus from the seek bar) must still hold |
| `prompt-key-passthrough` (idea) | Same key path on prompts, Up/Down and Play/Pause only; no overlap in the seek call |
| `player-ended-play-hardening` (idea) | Touches `PlaybackController` seek and stall recovery after ended; touches the same file, not the same path |
| `playback-controller-split` (idea) | Refactor of `PlaybackController`; a merge-order concern only |
| `player-callback-gate` (done) | `onSeekPerformed` is dispatched through the callback gate session |
| `perf-deferred` (idea) | No seek item in its list |
| Siblings from the 2026-11-28 audit: `gradle-build-cache`, `room-wal-journal-mode`, `trailer-preview-load-control`, `watch-state-batch-in-progress` | Different areas |

Probable duplicate: none.

## 5. External findings

| Finding | Source |
|---------|--------|
| `SeekParameters` presets `EXACT`, `CLOSEST_SYNC`, `PREVIOUS_SYNC`, `NEXT_SYNC`; sync seeks are "typically faster but less accurate"; `@UnstableApi` | https://developer.android.com/reference/androidx/media3/exoplayer/SeekParameters (read 2026-11-28) |
| `ExoPlayer.setSeekParameters(SeekParameters?)` sets the parameters for later seeks; `null` restores defaults | https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer (read 2026-11-28) |
| Every app operation, a seek included, is a message queued to the internal playback thread and consumed in order | same ExoPlayer page, "Threading model" |
| HLS: sync points are segment start times; the segment after the target counts as a sync point only with `#EXT-X-INDEPENDENT-SEGMENTS`. Otherwise `CLOSEST_SYNC` resolves to the start of the segment holding the target | `HlsChunkSource.getAdjustedSeekPositionUs`, https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsChunkSource.java (release branch, read 2026-11-28) |
| Scrubbing mode `ExoPlayer.setScrubbingModeEnabled(boolean)` "optimizes the player for many frequent seeks"; added in 1.8.0, scrubbing fixes in 1.9.0, 1.10.1, 1.11.0, 1.11.1 | https://raw.githubusercontent.com/androidx/media/release/RELEASENOTES.md |
| `ScrubbingModeParameters`: skip codec flush and keyframe reset where possible (default true), optional disabled track types, fractional seek tolerance, dynamic scheduling, raised codec operating rate; `useDecodeOnlyFlag` works only on API 34+ | https://developer.android.com/reference/androidx/media3/exoplayer/ScrubbingModeParameters (read 2026-11-28) |
| HLS content guidance recommends `#EXT-X-INDEPENDENT-SEGMENTS` | https://developer.android.com/media/media3/exoplayer/hls |

Unverified: whether KinoPub HLS playlists carry `#EXT-X-INDEPENDENT-SEGMENTS`, and their segment length. The answer must come from a playlist the app loads in ordinary use, not from a probe.

## 6. Options

| Option | What | Trade-offs | Size |
|--------|------|------------|------|
| A. Coalesce in the VM (recommended) | `PlayerVM` keeps a pending target. Each press steps from the pending target (not the player position), updates the indicator and `currentPosition` in one `updateContent`, and schedules a trailing `seekTo`. Throttle about 250 ms during the hold, then a final flush once presses stop (or on key-up if the hold end is plumbed). Resume, skip-segment, quality switch, progress save and exit flush or drop the pending target first | Exact final position; one seek per window instead of per repeat; fewer state copies; `onSeekPerformed` and stall recovery fire per real seek. Needs care at every other `seekTo` and `currentPosition` reader; `PlayerVMSeekBurstTest` needs rework; the video frame lags the indicator by up to one window | S-M |
| B. `CLOSEST_SYNC` for D-pad steps | `setSeekParameters(CLOSEST_SYNC)` around D-pad seeks, `EXACT` (default) for resume and skip | Two lines, cheaper seeks, but the number of seeks and state copies stays the same. On HLS without independent segments the target snaps back to the segment start, so a 10 s step from a position just past a segment start can land behind or at the same point. The next step starts from the snapped `currentPosition`, so holding the key can creep or stall. Final position is inexact | S |
| C. A plus scrubbing mode | Option A, plus `setScrubbingModeEnabled(true)` while a burst is active and `false` at the final flush | Skips codec flushes on intermediate seeks (Media3 1.11.0 has it). `@UnstableApi`; it has had a fix in almost every release since 1.8.0, including 1.11.1, which this project does not have yet (`media3-hagc-removal` blocks the bump). Its benefit on the API 29 box is unmeasured. A wrong exit leaves audio disabled | M |

## 7. Risks

| Risk | Where |
|------|-------|
| A pending D-pad target lands after a resume, a skip-segment jump or a quality switch and moves playback back | `vm/PlayerVM.kt:1476, 1868`; `vm/PlaybackController.kt:550-558, 652` |
| A progress save or exit reads the player position before the final flush and stores the pre-hold time | `saveCurrentPosition` callers in `vm/PlayerVM.kt` |
| The position tick overwrites the coalesced `currentPosition` with the stale player position while controls are up, so the thumb jumps back | `vm/PlayerVM.kt:1584-1594` |
| The skip/next-episode reset (`onSeekPerformed`) runs later than the press, so a countdown could start in between | `vm/PlayerVM.kt:1757-1764`, guarded today by the 1 500 ms burst |
| Stall recovery armed only on real seeks could fail over to the next URL during a long hold | `vm/SeekStallRecovery.kt:18-22` |
| `SeekHandler` uses wall-clock time, so a delay-based coalescer in coroutine tests and the step ladder run on different clocks | `vm/SeekHandler.kt:25` |
| Performance gain is claimed, not measured; the box is the measuring device (memory: device-box-only) | owner TV box `192.168.1.110:5555` |

## 8. Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Which mechanism? | Option A only; leave `SeekParameters` and scrubbing mode alone until a box measurement shows a need |
| Q2 | Throttle window and trailing flush? | Leading seek on the first press, then at most one seek per 250 ms, plus a trailing flush 250 ms after the last press. Key-up plumbing is not needed |
| Q3 | Should the seek-bar Left/Right and the media FF/RW keys go through the same coalescer? | Yes; they share the actions |
| Q4 | What proves the gain? | VM unit tests: seek count per held burst and exact final target. Box evidence: a held Right for about 5 s before and after, compared on seek count and frame/key latency from the existing telemetry, with no regression in prompt behaviour |

## 9. Sources

- Code: files cited in section 3; `gradle/libs.versions.toml:27`; `app/build.gradle.kts:52`
- Commits: `5d91fd66`, `9ab05955`, `88cdf071`
- Runs: `.flow/runs/seek-blocked-by-skip-prompt/state.md`, `prompt-key-passthrough`, `player-ended-play-hardening`, `playback-controller-split`, `player-callback-gate`, `perf-deferred`, `media3-hagc-removal`, the 2026-11-28 sibling runs
- Specs: `docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md`
- URLs: https://developer.android.com/reference/androidx/media3/exoplayer/SeekParameters, https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer, https://developer.android.com/reference/androidx/media3/exoplayer/ScrubbingModeParameters, https://developer.android.com/media/media3/exoplayer/hls, https://raw.githubusercontent.com/androidx/media/release/RELEASENOTES.md, https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsChunkSource.java
