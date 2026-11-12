# Research: next-episode-end-stall

## Request

"I see a recurring bug. The episode ends, a popup with a countdown to the next episode appears. I cancel it. I watch the episode to the end. But at the end the player does not move to the next episode; it stays on this one, the seek bar goes to the start, I see buffering and nothing plays. I exit, pick the next episode, and keep watching." (owner, translated from Russian)

## Understanding

When the next-episode prompt is cancelled, the player is coded to stay on the finished episode at `STATE_ENDED` and show the end-of-media controls instead of advancing. Not advancing is the intended behaviour, and a unit test pins it. The owner expects the opposite: cancel should only hide the prompt, and the next episode should still start at the end. There are also two defects in the ended state. The seek bar shows a stale position, because position is not published once playback stops. Any Play intent at the end restarts the episode from 0 through `seekToDefaultPosition()`, with no stall recovery armed for that reload. Together these plausibly explain "seek bar at the start, buffering, nothing plays" (`unverified` on a device).

## Current state in code

All paths are under `app/src/main/java/com/kino/puber/ui/feature/player/`.

| Where | What it does |
|---|---|
| `vm/PlayerVM.kt:1398-1404` | `cancelCountdown()` cancels the job and sets `countdownDismissed = true`. Back on the prompt goes through the same path (`:1474-1476`). |
| `vm/PlayerVM.kt:1359-1382` | `onPlaybackEnded()`. Film or last episode: exit. Otherwise, when not dismissed, start the countdown; when dismissed, `showControlsForEndedPlayback()` and stay. The comment at `:1375-1376` says "Cancelling on the credits means cancelled, not postponed". |
| `vm/PlayerVM.kt:1687-1694` | `onSeekPerformed()` is the only way back: any seek resets `countdownDismissed = false`. |
| `vm/PlayerVM.kt:1564-1588`, `model/PlayerCountdowns.kt:22,32,37,80` | The early prompt goes up at credits minus 2 s, or 22 s before the end without credits data, and fires after 7 s. An undismissed prompt switches episodes before `STATE_ENDED`, so the ended branch for a series is reached in practice only after a cancel. |
| `vm/PlayerVM.kt:795-799`, `vm/ControlsStateMachine.kt:113-116` | Ended controls: visible, `focusTarget = null`, `playbackEnded = true`, never auto-hidden. Back exits (`ControlsStateMachine.kt:94-101`). |
| `component/PlayerScreenContent.kt:83-99` | With the controls visible and a null `focusTarget`, no focus request runs, so focus stays on the video surface, where it went after the cancel (prompt gone → `focusRequesters.player`). |
| `vm/PlayerVM.kt:1505-1530`, `:1557` | The position tick publishes `currentPosition`/`duration` only while `isPlaying` or `isBuffering` and something is showing position. At `STATE_ENDED`, `isPlaying` is false (`vm/PlaybackController.kt:907-914`), and neither the ended path nor `applyControlsState()` (`:863-866`) publishes a final position. The ended seek bar therefore draws the last position written while the controls or the buffering bar were up. That can be 0 (`vm/ContentStateFactory.kt:28`) or an early value. |
| `vm/PlaybackController.kt:523-532` | `play()` at `STATE_ENDED` calls `seekToDefaultPosition()`, then `onSeekPerformed()`: the episode restarts from 0 and has to rebuffer from the start. Added in `03c2ca31` (Release 1.7.1). |
| `vm/PlaybackController.kt:535-544` vs `:523-532` | `seekTo()` arms `SeekStallRecovery` (8 s with no buffer progress, then the next URL; `vm/SeekStallRecovery.kt:66`). `play()` from ENDED does not, so a stalled reload from 0 shows the spinner with no recovery. |
| Play intents at the end | OK when `okTogglesPlayPause` is on (`vm/PlayerVM.kt:762-766`; default off in `data/repository/PlayerPreferencesRepository.kt:92`). OK, then OK again on the first button, which is Play/Pause (`component/PlayerButtonRow.kt:190-194`). The media Play/Pause keys (`component/PlayerVideoSurface.kt:213-221`). All of them reach `togglePlayPause()` (`vm/PlayerVM.kt:683-694`) and then `play()`. |
| `vm/PlaybackController.kt:229-235` | `STATE_ENDED` cancels stall recovery and calls `onPlaybackEnded()`. Nothing else re-prepares or seeks at the end. The only other automatic `seekToDefaultPosition` is `recoverBehindLiveWindow()` (`:322-327`), which applies to live streams only. |
| `component/PlayerButtonRow.kt:195-201` | The ended controls do show a Next-episode button, but focus is not placed on it. |

Tests around this code:

| Test | What it covers |
|---|---|
| `PlayerVMPromptDismissalTest.kt:69-78` | Pins "a cancelled next episode is not offered again when the episode runs out". |
| `PlayerVMPromptDismissalTest.kt:81-95` | A seek after a cancel brings the prompt back. |
| `PlayerVMEpisodeFlowTest.kt:129-147` | An episode switch resets the dismissed flag. |
| `PlayerVMOverlayAutoHideTest.kt:398` | Ended controls stay up. |

No test covers `play()` from `STATE_ENDED` (`PlaybackIntentTest.kt:32` covers only `isPlaybackIntended`), and none covers the seek bar position at the end.

## Related work

| Run / doc | Relation |
|---|---|
| `seek-blocked-by-skip-prompt` (done) | Q2 kept "a seek after a cancel brings the prompt back". Same dismissal flag. Spec `docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md:22,77`. |
| `player-overlay-autohide` (spec `docs/superpowers/specs/2026-10-10-player-overlay-autohide-design.md` AC-11, D7, D13) | Designed the ended controls to "stay until Back". This request changes what follows a cancel in that state. |
| `prompt-key-passthrough` (idea) | Play/Pause through the prompt; same prompt surface, different behaviour. |
| `prompt-hide-focus-under-controls` (idea) | Focus when a prompt hides; adjacent. |
| `player-vm-split`, `playback-controller-split` (idea, refactors) | Touch the same files; risk of merge overlap only. |
| `resume-progress-anomaly` (idea) | Separate symptom (resume at 0:02); not this. |
| Commits | `1a2c6cde` (countdown rework), `5d91fd66` (seek-burst hold), `03c2ca31` (`play()` from ENDED → `seekToDefaultPosition`), `1674417d` and `88cdf071` (dismissal flag origins). |

Possible duplicate: none.

## External findings

| Finding | Source |
|---|---|
| Google's Cast autoplay checklist gives an Up Next prompt three actions. Wait: the next item plays when the current one ends. Play: the next item starts now. Stop: autoplay is cancelled, the current item finishes, and the queue is kept. The current Puber cancel works like "Stop", while the owner expects "Wait". | https://developers.google.com/cast/docs/design_checklist/cast-autoplay (fetched 2026-11-12) |
| A maintainer describes ExoPlayer's ended behaviour: Play on an ended single VOD item plays from the window's default position, i.e. the start. This matches `PlaybackController.kt:526-529`. | https://github.com/google/ExoPlayer/issues/3689 (2018, pre-Media3) |
| `Player.STATE_ENDED`: the player finished playing all media. Project Media3 version: `1.11.0` (`gradle/libs.versions.toml:27`). | https://developer.android.com/reference/androidx/media3/common/Player |
| No Media3 issue found for a VOD HLS stream that stays stuck in buffering at the end instead of reaching ENDED. That alternative cause is `unverified`, and the "seek bar at the start" symptom argues against it, since a tail stall publishes the end position. | search 2026-11-12 |

## Options

| # | Change | Trade-offs | Size |
|---|---|---|---|
| 1 (recommended) | Cancel hides the prompt only. At `STATE_ENDED` a series episode with a next episode always switches, whatever `countdownDismissed` says (`PlayerVM.kt:1371-1381`). Also publish the final position and duration on the ended path. | Matches the report and Cast "Wait". Rewrites one pinned test (`PlayerVMPromptDismissalTest.kt:69`) and the comment at `:1375`. There is no in-player way to stop at the end except Back while the prompt is up. The ended controls survive only for the under-a-panel case. | S (`PlayerVM.kt` + 1-2 test files) |
| 2 | Keep cancel = stop. Repair the ended state instead: publish the final position, focus the Next button on the ended controls, and arm `SeekStallRecovery` (or a deliberate restart) for `play()` from ENDED. | Keeps the tested product rule. The owner still has to press Next, which the report asks to remove. Touches `PlaybackController`, a playback risk area. | M |
| 3 | Option 1 plus Option 2's playback hardening: stall recovery on restart from ENDED and the Next-button focus for ended-under-panel. | Covers both the request and the latent restart stall. Larger, and crosses into the controller. | M |

## Risks

- `PlaybackController.play()` from ENDED is shared by every ended path (films under a panel, ended-under-panel episodes). Any change there needs a device check of the restart from 0.
- The root cause of "nothing plays" after the restart is `unverified`. The code shows no recovery is armed, but not why the reload from 0 stalls (an expired CDN link or a slow start are guesses). It needs a device reproduction with the dev build. Owner-account playback evidence must stay out of Git (AGENTS.md).
- Whether the owner pressed OK or Play at the end is `unverified`. If he pressed nothing, the start-of-bar position is the stale-publish defect alone, and the buffering source is still unexplained.
- Option 1 changes behaviour that `seek-blocked-by-skip-prompt` and `player-overlay-autohide` decided. Their specs stay as history and need no edit.

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | What does Cancel on the next-episode prompt mean: stop autoplay for this episode (now), or hide the prompt and still advance at the end? | Hide and still advance at the end (Option 1). |
| Q2 | At the end after a cancel: switch at once, or show the countdown again? | Switch at once. The viewer has already answered once, and a second prompt would ask again. |
| Q3 | Should the playback hardening (stall recovery for `play()` from ENDED, Next focus on ended controls) be part of this run? | No. Capture it as a follow-up idea unless the device repro shows the restart stall on the Option 1 path. |

## Sources

- Code (master `4db9c0d4`):
  - `vm/PlayerVM.kt`
  - `vm/PlaybackController.kt`
  - `vm/ControlsStateMachine.kt`
  - `vm/SeekStallRecovery.kt`
  - `vm/ContentStateFactory.kt`
  - `model/PlayerCountdowns.kt`
  - `component/PlayerScreenContent.kt`
  - `component/PlayerButtonRow.kt`
  - `component/PlayerVideoSurface.kt`
  - `data/repository/PlayerPreferencesRepository.kt`
  - `gradle/libs.versions.toml`
- Tests:
  - `PlayerVMPromptDismissalTest.kt`
  - `PlayerVMEpisodeFlowTest.kt`
  - `PlayerVMOverlayAutoHideTest.kt`
  - `PlaybackIntentTest.kt`
- Commits: `03c2ca31`, `1a2c6cde`, `5d91fd66`, `d051c873`, `1674417d`, `88cdf071`.
- Runs:
  - `seek-blocked-by-skip-prompt`
  - `player-overlay-autohide` (spec)
  - `prompt-key-passthrough`
  - `prompt-hide-focus-under-controls`
  - `player-vm-split`
  - `playback-controller-split`
  - `resume-progress-anomaly`
  - `media3-hagc-removal`
  - `subtitle-upstream-port` (spec)
- URLs:
  - https://developers.google.com/cast/docs/design_checklist/cast-autoplay
  - https://github.com/google/ExoPlayer/issues/3689
  - https://developer.android.com/reference/androidx/media3/common/Player
