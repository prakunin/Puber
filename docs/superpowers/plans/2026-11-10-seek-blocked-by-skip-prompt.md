# Seek is blocked by the skip / next-episode prompt - plan

**Spec:** `docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md`
**Goal:** Left/Right and media seek keys keep seeking while a skip or next-episode prompt is on screen, the VM holds prompts down during a seek burst, and focus moves to and from a prompt only through `PlayerFocusEffects`.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Run both in the worktree `/home/user1/orca/workspaces/Puber/seek-blocked-by-skip-prompt`; never `./gradlew` there. Set `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Touched unit tests: `./tools/agentw :app:testDevDebugUnitTest --tests '<FQN>'`.
- Instrumented tests are not run by the implementer: qa-device runs `make DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.player.component.PlayerPromptFocusAndKeysTest` under a lease. The implementer only compiles them: `./tools/agentw :app:compileDevDebugAndroidTestKotlin`.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line. Do not push.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths: `P` = `app/src/main/java/com/kino/puber/ui/feature/player/`, `UT` = `app/src/test/kotlin/com/kino/puber/ui/feature/player/`, `IT` = `app/src/androidTest/kotlin/com/kino/puber/ui/feature/player/`. Line numbers are at base `6b087697`.
- Out of scope, do not touch: dismissal policy (`P/vm/PlayerVM.kt:1676` stays `countdownDismissed = false`); any visual value in `P/component/PlayerTimedActionOverlay.kt` (`TimedActionBottomPadding`, the `Row` 12 dp gap, label size and padding, shadow, `TIMED_ACTION_*_ALPHA`); forwarding Up/Down or Play/Pause from the prompt; the shared focus helper (`focus-request-helper`); `docs/design/`.

---

## Tasks

### Task 1: Add the seek-only key map

**Covers:** AC-8
**Files:**
- Modify: `P/component/PlayerVideoSurface.kt` - add `internal fun seekActionForKeyCode(keyCode: Int): PlayerAction?` directly below `playerActionForKeyCode` (`P/component/PlayerVideoSurface.kt:213`): returns `playerActionForKeyCode(keyCode)` when it is `PlayerAction.SeekForward` or `PlayerAction.SeekBackward`, else null. Do not copy the key map.
- Test: `UT/component/PlayerVideoSurfaceTest.kt` (existing cases at `:16`, `:25`)

**Consumes:** `playerActionForKeyCode` (`P/component/PlayerVideoSurface.kt:213`).
**Produces:** `seekActionForKeyCode(keyCode: Int): PlayerAction?` (Task 4).

- [x] Add `seekActionForKeyCode_returnsSeekActions_forLeftRightAndMediaSeekKeys`: `KEYCODE_DPAD_LEFT`, `KEYCODE_MEDIA_REWIND`, `KEYCODE_MEDIA_SKIP_BACKWARD` give `SeekBackward`; `KEYCODE_DPAD_RIGHT`, `KEYCODE_MEDIA_FAST_FORWARD`, `KEYCODE_MEDIA_SKIP_FORWARD` give `SeekForward`.
- [x] Add `seekActionForKeyCode_returnsNull_forUpDownCenterAndPlayPause`: `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_DOWN`, `KEYCODE_DPAD_CENTER`, `KEYCODE_MEDIA_PLAY_PAUSE` give null.
- [x] Implement the function.
- [x] Run the fast gate and `--tests 'com.kino.puber.ui.feature.player.component.PlayerVideoSurfaceTest'`; commit "Add a seek-only key map beside the player key map".

**Done when:** `PlayerVideoSurfaceTest` passes with the two new cases and the existing `playerActionForKeyCode_*` cases unchanged.

---

### Task 2: Hold prompts down during a seek burst in the VM

**Covers:** AC-3, AC-8 (guard)
**Files:**
- Modify: `P/vm/SeekHandler.kt` - `DEFAULT_SEEK_RESET_TIMEOUT_MS` (`:7`) goes from `private` to `internal`.
- Modify: `P/vm/PlayerVM.kt`:
  - field `private var seekBurstJob: Job? = null` beside the jobs at `:142-151`;
  - `seekForward` (`:706`) and `seekBackward` (`:714`) each call a new `private fun startSeekBurst()` that does `seekBurstJob?.cancel(); seekBurstJob = launch { delay(DEFAULT_SEEK_RESET_TIMEOUT_MS) }` (same `launch { delay(..) }` shape as `:700-703`);
  - in the tick (`:1510-1515`) keep `checkAutoMarkWatched()` and `checkNextEpisodePrefetch()` unconditional and run `checkEarlyNextEpisode()` and `checkSkipSegment()` only when `seekBurstJob?.isActive != true`.
  - Do not touch `onSeekPerformed` (`:1670-1677`): skip (`:1781`), resume (`:1403`) and start over (`:1412`) must not start a burst.
- Create: `UT/vm/PlayerVMSeekBurstTest.kt`

**Consumes:** `PlayerVMTestFixture` (`UT/vm/PlayerVMTestFixture.kt:42`; `callbackSlot` `:76`, `startedVM()` `:190`, `contentState(vm)` `:192`); `MainDispatcherExtension` registration and the mutable-position stub as in `UT/vm/PlayerVMSkipCountdownFitTest.kt:29-45` (`playingInto`) and `:53` (`playFor`).
**Produces:** no new interface.

Test shape: class `internal class PlayerVMSeekBurstTest : PlayerVMTestFixture()`, each case `runTest(mainDispatcher.dispatcher.scheduler) { ... vm.testCancelScope() }`. Stub `every { playbackController.seekTo(any()) } answers { position = firstArg() }` so `seekForward` (which reads `playbackController.currentPosition`, `P/vm/PlayerVM.kt:708`) moves the stubbed playhead. A seek = `vm.onAction(PlayerAction.SeekForward)` then `callbackSlot.captured.onSeekPerformed()`. Advance with `advanceTimeBy` + `runCurrent()`; the tick is 500 ms, the window `DEFAULT_SEEK_RESET_TIMEOUT_MS` = 1 500 ms.

| Test | Setup and assertion |
|---|---|
| `aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds` | Intro outside the start position, one seek lands inside (for example intro 30 000-60 000, start 20 000, first step 10 s). At +1 000 ms `activeSkipSegment` is null; after 1 500 ms plus one tick it is non-null and stays equal over two more ticks. |
| `aSeekBurstIntoTheCreditsLeadInRaisesNoNextEpisodePromptUntilItEnds` | No credits segment, duration 2 400 000 (fixture), start 100 000; for this case `seekTo` answers `position = 2_380_000L`. At +1 000 ms `nextEpisodeCountdown` is null; after the window plus one tick it is non-null. |
| `everySeekInTheBurstRestartsTheWindow` | Seeks at 0, 1 000, 2 000 ms into a long segment; prompt null at 3 000 ms, non-null after 3 500 ms plus one tick. |
| `aSkipLandingOnTheNextSegmentOffersItWithoutWaiting` | Two segments (for example `INTRO` 10 000-40 000 and `RECAP` 41 000-80 000), playing from 7 500 until the intro prompt is up; `vm.onAction(PlayerAction.SkipSegmentClicked)`, then `onSeekPerformed()`; within one tick `activeSkipSegment?.type == RECAP`. |

- [x] Write the four tests; run them on the unchanged VM and record the result under Red run (expected: the first three fail, the fourth passes).
- [x] Implement the field, `startSeekBurst()`, the two calls and the tick gate.
- [x] Run the fast gate and `--tests 'com.kino.puber.ui.feature.player.vm.*'` (covers `PlayerVMPromptDismissalTest`, `PlayerVMSkipCountdownFitTest`, `PlayerVMCreditsPromptTest`, `SeekHandlerTest`); commit "Hold the skip and next-episode prompts down while a seek burst runs".

**Done when:** all `com.kino.puber.ui.feature.player.vm.*` unit tests pass, including the four new ones and `PlayerVMPromptDismissalTest.seekingAfterCancellingPutsTheNextEpisodeQuestionBack` and `seekingWhileTheNextEpisodePromptIsUpTakesItDown`.

---

### Task 3: Add the prompt focus and key Compose tests

**Covers:** AC-1, AC-2, AC-4, AC-5, AC-6, AC-7 (tests only; production code unchanged)
**Files:**
- Create: `IT/component/PlayerPromptFocusAndKeysTest.kt`

**Consumes:** `PlayerScreenContent(state, onAction, exoPlayer)` (`P/component/PlayerScreenContent.kt:35`, signature unchanged by this plan); `PlayerContentState` (`P/model/PlayerViewState.kt:17`), `SkipSegmentUIState` (`:94`); `PlayerAction` (`P/model/PlayerAction.kt:6`); `PuberTheme` and the androidTest layout of `IT/component/PlayerAboutPanelTest.kt`; `ComponentActivity` from `androidx.ui.test.manifest` (`app/build.gradle.kts:428`).
**Produces:** the instrumented suite qa-device runs for Tasks 4 and 5.

Shape: `@get:Rule val rule = createAndroidComposeRule<ComponentActivity>()`; `var state by mutableStateOf<PlayerViewState>(...)`; `rule.setContent { PuberTheme { PlayerScreenContent(state, onAction = actions::add, exoPlayer = { null }) } }` with `val actions = mutableListOf<UIAction>()`. A file-local `contentState(...)` builder fills every `PlayerContentState` field (copy the values of `UT/vm/PlayerVMTestFixture.kt:274-323`, the fixture itself is not visible to androidTest) with `controlsVisible = false`, `isPlaying = true`. Skip prompt: `activeSkipSegment = SkipSegmentUIState(label = "Skip intro", targetPositionMs = 60_000, type = SkipSegmentType.INTRO, countdown = 7)`. Next-episode prompt: `nextEpisodeCountdown = 7`, label `rule.activity.getString(R.string.player_next_episode)`. Keys: `rule.onRoot().performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }`, then `rule.waitForIdle()`. Find the action by its label; if the label's node is not the focusable one, match `hasAnyDescendant(hasText(label))` or use the merged tree.

| Test | Assertion |
|---|---|
| `skipPrompt_leftAndRightSeekAndFocusStaysOnAction` | After Left and Right, `actions` contains `SeekBackward` and `SeekForward`; the action is focused. |
| `skipPrompt_heldRightSeeksOnEveryRepeat` | `keyDown(Key.DirectionRight)`, `advanceEventTime(1_000)`, `keyUp`: more than one `SeekForward` recorded. |
| `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` | As the first row with `nextEpisodeCountdown = 7`. |
| `skipPromptHidden_nextRightSeeks` | Skip prompt focused; `state` set to `activeSkipSegment = null`; after idle one Right records `SeekForward`. If it passes on the base, record that root-cause step 4 is refuted and keep it as `guard`. |
| `nextEpisodePromptHidden_nextRightSeeks` | Same with `nextEpisodeCountdown = null`. |
| `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction` | Skip prompt up, then `nextEpisodeCountdown = 7` with the skip still set: the next-episode action is focused. |
| `skipPrompt_appearsWithActionFocused_okSkips` | Action `assertIsFocused()`; `Key.DirectionCenter` down and up records `SkipSegmentClicked`. |
| `skipPrompt_backCancels` | `rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }` records `CancelSkipSegment`. |
| `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` | `controlsVisible = true`, `controlsFocusTarget = FocusTarget.SeekBar`, then a skip prompt: action not focused, Right records `SeekForward`. |
| `controlsHidingWhilePromptIsUp_focusesThePromptAction` | Previous state, then `controlsVisible = false`: the action is focused. |
| `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel` | Hold Left then Right (`advanceEventTime(1_000)` each): no `SkipSegmentClicked` or `CancelSkipSegment` recorded. |
| `skipPrompt_okUpWithoutDownDoesNotSkip` | Action focused; `rule.runOnUiThread { rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)) }`: no `SkipSegmentClicked`. |

- [x] Write the twelve tests.
- [x] Run the fast gate and `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; commit "Add Compose tests for seek keys and focus on the player prompts".
- [ ] Hand the commit to the conductor for the red run by qa-device; record its per-test result under Red run.

**Done when:** `:app:compileDevDebugAndroidTestKotlin` succeeds and the red run on this commit is recorded.

---

### Task 4: Seek from a focused prompt

**Covers:** AC-1, AC-2, AC-7
**Files:**
- Modify: `P/component/PlayerTimedActionOverlay.kt` - new parameter `onSeek: (PlayerAction) -> Unit` on `PlayerTimedActionOverlay` (`:165`, next to `onAction` `:177`); on the button `Row` (`:266-270`) add `Modifier.onPreviewKeyEvent { event -> val seek = seekActionForKeyCode(event.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false; if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) onSeek(seek); true }` (`android.view.KeyEvent`; down and repeats seek, every phase is consumed, other keys pass).
- Modify: `P/component/SkipSegmentOverlay.kt` (`:14`) and `P/component/NextEpisodeOverlay.kt` (`:13`) - add `onSeek: (PlayerAction) -> Unit` and pass it through.
- Modify: `P/component/PlayerOverlayLayers.kt` - pass `onSeek = onAction` to `SkipSegmentOverlay` (`:113`) and `NextEpisodeOverlay` (`:127`).

**Consumes:** `seekActionForKeyCode` (Task 1).
**Produces:** `PlayerTimedActionOverlay(..., onSeek: (PlayerAction) -> Unit, ...)`; the two overlay wrappers take `onSeek`.

- [x] Implement.
- [ ] If the red run of `skipPrompt_okUpWithoutDownDoesNotSkip` failed, also make the `Row` handler drop an OK `ACTION_UP` (`KEYCODE_DPAD_CENTER`, `KEYCODE_ENTER`, `KEYCODE_NUMPAD_ENTER`) whose `ACTION_DOWN` it did not see (a `remember`ed flag set on down, cleared on up); record it under Deviations. Not done: the precondition was matcher-blocked; decide after the re-run.
- [x] Run the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin` and the unit tests of `com.kino.puber.ui.feature.player.*`; commit "Seek from the skip and next-episode prompts instead of moving focus".

**Done when:** the fast gate is green and the touched unit tests pass; AC-1, AC-2 and AC-7 rows of Task 3 are expected green at verification.

---

### Task 5: Make PlayerFocusEffects the only owner of prompt focus

**Covers:** AC-4, AC-5, AC-6, AC-9 (device, M1-M5 by qa-device)
**Files:**
- Modify: `P/component/PlayerFocusRequesters.kt` - add `val skipPrompt = FocusRequester()` and `val nextEpisodePrompt = FocusRequester()` to `PlayerFocusRequesters` (`:7-14`).
- Modify: `P/component/PlayerTimedActionOverlay.kt` - replace `focusPrimaryAction: Boolean` (`:171`) with `focusRequester: FocusRequester`; delete `primaryFocusRequester`, `cancelFocusRequester` and the focus `LaunchedEffect` (`:189-197`); the action button (`:279-282`) takes `focusRequester`; the X (`:271-278`) passes none: `TimedActionButton.focusRequester` (`:309`) becomes `FocusRequester?` and `.focusRequester(focusRequester)` (`:381`) applies only when non-null.
- Modify: `P/component/SkipSegmentOverlay.kt` (drop `focusPrimaryAction = false`, `:28`) and `P/component/NextEpisodeOverlay.kt` (drop `focusPrimaryAction = true`, `:26`) - add `focusRequester: FocusRequester` and pass it through.
- Modify: `P/component/PlayerOverlayLayers.kt` - `PlayerOverlayLayers` (`:105`) gains `focusRequesters: PlayerFocusRequesters`; passes `focusRequesters.skipPrompt` to `SkipSegmentOverlay` (`:113`) and `focusRequesters.nextEpisodePrompt` to `NextEpisodeOverlay` (`:127`).
- Modify: `P/component/PlayerScreenContent.kt`:
  - `PlayerContent` (`:131`) passes `focusRequesters = focusRequesters` to `PlayerOverlayLayers`;
  - in `PlayerFocusEffects` (`:72`) compute the visible prompt kind: `NextEpisode` if `content?.nextEpisodeCountdown != null`, else `Skip` if `content?.activeSkipSegment != null`, else null (file-private enum); the first effect (`:77-82`) also keys on that kind and, with the controls hidden, no panel and no resume dialog, requests `nextEpisodePrompt`, `skipPrompt` or `player` respectively;
  - the request is a private `suspend fun FocusRequester.requestFocusOrRetryNextFrame()`: `runCatching { requestFocus() }`, and on failure `withFrameNanos { }` and one more `runCatching { requestFocus() }` (design risk 1; this effect only).
  - The second effect (`:84-89`) is unchanged.

**Consumes:** Task 4's `PlayerTimedActionOverlay` signature.
**Produces:** `PlayerFocusRequesters.skipPrompt`, `PlayerFocusRequesters.nextEpisodePrompt`; `PlayerOverlayLayers(content, onAction, focusRequesters, bottomInset)`.

- [x] Implement.
- [x] `grep -rn focusPrimaryAction app/src` returns nothing.
- [ ] Run the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin`, then the full gate; commit "Move prompt focus into the player focus effects and focus the skip action".

**Done when:** the full gate is green; qa-device's run of `PlayerPromptFocusAndKeysTest` passes all twelve tests, and manual checks M1-M5 and the visual checks in the spec's Test design pass on the emulator.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- a90ef78f Add a seek-only key map beside the player key map
- e272b9bf Hold the skip and next-episode prompts down while a seek burst runs
- 7b313a1c Add Compose tests for seek keys and focus on the player prompts
- b7854fbb Match the prompt action chip by its label as a substring in the focus tests
- ec2a33a0 Seek from the skip and next-episode prompts instead of moving focus
- d053f7db Move prompt focus into the player focus effects and focus the skip action

**Red run** (P0 `red` tests on the unchanged code):

- red: `aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds` - AssertionFailedError at the +1 000 ms `assertNull(activeSkipSegment)` (prompt already up)
- red: `aSeekBurstIntoTheCreditsLeadInRaisesNoNextEpisodePromptUntilItEnds` - AssertionFailedError at the +1 000 ms `assertNull(nextEpisodeCountdown)`
- red: `everySeekInTheBurstRestartsTheWindow` - AssertionFailedError at the 3 000 ms `assertNull(activeSkipSegment)`
- green on base: `aSkipLandingOnTheNextSegmentOffersItWithoutWaiting` - guard, as the plan expects (a skip starts no burst)
- Task 3 instrumented rows (device run at `82909d99`, 12 ran, 9 failed, 3 passed):
  - red: `skipPrompt_leftAndRightSeekAndFocusStaysOnAction` - `:91` `assertTrue(SeekBackward in actions)`
  - red: `skipPrompt_heldRightSeeksOnEveryRepeat` - `:102` `assertTrue(count{SeekForward} > 1)`
  - red: `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` - `:112` `assertTrue(SeekBackward in actions)`
  - Red run of `PlayerPromptFocusAndKeysTest` at `b7854fbb` (production prompt UI as on base, 5 pass / 6 fail; `.flow/runs/seek-blocked-by-skip-prompt/red-run-b7854fbb.md`):
    - red: `skipPrompt_appearsWithActionFocused_okSkips` - `:150` `action(SKIP_LABEL).assertIsFocused()`
    - red: `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` - `:180` `assertTrue(SeekForward in actions)`
    - red: `controlsHidingWhilePromptIsUp_focusesThePromptAction` - `:194` `assertIsFocused()`
    - red: `skipPrompt_okUpWithoutDownDoesNotSkip` - `:211` `assertIsFocused()` (the precondition focus, not the OK-up check itself)
    - red: `skipPrompt_leftAndRightSeekAndFocusStaysOnAction` `:92`, `skipPrompt_heldRightSeeksOnEveryRepeat` `:103`, `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` `:113` - seek blocked
    - green on base: `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction` - guard, it has no test-design row
  - green on base: `skipPromptHidden_nextRightSeeks`, `nextEpisodePromptHidden_nextRightSeeks` - root-cause step 4 refuted; kept as guards, they still check AC-4
  - green on base: `skipPrompt_backCancels`, `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel` - guards

**Deviations from the plan:**

- The position tick is `PlayerCountdowns.TICK_MS` = 1 000 ms, not 500 ms as the plan says; the Task 2 tests advance in those steps (the prompt still appears at the first tick after the 1 500 ms window).
- The segment stub in `PlayerVMSeekBurstTest` ends a segment at `position < endMs`, not `<=`: with `<=` the skip's landing point at 40 000 still matched the intro, the prompt re-armed and the test ran out of heap.

**Files touched outside the plan:**

**For the reviewers:**

- Matcher fix `b7854fbb`: the chip prints `label + U+00A0` and draws the digits, so exact `hasText(label)` never matched; the matcher is now `hasClickAction() and hasText(label, substring = true)` on the merged node. No production `testTag` added. Not run on a device.
- Task 4 left the `ACTION_UP` guard out: `skipPrompt_okUpWithoutDownDoesNotSkip` is red only on its precondition focus assertion, so the OK-up check itself has no red run.
- Task 5 retry also repeats on `requestFocus() == false`, not only on an exception (readiness advisory 1).

**Fix round 1** (triage-1 rows #1, #3, #6, #7, #8; #5 is the red-run block above):

- #1: the cancel X (`iconOnly`) gets `focusProperties { canFocus = false }`; pointer click stays. No androidTest asserted focus on the X.
- #3: `loadSkipSegments` skips the late-credits `startNextEpisodeCountdown()` while `seekBurstJob` is active; the tick offers the prompt after the burst. New test `creditsLoadedMidBurstRaiseNoNextEpisodePromptUntilTheBurstEnds`.
- #6: `everySeekInTheBurstRestartsTheWindow` asserts null after every `wait(1_000)`.
- #7: new `aSeekBackwardBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds`.
- #8: AC-4 refocus-on-hide (`PlayerScreenContent` branch and its retry) is device-only (M2); `skipPromptHidden_nextRightSeeks`, `nextEpisodePromptHidden_nextRightSeeks` and the two hide Compose tests pass on base and are guards.
- Mutation checks (each applied temporarily, reverted):
  - red: `creditsLoadedMidBurst...` with the `seekBurstJob` check removed from `loadSkipSegments` - `PlayerVMSeekBurstTest.kt:117` assertNull(nextEpisodeCountdown)
  - red: `everySeekInTheBurstRestartsTheWindow` with `startSeekBurst` not restarting an active window - `:141` assertNull after the second wait
  - red: `aSeekBackwardBurst...` with `startSeekBurst()` removed from `seekBackward` - `:98` assertNull(activeSkipSegment)

**Blocked:**

## Readiness

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md`, plan `docs/superpowers/plans/2026-11-10-seek-blocked-by-skip-prompt.md` (worktree HEAD `1cd4fba6`, base `6b087697`, no `app/` change since base).

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 3, 4, 5 | `skipPrompt_leftAndRightSeekAndFocusStaysOnAction`, `skipPrompt_heldRightSeeksOnEveryRepeat` |
| AC-2 | 3, 4 | `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` |
| AC-3 | 2 | `aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds`, `aSeekBurstIntoTheCreditsLeadInRaisesNoNextEpisodePromptUntilItEnds` |
| AC-4 | 3, 5 | `skipPromptHidden_nextRightSeeks`, `nextEpisodePromptHidden_nextRightSeeks`, M2 |
| AC-5 | 3, 5 | `skipPrompt_appearsWithActionFocused_okSkips` |
| AC-6 | 3, 5 | `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` |
| AC-7 | 3, 4 | `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel` |
| AC-8 | 1, 2 | `PlayerVMPromptDismissalTest.seekingAfterCancellingPutsTheNextEpisodeQuestionBack` (`:81`), `PlayerVideoSurfaceTest` (`:16`, `:25`) |
| AC-9 | 5 | manual M1, M5 |

## Citations sampled (all resolve at base)

| Citation | Found |
|---|---|
| `component/PlayerVideoSurface.kt:213` | `playerActionForKeyCode`; maps Left/Right, FF/RW, skip fwd/back to seek actions |
| `component/PlayerTimedActionOverlay.kt:171`, `:181`, `:191-197`, `:266`, `:274`/`:282`, `:309`, `:381` | `focusPrimaryAction`, `BackHandler`, focus `LaunchedEffect(visible)`, button `Row`, the two requesters, `TimedActionButton.focusRequester`, `.focusRequester(focusRequester)` |
| `component/SkipSegmentOverlay.kt:28`, `component/NextEpisodeOverlay.kt:26` | `focusPrimaryAction = false` / `= true`; both are the only callers of `PlayerTimedActionOverlay` |
| `component/PlayerScreenContent.kt:72`, `:77-82`, `:84-89`, `:131` | `PlayerFocusEffects`, both effects, the `PlayerOverlayLayers` call (its only caller) |
| `component/PlayerOverlayLayers.kt:105`, `:112-113`, `:127` | signature, skip overlay gated on `nextEpisodeCountdown == null`, `NextEpisodeOverlay` |
| `component/PlayerFocusRequesters.kt:7-14` | class with six `val FocusRequester` fields |
| `vm/SeekHandler.kt:7`, `:25` | `private const val DEFAULT_SEEK_RESET_TIMEOUT_MS = 1_500L`; `System.currentTimeMillis()` |
| `vm/PlayerVM.kt:142-151`, `:700-703`, `:706`, `:714`, `:1510-1515`, `:1670-1677`, `:1403`, `:1412`, `:1781` | job fields, `launch { delay }` shape, `seekForward`/`seekBackward`, the gated tick block, `onSeekPerformed`, resume / start-over / skip `seekTo` |
| `vm/PlayerVM.kt:1543-1568` | early next-episode fallback: offset 22 000 ms (`PlayerCountdowns.kt:80`), so 2 380 000 of 2 400 000 is inside |
| `UT/vm/PlayerVMTestFixture.kt:42`, `:76`, `:190`, `:192`, `:274-323`; `UT/vm/PlayerVMSkipCountdownFitTest.kt:29-45`, `:53` | fixture class, `callbackSlot`, `startedVM`, `contentState`, `testContentState` (`hasNextEpisode = true`), `playingInto`, `playFor` |
| `model/PlayerViewState.kt:17`, `:94`; `model/PlayerAction.kt:6`; `app/build.gradle.kts:428`; `Makefile` `itest` | `PlayerContentState`, `SkipSegmentUIState`, `PlayerAction`, `ui.test.manifest`, target present |

## Ordering and build

Tasks 1 -> 2 -> 3 -> 4 -> 5 follow their Consumes/Produces. Each task commits with the fast gate green; red tests exist only in Task 3 (instrumented, outside `check.full`) and Task 2 (implemented before its commit). All callers of the changed signatures are inside the plan's file lists.

## P0 rows

Every automated P0 row is marked `red` or `guard`, names its test in backticks and the mutation it catches. AC-9 is a manual P0 (M1, M5), manual by nature.

## Scope and questions

Plan constraints exclude `PlayerVM.kt:1676`, visual constants, Up/Down/Play-Pause forwarding, `focus-request-helper` and `docs/design/`. Blocking Q1 and Q3 are answered; Q2, Q4 non-blocking and answered.

## Advisory (no fix required)

1. Task 5 `requestFocusOrRetryNextFrame` retries only on an exception. With compose BOM `2026.08.00`, `FocusRequester.requestFocus()` returns `Boolean`, so a silent `false` (design risk 1) would not retry; the implementer should retry on `false` too.
2. Task 3's red run needs qa-device mid-implementation (device lease); the conductor must schedule it before Task 4 starts.
