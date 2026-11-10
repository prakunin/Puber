# Test design: seek-blocked-by-skip-prompt

Base 6b087697. ACs: `brief.md#acceptance-criteria`; decisions and risks R1-R7: `design-architect.md`. Paths below are relative to `app/src/`.

## Where the tests live

| File | Level | Notes |
|---|---|---|
| `test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMSeekBurstTest.kt` (new) | unit | Extends `PlayerVMTestFixture`; `MainDispatcherExtension`, `runTest(mainDispatcher.dispatcher.scheduler)`, `advanceTimeBy`/`runCurrent`. Mutable-position stub copied from `PlayerVMSkipCountdownFitTest.playingInto`. A seek is `vm.onAction(PlayerAction.SeekForward)` followed by `callbackSlot.captured.onSeekPerformed()`, which the real player fires. |
| `test/kotlin/com/kino/puber/ui/feature/player/component/PlayerVideoSurfaceTest.kt` (extend) | unit | `seekActionForKeyCode` cases. |
| `androidTest/kotlin/com/kino/puber/ui/feature/player/component/PlayerPromptFocusAndKeysTest.kt` (new) | instrumented | `createAndroidComposeRule<ComponentActivity>()`, `PuberTheme { PlayerScreenContent(state, onAction = actions::add, exoPlayer = { null }) }` with the state in a `mutableStateOf`. Goes through `PlayerScreenContent` because its signature does not change, so every `red` row compiles and runs on the base. A file-local `contentState(...)` builder fills `PlayerContentState` (the unit fixture is not visible to `androidTest`). Keys through `onRoot().performKeyInput { keyDown/keyUp(Key.DirectionRight) }`; nodes found by the action label. Run: `make DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.player.component.PlayerPromptFocusAndKeysTest`. |

## Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Seek keys on a focused prompt move focus instead of seeking (the bug) | 3 | 3 | 9 | AC-1, AC-2 instrumented `red` |
| R1: focus request on hide fails silently, first D-pad press swallowed | 2 | 3 | 6 | AC-4 instrumented `red`, M2 |
| Burst gate never releases, or delays prompts after skip/resume/start over | 2 | 3 | 6 | AC-3 unit, `aSkipLandingOnTheNextSegmentOffersItWithoutWaiting` |
| An appearing prompt takes focus from the seek bar | 3 | 2 | 6 | AC-6 instrumented `red`, M4 |
| R7: no skip-segment data on a worktree build | 3 | 2 | 6 | Skip prompt covered by instrumented tests; M3 source named by qa-device |
| R2: skip prompt replaced by next-episode prompt orphans focus | 2 | 2 | 4 | `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction` |
| R3: stray OK `ACTION_UP` fires the prompt action | 2 | 2 | 4 | `skipPrompt_okUpWithoutDownDoesNotSkip` |
| Prompt forwards the wrong key set (Up/Down, play/pause, missing media keys) | 1 | 2 | 2 | `seekActionForKeyCode_*` |
| R5: recomposition cost of the new parameters | 1 | 2 | 2 | Accepted: requesters hold only `val FocusRequester`. |
| R6: rebase conflict with `player-overlays-port` | 2 | 1 | 2 | Accepted: process, not behaviour. |
| R4: burst job survives an episode switch (prompt up to 1.5 s late) | 1 | 1 | 1 | Accepted, bounded by the window. |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 `red` | `skipPrompt_leftAndRightSeekAndFocusStaysOnAction` | `onAction` receives `SeekBackward`, `SeekForward` and the action stays focused; fails if the `Row` preview handler is removed (base: Right moves focus X to action, no seek). |
| AC-1 | instrumented | P0 `red` | `skipPrompt_heldRightSeeksOnEveryRepeat` | `keyDown`, `advanceEventTime(1_000)`, `keyUp` yields more than one `SeekForward`; fails if the handler ignores repeats (`repeatCount > 0`) or acts on up. |
| AC-1 | unit | P1 `guard` | `SeekHandlerTest.rapidConsecutiveCalls_incrementsStep` (existing) | 10/10/20/30/60 s escalation unchanged. |
| AC-2 | instrumented | P0 `red` | `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` | Same as AC-1 for `nextEpisodeCountdown`; fails if only the skip overlay gets `onSeek`. |
| AC-2 | unit | P1 `guard` | `PlayerVMPromptDismissalTest.seekingWhileTheNextEpisodePromptIsUpTakesItDown` (existing) | A seek still cancels the countdown. |
| AC-3 | unit | P0 `red` | `aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds` | Start outside the intro, seek in; at +1 000 ms `activeSkipSegment` is null (base: raised by the 500 ms tick); after the window plus one tick it is set and stays equal over two more ticks. Fails if the tick gate is removed. |
| AC-3 | unit | P0 `red` | `aSeekBurstIntoTheCreditsLeadInRaisesNoNextEpisodePromptUntilItEnds` | Same for `nextEpisodeCountdown` (seek from 100 000 to 2 380 000 ms); fails if only `checkSkipSegment` is gated. |
| AC-3 | unit | P1 | `everySeekInTheBurstRestartsTheWindow` | Seeks at 0, 1 000, 2 000 ms: prompt still null at 3 000 ms, set after 3 500 ms plus a tick; fails if the job is not restarted per seek. |
| AC-3 | unit | P1 `guard` | `aSkipLandingOnTheNextSegmentOffersItWithoutWaiting` | After `SkipSegmentClicked` onto a second segment, its prompt is up within one tick; fails if the burst starts in `onSeekPerformed`. |
| AC-4 | instrumented | P0 `red` | `skipPromptHidden_nextRightSeeks` | Prompt visible and focused, state flips to `activeSkipSegment = null`, after idle one Right yields `SeekForward`; fails if the hide does not refocus the surface. If it passes on the base, root-cause step 4 is refuted: record it and keep the test as `guard`. |
| AC-4 | instrumented | P0 `red` | `nextEpisodePromptHidden_nextRightSeeks` | Same for `nextEpisodeCountdown = null`. |
| AC-4 | instrumented | P1 | `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction` | R2: the effect keys on the prompt kind; fails if it keys on a visible boolean. |
| AC-5 | instrumented | P0 `red` | `skipPrompt_appearsWithActionFocused_okSkips` | Action node `assertIsFocused()`, OK yields `SkipSegmentClicked`; fails if the X keeps default focus. |
| AC-5 | instrumented | P1 `guard` | `skipPrompt_backCancels` | `activity.onBackPressedDispatcher.onBackPressed()` yields `CancelSkipSegment`. |
| AC-6 | instrumented | P0 `red` | `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` | Controls up, `controlsFocusTarget = SeekBar`, then a skip prompt: action not focused, Right yields `SeekForward`; fails if the prompt requests focus while controls are visible. |
| AC-6 | instrumented | P1 `red` | `controlsHidingWhilePromptIsUp_focusesThePromptAction` | `controlsVisible` flips to false with the prompt up: action focused; fails if the effect sends focus to the surface. |
| AC-7 | instrumented | P0 `guard` | `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel` | Recorded actions during held Left and Right contain no `SkipSegmentClicked` or `CancelSkipSegment`; fails if the handler lets the key reach the focused button. |
| AC-7 | instrumented | P1 | `skipPrompt_okUpWithoutDownDoesNotSkip` | R3: a native `ACTION_UP` `KEYCODE_DPAD_CENTER` sent through `activity.dispatchKeyEvent` with the action focused records no `SkipSegmentClicked`. |
| AC-8 | unit | P0 `guard` | `PlayerVMPromptDismissalTest.seekingAfterCancellingPutsTheNextEpisodeQuestionBack` (existing) | Next-episode prompt still returns after seek-after-cancel (Q2). |
| AC-8 | unit | P0 `guard` | `PlayerVideoSurfaceTest.playerActionForKeyCode_opensButtons_whenDownPressed`, `playerActionForKeyCode_opensSeekControls_whenUpPressed` (existing) | Surface key map unchanged; whole suite via `./tools/agentw testDevDebugUnitTest`. |
| AC-8 | unit | P1 | `seekActionForKeyCode_returnsSeekActions_forLeftRightAndMediaSeekKeys` | Left, Right, FF, RW, skip forward/backward map to the seek actions. |
| AC-8 | unit | P1 | `seekActionForKeyCode_returnsNull_forUpDownCenterAndPlayPause` | Fails if the prompt forwards the whole `playerActionForKeyCode` map. |
| AC-9 | manual | P0 | M1, M5 | Owner sequence on the device. |

## Failing-first test

`skipPrompt_leftAndRightSeekAndFocusStaysOnAction`. On the base the X holds focus (`component/SkipSegmentOverlay.kt:28`, `focusPrimaryAction = false`) and the `Row` (`component/PlayerTimedActionOverlay.kt:266`) has no key handler, so Right only moves focus and `onAction` records no `SeekForward`. VM side: `aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds` (base tick at `vm/PlayerVM.kt:1510-1515` raises the prompt at 500 ms).

## Manual checks

Emulator, worktree dev APK, lease and `adb -s <serial>`. Keys: Right 22, Left 21, Up 19, OK 23, Back 4. Series episode for the next-episode prompt; qa-device names the skip-segment source for M3.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | Play an episode; press Right repeatedly (under 1.5 s apart) until the playhead passes into the credits lead-in; stop; wait for the prompt; press Right twice. | No prompt during the burst; prompt appears about 1.5 s after the last press with the action focused; both Right presses seek (seek indicator moves). | Seeking stops; focus ring moves to X. |
| M2 | With the next-episode prompt up, hide it in turn by: Left, Back, waiting out the countdown, OK (then in the new episode). After each, press Right once. | The first Right after every hide seeks. | First press swallowed; no focused node; crash. |
| M3 | Reach an intro with the skip prompt; check focus; press Right, then let it reappear and press OK; repeat and press Back, then Right. | Action chip focused (85 % hairline, 1.1 scale); Right seeks; OK skips; Back dismisses and the next Right seeks. | X focused by default; seek key fires skip or cancel. |
| M4 | Press Up to open the seek bar; press Right until the playhead enters the lead-in; wait for the prompt; press Right; then wait for the controls to auto-hide. | Seek bar keeps focus while the prompt shows idle; Right seeks; after auto-hide the prompt action is focused. | Prompt steals focus from the seek bar; no focus after auto-hide. |
| M5 | `input keyevent --longpress 22` through an intro or the credits lead-in. | Seeking continues through the section. | Seeking halts; prompt action or cancel fires. Owner judges any prompt flash. |

Visual checks: `design-ux-designer.md#for-visual-review`.

## Perf

N/A: perf track is off.
