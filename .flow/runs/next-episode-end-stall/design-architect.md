# Design (architect): next-episode-end-stall

Base 4db9c0d4. Paths below are under `app/src/main/java/com/kino/puber/ui/feature/player/vm/` unless they start with `app/`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where does "cancel still advances at the end" live? | Only in `onPlaybackEnded()` (`PlayerVM.kt:1360-1382`) and `onPanelClosed()` (`PlayerVM.kt:848-861`). `cancelCountdown()` (`PlayerVM.kt:1398-1404`) and `countdownDismissed` keep their meaning: "do not show the prompt again before the end". | `countdownDismissed` also gates the early prompt (`PlayerVM.kt:1572`) and the credits check (`PlayerVM.kt:1644`); AC-4 needs that unchanged. |
| What does the ended branch do after a cancel with no panel open? | Calls `playNextEpisode()` (`PlayerVM.kt:1046-1054`) right after `autoMarkCurrentAsWatched()` (`PlayerVM.kt:1361`). It does not start a countdown (Q1). | `playNextEpisode()` is the single path the countdown, the Next button and the credits skip (`PlayerVM.kt:1394`, `:668`, `:1796`) already use; `switchEpisode()` (`PlayerVM.kt:1031-1044`) saves the position and resets the session. |
| Shape of the new `when` in `onPlaybackEnded()` | Ordered: panel open → mark only (as base); `nextEpisodeCountdown != null` → `showControlsForEndedPlayback()` (as base); `countdownDismissed` → advance; else → `startNextEpisodeCountdown()`. | Same results as base in every branch except the dismissed one. |
| What if `findNextEpisode` returns null although `hasNextEpisode` is true? | `playNextEpisode()` returns `Boolean` (true when it called `switchEpisode`); the ended branch falls back to `showControlsForEndedPlayback()` on false. The other three callers ignore the result. | Without it the viewer would be left on a still frame with no controls (risk 2). |
| How does a panel closing on an ended, dismissed episode advance (Q2, AC-6)? | New field `advanceWhenPanelCloses`, next to `exitWhenPanelCloses` (`PlayerVM.kt:164`). `onPlaybackEnded()` sets it when a panel is open, the episode has a next one and `countdownDismissed`. `onPanelClosed()` consumes it after the `exitWhenPanelCloses` check: clear, then `playNextEpisode()`; on false it falls through to the existing `checkEarlyNextEpisode()`, because `closePanel()` already shows the controls with focus on the panel opener (`ControlsStateMachine.kt:83-91`). | Reading `countdownDismissed` at close time is not enough: `selectEpisodeById()` closes the panel before its own switch (`PlayerVM.kt:991-993`) and would be overtaken by the next episode, because `switchEpisode()` drops a second call while one is in progress (`PlayerVM.kt:1032`). |
| Where is `advanceWhenPanelCloses` cleared? | In `onPlaybackResumed()` (`PlayerVM.kt:805-808`, which `resetMediaSession()` also calls, `:1028`) and in `selectEpisodeById()` beside `exitWhenPanelCloses = false` (`PlayerVM.kt:991`). It is **not** cleared by the hide timeout (`PlayerVM.kt:818`). | An Episodes row closed by its 6 s timeout on an ended episode is still "the panel closes" in AC-6; only `exitWhenPanelCloses` carries the "a timeout never leaves the player" rule. |
| AC-7: final position on the ended path | `onPlaybackEnded()` publishes `currentPosition`, `duration` and `bufferedPosition` from `playbackController` once, right after the `Content` check (`PlayerVM.kt:1362`), on every path. | The tick stops publishing at ENDED (`PlayerVM.kt:1513-1523`; `isPlaying` false per `PlaybackController.kt:907-914`). One copy at the top covers both "controls now" and "controls when the panel closes". `bufferedPosition` keeps the bar's buffered segment consistent with the position. |
| Comment and pinned test | Replace the comment at `PlayerVM.kt:1375-1376` with the new rule; rewrite `PlayerVMPromptDismissalTest.kt:69-78` (path under `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/`) to assert the switch. | Brief scope. |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data change. |
| ViewModel state and actions | `PlayerVM.kt` only: field `advanceWhenPanelCloses` (near `:164`); `onPlaybackEnded()` (`:1360-1382`) publishes the final position, reorders the `when`, advances or arms the flag; `onPanelClosed()` (`:848-861`) consumes the flag; `onPlaybackResumed()` (`:805-808`) and `selectEpisodeById()` (`:991`) clear it; `playNextEpisode()` (`:1046-1054`) returns `Boolean`. `ControlsStateMachine.kt` unchanged. `PlaybackController.kt` unchanged (Q3). |
| Screen and DI module | N/A: no wiring change. |
| Compose content | N/A: `PlayerContentState` fields already exist; the seek bar reads them as before. |
| Resources | N/A. |
| Tests | `PlayerVMPromptDismissalTest.kt`: rewrite `:69-78`; the AC rows themselves come from test-design. Fixture stubs `findNextEpisode` to null (`app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:117`); advancing tests stub it to a pair and verify `playbackController.release()` / Loading state, as `PlayerVMEpisodeFlowTest.kt:129-147` does through `SelectEpisode`. Back on the prompt (AC-2) goes through `vm.onBackPressed()` (`PlayerVM.kt:1450`), which reaches `cancelCountdown()` at `:1474-1476`. |

## Alternatives rejected

- Drop `countdownDismissed` from the ended branch only and let it start a countdown: contradicts Q1 (switch at once).
- Clear `countdownDismissed` in `cancelCountdown()`: brings the early prompt straight back on the next tick, breaking AC-4.
- Decide the panel-close advance from `controlsStateMachine.state.playbackEnded && countdownDismissed`: `selectEpisodeById()` would lose its chosen episode to the next one.
- Fix `PlaybackController.play()` from ENDED: Out of scope (Q3, run `player-ended-play-hardening`).

## Risks

| # | Risk | Where | Mitigation |
|---|---|---|---|
| 1 | Correctness: picking an episode from the Episodes row on an ended, dismissed episode plays the next episode instead of the picked one. | `selectEpisodeById()` `PlayerVM.kt:983-997` → `closePanel()` → `onPanelClosed()`; `switchEpisode()` guard `:1032`. | Clear `advanceWhenPanelCloses` before `closePanel()`; test-design should add a row for it. |
| 2 | Lifecycle: `hasNextEpisode` true but `findNextEpisode` null leaves no controls and no exit. | `onPlaybackEnded()`, `playNextEpisode()` `PlayerVM.kt:1046-1054`. | Boolean result and fallback to `showControlsForEndedPlayback()`. |
| 3 | Lifecycle: the advance on panel close fires during exit. | `scheduleControlsHide()` already returns when `closing` (`PlayerVM.kt:815`); `exitWhenPanelCloses` is checked first in `onPanelClosed()`, and both flags are never set together (film/last episode vs. has-next). | Keep the consume after the exit check. |
| 4 | Data: the finished episode must be marked watched and its position saved before the switch. | `autoMarkCurrentAsWatched()` `PlayerVM.kt:1361` runs first; `switchEpisode()` calls `saveCurrentPosition()` `:1034`. | Keep the order; a unit test verifies the watched request before `release()`. |
| 5 | Focus / D-pad: after an automatic advance focus lands where a normal switch leaves it (Loading → new Content, controls re-initialized). No new focus path. | `ControlsStateMachine.initialize()` `ControlsStateMachine.kt:45-50`. | Covered by AC-8 on the device. |
| 6 | Recomposition: one extra `PlayerContentState` copy per ended event. | `onPlaybackEnded()`. | Negligible; once per media end. |
| 7 | Rollout: no in-player way remains to stay on a finished series episode that has a next one (Back on the prompt cancels too, AC-2); earlier decisions in `seek-blocked-by-skip-prompt` and `player-overlay-autohide` are superseded for this case. | Product behaviour. | Owner decision (state.md Decisions); no spec edit. |

## Open questions

none
