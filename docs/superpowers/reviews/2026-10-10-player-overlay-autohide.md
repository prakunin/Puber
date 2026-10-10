# Player overlay auto-hide — review

**Spec:** `docs/superpowers/specs/2026-10-10-player-overlay-autohide-design.md`
**Plan:** `docs/superpowers/plans/2026-10-10-player-overlay-autohide.md`
**Evidence:** `docs/superpowers/evidence/2026-10-10-player-overlay-autohide-device.md`
**Branch:** `player-overlay-autohide` at `9503c2ef`

<!-- The conductor writes the rounds from the reviewers' returns; the
final-judge writes the Gate. Each finding gets a disposition: fixed (<sha>),
rejected (<why>), or deferred (<board row>). Nothing stays open silently. -->

## Round 1

### code-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | minor | ControlsStateMachine.kt:120-124 | Episodes timeout after the media ended lands on the ended controls with focus on `EpisodesButton`, the same target that loses its ring on Up-close; not checked on a device | deferred (player-focus-after-panel-close): repro added to the row |
| 2 | minor | PlayerVM.kt:1253, EpisodesPanel.kt:201 | Last episode ends with Episodes open: waiting keeps the ended controls (D13), but Up in the row closes the panel and exits at once; Up predates the branch and answer 9 does not mention it | rejected for this branch: pre-existing Up behaviour, reported to the user as a note on answer 9 for their test |

### test-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | major | PlayerVMOverlayAutoHideTest.kt | AC-11: no VM test for Episodes open when a non-last episode ends; `markPlaybackEnded()` could sit only in the last-episode branch and every test would pass | fixed (14a916b0) |
| 2 | major | PlayerVMOverlayAutoHideTest.kt | D13 side effect untested: Settings/About open at the end, viewer closes it, controls must stay and Back must exit | fixed (14a916b0) |
| 3 | major | PlayerVMOverlayAutoHideTest.kt | AC-13 half covered: no test of the debug preference on through the Episodes timeout path | fixed (14a916b0) |
| 4 | minor | PlayerVMOverlayAutoHideTest.kt `controlsTimerDuringExit_changesNothing` | Never opens Episodes nor asserts no next-episode countdown on the panel-close branch (D9) | fixed (14a916b0) |
| 5 | minor | PlayerVMOverlayAutoHideTest.kt `pausing_setsIsPlayingFalse_andResumingSetsItTrue` | End-of-media step cannot fail (isPlaying already false) | fixed (14a916b0) |
| 6 | minor | PlayerVideoSurfaceTest.kt | Tests the helper, not the call sites; `minimalContent()` duplicates the fixture's `testContentState` | fixed (14a916b0): reuses the fixture; call-site coverage stays with device AC-15 |
| 7 | minor | PlayerVMOverlayAutoHideTest.kt | No VM case for `ResetControlsTimer` while held, nor for reopening Episodes after a timeout | fixed (14a916b0) |
| 8 | minor | PlayerSettingsPanels.kt | Key hook and hold lambdas have no JVM test | rejected: accepted residual risk in the design (no Compose UI test harness for this path); covered by device checks D3/D4, both PASS |

### codex-reviewer

Ran: review, adversarial

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | minor | ControlsStateMachine.kt:120-125 | Same as code #1: ended-media timeout landing can show controls without usable focus | deferred (player-focus-after-panel-close) |
| 2 | minor | PlayerVM.kt:196-198 | A stall longer than the 800 ms debounce keeps Episodes open for the whole stall plus 6 s | rejected: D12 resumes the timer after buffering by design, the bar behaves the same on master, no AC covers a long stall; raised to the user as a question for their test |

Rejected Codex claims:

- Severity "medium" for the long stall and the claim it violates AC-3/AC-6: the ACs assume no stall, and D12 resumes the timer after buffering on purpose.
- Severity "medium" for the ended-controls focus: Back and Up still work, and the root cause is the deferred focus bug.
- The `review` pass itself had no findings.

### ux-designer (visual changes)

Not run: no Compose layout change (the diff adds a non-consuming key modifier and dialog callbacks only).

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|

## Round 2

Re-ran only test-reviewer (the code-reviewer and codex findings were deferred or rejected, with no code change). Verdict: adequate; #1–#3, #6, #7 resolved, #8 accepted residual risk.

### test-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | minor | PlayerVMOverlayAutoHideTest.kt `episodesTimerDuringExit_startsNoCountdownAndChangesNothing` | Cannot fail on the `closing` guard (Episodes plus `closing` is unreachable); name overclaims | fixed (7d7aa09c): renamed `lastEpisode_backOutOfEpisodesAfterEnd_exitsOnce_andLaterTimeChangesNothing`; the bar-only guard stays pinned by `controlsTimerDuringExit_changesNothing` |
| 2 | minor | PlayerVMOverlayAutoHideTest.kt `playbackEnd_setsIsPlayingFalse_withoutAPauseBefore` | `onPlaybackEnded` never sets `isPlaying`; the test pins the state callback | fixed (7d7aa09c): renamed `playbackStateChange_toNotPlaying_setsIsPlayingFalse_withoutAPauseBefore`, `onPlaybackEnded()` dropped |

## Gate

<!-- final-judge, gate mode. -->

**Decision:** CONCERNS (final-judge, 2026-10-10, branch at `4db1e775`)

What I checked myself:
- I read `git diff master...HEAD -- app/src/main`.
- Production code is unchanged since the QA build `f00f1712`. `git diff f00f1712..HEAD -- app/` touches tests only.
- `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest :app:detektAll` is green at HEAD. Results on disk: 1415 tests, 0 failures, 0 errors, 0 skipped. `PlayerVMOverlayAutoHideTest` ran 29 of 29, including both tests renamed in round 2.
- `tools/mobile-evidence-audit.sh docs/superpowers/evidence`: passed.
- No `Co-Authored-By` in `master..HEAD`.
- `applyControlsVisibility` has no references left.

**Done as asked.** The diff is the design, D1-D13, and nothing more:
- one shared timer, with `onHideTimeout()` deciding what a timeout means;
- Episodes arms the timer, Settings and About cancel it;
- `closePanel` does nothing when no panel is open;
- `closing` guard in the timer job;
- buffering-end condition (D12) and `markPlaybackEnded` (D13);
- the context-menu hold;
- the key hook, placed in `PlayerSettingsPanels` only;
- `shouldKeepScreenOn()`;
- a comment-only change in the trailer.

What the scope rules required:
- The files owned by other worktrees (`EpisodesPanel.kt`, `PlayerScreenContent.kt`) and the lines `subtitle-player-fixes` will touch (`applyControlsState`, `processEffects`, `openPanel`, `preparePlayback`, `ControlsStateMachine` lines 33-40) are untouched.
- There is no visual change and no new setting. The 1.5 s and 7 s timings are unchanged.

### Traceability

| AC | Test / check | Evidence | Status |
|---|---|---|---|
| AC-1 | VM `controlsBar_staysAt5900ms_hidesAfter6000ms`; SM `onHideTimeout_noPanel_hidesControls`; device, 5 runs | unit green; evidence AC-1 (6.0-6.2 s) | PASS |
| AC-2 | VM `resetControlsTimer_at4s_restartsTheSixSeconds`; device | unit green; evidence AC-2 (hid 6.2 s after the last key) | PASS |
| AC-3 | VM `openEpisodesPanel_closesItself_after6s`; SM `onHideTimeout_episodesOpen_closesToCleanVideoAndReportsPanelClosed`, `openPanel_episodes_keepsPlaybackRunning`; VM `episodesPanel_stillClosesAfterBufferingBlip`, `reopeningEpisodesAfterTimeout_armsTheTimerAgain`; device D1 | unit green; evidence D1 | PASS |
| AC-4 | VM `resetControlsTimer_withEpisodesOpen_restartsTheSixSeconds`; device D3 (key hook) | unit green; evidence D3 (fast separate presses, not true auto-repeat) | PASS |
| AC-5 | SM `holdEpisodesPanel_*`; VM `episodesPanelHeldByContextMenu_staysFor30s_thenClosesSixSecondsAfterRelease`, `heldEpisodesPanel_staysThroughBufferingBlip`, `resetControlsTimer_whileEpisodesPanelHeld_doesNotCloseIt`; device D3 | unit green; evidence D3 (menu 21 s, row closed 6 s after the menu) | PASS |
| AC-6 | same VM test (one action); device | evidence AC-6 | PASS |
| AC-7 | VM `settingsPanel_staysOpenFor60s`, `aboutPanel_staysOpenFor60s`, `settingsPanel_staysOpenAcrossBufferingStartAndEnd`; SM `onHideTimeout_withSettingsOpen_doesNotHide`, `onHideTimeout_withAboutOpen_returnsNoEffects`; device | unit green; evidence AC-7 (stream info 36 s, About 35 s) | PASS |
| AC-8 | VM `closingSettingsPanel_bringsBackControlsThatHideAfter6s`; device | unit green; evidence AC-8 (6.0 s) | PASS |
| AC-9 | SM `closePanel_withNoPanel_returnsNoEffectsAndLeavesStateAlone`; VM `staleClosePanelAfterEpisodesTimeout_doesNotReshowControls`, `staleResetControlsTimerAfterEpisodesTimeout_doesNotReopenAnything`, `backAfterEpisodesTimeout_exitsOnTheFirstPress`; device D2 (Left seeks, Back leaves on the first press), D4 | unit green; evidence D2, D4 | PASS |
| AC-10 | VM `resumeDialog_noHideTimerRunsWhileItIsUp`; device (dialog stays 40 s) | unit green; evidence AC-10 (timer after the answer: unit test only) | PASS |
| AC-11 | SM `onHideTimeout_afterPlaybackEnded_keepsControlsUp`; VM `endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime`, `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays`, `nonLastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays`, `settings/aboutClosedAfterEnd_keepsControlsUp_andBackExitsOnce` | unit green; device: not checked (evidence explains why) | PASS (unit only) |
| AC-12 | VM `controlsHideAfter6s_whilePaused`; device | unit green; evidence AC-12 (6.1 s) | PASS |
| AC-13 | VM `debugReadings_dropWhenEpisodesPanelTimesOut`, `debugReadings_stayWhenEpisodesPanelTimesOut_withDebugOverlayEnabled`. Overlay visibility is the unchanged Compose condition `controlsVisible && debugInfo != null` | unit green; device: not checked | PASS (unit only) |
| AC-14 | device D1 (Down, 7 s, clean; stream info 30 s+) | evidence D1, AC-7 | PASS |
| AC-15 | VS `shouldKeepScreenOn_*`; VM `pausing_setsIsPlayingFalse_andResumingSetsItTrue`, `playbackStateChange_toNotPlaying_setsIsPlayingFalse_withoutAPauseBefore`; device `mHoldScreenWindow` | unit green; evidence AC-15 | PASS |
| AC-16 | device D5 (user-authorized `stay_on_while_plugged_in 0`, settings restored and read back) | evidence AC-16 (Colors dream while paused, one key wakes it, still paused; no dream while playing) | PASS (the user judges it after their own test) |

No AC is NOT VERIFIED.

### Findings

Every round 1 and round 2 finding has a disposition. None was silently dropped.
- The test-reviewer majors #1-#3 are fixed in `14a916b0`. I confirmed the tests are present and green: `nonLastEpisode_episodesTimeoutAfterEnd_*`, `settings/aboutClosedAfterEnd_*` and `debugReadings_stay…_withDebugOverlayEnabled`.
- The round 2 minors are fixed in `7d7aa09c`.
- Code #1 and codex #1 are deferred to board row `player-focus-after-panel-close`. The row is on master in `322cfa7b`, and `a1e1e065` adds the ended-controls repro.
- Code #2 (Up after the end on the last episode) and codex #2 (long stall) are rejected with reasons and passed to the user as notes.
- Test #8 is accepted residual risk, covered by device D3/D4.

### Non-functional

| Area | Assessment |
|---|---|
| Performance | No new composition state is read. The key modifier is remembered against `onAction`. One coroutine `launch` per key press, with the previous job cancelled first; this is how the bar already works. Nothing new in hot paths, no cache, no startup change. Not a perf route, so there are no numbers. |
| Reliability | One timer, cancelled in `onCleared`, on buffering start, and on panel open or hold. The job returns early while `closing`. A stale `ClosePanel` does nothing. A held panel cannot be closed by a stray reschedule, because `onHideTimeout` checks the hold flag. `openPanel` and `closePanel` clear the flag. Process death: panel state lives only in the VM and comes back as None, the same as before. A timeout never exits the player. Every case has a unit test. |
| Maintainability | Follows `AGENTS.md`: Koin and screen wiring untouched, the Compose content stays state plus `onAction`, JUnit 5 with `MainDispatcherExtension`, builds through `agentw`. The removed `applyControlsVisibility` has no references left. No duplicated helper: `PlayerVideoSurfaceTest` now reuses the fixture. New code sits at the end of the class, as R5 requires. detekt is green. |
| Security / privacy | No secrets or account data in the code, the evidence or the logs, and the evidence audit passed. The device run changed the test account's history: progress on S2E3, and S2E4 is now marked watched. The evidence records this. |
| TV (focus, D-pad, Back) | After the auto-close, focus is on the video: Left seeks, Down brings the bar back (D2). Back leaves on the first press. The key hook never consumes the event, so Up-to-close and long-press still work (D3, D4). Overscan: no layout change. Known gap that predates the branch: no focus ring after Up closes Episodes, deferred. |

### Must fix

None.

### Concerns for the user

1. **Questions 8 and 9 were decided for you; check them in your test.**
   - The trailer keeps 4.5 s while the player uses 6 s.
   - After the media ends with Episodes open, the 6 s timeout lands on the end-of-media controls, which stay until Back. The player never exits on its own.
   - Side effect (D13): closing Settings or About yourself after the end now keeps the controls up until Back.
   - Pre-existing: on the last episode, Up in the row after the end still exits at once.
2. **AC-11 and AC-13 are checked by unit tests only.** The end-of-media controls and the debug overlay were not seen on the TV. Reaching the ended controls needs a non-last episode played to its end.
3. **Focus ring after closing Episodes.** After Up closes Episodes, and probably on the ended controls after the timeout, the bar can come up with no focus ring. This predates the branch and is tracked in `player-focus-after-panel-close`.
4. **Long stalls.** During a buffering stall longer than 0.8 s, the Episodes row stays up for the whole stall plus 6 s. This is by design (D12), and the bar has always behaved the same way.
5. **Account history.** The device run changed the test account's watch history: S2E4 is marked watched, and the S2E3 position moved.
