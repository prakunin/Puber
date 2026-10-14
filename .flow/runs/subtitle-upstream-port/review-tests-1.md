LENS: tests  ROUND: 1  BASE: be8395a4
Suite: I ran `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree at HEAD 4ae32d90 and it passed. Gradle reused cached results from an earlier successful run at this HEAD. The test XML shows 1459 tests with 0 failures, 0 errors and 0 skipped, and includes `SubtitleLabelsTest`, `SubtitleLinkTest` and `mapSubtitleTracks_namesLanguagesInTheProvidersLocale`. Detekt and lint are green.

Checked and found sound (not findings):
- **T-M2 `controlsTimerDuringExit_changesNothing`:** it snapshots the content after a real exit while the save is held, and a hide timer armed at 0 fires during that exit. Mutation (a), removing `if (closing) return@launch`, turns it red.
- **T-M1 first half:** it fails if `publishPreparedContent` is reverted, because the fixture content has `controlsVisible = true`.
- **T-D3a, T-D3b and the rewritten ambiguous-language test:** each fails against the base `restoreTrackPreferences`, which calls nothing when no saved choice resolves. T-D3b uses `verifyOrder` on the stale row 2 followed by row 0.
- **The ResourceProvider.locale test:** together with the English case in T-L1 (`variant("Russian", …)` under the fake's default `Locale.ENGLISH`), a mapper using `Locale.getDefault()` goes red on both an English host and a Russian host. `AndroidResourceProvider.locale` itself has no unit test. It is covered by the round-2 device evidence (emulator, in-app Russian PASS).
- **T-P2:** that `controlsHideJob?.cancel()` cannot be observed is already documented in the plan, so I am not reporting it.
- **Untested ACs:** AC-2, AC-4, AC-6 (`SELECTION_FLAG_FORCED`) and the AC-1 DEFAULT/FORCED manifest case have no automated test. Owner decisions Q2/Q3 accepted that, and I am not reporting it again.

### F1 [minor] T-M1's second half is driven by its own `ShowControls`, so it no longer tests what happens after resume, and its name claims something it cannot check
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMOverlayAutoHideTest.kt:171-196
- Problem: after `ResumeFromPosition` the test immediately sends `ShowControls(FocusTarget.Buttons)`. That arms its own hide timer, so the 5.9 s / 6.1 s checks only exercise the generic ShowControls timer, which other tests already cover. Nothing is asserted about the state right after resume: neither that `resumeDialog` became null nor that the controls stayed hidden and unfocused. The name `resumeDialog_noHideTimerRunsWhileItIsUp` describes the very property the plan's "T-M1 known limit" admits can no longer be observed.
- Failure scenario: `resumeFromSavedPosition` stops clearing `resumeDialog`, or starts popping the controls up with focus. T-M1 stays green, because the explicit `ShowControls` hides both effects.
- Fix: right after `ResumeFromPosition`, before `ShowControls`, add `assertNull(contentState(vm).resumeDialog)`, `assertFalse(contentState(vm).controlsVisible)` and `assertNull(contentState(vm).controlsFocusTarget)`. Rename the test to something like `resumeDialog_keepsControlsHiddenAndUnfocusedWhileItIsUp`.

### F2 [nit] `initialize_dropsWhatThePreviousMediaLeft` ends with an assertion that cannot fail, guarding a reset nothing can observe
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/ControlsStateMachineTest.kt:41-56 (production: ControlsStateMachine.kt:50)
- Problem: the closing `openPanel(About)` / `closePanel()` / `assertEquals(AboutButton, …)` is decided by `openPanel` setting `lastPanelOpener = AboutButton`. It does not depend on `initialize`. Every reader of `lastPanelOpener` (`closePanel` at :89 and `onHideTimeout` at :139) needs an active panel, and a panel can only become active through `openPanel`, which overwrites the value first. So the `lastPanelOpener = FocusTarget.Buttons` line in `initialize` is dead, and the test passes with it deleted.
- Failure scenario: delete `ControlsStateMachine.kt:50` and the test stays green, as does every other test.
- Fix: drop the reset line and the three trailing lines of the test. Alternatively, keep the line and drop only the assertion, with a comment that it is defensive.

### F3 [nit] The plan's implementation record still describes the old T-L1 setup
- Where: docs/superpowers/plans/2026-10-14-subtitle-upstream-port.md:441-444, :591, :606, :661-664
- Problem: the record says T-L1 uses a `mockk<Context>` and that the Russian case is covered only at the pure-function level. Since 0bf26ee5, `PlayerUIMapperTest` uses `FakeResourceProvider` and adds a Russian case through the mapper (`mapSubtitleTracks_namesLanguagesInTheProvidersLocale`). Anyone reading the plan will misjudge what is covered.
- Failure scenario: a later reviewer relies on the record and reports ru-through-mapper as missing, or tries to keep a `Context` mock that no longer exists.
- Fix: update those lines to say T-L1 uses `FakeResourceProvider` with a settable `locale`, and that the Russian case is covered through the mapper.
