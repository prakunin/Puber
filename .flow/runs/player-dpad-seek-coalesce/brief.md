# Brief: player-dpad-seek-coalesce

Kind: improvement   Size: M   Tracks: none   Base: 6534e4e6
Device verification: yes (D-pad input and playback seeks on the TV box 192.168.1.110:5555).
Research: `research.md` (code map, Media3 seek findings, options A-C; not repeated here).

## Scope
Option A (research.md: 6. Options): coalesce `SeekForward` / `SeekBackward` in `PlayerVM`. `SeekParameters` and scrubbing mode stay untouched (Q1).

Paths below are under `app/src/main/java/com/kino/puber/ui/feature/player/vm/`.

Why it wastes work, confirmed by reading the code at the base commit: `seekForward` / `seekBackward` (`PlayerVM.kt:733-749`) call `playbackController.seekTo` on every key-down, auto-repeats included. `PlaybackController.seekTo` (`PlaybackController.kt:568-578`) then calls `ExoPlayer.seekTo` with exact seek parameters, `onSeekPerformed` (`PlayerVM.kt:1757-1764`, one `updateContent`) and `seekStallRecovery.start`. With the two `updateContent` calls in the VM, each repeat writes the state three times. The next step comes from `playbackController.currentPosition`, which right now only works because ExoPlayer masks the position to the last seek target.

Size M because D-pad input and playback are risk areas (`.flow/roles/analyst.md`). The change also reaches every place that reads or moves the position:

| Site | Needed behaviour with a pending target |
|------|----------------------------------------|
| `PlayerVM.kt:733-749` | Step from the pending target if there is one, else from the player position. Write position and indicator in one `updateContent` |
| `PlayerVM.kt:1584-1594` position tick | Do not overwrite `currentPosition` with the player position while a target is pending |
| `saveCurrentPosition` `PlayerVM.kt:1898` (callers `:701, :1056, :1499, :1567, :2040`) | Commit the pending seek first, or save the pending target |
| `switchStreamUrl` / `switchStream` callers `PlayerVM.kt:450-528` | Commit before the switch, because `PlaybackController.kt:550-558` re-seeks from the player position |
| Resume `:1476`, start-over `:1485`, skip-segment `:1868` | Drop the pending target, so it cannot land after the jump |
| `onCleared` `:2054`, episode switch `:1053` | Cancel the trailing job |

## Acceptance criteria
- **AC-1** Given playback at position P, When six Seek-forward actions arrive 50 ms apart in virtual time, Then the indicator and `currentPosition` after each action show the cumulative target (P+10, +20, +40, +70, +130, +190 s, clamped to duration), and `playbackController.seekTo` gets the first target immediately (leading seek). (unit)
- **AC-2** Given the burst from AC-1, When 250 ms pass after the last action, Then `seekTo` has been called no more than once per 250 ms window plus one trailing call, and the last call carries exactly the final target. (unit)
- **AC-3** Given a single Seek-backward action, When it is handled, Then `seekTo` is called once with the target clamped at 0, and no second call follows. (unit)
- **AC-4** Given a pending target, When resume, start-over or skip-segment seeks, Then no D-pad target reaches `seekTo` after that seek. (unit)
- **AC-5** Given a pending target, When a progress save (pause, background, sync, close) or a quality switch happens, Then the saved time and the position the switch keeps equal the pending target. (unit)
- **AC-6** Given a pending target with controls shown and playback running, When the position tick fires, Then `currentPosition` stays at the pending target. (unit)
- **AC-7** Given a skip or next-episode prompt that would apply, When the viewer holds Left/Right through it, Then the prompt stays hidden during the hold and its behaviour after the hold matches `docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md` (Q2/Q3). Existing `PlayerVMSeekBurstTest` / `PlayerVMSkipCountdownFitTest` cases are rewritten for the coalesced call pattern, not dropped. (unit)
- **AC-8** Given an HLS title playing on the box, When Right is held for about 5 s and released, Then the seek bar and indicator follow every repeat, playback resumes at the released target time ±1 s, and no stall failover or prompt flicker shows. Repeated for Left and for the focused seek bar. (device observation)

## Risks
| Risk | Where |
|------|-------|
| `SeekHandler` uses `System.currentTimeMillis()` (`SeekHandler.kt:25`) while the coalescer uses coroutine `delay`, so tests run the ladder and the throttle on different clocks; so the tests need an injectable clock | `SeekHandler.kt:17-38` |
| The video frame lags the indicator by up to one window during a hold | `PlayerVM.kt:733-749` |
| `onSeekPerformed` now fires per real seek, not per press; the 1 500 ms burst (`PlayerVM.kt:728-731`) must still start on every press so a countdown cannot start between presses | `PlayerVM.kt:1597-1600, 1757-1764` |
| Stall recovery re-arms only on real seeks; with a 250 ms window it is still re-armed far inside its 8 s check | `SeekStallRecovery.kt:18-22` |
| The gain is not measured as a number (Q3) | `research.md: 7. Risks` |

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Mechanism? | Option A only; no `SeekParameters` or scrubbing mode until a box measurement shows a need (research.md: 8. Open questions Q1). | no |
| Q2 | Window? | Leading seek on the first press, at most one seek per 250 ms, trailing flush 250 ms after the last press; no key-up plumbing. Seek bar and media FF/RW keys share the path. | no |
| Q3 | Measure a before/after number on the box (track `perf`)? | No: AC-2 counts seeks in a unit test and AC-8 observes the hold; a perf track would need a telemetry baseline the request does not ask for. | no |
