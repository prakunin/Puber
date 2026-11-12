# Brief: next-episode-end-stall

Base 4db9c0d4. Analyst, 2026-11-12. Builds on `research.md` (cited as research.md: section). No brief template exists in the plugin templates; this follows the analyst job layout.

kind: bug. size: M. tracks: none. Device verification: **yes** (playback end state, ViewModel state, player overlays).

## Request
After the owner cancels the next-episode countdown and watches to the end, the player stays on the finished episode: seek bar at the start, buffering, nothing plays. The owner expects the next episode to start at the end even after a cancel (state.md Log). That answers research.md: Open questions Q1 as "hide the prompt, still advance at the end" (Option 1).

## Root cause
| # | Symptom | Cause | Status |
|---|---------|-------|--------|
| 1 | No move to the next episode | `cancelCountdown()` sets `countdownDismissed = true` (`PlayerVM.kt:1398-1404`; Back on the prompt goes there too, `:1474-1476`). `onPlaybackEnded()` then skips the countdown and calls `showControlsForEndedPlayback()` (`PlayerVM.kt:1374-1381`). Intended on base and pinned by `PlayerVMPromptDismissalTest.kt:69-78`. | confirmed (code trace) |
| 2 | Seek bar at the start | The position tick publishes only while `isPlaying` or `isBuffering` (`PlayerVM.kt:1513-1523`); at `STATE_ENDED` `isPlaying` is false (`PlaybackController.kt:907-914`) and the ended path publishes no final position, so the bar draws a stale value, possibly 0 (research.md: Current state in code). | plausible |
| 3 | Buffering, nothing plays | Any Play intent at the end runs `play()`, which calls `seekToDefaultPosition()` (`PlaybackController.kt:523-532`) and restarts from 0 with no `SeekStallRecovery` armed (compare `seekTo()`, `:535-544`). Why the reload stalls is unknown. | plausible, not reproduced on a device |

With cause 1 removed, the series-with-next-episode path no longer reaches the ended controls outside the panel case, so causes 2 and 3 no longer apply to the reported scenario.

## Scope
In: `onPlaybackEnded()` (`PlayerVM.kt:1359-1382`) switches to the next episode at once when the countdown was dismissed and no panel is open; a panel that closes on an ended, dismissed episode does the same (`onPanelClosed()`, `PlayerVM.kt:848-861`; see Q2); the ended path publishes the final `currentPosition`/`duration` once. Rewrite `PlayerVMPromptDismissalTest.kt:69-78` and the comment at `PlayerVM.kt:1375-1376`.
Out: `PlaybackController.play()` from ENDED and stall recovery for it; Next-button focus on the ended controls (Q3).

## Acceptance criteria
- AC-1. Given a series episode with a next episode, the countdown cancelled, no panel open, When playback reaches `STATE_ENDED`, Then `switchEpisode` runs for the next episode without a second countdown and the ended controls are not shown. (unit, red on base)
- AC-2. Given the same, but cancelled with Back on the prompt, When playback ends, Then the next episode starts as in AC-1. (unit)
- AC-3. Given a series episode with no countdown cancelled, When playback ends, Then the countdown starts and plays the next episode when it runs out, as on base. (unit, existing tests green)
- AC-4. Given a cancel and then a seek, When the tick reaches the prompt position, Then the prompt shows again (`PlayerVMPromptDismissalTest.kt:81-95` green unchanged).
- AC-5. Given a film or the last episode, When playback ends, Then the player exits, or exits when the open panel closes, as on base. (unit, existing tests green)
- AC-6. Given a dismissed countdown and a panel open when playback ends, When the panel closes, Then the next episode starts. (unit; depends on Q2)
- AC-7. Given playback ends on any path that shows the ended controls, When they appear, Then `currentPosition` and `duration` equal the player's final values. (unit, red on base)
- AC-8. Given a series episode on the TV, When the user cancels the countdown with the D-pad and watches to the end, Then the next episode starts playing from its start with no spinner left on the finished episode. (device)

## Risks
- Risk areas "playback" and "ViewModel state", hence M rather than the research's S; the diff itself is `PlayerVM.kt` plus tests.
- Removing the stop-at-end behaviour leaves Back on the prompt as the only way to stay at the end; the decisions in `seek-blocked-by-skip-prompt` and `player-overlay-autohide` (research.md: Related work) are superseded for this case.
- `onPlaybackEnded()` also runs `autoMarkCurrentAsWatched()` first (`PlayerVM.kt:1360`); the switch must keep that order so the finished episode is still marked.
- AC-8 needs an owner-account episode played to its end on the emulator; playback evidence stays out of Git (AGENTS.md).

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | At the end after a cancel: switch at once, or show the countdown again? | Switch at once (research.md: Open questions Q2). | yes |
| Q2 | When a panel is open as the dismissed episode ends, should closing the panel start the next episode? | Yes, same rule as with no panel (AC-6). | yes |
| Q3 | Include the hardening of `play()` from ENDED (stall recovery, Next focus on ended controls)? | No; capture as a follow-up idea run. | no |
