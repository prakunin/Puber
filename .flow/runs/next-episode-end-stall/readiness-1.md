# Readiness 1: next-episode-end-stall

Verdict: **NOT READY**. Spec and plan at worktree HEAD 071a4bd0. The fixes below touch only the documents. The code plan is sound.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 | `aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut`, R5 and R7 rows |
| AC-2 | Task 1 | `backOnTheNextEpisodePromptStillStartsItWhenTheEpisodeRunsOut` |
| AC-3 | Task 1 (guards unchanged) | existing guard row |
| AC-4 | Task 1 (guard unchanged) | `seekingAfterCancellingPutsTheNextEpisodeQuestionBack` |
| AC-5 | Task 1 (guards unchanged) | existing guard row |
| AC-6 | Task 2 | P0 red, P1 red, R3, R6 and the existing guard row |
| AC-7 | Task 1 | `theEndedControlsShowTheFinalPosition`, P1 variant |
| AC-8 | qa-device | M1-M4 |

## Citations checked (all resolve)

| Citation | Symbol found |
|---|---|
| `PlayerVM.kt:158`, `:164` | `countdownDismissed`, `exitWhenPanelCloses` |
| `PlayerVM.kt:795-799`, `:805-808`, `:815`, `:818` | `showControlsForEndedPlayback()`, `onPlaybackResumed()`, `if (closing) return@launch`, the timeout clearing `exitWhenPanelCloses` |
| `PlayerVM.kt:848-861`, `:854-857` | `onPanelClosed()`, its `exitWhenPanelCloses` block |
| `PlayerVM.kt:983-997`, `:991`, `:992` | `selectEpisodeById()`, `exitWhenPanelCloses = false`, `closePanel()` |
| `PlayerVM.kt:1022`, `:1028`, `:1032`, `:1034`, `:1046-1054`, `:1066-1068` | `countdownDismissed = false`, `onPlaybackResumed()` in `resetMediaSession()`, the `switchEpisode` guard, `saveCurrentPosition()`, `playNextEpisode()`, `switchEpisode(Pair)` |
| `PlayerVM.kt:1360-1381`, `:1394`, `:1398-1404`, `:1474-1476` | `onPlaybackEnded()` with the `when` and its comment at `:1375-1376`; the countdown's `playNextEpisode()`; `cancelCountdown()`; Back reaching `cancelCountdown()` |
| `PlayerVM.kt:668`, `:1796`, `:1513-1523`, `:1572`, `:1644`, `:1687-1694`, `:1880-1882` | the `NextEpisode` action, the credits skip, the tick publish, the early-prompt gate, the credits gate, `onSeekPerformed()`, `autoMarkCurrentAsWatched()` |
| `ControlsStateMachine.kt:83-91`, `:113-116`, `:168-170` | `closePanel()` focusing `lastPanelOpener`, `showControlsForEndedPlayback()`, `markPlaybackEnded()` |
| `PlaybackController.kt:523-532`, `:907-914` | `play()` from ENDED, `isPlaybackIntended` |
| `PlayerVMTestFixture.kt:117`, `PlayerVMOverlayAutoHideTest.kt:41-49`, `:398`, `PlayerVMPromptDismissalTest.kt:69-78`, `:81`, `:103-119`; `detekt.yml:756-759`, `:90-93`; `MainDispatcherExtension.kt:12` | the null `findNextEpisode` stub, `vmTest`, the guard test, the test to rewrite, AC-4 guard, `aLateTickDoesNotTakeTheNextEpisodePromptDown`; `ReturnCount` 3 and `CyclomaticComplexMethod` 14; `UnconfinedTestDispatcher` |

Test API names in the plan exist: `PlayerAction.OnBackPressed` (`PlayerAction.kt:74`), `Callback.onPlaybackStateChanged(isPlaying, isBuffering, position, duration, buffered)` (`PlaybackController.kt:60-66`), `Episode(id, number, title, ...)` (`Models.kt:542`). `PanelClosed` reaches `onPanelClosed()` (`PlayerVM.kt:881`), so the timeout row of AC-6 exercises the new consume.

## Order, build, scope

- Task 1 builds on its own: the panel branch is `Unit` until Task 2, and the new helper has a caller. Task 2 consumes only Task 1 output.
- `endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime` reaches the R7 fallback, since the fixture's `findNextEpisode` returns null, and stays green, as the plan says.
- The production change is limited to `PlayerVM.kt`. `PlaybackController.play()` and the Next-button focus stay out of scope. Q1-Q3 and plan Q1 are answered.

## Fix list

1. The P0 guard row in spec Test design, "AC-6 | ... `nonLastEpisode_episodesTimeoutAfterEnd_...`, `settingsClosedAfterEnd_...`, `aboutClosedAfterEnd_...`", claims to catch "a cancel made after the end (under the panel) arms the advance". It cannot. These tests run with the fixture's `findNextEpisode` = null (`PlayerVMTestFixture.kt:117`), so an armed `advanceWhenPanelCloses` makes `playNextEpisode()` return false. The test then falls through to `checkEarlyNextEpisode()` and gets the same result as unarmed. Add a guard test that uses the next stub (OpenSettingsPanel, `onPlaybackEnded()`, `CancelNextEpisodeCountdown`, ClosePanel: `release()` never called) to the spec row and to Task 2. Or reword the row so it names a mutation those tests do catch.
2. Plan Task 1, step "Red run": "the four `red` rows fail". The table has five red rows: AC-1, AC-2, R5, AC-7 P0 and AC-7 P1. Write "the `red` rows fail" (no derived count).
3. Spec Design, row "How does a panel closing ... advance (Q2, AC-6)?" still says `onPanelClosed()` falls back to `showControlsForEndedPlayback()` on false. Plan Q1 in spec Decisions and plan Task 2 fall through to `checkEarlyNextEpisode()`. Update the Design row so the spec states one rule.
