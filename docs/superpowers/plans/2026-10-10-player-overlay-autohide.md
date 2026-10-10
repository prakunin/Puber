# Player overlays: auto-hide transient, keep opened — plan

**Spec:** `docs/superpowers/specs/2026-10-10-player-overlay-autohide-design.md`
(`## Design` D1-D13, `## Test design`)
**Goal:** The controls and the Episodes panel hide after 6 s without a key press. The context menu holds the panel, and a buffering stall cannot strand it. Settings, About, the resume dialog and the end-of-media controls stay until the viewer closes them.

## Global constraints

- Worktree: `/home/user1/orca/workspaces/Puber/player-overlay-autohide`. Run every command as
  `cd /home/user1/orca/workspaces/Puber/player-overlay-autohide && …`.
- Builds: `AGENT_GRADLE_SERIAL=1 ./tools/agentw <gradle tasks>`, never `./gradlew` (the hook
  refuses it). The test command used below:
  `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<FQCN>'`.
- One task = green build + green tests of the touched classes + one commit. Commit message: the
  sentence given in the task, imperative English, no prefix, **no `Co-Authored-By` line**.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited
  below, and nothing else.
- Merge hygiene (spec R5, R7). An uncommitted worktree, `subtitle-player-fixes`, edits the same classes:
  - Do not edit `ControlsStateMachine.kt` lines 33-40. That worktree inserts `initialize` after line 36.
  - Do not edit `PlayerVM.applyControlsState`, `processEffects`, `openPanel` or `preparePlayback`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:748] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:759] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:715] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:296]
  - Do not edit `EpisodesPanel.kt` or `PlayerScreenContent.kt` (`player-overlays-port` owns them).
  - Put new functions at the end of `ControlsStateMachine`.
  - Put new `ControlsStateMachineTest` tests at the end of the class, never near `setUp`.
- Coroutine tests: VM tests that advance time use
  `runTest(mainDispatcher.dispatcher.scheduler) { … ; vm.testCancelScope() }` and
  `advanceTimeBy`, never `advanceUntilIdle()`. The position loop never ends, so idling it runs out of memory. Copy the pattern from `PlayerVMPromptDismissalTest`. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPromptDismissalTest.kt:18] [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPromptDismissalTest.kt:34] [Source: app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:131] [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMEpisodeFlowTest.kt:29]
- Fixture facts the tests rely on (`PlayerVMTestFixture`) [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:41]:
  - `startedVM()` [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:155]
  - `contentState(vm)` [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:157]
  - `callbackSlot` [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:75] and `router` (relaxed mock) [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:82]
  - The factory returns `testContentState` with `controlsVisible = true`, `hasNextEpisode = true`, `isMovie = false`, `resumeDialog = null`. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:240]
  - The debug overlay preference is off. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:110]
  - Preparation schedules the controls hide when there is no resume dialog. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:357]
- Callback signature, used to fake buffering: `onPlaybackStateChanged(isPlaying, isBuffering, position, duration, buffered)`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:59] The buffering spinner, and the hide cancellation with it, waits for the 800 ms debounce. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:191] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1840]
- Time boundaries in tests: check at +5_900 ms (still up), then +200 ms (gone).

---

### Task 1: Close the Episodes panel and the controls after 6 idle seconds

**Covers:** AC-1, AC-2, AC-3, AC-4 (VM part), AC-6, AC-7 (no buffering), AC-8, AC-9 (VM part),
AC-12, AC-13, design D1, D2 (playing case), D3, D6, D7, D8, D9
**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt`
  - `State`: add `val episodesPanelHeld: Boolean = false` after `playbackEnded`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:17]
  - `openPanel`: copy `episodesPanelHeld = false`, and return `listOf(Effect.ScheduleHide)` for `ActivePanel.Episodes` and `listOf(Effect.CancelHide)` otherwise. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:47]
  - `closePanel`: first line `if (state.activePanel == ActivePanel.None) return emptyList()`. Add `episodesPanelHeld = false` to the copy. The effects stay the same. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:59]
  - Delete `applyControlsVisibility(visible: Boolean)`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:99]
  - Append at the end of the class:
    ```kotlin
    /**
     * The 6 s without a key ran out. The bar hides; the Episodes row closes to the clean video,
     * or to the end-of-media controls once the media has run out; anything opened on purpose
     * (Settings, About, a held Episodes row, the ended controls) stays.
     */
    fun onHideTimeout(): List<Effect> {
        val panel = state.activePanel
        return when {
            panel == ActivePanel.Episodes && !state.episodesPanelHeld -> {
                state = if (state.playbackEnded) {
                    state.copy(
                        activePanel = ActivePanel.None,
                        controlsVisible = true,
                        focusTarget = lastPanelOpener,
                        episodesPanelHeld = false,
                    )
                } else {
                    state.copy(
                        activePanel = ActivePanel.None,
                        controlsVisible = false,
                        focusTarget = null,
                        episodesPanelHeld = false,
                    )
                }
                listOf(Effect.PanelClosed)
            }
            panel != ActivePanel.None -> emptyList()
            state.playbackEnded -> emptyList()
            else -> {
                state = state.copy(controlsVisible = false, focusTarget = null)
                emptyList()
            }
        }
    }
    ```
    The `playbackEnded` branch inside Episodes is only reachable after Task 2. It is written here so the function is complete in one place.
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt`
  - `scheduleControlsHide()`: replace the job body. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:706]
    ```kotlin
    controlsHideJob = launch {
        delay(CONTROLS_HIDE_DELAY_MS)
        // Leaving already: a panel closing now would re-run the next-episode check on the way out.
        if (closing) return@launch
        val effects = controlsStateMachine.onHideTimeout()
        // A timeout never takes the viewer out of the player; only Back does.
        if (ControlsStateMachine.Effect.PanelClosed in effects) exitWhenPanelCloses = false
        applyControlsState()
        processEffects(effects)
    }
    ```
    The fields used here already exist: `closing` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:170] and `exitWhenPanelCloses` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:159].
  - `CONTROLS_HIDE_DELAY_MS`: change `4_500L` to `6_000L`, with the KDoc `/** Idle time before the controls bar or the Episodes row goes away. */`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1834]
- Modify test: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/ControlsStateMachineTest.kt`
  - `openPanel_episodes_keepsPlaybackRunning`: keep the name, expect `listOf(ScheduleHide)`, and add `assertFalse(machine.state.controlsVisible)`. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/ControlsStateMachineTest.kt:72]
  - Rewrite `applyControlsVisibility_false_withOpenPanel_doesNotHide` as `onHideTimeout_withSettingsOpen_doesNotHide` (state untouched, `[]`). Rewrite `applyControlsVisibility_false_noPanel_hides` as `onHideTimeout_noPanel_hidesControls` (`controlsVisible == false`, `focusTarget == null`, `[]`). [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/ControlsStateMachineTest.kt:190]
  - Append: `onHideTimeout_episodesOpen_closesToCleanVideoAndReportsPanelClosed` (effects exactly `[PanelClosed]`), `onHideTimeout_withAboutOpen_returnsNoEffects`, `onHideTimeout_afterPlaybackEnded_keepsControlsUp` (after `showControlsForEndedPlayback()`: state untouched, `[]`), and `closePanel_withNoPanel_returnsNoEffectsAndLeavesStateAlone`.
- Create test: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMOverlayAutoHideTest.kt`. It extends `PlayerVMTestFixture`, has a companion `@JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()`, and uses JUnit 5.

**Consumes:** `PlayerAction.OpenEpisodesPanel`, `OpenSettingsPanel`, `OpenAboutPanel`, `ClosePanel`, `ResetControlsTimer`, `OnBackPressed`, `ResumeFromPosition`, `CancelNextEpisodeCountdown`, `TogglePlayPause`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt:9] [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt:24] [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt:27]
**Produces:**
- `ControlsStateMachine.onHideTimeout(): List<Effect>`
- `State.episodesPanelHeld`, as a field only: nothing sets it yet. It stays `false` until Task 3, but every reset point (`openPanel`, `closePanel`, `onHideTimeout`) already clears it.

Steps:
- [ ] **Failing first (bug route).** Before touching production code, write these two in `PlayerVMOverlayAutoHideTest`, run them, and record that both fail:
  - `openEpisodesPanel_closesItself_after6s`: `startedVM()`; `OpenEpisodesPanel`; `advanceTimeBy(5_900)`, panel is `Episodes`; `advanceTimeBy(200)`, `activePanel == None`, `controlsVisible == false`, `controlsFocusTarget == null`.
  - `controlsBar_staysAt5900ms_hidesAfter6000ms`: `startedVM()`; `advanceTimeBy(5_900)`, `controlsVisible`; `advanceTimeBy(200)`, `!controlsVisible`.
- [ ] Make the production changes above, then the state-machine test edits.
- [ ] Add these VM tests to the same file (all `runTest` + `testCancelScope`):
  - `resetControlsTimer_at4s_restartsTheSixSeconds` (AC-2): at 4_000 send `ResetControlsTimer`. Still visible at 9_900, hidden at 10_100.
  - `resetControlsTimer_withEpisodesOpen_restartsTheSixSeconds` (AC-4): open Episodes, then `ResetControlsTimer` at 5_000. Open at 10_900, closed at 11_100.
  - `settingsPanel_staysOpenFor60s` and `aboutPanel_staysOpenFor60s` (AC-7): open the panel, `advanceTimeBy(60_000)`, the panel is still active.
  - `closingSettingsPanel_bringsBackControlsThatHideAfter6s` (AC-8): open Settings, `ClosePanel`, the bar is visible. Still visible at 5_900, hidden at 6_100.
  - `staleClosePanelAfterEpisodesTimeout_doesNotReshowControls` (AC-9): after the timeout, send `ClosePanel`. `controlsVisible == false`, panel `None`.
  - `staleResetControlsTimerAfterEpisodesTimeout_doesNotReopenAnything` (AC-9, P1): after the timeout, send `ResetControlsTimer` and `advanceTimeBy(6_100)`. Panel `None`, `!controlsVisible`.
  - `backAfterEpisodesTimeout_exitsOnTheFirstPress` (AC-9): after the timeout, send `OnBackPressed` once. Then `verify(exactly = 1) { router.back(RESULT_CONTENT_CHANGED, any()) }` (import `com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED`, the way `PlayerVMPlaybackFlowTest` does). [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt:5]
  - `resumeDialog_noHideTimerRunsWhileItIsUp` (AC-10):
    - Set up the factory: `coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(resumeDialog = ResumeDialogState(savedPosition = 60_000L, formattedTime = "1:00"))`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIModels.kt:89]
    - `advanceTimeBy(60_000)`: `resumeDialog != null` and `controlsVisible` (the value the factory published).
    - Send `ResumeFromPosition`. Still visible at 5_900, hidden at 6_100.
  - `controlsHideAfter6s_whilePaused` (AC-12): call `onPlaybackStateChanged(false, false, 0L, 2_400_000L, 0L)` and stub `every { playbackController.isPlaying } returns false`. Then `ShowControls(FocusTarget.Buttons)`, `advanceTimeBy(6_100)`, `!controlsVisible`.
  - `debugReadings_dropWhenEpisodesPanelTimesOut` (AC-13, P1):
    - Stub `getDebugInfo()` to return a `PlaybackControl.DebugInfo`. Build it like `testDebugInfo` in `PlayerControlsInteractionTest`. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerControlsInteractionTest.kt:211]
    - `OpenSettingsPanel` (fills the readings), then `OpenEpisodesPanel`, then `advanceTimeBy(6_100)`: `debugInfo == null`.
    - This works because the position loop does not rewrite `debugInfo` while Episodes is open and the bar is hidden. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1384]
  - `controlsTimerDuringExit_changesNothing` (D9, P1):
    - Hold the final save open with `coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } coAnswers { releaseSave.await() }`, the way the existing pending-save tests do. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt:77]
    - `startedVM()`, `OnBackPressed` (the player starts closing), `advanceTimeBy(6_100)`: `controlsVisible` is still `true` (the job returned early).
    - `releaseSave.complete(Unit)`.
- [ ] Re-check the existing player tests:
  `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.*'`.
  None depends on 4.5 s (checked: no `4_500` or `CONTROLS_HIDE_DELAY_MS` in `app/src/test`). If one fails because a test idles past 6 s with Episodes open, fix the test's timing, not the code, and note it in the Implementation record.
- [ ] Commit: `Close the Episodes panel and the controls after six idle seconds`

**Done when:** `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.*'` is green. The two failing-first tests were seen failing before the change.

---

### Task 2: Keep the Episodes timer through buffering and land on the ended controls

**Covers:** AC-3/AC-4 under stalls (test-designer R8), AC-7 across buffering, AC-11, design D2 (ended case), D12, D13, open question 9
**Files:**
- Modify: `ControlsStateMachine.kt`. Append at the end:
  ```kotlin
  /** Only the Episodes row runs on the idle timer among the panels, and not while it is held. */
  fun isEpisodesPanelAwaitingTimeout(): Boolean =
      state.activePanel == ActivePanel.Episodes && !state.episodesPanelHeld

  /**
   * The media ran out while a panel had the screen. Nothing changes on screen now; the panel
   * closes onto the end-of-media controls, and those stay until Back.
   */
  fun markPlaybackEnded() {
      state = state.copy(playbackEnded = true)
  }
  ```
- Modify: `PlayerVM.kt`
  - Buffering-end branch: change only the condition `if (controlsVisible)` to `if (controlsVisible || controlsStateMachine.isEpisodesPanelAwaitingTimeout())`. Keep the content-based `controlsVisible` read (spec D12). [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:204]
  - `onPlaybackEnded()`: after `val content = state.content`, add `if (content.activePanel != ActivePanel.None) controlsStateMachine.markPlaybackEnded()`. Leave the rest alone. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1235]
  - `onPlaybackResumed()` already clears the flag through the machine. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:93]
- Tests:
  - `ControlsStateMachineTest` (append): `onHideTimeout_episodesOpenAfterPlaybackEnded_landsOnEndedControls` (`openPanel(Episodes)`, `markPlaybackEnded()`, `onHideTimeout()` gives `[PanelClosed]`, `controlsVisible`, `focusTarget == EpisodesButton`, `playbackEnded`), then `onHideTimeout()` again gives `[]` with the controls still up. Also `isEpisodesPanelAwaitingTimeout_onlyForAnOpenEpisodesPanel` (false with None, Settings or About; true with Episodes).
  - `PlayerVMOverlayAutoHideTest`: add a helper
    `private fun TestScope.bufferingBlip(playingAfter: Boolean = true)`, with these steps:
    - `onPlaybackStateChanged(false, true, 0L, 2_400_000L, 0L)`
    - `advanceTimeBy(1_000)` (past the debounce)
    - `onPlaybackStateChanged(playingAfter, false, 0L, 2_400_000L, 0L)`

**Consumes:** Task 1 (`onHideTimeout`, `episodesPanelHeld`).
**Produces:** `isEpisodesPanelAwaitingTimeout()`, `markPlaybackEnded()`.

Steps:
- [ ] **Failing first.** Write `episodesPanel_stillClosesAfterBufferingBlip` (R8): open Episodes; `advanceTimeBy(2_000)`; `bufferingBlip()`; `advanceTimeBy(5_900)`, still `Episodes`; `advanceTimeBy(200)`, `None`. Run it: it fails after Task 1. Record that.
- [ ] Write `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays`. It replaces the test designer's `lastEpisode_episodesTimeout_exitsThePlayer`, per spec R4 and question 9.
  - Factory returns `testContentState.copy(hasNextEpisode = false)`.
  - `startedVM()`; `OpenEpisodesPanel`; `callbackSlot.captured.onPlaybackEnded()`; `verify(exactly = 0) { router.back(any(), any()) }`.
  - `advanceTimeBy(6_100)`: `activePanel == None`, `controlsVisible`, `controlsFocusTarget == FocusTarget.EpisodesButton`, no `router.back`.
  - `advanceTimeBy(60_000)`: still `controlsVisible`, no `router.back`.
  - Send `OnBackPressed` once: `verify(exactly = 1) { router.back(RESULT_CONTENT_CHANGED, any()) }`.
  - Run it: it fails, because the timeout lands on a clean video.
- [ ] Make the production changes above.
- [ ] Add these VM tests:
  - `settingsPanel_staysOpenAcrossBufferingStartAndEnd` (AC-7): open Settings; `bufferingBlip()`; `advanceTimeBy(60_000)`; still `Settings`.
  - `endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime` (AC-11): use the default factory (a non-last episode).
    - `onPlaybackEnded()` starts the countdown; `CancelNextEpisodeCountdown`; `onPlaybackEnded()` again brings up the ended controls. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1243]
    - `bufferingBlip(playingAfter = false)`; `ResetControlsTimer`; `advanceTimeBy(60_000)`: `controlsVisible`.
    - `OnBackPressed`: `router.back` called once.
- [ ] Run the player test package; commit: `Keep the Episodes timer through buffering and land on the ended controls`

**Done when:** `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.*'` is green, including the existing end-of-media tests. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt:496] [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMEpisodeFlowTest.kt:70]

---

### Task 3: Hold the Episodes panel while its context menu is open and restart it on keys

**Covers:** AC-4 (key hook), AC-5, test-designer R9, design D4, D5
**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt`. Under `// Panels`, add:
  ```kotlin
  /** The episode context menu opened over the Episodes row (true) or went away (false). */
  data class SetEpisodesPanelHeld(val held: Boolean) : PlayerAction
  ```
  [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt:26]
- Modify: `ControlsStateMachine.kt`. Append:
  ```kotlin
  /** A dialog opened from the Episodes row keeps the row up; releasing it restarts the 6 s. */
  fun holdEpisodesPanel(held: Boolean): List<Effect> {
      if (state.activePanel != ActivePanel.Episodes) return emptyList()
      state = state.copy(episodesPanelHeld = held)
      return listOf(if (held) Effect.CancelHide else Effect.ScheduleHide)
  }
  ```
- Modify: `PlayerVM.kt`
  - `onAction`: add `is PlayerAction.SetEpisodesPanelHeld -> holdEpisodesPanel(action.held)` next to `ClosePanel`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:562]
  - Add a private function after `closePanel()` that copies its shape. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:727]
    ```kotlin
    private fun holdEpisodesPanel(held: Boolean) {
        val effects = controlsStateMachine.holdEpisodesPanel(held)
        applyControlsState()
        processEffects(effects)
    }
    ```
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt` [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:35]
  - Before `PlayerEpisodesPanel(...)`, add:
    ```kotlin
    // Any key in the row is the viewer still choosing: restart the idle timer. Never consumes.
    val episodesKeyActivity = remember(onAction) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                onAction(PlayerAction.ResetControlsTimer)
            }
            false
        }
    }
    ```
    Pass it as `modifier = episodesKeyActivity`. This is `PlayerEpisodesPanel`'s existing parameter, which is applied to the row's outer `BoxWithConstraints`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:137] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:148] The pattern is `ControlsScrim`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerControlsOverlay.kt:146] Imports: `android.view.KeyEvent`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.input.key.onPreviewKeyEvent`.
  - `onEpisodeContextMenu = { episodeContextMenuItem = it; onAction(PlayerAction.SetEpisodesPanelHeld(true)) }`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:40]
  - Dialog `onDismiss = { episodeContextMenuItem = null; onAction(PlayerAction.SetEpisodesPanelHeld(false)) }`. Every dialog action dismisses first. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:47] [Source: app/src/main/java/com/kino/puber/core/ui/uikit/component/TvContextMenu.kt:117]
  - Do not edit `EpisodesPanel.kt`.
- Tests:
  - `ControlsStateMachineTest` (append):
    - `holdEpisodesPanel_true_cancelsHideAndTimeoutKeepsPanel` (`[CancelHide]`, then `onHideTimeout()` gives `[]` and the panel is still `Episodes`)
    - `holdEpisodesPanel_false_schedulesHide` (`[ScheduleHide]`, flag false)
    - `holdEpisodesPanel_withOtherPanelOrNone_isNoOp` (`[]`, state equal before and after)
    - `openPanel_and_closePanel_clearTheHeldFlag`
    - `isEpisodesPanelAwaitingTimeout_isFalseWhileHeld`
  - `PlayerVMOverlayAutoHideTest`:
    - `episodesPanelHeldByContextMenu_staysFor30s_thenClosesSixSecondsAfterRelease`: open Episodes; `SetEpisodesPanelHeld(true)`; `advanceTimeBy(30_000)`, still `Episodes`; `SetEpisodesPanelHeld(false)`; still open at 5_900, `None` at 6_100.
    - `heldEpisodesPanel_staysThroughBufferingBlip` (R9): open, hold, `bufferingBlip()`, `advanceTimeBy(30_000)`, still `Episodes`.

**Consumes:** Task 1 and Task 2 (`onHideTimeout`, `isEpisodesPanelAwaitingTimeout`, `bufferingBlip`).
**Produces:** `PlayerAction.SetEpisodesPanelHeld(held: Boolean)`, `ControlsStateMachine.holdEpisodesPanel(held: Boolean): List<Effect>`.

Steps:
- [ ] Write the state machine and VM tests above (they do not compile until the action exists, which counts as the failing step). Then make the production changes.
- [ ] `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin` and run the player test package.
- [ ] Commit: `Hold the Episodes panel while its context menu is open and restart it on keys`

**Done when:** the player test package is green and `:app:compileDevDebugKotlin` passes. The Compose hook has no JVM test, so device checks D3 and D4 in the spec cover it (owned by qa).

---

### Task 4: Pin the keep-screen-on rule and correct the trailer delay comment

**Covers:** AC-15, design D10, open question 8
**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt`
  - Add at file level:
    ```kotlin
    /** Only actual playback holds the screen on; paused, ended or stalled lets the screensaver in. */
    internal fun PlayerContentState.shouldKeepScreenOn(): Boolean = isPlaying
    ```
  - Replace `content.isPlaying` with `content.shouldKeepScreenOn()` at the three call sites. Do not change anything else. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:45] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:54] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:65]
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt`. Replace the comment on `CONTROLS_HIDE_DELAY_MS` with `/** The trailer's own idle delay; the player waits longer (6 s) because it carries more controls. */`. The value stays `4_500L`. [Source: app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt:47]
- Tests:
  - `app/src/test/kotlin/com/kino/puber/ui/feature/player/component/PlayerVideoSurfaceTest.kt` (append): `shouldKeepScreenOn_isFalse_whenPausedOrEnded_andTrueWhenPlaying`. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/component/PlayerVideoSurfaceTest.kt:9]
    - Build `PlayerContentState` values with `copy` from a minimal instance. `testContentState` lives in the VM fixture and is not visible here, so construct one with the same named arguments as the fixture. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:240] [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerViewState.kt:17]
    - Assert false for `isPlaying = false, isBuffering = true` (R10) and for `isPlaying = false, isBuffering = false`, and true for `isPlaying = true`.
  - `PlayerVMOverlayAutoHideTest`: `pausing_setsIsPlayingFalse_andResumingSetsItTrue` (P1).
    - `TogglePlayPause` with `playbackController.isPlaying` stubbed `true`: `isPlaying == false`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:592]
    - `onPlaybackStateChanged(true, false, …)`: `isPlaying == true`.
    - `onPlaybackStateChanged(false, false, …)` followed by `onPlaybackEnded()`: `isPlaying == false`.

**Consumes:** nothing new.
**Produces:** `PlayerContentState.shouldKeepScreenOn(): Boolean`.

Steps:
- [ ] Write the VS test against the extension (it fails to compile until the extension exists). Then make the change, the comment edit and the VM test.
- [ ] Full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest :app:detektAll :app:assembleDevDebug`.
- [ ] Commit: `Pin the keep-screen-on rule and correct the trailer delay comment`

**Done when:** the full gate is green. `git log --oneline master..HEAD` shows the four task commits (on top of the dossier commits), with no `Co-Authored-By` in any message (`git log master..HEAD --format=%B | grep -c Co-Authored-By` prints `0`).

---

## Not in this plan

- Device checks D1-D6 (AC-14, AC-16, focus R1): qa runs them with the lease. If D2 shows focus stuck after the auto-close, the fix lives in `EpisodesPanel.kt` and waits for `player-overlays-port` to merge (spec R1).
- `afterInitialize_episodesPanelHeldIsReset` (P2): add it only after `subtitle-player-fixes` merges and `initialize` exists.
- `downFromButtons_viaOpenEpisodesPanel_isTheSamePathAsTheButton`: not written. The Test design marks it as a note only.

## Implementation record

<!-- implementer -->

**Commits:**

**Deviations from the plan:**

**Files touched outside the plan:**

**For the reviewers:**

**Blocked:**

## Readiness

<!-- final-judge, readiness mode: READY | NOT READY and why. -->
