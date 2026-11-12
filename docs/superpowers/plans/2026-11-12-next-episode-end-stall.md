# After a cancelled next-episode countdown, the end of the episode stalls - plan

**Spec:** `docs/superpowers/specs/2026-11-12-next-episode-end-stall.md`
**Goal:** A cancelled next-episode countdown hides the prompt only; at `STATE_ENDED` (or when a panel over the ended episode closes) the next episode starts, and the ended controls show the final position.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Run from the worktree root; never `./gradlew`.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.vm.*'`.
- One task = green fast gate + green tests of the touched code + one commit, message per commit style (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Production change is `PlayerVM.kt` only. `PlaybackController.kt` and `ControlsStateMachine.kt` stay untouched (spec Out of scope, Q3).
- Existing guard tests named in the spec Test design (AC-3, AC-4, AC-5, AC-6 guard rows, `endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime`) stay unchanged and green.

Paths: `VM` = `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt`; `T/` = `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/`.

### Test conventions (both tasks)

| Term | Meaning |
|---|---|
| next stub | `every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)`; fixture default is null (`T/PlayerVMTestFixture.kt:117`). |
| switches to 1x2 | `verify { playbackController.release() }` and `verify { interactor.resolveMedia(item = any(), seasonNumber = 1, episodeNumber = 2, videoNumber = null) }`. `release()` is the discriminating check: the prefetch (`prefetchNextEpisode()` `VM:1606-1630`) also calls `resolveMedia(item, 1, 2)` once the prompt has been raised. |
| prompt raised by the tick | Under `runTest(mainDispatcher.dispatcher.scheduler)`: `every { playbackController.currentPosition } returns 2_380_000L`, `startedVM()`, `advanceTimeBy(600)`, `assertNotNull(contentState(vm).nextEpisodeCountdown)`; as `aLateTickDoesNotTakeTheNextEpisodePromptDown` (`T/PlayerVMPromptDismissalTest.kt:103-119`). End with `vm.testCancelScope()`. |
| no runTest | Plain `@Test`: the position loop never ticks, so no prefetch and no tick-driven publish. |

Fixture facts relied on: `startedVM()` `T/PlayerVMTestFixture.kt:190`, `contentState()` `:192`, `callbackSlot` `:76`, controller stubs `isPlaying` true / `duration` 2_400_000 / `bufferedPosition` 0 (`:89-96`), `resolveMedia` stub `:99`, `markCurrentAsWatched` stub `:101-102`, `testItem` `:217-223`, `withCurrentEpisodeWatched` `:234-245` (shape of `Season` / `Episode`), `testContentState` (`hasNextEpisode = true`, `currentPosition = 0L`, `duration = 2_400_000L`) `:274-323`. `MainDispatcherExtension` uses `UnconfinedTestDispatcher` (`app/src/test/kotlin/com/kino/puber/util/MainDispatcherExtension.kt:12-14`), so switches and `markCurrentAsWatched` run synchronously.

---

## Tasks

### Task 1: Advance at the end after a cancel, and publish the final position

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-7
**Files:**
- Modify: `VM`
  - `playNextEpisode()` `VM:1046-1054` - return `Boolean`: true when `findNextEpisode` gave a pair and `switchEpisode(Pair)` (`VM:1066-1068`) was called. Callers at `VM:668` (`when (action)` statement), `VM:1394` (last expression in `launch`, coerced to Unit) and `VM:1796` ignore it.
  - New private `playNextEpisodeOrShowEndedControls()`: `if (!playNextEpisode()) showControlsForEndedPlayback()` (`VM:795-799`).
  - `onPlaybackEnded()` `VM:1360-1382`: keep `autoMarkCurrentAsWatched()` first (`VM:1361`, defined `:1880-1882`). Right after the `Content` check (`VM:1362`) publish once: `updateContent { copy(currentPosition = playbackController.currentPosition, duration = playbackController.duration, bufferedPosition = playbackController.bufferedPosition) }`. Keep `markPlaybackEnded()` (`:1364`) and the film / last-episode branch (`:1368-1371`) as they are. Replace the `when` (`:1372-1381`) and its comment at `:1375-1376` with the ordered form below; the new comment states the rule "a cancel only hides the prompt; the end of the episode still moves on".
- Test: `T/PlayerVMPromptDismissalTest.kt`, `T/PlayerPositionPublishingTest.kt`

```kotlin
when {
    // An open panel owns the screen: its close decides (onPanelClosed).
    content.activePanel != ActivePanel.None -> Unit
    content.nextEpisodeCountdown != null -> showControlsForEndedPlayback()
    countdownDismissed -> playNextEpisodeOrShowEndedControls()
    else -> startNextEpisodeCountdown()
}
```

`content` is the snapshot taken before the publish; it reads only `activePanel`, `isMovie`, `hasNextEpisode`, `nextEpisodeCountdown`, none of which the publish changes. Keep `onPlaybackEnded()` at no more than its current `return`s (detekt `ReturnCount` max 3, `config/detekt/detekt.yml:756-759`; `CyclomaticComplexMethod` 14, `:90-93`).

**Consumes:** `countdownDismissed` `VM:158` (set by `cancelCountdown()` `VM:1398-1404`, reached from Back at `VM:1474-1476`; cleared by `onSeekPerformed()` `VM:1687-1694` and `resetMediaSession()` `VM:1022`); `switchEpisode()` guard `VM:1032`.
**Produces:** `playNextEpisode(): Boolean`; `playNextEpisodeOrShowEndedControls()` (Task 2 calls it).

| Test | File | Priority | Body |
|---|---|---|---|
| `aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut` | `PlayerVMPromptDismissalTest`, replaces `aCancelledNextEpisodeIsNotOfferedAgainWhenTheEpisodeRunsOut` (`:68-78`) | P0 red | next stub; prompt raised by the tick; `CancelNextEpisodeCountdown`; `assertNull(nextEpisodeCountdown)`; `verify(exactly = 0) { playbackController.release() }`; `callbackSlot.captured.onPlaybackEnded()` with no further virtual time; switches to 1x2. |
| `backOnTheNextEpisodePromptStillStartsItWhenTheEpisodeRunsOut` | `PlayerVMPromptDismissalTest` | P0 red | next stub; prompt raised by the tick; `PlayerAction.OnBackPressed`; `onPlaybackEnded()`; switches to 1x2; `verify(exactly = 0) { router.back(any(), any()) }`. |
| `theFinishedEpisodeIsMarkedWatchedBeforeTheNextOneLoads` | `PlayerVMPromptDismissalTest`, no runTest | P1 red | next stub; `startedVM()`; `CancelNextEpisodeCountdown` with no prompt up; `onPlaybackEnded()`; `coVerifyOrder { interactor.markCurrentAsWatched(42, 1, 1); playbackController.release() }`. |
| `anEndedDismissedEpisodeWithNoNextToFindKeepsTheEndedControls` | `PlayerVMPromptDismissalTest`, no runTest | P1 guard | fixture `findNextEpisode` null; `HideControls`, `assertFalse(controlsVisible)`; `CancelNextEpisodeCountdown`; `onPlaybackEnded()`; `assertTrue(controlsVisible)`; `release()` and `router.back` never called. |
| `theEndedControlsShowTheFinalPosition` | `PlayerPositionPublishingTest`, no runTest | P0 red | `startedVM()`; `OpenSettingsPanel`; stub `isPlaying` false, `currentPosition` / `duration` / `bufferedPosition` 2_410_000L; `onPlaybackEnded()`; `CancelNextEpisodeCountdown`; `ClosePanel`; `controlsVisible` true and `currentPosition`, `duration`, `bufferedPosition` equal 2_410_000L. |
| `theEndedControlsAfterAMissingNextEpisodeShowTheFinalPosition` | `PlayerPositionPublishingTest`, no runTest | P1 red | as the R7 row above with the controller at 2_410_000L; `currentPosition` and `duration` equal 2_410_000L. |

- [x] Write the six tests; add imports (`io.mockk.verify`, `io.mockk.coVerifyOrder`, `assertTrue`, `assertFalse` where missing).
- [x] Red run on unchanged `VM`: the `red` rows fail, the guard row passes; record in the Implementation record.
- [x] Change `VM` as above.
- [x] Fast gate; touched tests green (all of `com.kino.puber.ui.feature.player.vm.*`, guards included); commit, e.g. "Start the next episode at the end even after its countdown was cancelled".

**Done when:** the touched-tests command passes with the six new tests and `seekingAfterCancellingPutsTheNextEpisodeQuestionBack`, `playbackEnded_withNextEpisode_countsDownInsteadOfLeaving`, `playbackEnded_onMovie_returnsToTheCallingScreen`, `endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime` unchanged and green.

---

### Task 2: Advance when a panel over an ended, dismissed episode closes

**Covers:** AC-6
**Files:**
- Modify: `VM`
  - New field `private var advanceWhenPanelCloses = false` after `exitWhenPanelCloses` (`VM:159-164`, field at `:164`), with a KDoc in the same style: the media ran out under a panel after the viewer had cancelled the countdown, so the next episode starts when the panel comes down.
  - `onPlaybackEnded()`: the panel branch from Task 1 becomes `content.activePanel != ActivePanel.None -> advanceWhenPanelCloses = countdownDismissed`.
  - `onPanelClosed()` `VM:848-861`: after the `exitWhenPanelCloses` block (`:854-857`) add `if (advanceWhenPanelCloses) { advanceWhenPanelCloses = false; if (playNextEpisode()) return }`, then the existing `checkEarlyNextEpisode()`. No `showControlsForEndedPlayback()` here: `closePanel()` already shows the controls with focus on the opener (`app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:83-91`) and `playbackEnded` is set (`ControlsStateMachine.kt:168-170`).
  - `onPlaybackResumed()` `VM:805-808`: also clear `advanceWhenPanelCloses`; extend its KDoc (`VM:801-804`) to name the advance. `resetMediaSession()` reaches it (`VM:1028`); `onPlaybackStateChanged(isPlaying = true)` reaches it (`VM:210`).
  - `selectEpisodeById()` `VM:983-997`: clear `advanceWhenPanelCloses` beside `exitWhenPanelCloses = false` (`:991`), before `closePanel()` (`:992`); extend the comment at `:989-990`.
  - The hide timeout (`VM:818`) is **not** changed: it clears only `exitWhenPanelCloses`.
- Test: `T/PlayerVMPromptDismissalTest.kt`, `T/PlayerVMOverlayAutoHideTest.kt`

**Consumes:** Task 1's `playNextEpisode(): Boolean` and the reordered `when`.
**Produces:** nothing later tasks use.

| Test | File | Priority | Body |
|---|---|---|---|
| `closingAPanelOverAnEndedDismissedEpisodeStartsTheNextOne` | `PlayerVMPromptDismissalTest` | P0 red | next stub; prompt raised by the tick; `CancelNextEpisodeCountdown`; `OpenSettingsPanel`; `onPlaybackEnded()`; `verify(exactly = 0) { playbackController.release() }`; `ClosePanel`; switches to 1x2. |
| `anEpisodesRowTimingOutOverAnEndedDismissedEpisodeStartsTheNextOne` | `PlayerVMOverlayAutoHideTest`, `vmTest` (`:41-49`) | P1 red | next stub before `vmTest`; inside: stub `currentPosition` 2_380_000L, `advanceTimeBy(600)`, assert prompt; `CancelNextEpisodeCountdown`; `OpenEpisodesPanel`; `onPlaybackEnded()`; `advanceTimeBy(6_100)`; switches to 1x2; `router.back` never called. |
| `pickingAnEpisodeOverAnEndedDismissedEpisodePlaysThePickedOne` | `PlayerVMPromptDismissalTest`, no runTest | P0 guard | next stub; an item `testItem.copy(seasons = listOf(Season(id = 1, number = 1, episodes = listOf(Episode(101, 1, ...), Episode(102, 2, ...), Episode(103, 3, ...)))))` returned by both `interactor.getItemDetails(any())` and `interactor.markCurrentAsWatched(any(), any(), any())` (`coEvery`); `startedVM()`; `CancelNextEpisodeCountdown`; `OpenEpisodesPanel`; `onPlaybackEnded()`; `SelectEpisodeById(103)`; `resolveMedia(item = any(), seasonNumber = 1, episodeNumber = 3, videoNumber = null)` called, `(…, 1, 2, null)` called exactly 0 times. |
| `aRestartUnderThePanelCancelsTheAdvanceOnClose` | `PlayerVMPromptDismissalTest`, no runTest | P1 guard | next stub; `startedVM()`; `CancelNextEpisodeCountdown`; `OpenSettingsPanel`; `onPlaybackEnded()`; `callbackSlot.captured.onPlaybackStateChanged(isPlaying = true, isBuffering = false, position = 0L, duration = 2_400_000L, buffered = 0L)`; `ClosePanel`; `release()` never called. |
| `aCancelAfterTheEndUnderAPanelDoesNotStartTheNextOneOnClose` | `PlayerVMPromptDismissalTest`, no runTest | P0 guard | next stub; `startedVM()`; `OpenSettingsPanel`; `onPlaybackEnded()`; `CancelNextEpisodeCountdown`; `ClosePanel`; `release()` never called. |

- [x] Write the tests (imports for `Season`, `Episode`, `coEvery` in `PlayerVMPromptDismissalTest`).
- [x] Red run on Task 1's `VM`: the `red` rows fail; record it.
- [x] Change `VM` as above.
- [x] Fast gate; touched tests green, including unchanged `closePanel_startsCountdownDeferredWhilePanelWasOpen`, `playbackEnded_underOpenPanel_waitsForThePanelToClose`, `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays`, `nonLastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays`, `settingsClosedAfterEnd_keepsControlsUp_andBackExitsOnce`, `aboutClosedAfterEnd_keepsControlsUp_andBackExitsOnce`.
- [x] Full gate; commit, e.g. "Start the next episode when a panel closes over an ended episode whose countdown was cancelled".

**Done when:** the full gate passes; the device checks M1-M4 (spec Test design, Manual checks) are left to qa-device.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** `245d698c` Task 1; Task 2 commit follows it on the branch.

**Red run** (P0 `red` tests on the unchanged code):

- red: `aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut` - `verify { release() }` failed (PlayerVMPromptDismissalTest.kt:78)
- red: `backOnTheNextEpisodePromptStillStartsItWhenTheEpisodeRunsOut` - same verify failed
- red: `theFinishedEpisodeIsMarkedWatchedBeforeTheNextOneLoads` - `coVerifyOrder` failed, no `release()`
- red: `theEndedControlsShowTheFinalPosition` - position assertion failed (PlayerPositionPublishingTest.kt:108)
- red: `theEndedControlsAfterAMissingNextEpisodeShowTheFinalPosition` - position assertion failed (:127)
- red (Task 2, on Task 1 code): `closingAPanelOverAnEndedDismissedEpisodeStartsTheNextOne` - `verify { release() }` failed; `anEpisodesRowTimingOutOverAnEndedDismissedEpisodeStartsTheNextOne` - same
- guards green on base as designed: `anEndedDismissedEpisodeWithNoNextToFindKeepsTheEndedControls`, `pickingAnEpisodeOverAnEndedDismissedEpisodePlaysThePickedOne`, `aRestartUnderThePanelCancelsTheAdvanceOnClose`, `aCancelAfterTheEndUnderAPanelDoesNotStartTheNextOneOnClose`

**Deviations from the plan:** none. The `runTest` tests in `PlayerVMPromptDismissalTest` go through a `promptUpTest` helper with `try/finally` cancelling the scope, because a failed assertion otherwise left the position loop running and the red run died with an OutOfMemoryError.

**Files touched outside the plan:** none.

**For the reviewers:** the `onPlaybackEnded()` panel branch arms `advanceWhenPanelCloses` from `countdownDismissed` at the moment of the end; a cancel after the end does not arm it.

**Blocked:**

### Fix round 1

| Row | Change | Notes |
|---|---|---|
| #1 | `advanceAfterDismissedEnd()` in `PlayerVM.kt`: the dismissed-end switch runs in a `launch` after `delay(1)`, so `release()` is outside ExoPlayer's `STATE_ENDED` dispatch. `autoMarkCurrentAsWatched()` stays first; the block re-checks Content, `episodeSwitchInProgress`, `countdownDismissed`, no panel and no countdown before `playNextEpisodeOrShowEndedControls()`, so the null-next fallback is kept. | A bare `yield()` was tried first: it ran inline under the test dispatcher and would run inline on `Main.immediate`, so it was dropped. Red/guard: `theSwitchOfAnEndedDismissedEpisodeLeavesThePlayersEndedCallbackFirst` failed with `yield()`, passes with `delay(1)`. |
| #1 tests | Five `PlayerVMPromptDismissalTest` cases advance 2 ms after `onPlaybackEnded()`; two became `runTest` cases. Assertions unchanged. | |
| #2 | `theEndedControlsAfterAMissingNextEpisodeShowTheFinalPosition` rewritten to the R7 setup (no panel, `HideControls`, cancel). | |
| #3 | New `theEndedControlsOverARunningCountdownShowTheFinalPositionAndKeepTheCountdown`. | Fails with the `nextEpisodeCountdown != null` branch deleted. |
| #4 | `assertNull(nextEpisodeCountdown)` added to `anEndedDismissedEpisodeWithNoNextToFindKeepsTheEndedControls`. | Landed in the #1 commit with its other edits to that file. |

Reviewers: the 1 ms delay is a visible gap between the end and the switch; it is not part of the viewer's wait.

### Fix round 2

| Row | Change | Notes |
|---|---|---|
| #1 | The re-check in `advanceAfterDismissedEnd()` arms `advanceWhenPanelCloses` and calls `controlsStateMachine.markPlaybackEnded()` when a panel opened inside the 1 ms wait (what `onPlaybackEnded` does for a panel open at the end); a running countdown still returns plainly. | red: `aPanelOpenedInsideTheDeferralOfAnEndedDismissedEpisodeStartsTheNextOneOnClose` - `release()` never called after `ClosePanel`. |
| #2 | `closing` joins the re-check; no job tracking, since the block only reads state. | red: `leavingInsideTheDeferralOfAnEndedDismissedEpisodeDoesNotSwitch` - `release()` called after Back, Back. Two Backs: the first only hides the ended controls. |

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

## Readiness 2: next-episode-end-stall

Verdict: **READY**. Spec and plan at worktree HEAD aadb323a. The only change since 071a4bd0 is in the docs. Coverage, citations and order are as in `readiness-1.md` and still hold.

### Round-1 fix list

| # | Status | Evidence |
|---|---|---|
| 1 | Resolved | New P0 guard `aCancelAfterTheEndUnderAPanelDoesNotStartTheNextOneOnClose` in spec Test design, plan Task 2 and `test-design.md:36`. It catches the named mutation. Base `onPanelClosed()` falls to `checkEarlyNextEpisode()`, which is gated by `!countdownDismissed` (`PlayerVM.kt:1572`), so `release()` stays uncalled. A close path that reads `countdownDismissed` calls `playNextEpisode()` with the next stub. The old row (`test-design.md:37`) now names what the null-next fixture (`PlayerVMTestFixture.kt:117`) can catch. |
| 2 | Resolved | Task 1 and Task 2 red-run steps now say "the `red` rows fail" with no count. |
| 3 | Resolved | The spec Design row and `design-architect.md:13` now fall through to `checkEarlyNextEpisode()` on false, matching plan Task 2 (`onPanelClosed()` step). The `showControlsForEndedPlayback()` fallback stays only on the no-panel ended branch (R7). |

### New citations checked

| Citation | Symbol found |
|---|---|
| `PlayerVM.kt:848-861` | `onPanelClosed()`: the `exitWhenPanelCloses` block, then `checkEarlyNextEpisode()` |
| `PlayerVM.kt:1398-1404` | `cancelCountdown()` sets `countdownDismissed = true` with no prompt up |
| `PlayerAction.kt:27`, `:30`, `:56` | `OpenSettingsPanel`, `ClosePanel`, `CancelNextEpisodeCountdown` |
| `PlayerVMTestFixture.kt:76`, `:190` | `callbackSlot`, `startedVM()` |

### Fix list

None.
