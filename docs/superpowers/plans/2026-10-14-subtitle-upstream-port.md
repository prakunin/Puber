# Bring upstream's subtitle fixes into this fork - plan

**Spec:** `docs/superpowers/specs/2026-10-14-subtitle-upstream-port.md`
**Goal:** Land the uncommitted subtitle port on master be8395a4. Subtitles stay Off until the viewer picks them, also when a pick from the previous media is still pending. Picker rows are named readably. Back on fresh media hides the controls. Master's player suite stays green with its intent intact.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Worktree: `/home/user1/orca/workspaces/Puber/subtitle-player-fixes` (branch
  `subtitle-player-fixes`). Every command below runs there (`cd <wt> && ...`).
  Never run `./gradlew` in the worktree (the guard hook refuses it). Prefix with
  `AGENT_GRADLE_SERIAL=1` whenever another worktree may build.
- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`.
  Full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests:
  `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<FQCN or pattern>'`.
  The player package as a whole:
  `--tests 'com.kino.puber.ui.feature.player.*'`.
- One task = green fast gate + green tests of the touched code + one commit.
  The message is one imperative English sentence with no prefix. Never add a
  `Co-Authored-By` line, whatever the harness suggests. Task 2 is the exception:
  it rewrites history and adds no commit.
- Stage explicit paths only (`git add <path>...`). Never use `git add -A` or `.`.
  Never commit `replay_pid1700735.log` or any other `replay_pid*.log`. It is a
  JVM crash artefact and must stay untracked. This plan file stays untracked
  until Task 9.
- Device verification (smoke M1-M8 in the spec) belongs to qa-device after this
  plan. No task here installs, launches or touches a device.
- Citations: `master:` = main checkout `/mnt/data/projects/PetProjets/Puber` at
  be8395a4. `wt:` = the worktree as it stands before Task 1, with the
  implementation uncommitted on old base 7ad23247. Every `path:line` below was
  read. After Task 2, `PlayerVM.kt` lines below master `:361` shift by +9 (the
  inserted `publishPreparedContent`). Test files that the diff does not touch
  keep their master lines.
- Scope: only the files named per task. No change to `PlaybackController.kt`
  beyond what the uncommitted diff already holds. No DI, screen or Compose
  layout change, and no change to `PlayerSidePanel` / `PlayerPanelItem` sizes
  (ux-designer: "parameterise, do not retune").

Paths used below:

- `VM` = `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt`
- `CSM` = `app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt`
- `T/` = `app/src/test/kotlin/com/kino/puber/ui/feature/player/`

---

## Tasks

### Task 1: Commit the existing uncommitted work in place

**Covers:** AC-1..AC-8 (the code as ported). This also provides the base for
AC-9.
**Files** (all already modified or created in the worktree, from `git status`):
- `app/src/main/java/com/kino/puber/data/api/models/Models.kt` (`SubtitleLink.forced`)
- `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt`
- `app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLabels.kt` (new)
- `CSM` (`initialize` at wt:`:38-50`)
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`
- `VM` (`publishPreparedContent` wt:`:361-368`, `withControlsState` wt:`:762-768`)
- `app/src/main/res/values-en/strings.xml`, `app/src/main/res/values/player.xml`
- `T/vm/ControlsStateMachineTest.kt`, `T/vm/PlayerVMMutationAndResultTest.kt`,
  `T/vm/PlayerVMPlaybackFlowTest.kt`, `T/model/SubtitleLabelsTest.kt` (new)
- `docs/superpowers/evidence/2026-10-10-subtitle-upstream-port-device.md` and
  `docs/superpowers/evidence/2026-10-10-subtitle-upstream-port/` (3 PNGs). Stage
  these only after the audit passes.

**Consumes:** nothing.
**Produces:** one commit on top of 3eb98f96 holding the port as it stood on the
old base.

- [x] Audit the evidence:
  `tools/mobile-evidence-audit.sh docs/superpowers/evidence/2026-10-10-subtitle-upstream-port com.kino.puber.stage`.
  The dev flavor package is `com.kino.puber` + `.stage` (`app/build.gradle.kts:51`
  and the `dev` flavor's `applicationIdSuffix`). The script takes a directory, so
  audit the markdown file as well: copy it into a fresh `mktemp -d` directory and
  run the script on that directory. If either run fails, leave the evidence out
  of the commit and record it under Blocked. Do not edit the evidence to make it
  pass.
- [x] Run the fast gate (on the old base).
- [x] `git add` the paths listed above by name. Check that `git status --short`
  still shows `?? replay_pid1700735.log` and the untracked plan file, and that
  nothing else is left modified.
- [x] Commit, e.g. "Port upstream's first batch of subtitle fixes to the player".

**Done when:** `git show --stat HEAD` lists exactly the files above (plus the
evidence, if the audit passed), and `git log -1 --name-only | grep -c replay_pid`
prints `0`.

---

### Task 2: Rebase the branch onto master be8395a4

**Covers:** AC-9 (precondition).
**Files:** conflict resolution only. Expected conflicts:
- `T/vm/ControlsStateMachineTest.kt`: adjacency. Master rewrote the file (+151
  lines). Our 4 `initialize_*` tests sit directly after `setUp()`
  (master `:15-18`), in a section headed `// initialize` (wt `:20-63`). Keep
  master's file whole and re-insert our section after `setUp()`.
- `VM`, near `applyControlsState` (master `:760-769`). Master added
  `holdEpisodesPanel` (`:739-743`) and `onPanelClosed` (`:745-758`) just above it.
  Resolve to master's code plus our two hunks:
  - `preparePlayback` calls `publishPreparedContent(contentState)` instead of
    `updateViewState(PlayerViewState.Content(contentState))` (master `:352`).
  - `publishPreparedContent` is inserted after `preparePlayback`.
  - `applyControlsState` becomes `updateContent { withControlsState(cs) }`,
    with the private extension `withControlsState` after it.

  Keep master's `scheduleControlsHide()` at master `:357` untouched.
- `CSM`, `PlayerUIMapper.kt`, `PlaybackController.kt`, `Models.kt` and the
  strings are expected to merge cleanly. The spec's rebase assessment explains
  why.

**Consumes:** Task 1 commit.
**Produces:** branch = be8395a4 + 3 rewritten commits (spec 379ffd50', gate 1
3eb98f96', port). The new SHAs go into the Implementation record.

- [x] `git rebase be8395a4`. On each conflict, resolve as above, `git add <file>`,
  then `git rebase --continue`. Do not use `--skip`, and do not resolve by taking
  one side whole for `VM`.
- [x] Semantic checks, by reading the rebased files:
  - `CSM.initialize` builds a fresh `State(...)`. Master's `episodesPanelHeld`
    and `playbackEnded` therefore fall back to `false` (master `CSM:8-19`).
  - `PlayerUIMapper.kt` has no leftover `java.util.Locale` import. Master removed
    it (master diff at the top of the file), and the mapper does not need it.
  - `VM` compiles with master's `holdEpisodesPanel` in place.
- [x] Run the fast gate.
- [x] Run the player tests (`--tests 'com.kino.puber.ui.feature.player.*'`).
  Exactly these two may fail, both in `T/vm/PlayerVMOverlayAutoHideTest.kt`:
  - `resumeDialog_noHideTimerRunsWhileItIsUp`
  - `controlsTimerDuringExit_changesNothing`

  Task 3 rewrites both, directly after this task. Any other failure means
  stop. Record the test and its assertion under Blocked: the owner approved rewriting only these two (Q5).
- [x] `git log --oneline be8395a4..HEAD` shows 3 commits. Write the old to new
  SHA mapping into the Implementation record now. History was rewritten, so
  3eb98f96 and the Task 1 SHA no longer exist on the branch.

**Done when:** `git merge-base --is-ancestor be8395a4 HEAD` exits 0, the fast
gate is green, the player tests fail only on the two named tests, and
`git log be8395a4..HEAD --name-only | grep -c replay_pid` prints `0`. The
rebased port commit is the only commit on the branch whose unit suite carries
these two known red tests. That is the accepted consequence of owner Q1 (commit
in place, then rebase). Task 3 makes the suite green again, and every later
task ends on a green suite. The Implementation record states this (Task 9).

---

### Task 3: D8, rewrite the two master overlay-autohide tests with the same intent

**Covers:** AC-8 (the resume half), AC-9.
**Files:**
- Test: `T/vm/PlayerVMOverlayAutoHideTest.kt`. Relied on from that file:
  - `vmTest` `:41-49` (virtual time, `runTest(mainDispatcher.dispatcher.scheduler)`)
  - `resumeDialog_noHideTimerRunsWhileItIsUp` `:171-191`
  - `controlsTimerDuringExit_changesNothing` `:256-270`
  - the pattern of `lastEpisode_backOutOfEpisodesAfterEnd_exitsOnce_andLaterTimeChangesNothing`
    `:272-292` (`hasNextEpisode = false`, save held via `CompletableDeferred`,
    snapshot `before`)
- Read only:
  - `VM.onAction` ignores everything but Back while `closing` (master `:543-549`)
  - `scheduleControlsHide` is at master `:707-719`; its job returns early when
    `closing` (`:712`)
  - `exitPlayer` (master `:1811-1830`) does not cancel `controlsHideJob`
  - `onPlaybackEnded` (master `:1244-1255`): for `!hasNextEpisode` with no panel
    open, it calls `exitPlayer()` directly
  - `preparePlayback` arms the 6 s hide when there is no resume prompt (master `:357`)
  - `resumeFromSavedPosition` (master `:1290-1307`) calls `scheduleControlsHide()`
    but does not show the controls
  - fixture `controlsVisible = true` (`PlayerVMTestFixture.kt:256`)

**Consumes:** `publishPreparedContent` (the machine now owns the published
controls fields).
**Produces:** nothing new.

- [x] Rewrite `resumeDialog_noHideTimerRunsWhileItIsUp` (T-M1). Keep both stubs
  (`watchingTime = 1_200`, factory content with `ResumeDialogState(60_000L, "1:00")`).
  Body:
  - right after start, `assertFalse(controlsVisible)` and `assertNull(controlsFocusTarget)`
  - `advanceTimeBy(60_000)`, then `assertNotNull(resumeDialog)`, controls still
    hidden, focus still `null`
  - `vm.onAction(PlayerAction.ResumeFromPosition)`, then
    `vm.onAction(PlayerAction.ShowControls(FocusTarget.Buttons))`
  - `advanceTimeBy(5_900)`, then `assertTrue(controlsVisible)`
  - `advanceTimeBy(200)`, then `assertFalse(controlsVisible)`
  - Known limit, for the reviewers (readiness note): with the controls now
    hidden during the prompt, a stray hide timer armed while the prompt is up
    has no observable effect, and `resumeFromSavedPosition` cancels it anyway
    via `scheduleControlsHide`. So T-M1 can no longer observe the "no timer
    while the prompt is up" half of the original intent, like T-P2's limit.
    It keeps the observable half: hidden with no focus during the prompt, and
    5.9 s / 6.1 s after resume plus `ShowControls`. That half is
    mutation-sensitive to reverting `publishPreparedContent`.
- [x] Rewrite `controlsTimerDuringExit_changesNothing` (T-M2). Intent: a
  controls hide timer that fires during the exit changes nothing.
  - **Deviation from the spec's recipe:** the spec suggests `HideControls`,
    then Back, then `ResetControlsTimer`. That recipe cannot arm a timer, because
    `ResetControlsTimer` is dropped once `closing` is set (master `:543-549`).
    And once the controls are already hidden, a firing timer changes nothing
    whether or not the guard exists. Use the last-episode exit instead, where the
    controls are up when the exit starts.
  - stub the factory to `testContentState.copy(hasNextEpisode = false)`, and
    hold `saveWatchingTime(42, 1, 0, 1)` on a `CompletableDeferred`, as in
    `:274-280`
  - in `vmTest`: `assertTrue(controlsVisible)`. Fresh media has the controls up,
    and the hide was armed at t = 0.
  - `advanceTimeBy(3_000)`, then `callbackSlot.captured.onPlaybackEnded()`. The
    exit starts and the save is held.
  - `val before = contentState(vm)`, then `advanceTimeBy(3_100)`. The hide armed
    at 0 fires at 6 s, during the exit.
  - `assertEquals(before, contentState(vm))`, `assertTrue(contentState(vm).controlsVisible)`,
    `verify(exactly = 0) { router.back(any(), any()) }`, then
    `releaseSave.complete(Unit)`
  - If `before` differs because of an unrelated asynchronous field, for example
    the auto-mark mutation, compare `controlsVisible`, `controlsFocusTarget` and
    `activePanel` instead, and say so in For the reviewers.
- [x] Mutation checks. These are local experiments: revert them, never commit
  them.
  - (a) Delete `if (closing) return@launch` in `scheduleControlsHide` (rebased
    `VM`, master `:712` + 9). T-M2 must go red.
  - (b) Read the diff and confirm that T-M1's first assertion would fail if
    `publishPreparedContent` were reverted to `updateViewState(PlayerViewState.Content(contentState))`.
    The fixture content says the controls are visible. Do not check out the
    reverted code.
  - Note both outcomes in For the reviewers.
- [x] Run `--tests '*PlayerVMOverlayAutoHideTest'` (all green), then the player
  tests (`--tests 'com.kino.puber.ui.feature.player.*'`, all green), then the
  fast gate. Commit, e.g. "Keep the overlay auto-hide tests' intent now that new
  media starts with the controls owned by the state machine".

**Done when:** the whole player test package is green, and mutation (a) went red
and was reverted (`git diff` after the commit is empty).

---

### Task 4: D3, re-apply the picker's row when the saved subtitle does not resolve (test first)

**Covers:** AC-1, AC-3 (R1 in the Test design).
**Files:**
- Test: `T/vm/PlayerVMTrackSelectionTest.kt`. The fixture comes from
  `T/vm/PlayerVMTestFixture.kt`:
  - `startedVM()` `:155`, `contentState(vm)` `:157`, `callbackSlot` `:75`
  - default `getPreferredSubtitleLang/Url` stubs returning `null` `:106-107`
  - `testSubtitleTracks` `:229-238`. Row 0 is Off with `url = ""`. Rows 1 and 2
    are both `rus`, with urls `.../rus.vtt` and `.../rus-forced.vtt`.
  - `testContentState.selectedSubtitleIndex = 0` `:267`
  - `contentStateFactory.build` returns `testContentState` on every prepare
    `:99`.
- Modify: `VM` `restoreTrackPreferences`, master `:517-541`, after the rebase
  `:526-550`. The `if (subtitleIndex >= 0) { applySubtitleSelection(subtitleIndex, persist = false) }`
  block is at master `:538-540`, after the rebase `:547-549`.
  `applySubtitleSelection(index, persist)` is at master `:792`. It sets
  `selectedSubtitleIndex` and calls `playbackController.selectSubtitle(row)`.
  Restore runs once per media from `onTracksUpdated` (master `:211-222`, guarded
  by `tracksRestoredForCurrentMedia`). The guard is reset on episode switch,
  buffer preset change and DNS toggle (master `:856`, `:867`, `:911`).
- Read only: `findSubtitleTrackIndex`
  (`app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt:24-37`)
  matches on exact url, then stable key, then unambiguous language. Otherwise it
  returns -1. `stableSubtitleKey` is in
  `app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackIdentity.kt:7-12`.
  Switching episode uses `PlayerAction.SelectEpisode(1, 2)`, the pattern in
  `T/vm/PlayerVMEpisodeFlowTest.kt:108-126`.

**Consumes:** the rebased branch.
**Produces:** after the first restore for each media, the VM always calls
`playbackController.selectSubtitle(...)`. It uses the resolved row, or the
picker's current row when nothing resolves, with `persist = false`.

- [x] Write `tracksUpdated_reappliesOffRow_whenSavedSubtitleDoesNotResolve`
  (T-D3a):
  - stub `getPreferredSubtitleLang(42)` to `null` and
    `getPreferredSubtitleUrl(42)` to `"https://test/subtitles/gone.vtt"`
  - `startedVM()`, then `onTracksUpdated(tracks, 0)` using the same audio
    `tracks` list as `PlayerVMTrackSelectionTest.kt:117`
  - assert `verify(exactly = 1) { playbackController.selectSubtitle(testSubtitleTracks[0]) }`
  - assert `verify(exactly = 0)` for rows `[1]` and `[2]`
  - assert `verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) }`
  - assert `selectedSubtitleIndex == 0`
- [x] Write `tracksUpdated_nextMediaWithUnresolvedChoice_sendsOffRow` (T-D3b):
  - `startedVM()`, then `vm.onAction(PlayerAction.SelectSubtitle(2))`
  - stub `getPreferredSubtitleLang(42)` to `"rus"` and `getPreferredSubtitleUrl(42)`
    to `null`. The language is ambiguous over rows 1 and 2, so the saved choice
    does not resolve.
  - `vm.onAction(PlayerAction.SelectEpisode(1, 2))`, then `onTracksUpdated(tracks, 0)`
  - assert `verifyOrder { playbackController.selectSubtitle(testSubtitleTracks[2]); playbackController.selectSubtitle(testSubtitleTracks[0]) }`
    (import `io.mockk.verifyOrder`)
  - assert `selectedSubtitleIndex == 0`
- [x] Rewrite `tracksUpdated_doesNotRestoreAmbiguousSubtitleLanguage_whenUrlIsMissing`
  (master `:138-149`). Rename it to
  `tracksUpdated_ambiguousSubtitleLanguage_withoutUrl_reappliesOffRow`.
  Replace `verify(exactly = 0) { playbackController.selectSubtitle(any()) }` at
  `:147` with `verify(exactly = 1) { playbackController.selectSubtitle(testSubtitleTracks[0]) }`.
  Keep `selectedSubtitleIndex == 0`. Add the `saveTrackPreferences`
  `exactly = 0` check.
- [x] Run `--tests '*PlayerVMTrackSelectionTest'`. All three must be red for
  the reason above: `selectSubtitle(Off)` was never called. Record that they
  were red.
- [x] Replace the `if` block with:
  ```kotlin
  // Nothing saved resolves for this media: re-send the picker's own row, so a pick still
  // pending in the controller from the previous media cannot turn text back on under an
  // Off picker. On a rebuild the row is the viewer's live pick, so nothing changes.
  applySubtitleSelection(
      index = subtitleIndex.takeIf { it >= 0 } ?: content.selectedSubtitleIndex,
      persist = false,
  )
  ```
  `content` is the local at master `:522`. Restoring audio does not change
  `selectedSubtitleIndex`.
- [x] Run `--tests '*PlayerVMTrackSelectionTest' --tests '*PlayerVMEpisodeFlowTest'`
  (green), then the fast gate. Commit, e.g. "Drop a subtitle pick left by the
  previous media when the saved choice does not resolve".

**Done when:** the three tests were red before the `VM` edit and are green after
it. `PlayerVMEpisodeFlowTest` is green. The diff in `VM` is limited to
`restoreTrackPreferences`.

---

### Task 5: D7, pin the `episodesPanelHeld` reset in `initialize`

**Covers:** AC-8 (state left by previous media).
**Files:**
- Test: `T/vm/ControlsStateMachineTest.kt`, test
  `initialize_dropsWhatThePreviousMediaLeft` (wt `:41-53`; after the rebase,
  find it by name).
- Read only: `CSM.holdEpisodesPanel` (master `:159-163`). It only acts while
  `activePanel == Episodes`, and sets `episodesPanelHeld`.

**Consumes:** `CSM.initialize(resumeDialogVisible)` from Task 1.
**Produces:** nothing new.

- [x] Right after `machine.openPanel(ActivePanel.Episodes)`:
  - add `machine.holdEpisodesPanel(true)`
  - add the precondition `assertTrue(machine.state.episodesPanelHeld)`
- [x] After `machine.initialize(resumeDialogVisible = false)`, add
  `assertFalse(machine.state.episodesPanelHeld)`. Keep the existing assertions.
- [x] Run `--tests '*ControlsStateMachineTest'` and the fast gate. Commit, e.g.
  "Check that new media also releases a held Episodes panel".

**Done when:** `ControlsStateMachineTest` is green and the test holds both new
assertions.

---

### Task 6: AC-8 VM tests for media change (T-P1, T-P2)

**Covers:** AC-8 (owner Q6: a media change closes any panel and focuses the
controls; the hide-job race from architect risk 4).
**Files:**
- Test: `T/vm/PlayerVMPlaybackFlowTest.kt`, next to
  `backPressed_onFreshMedia_hidesTheControlsInsteadOfLeaving` (wt `:59-69`).
  The file runs without `runTest` (Unconfined main dispatcher), so no timers
  are advanced there.
- Test: `T/vm/PlayerVMOverlayAutoHideTest.kt` (needs `vmTest` virtual time).
- Read only:
  - `switchEpisode` (master `:915-928`) does not close a panel, and starts the
    new media with `StartFromBeginning` (`:926`), so a switch never shows a
    resume prompt
  - `retryPlayback` (master `:1327-1332`) reloads with `params.startMode`
    (`ResumeIfAvailable`) and does not cancel `controlsHideJob`
  - `buildResumeDialog` (master `:365-368`) returns a prompt for
    `watchingTime = 1_200` against `duration = 2400`

**Consumes:** `publishPreparedContent` / `CSM.initialize`.
**Produces:** nothing new.

- [x] `publishPreparedContent_closesPanelLeftByPreviousMedia` (T-P1, in
  `PlayerVMPlaybackFlowTest`):
  - `startedVM()`, then `OpenEpisodesPanel`, then assert
    `activePanel == ActivePanel.Episodes`
  - `SelectEpisode(1, 2)`, then assert `activePanel == None`,
    `controlsVisible == true` and `controlsFocusTarget == FocusTarget.Buttons`
  - `OnBackPressed`, then assert `controlsVisible == false`, `activePanel == None`
    and `verify(exactly = 0) { router.back(any(), any()) }`

  This is mutation-sensitive. Without `initialize`, the machine would still hold
  Episodes, and Back would close it onto visible controls focused on
  `EpisodesButton`.
- [x] `publishPreparedContent_resumePromptAfterRetry_staysAloneThroughTheOldHideDeadline`
  (T-P2, in `PlayerVMOverlayAutoHideTest`):
  - stub `resolveMedia(any(), any(), any(), any())` `returnsMany listOf(testResolvedMedia, testResolvedMedia.copy(watchingTime = 1_200))`
  - stub the factory `returnsMany listOf(testContentState, testContentState.copy(resumeDialog = ResumeDialogState(60_000L, "1:00")))`
  - in `vmTest`: `assertTrue(controlsVisible)` (hide armed at 0), `advanceTimeBy(3_000)`,
    then `vm.onAction(PlayerAction.RetryPlayback)`
  - assert `resumeDialog != null`, `controlsVisible == false` and
    `controlsFocusTarget == null`
  - `advanceTimeBy(3_100)`, past the first media's 6 s deadline: same assertions
  - `advanceTimeBy(60_000)`: same assertions

  Known limit, for the reviewers: the `controlsHideJob?.cancel()` in
  `publishPreparedContent` cannot be observed on its own. A stale timeout on
  hidden controls is a no-op, and every `ShowControls` re-arms the timer. This
  test pins the published state through the stale deadline, not the cancel line.
  The spec's name `publishPreparedContent_cancelsPendingHideJob` would overstate
  what it proves, hence the name above.

  If `retryPlayback` from a `Content` state needs more stubs than these, add
  them in the test only and list them in For the reviewers.
- [x] Run `--tests '*PlayerVMPlaybackFlowTest' --tests '*PlayerVMOverlayAutoHideTest'`
  and the fast gate. Commit, e.g. "Cover the controls and panels on a media
  change in the player view model tests".

**Done when:** both tests are green, and T-P1's mutation reasoning is noted in
For the reviewers.

---

### Task 7: Label and model tests (T-L1, T-L2, T-L3, T-F1)

**Covers:** AC-5, AC-6, AC-7.
**Files:**
- Test: `T/model/PlayerUIMapperTest.kt`. Master's file is 73 lines. Its shared
  `context` mock (`:15-17`) stubs only `player_aspect_auto`, so build a separate
  mock in the new test.
- Test: `T/model/SubtitleLabelsTest.kt` (wt, 58 lines). Helpers: `labels(...)`
  `:10-18` and `sub(lang, forced)` `:20-21`.
- Create: `app/src/test/kotlin/com/kino/puber/data/api/models/SubtitleLinkTest.kt`.
  Use the style of `HistorySerializationTest.kt:14-19` (JUnit 5, a local `Json`).
- Read only:
  - `PlayerUIMapper.mapSubtitleTracks` (wt `PlayerUIMapper.kt:38-71`). It reads
    `context.resources.configuration.locales[0]`, `getString(player_subtitles_off)`,
    `getString(player_subtitle_ai_generated)` and `getString(player_subtitle_forced)`,
    plus `getString(player_subtitle_variant_label, label, ordinal)` and
    `getString(player_subtitle_unknown, position)`.
  - `subtitleLabels` (wt `SubtitleLabels.kt:17-40`). Unknown rows are numbered
    by 1-based API position (`:31`). Collisions are counted on the final label
    (`:35-39`).
  - String values: wt `values/player.xml:44-47`, wt `values-en/strings.xml:246-249`.
  - `SubtitleLink` (master `Models.kt:429-434`, plus `forced` from Task 1).
  - The client JSON is `Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }`
    (master `KinoPubApiClient.kt:122-126`).

**Consumes:** `subtitleLabels`, `PlayerUIMapper`, `SubtitleLink.forced`.
**Produces:** nothing new.

- [x] T-L1 `mapSubtitleTracks_namesLanguagesAiForcedUnknownAndVariants`, in
  `PlayerUIMapperTest`:
  - **Deviation from the spec:** the spec says `FakeResourceProvider`, but the
    mapper takes a `Context`, not a `ResourceProvider`. Use a `mockk<Context>`
    instead:
    - `resources.configuration.locales[0]` returns `Locale.ENGLISH`, via mocked
      `Resources`, `Configuration` and `LocaleList`
    - the three plain strings return `"Off"`, `"AI-generated"` and `"forced"`
    - the two format strings use `*anyVararg()` and answer with
      `String.format("%1\$s · variant %2\$d", ...)` and `"Subtitles %1\$d"`. With
      mockk, the vararg arrives as `args[1] as Array<*>`; adapt if mockk
      flattens it.
  - input: `rus`, `ai`, `eng` (forced), `" "`, `rus`
  - expect these labels:
    - `Off`
    - `Russian · variant 1`
    - `AI-generated`
    - `English · forced`
    - `Subtitles 4`
    - `Russian · variant 2`
  - expect indices `0..5`. Row 0 has `language = ""` and `url = ""`. Each API
    row keeps its `lang` and `url`.
  - `selectedSubtitleIndex = 0` is set by `ContentStateFactory.kt:39`, not by
    the mapper. It is out of this test.
- [x] T-L2 `composedLabel_usesTheSameSeparatorAsTheVariantString`, in
  `SubtitleLabelsTest`:
  - read the `player_subtitle_variant_label` value from
    `src/main/res/values/player.xml` and from `src/main/res/values-en/strings.xml`
  - the paths are relative to the module directory, which is the unit test
    working directory. If it differs, resolve them from `System.getProperty("user.dir")`.
  - extract the value with a regex on `<string name="player_subtitle_variant_label">(...)</string>`
  - use `String.format(value, label, ordinal)` as `variantLabel`
  - ru: `labels(sub("rus", true), sub("rus", true))` gives
    `["Русский · частичные · вариант 1", "Русский · частичные · вариант 2"]`
  - en: `Locale.ENGLISH` with qualifier `"forced"` gives
    `["Russian · forced · variant 1", "Russian · forced · variant 2"]`. Call
    `subtitleLabels` directly, because the helper hardcodes the ru strings.
- [x] T-L3 `ordinals_countFinalLabels_notRawLanguages`:
  `labels(sub("rus"), sub("ru"), sub("rus", forced = true))` gives
  `["Русский · вариант 1", "Русский · вариант 2", "Русский · частичные"]`.
- [x] T-F1 `SubtitleLinkTest`, with the client's `Json` settings. Decode:
  - `{"lang":"rus","url":"u"}`: `forced == null`
  - `{"lang":"rus","url":"u","forced":true}`: `true`
  - `{"lang":"rus","url":"u","forced":false}`: `false`
  - `{"lang":"rus","url":"u","forced":null,"unknown":1}`: `forced == null`, no
    exception
- [x] Run `--tests '*PlayerUIMapperTest' --tests '*SubtitleLabelsTest' --tests '*SubtitleLinkTest'`
  and the fast gate. Commit, e.g. "Cover subtitle labels through the mapper,
  the shared separator and the forced field".

**Done when:** the three classes are green, and T-L2 reads both resource files
(a separator change in either file turns it red).

---

### Task 8: Update the player preview's subtitle rows to the shipped label shapes

**Covers:** ux-designer note (track `ui`). This is a nit, not an AC.
**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerPreview.kt:36-42`
  (`previewSubtitleTracks`, the rows at `:37-41`). The same list is used at
  `:136` and as `.take(3)` at `:193`.
- `SubtitleTrackUIState(index, label, language, url)`:
  `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIModels.kt:27-32`.

**Consumes:** nothing.
**Produces:** nothing.

- [x] Replace the rows with what `subtitleLabels` produces in Russian for
  `rus`, `rus` (forced), `eng`, `eng`, `ai`:
  - `(0, "Выкл.", "", "")`
  - `(1, "Русский", "rus", ".../ru.vtt")`
  - `(2, "Русский · частичные", "rus", ".../ru-forced.vtt")`
  - `(3, "Английский · вариант 1", "eng", ".../en.vtt")`
  - `(4, "Английский · вариант 2", "eng", ".../en-sdh.vtt")`
  - `(5, "Созданные ИИ", "ai", ".../ai.vtt")`

  This is the ux "Content for the stand" list, with English written as the ru
  locale names it.
- [x] Run the fast gate. Commit, e.g. "Show the shipped subtitle label shapes in
  the player preview".

**Done when:** `PlayerPreview.kt` compiles. Its rows hold no `Русские` and no
`Созданы нейросетью`.

---

### Task 9: Full gate and the record

**Covers:** AC-9.
**Files:** this plan file (Implementation record only).

- [x] Run the full gate:
  `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. If
  detekt or lint fails, fix only findings in files this plan touched, in a
  separate commit named after the fix. Re-run until green.
- [x] Fill in the Implementation record:
  - commits as `be8395a4..HEAD` with SHAs, plus the Task 2 old to new mapping
  - deviations: at least the T-M2 recipe, the T-L1 `Context` mock and the
    T-P2 name
  - the outcomes of the mutation checks and the red-first runs
  - that the rebased port commit (Task 2) is the only commit carrying the two
    known red tests `resumeDialog_noHideTimerRunsWhileItIsUp` and
    `controlsTimerDuringExit_changesNothing`, as the accepted consequence of
    owner Q1, and that Task 3 turned them green
  - under For the reviewers: T-M1's known limit (a stray hide timer during the
    resume prompt is no longer observable; see Task 3)
- [x] `git add docs/superpowers/plans/2026-10-14-subtitle-upstream-port.md`
  and commit, e.g. "Record the subtitle-upstream-port implementation".
- [x] Check that `git status --short` shows only `?? replay_pid1700735.log`.

**Done when:** the full gate is green on HEAD, and the Implementation record
names every commit in `be8395a4..HEAD` except the one that adds it.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** `be8395a4..HEAD` (the commit adding this record follows them)

- a82a12c4 Add the subtitle-upstream-port spec
- 90e1fb35 Record gate 1 approval in the subtitle-upstream-port spec
- f1be246e Port upstream's first batch of subtitle fixes to the player (Task 1)
- 938b9aff Keep the overlay auto-hide tests' intent now that new media starts with the controls owned by the state machine (Task 3)
- 784db486 Drop a subtitle pick left by the previous media when the saved choice does not resolve (Task 4)
- 8461dfdb Check that new media also releases a held Episodes panel (Task 5)
- 60d95322 Cover the controls and panels on a media change in the player view model tests (Task 6)
- e34c0a22 Cover subtitle labels through the mapper, the shared separator and the forced field (Task 7)
- 7c557dad Show the shipped subtitle label shapes in the player preview (Task 8)
- eadf8ca3 Format the variant string with a fixed locale in the subtitle label test (Task 9, detekt ImplicitDefaultLocale in the T-L2 test)

Task 2 rebase, old to new SHA (no commit added; no conflicts arose, git resolved
`ControlsStateMachineTest.kt` and `PlayerVM.kt` automatically):

- 379ffd50 -> a82a12c4 (spec)
- 3eb98f96 -> 90e1fb35 (gate 1)
- 5663ca6f -> f1be246e (port; 5663ca6f was the Task 1 commit on the old base 7ad23247)

The rebased port commit f1be246e is the only commit carrying the two known red
tests `resumeDialog_noHideTimerRunsWhileItIsUp` and `controlsTimerDuringExit_changesNothing`
(accepted consequence of owner Q1, commit in place and then rebase). Task 3 turned them
green; every later commit ends on a green player suite.

Gates: after the rebase the player package ran 329 tests with exactly those two failing.
Final `check.full` (testDevDebugUnitTest, detektAll, lintDevDebug) is BUILD SUCCESSFUL.
The evidence audit passed on the directory (3 files) and on the markdown file.

**Deviations from the plan:**

- T-M2 uses the last-episode exit recipe from the plan (not the spec's HideControls/Back/ResetControlsTimer).
- T-L1 uses a `mockk<Context>` (with mocked Resources, Configuration, LocaleList) instead of `FakeResourceProvider`. mockk `answers` flattens varargs via `args.drop(1)`, handled for both flat and array shapes.
- T-P2 is named `publishPreparedContent_resumePromptAfterRetry_staysAloneThroughTheOldHideDeadline`.
- Extra commit eadf8ca3: the full gate's detekt flagged `String.format(...)` without a locale in my T-L2 test; fixed with `Locale.ROOT`.
- Red-first runs: the three Task 4 tests (T-D3a, T-D3b, rewritten ambiguous-language test) failed before the `VM` edit and passed after.

**Files touched outside the plan:** none.

**For the reviewers:**

- Mutation (a): deleting `if (closing) return@launch` in `scheduleControlsHide` turned `controlsTimerDuringExit_changesNothing` red (1 failure in `PlayerVMOverlayAutoHideTest`); reverted with `git checkout`, tree clean afterwards.
- Mutation (b), by reading: T-M1's first assertion (`controlsVisible == false` right after start, with the resume prompt) fails if `publishPreparedContent` were reverted to `updateViewState(PlayerViewState.Content(contentState))`, because the fixture content says `controlsVisible = true`.
- T-M1 known limit: with the controls hidden during the resume prompt, a stray hide timer armed while the prompt is up has no observable effect (and `resumeFromSavedPosition` cancels it via `scheduleControlsHide`). The observable half is kept: hidden with no focus during the prompt, and 5.9 s / 6.1 s after resume plus `ShowControls`.
- T-P2 limit: the `controlsHideJob?.cancel()` in `publishPreparedContent` is not observable on its own. The test pins the published state through the stale 6 s deadline.
- T-P1 is mutation-sensitive by reasoning only (not run): without `initialize`, the machine keeps Episodes open and Back would close it onto visible controls focused on `EpisodesButton`.
- T-M2 compares the whole `contentState` before/after (no unrelated async field interfered).
- T-L1 covers en through the mapper; ru stays at the pure-function level (`SubtitleLabelsTest`, T-L2 reading both real resource files).
- AC-2, AC-4, AC-6 (`SELECTION_FLAG_FORCED`) and AC-1 DEFAULT/FORCED manifest have no automated proof by owner decision Q2/Q3.
- `replay_pid1700735.log` stays untracked.

**Blocked:** nothing.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

### Readiness 1: subtitle-upstream-port

Judge: final-judge, mode readiness, round 1. Date: 2026-10-14.
Read: spec `docs/superpowers/specs/2026-10-14-subtitle-upstream-port.md` (approved, wt),
plan `docs/superpowers/plans/2026-10-14-subtitle-upstream-port.md` (wt, untracked),
`state.md` (Q1-Q6 answered), `test-design.md`, master be8395a4 via `git show`, and the
worktree at 3eb98f96 with its uncommitted diff.

**Verdict: NOT READY** (one ordering fix; everything else holds).

#### 1. AC coverage (plan task + Test design row)

| AC | Plan task(s) | Test design row(s) | Covered |
|----|--------------|--------------------|---------|
| AC-1 | 1 (code), 3 (D3) | T-D3a, T-D3b, rewritten ambiguous-language test; M1; DEFAULT/FORCED manifest UNTESTABLE (owner Q2) | yes |
| AC-2 | 1 (code) | UNTESTABLE at unit, optional M6 (owner Q2) | yes, as accepted |
| AC-3 | 1, 3 | existing `tracksUpdated_restores*`, `selectSubtitle_*`; M2, M3 | yes |
| AC-4 | 1 (code) | M2 record-only (owner Q3); matching internals UNTESTABLE | yes, as accepted |
| AC-5 | 1, 7 (T-L1) | `SubtitleLabelsTest` (4 cases), T-L1 | yes |
| AC-6 | 1, 7 (T-F1) | `forcedSubtitles_getAQualifier`, T-F1; `SELECTION_FLAG_FORCED` UNTESTABLE | yes |
| AC-7 | 1, 7 (T-L2, T-L3) | `rowsThatStillReadTheSame_getOrdinals`, T-L2, T-L3 | yes |
| AC-8 | 1, 4, 5, 6 | `initialize_*` (4), T-D7, `backPressed_onFreshMedia_*`, T-M1, T-P1, T-P2; M4, M7, M8 | yes |
| AC-9 | 2, 5, 9 | full gate, T-M1/T-M2 rewrites | yes |

##### The three replaced recipes

- **T-M2 (last-episode exit instead of HideControls/Back/ResetControlsTimer).** Accepted, and
  better than the spec's recipe. Verified: master `PlayerVM.kt:543-549` drops every action but
  Back while `closing`, so `ResetControlsTimer` cannot arm a timer during exit; and with the
  controls already hidden a firing timer is a no-op, so the spec recipe would pass vacuously.
  The new recipe starts the exit (`onPlaybackEnded`, `!hasNextEpisode`, no panel ->
  `exitPlayer()`, master `:1244-1255`) with the controls up and the t=0 timer pending; the
  mutation check (a) on `if (closing) return@launch` (master `:712`) makes it non-vacuous.
  Intent and AC-8/AC-9 coverage kept.
- **T-L1 (mocked `Context` instead of `FakeResourceProvider`).** Accepted. `PlayerUIMapper`
  takes a `Context` (wt `PlayerUIMapper.kt:40-58`), and master's `PlayerUIMapperTest.kt:15-17`
  already mocks `Context`. The en expected list was recomputed by hand from
  `SubtitleLabels.kt:17-40` and matches. The ru half of the spec's T-L1 is covered at the pure
  function level by `SubtitleLabelsTest` plus T-L2 (which reads both real resource files), so
  AC-5 keeps ru and en coverage.
- **T-P2 (renamed, stated limit).** Accepted. The `controlsHideJob?.cancel()` in
  `publishPreparedContent` (wt `PlayerVM.kt:363`) is not observable on its own; the test pins
  the published state through the old deadline after a `RetryPlayback` into a resume prompt,
  which is what AC-8 asks ("with a resume prompt the controls start hidden"). AC-8 also stays
  covered by T-M1, T-P1, the `initialize_*` tests and `backPressed_onFreshMedia_*`.

#### 2. Citation sample (all resolved against code, not summaries)

| Citation | Claimed | Found |
|----------|---------|-------|
| master `PlayerVM.kt:352`, `:357` | `updateViewState(Content(...))`, `if (resumeDialog == null) scheduleControlsHide()` | yes |
| master `PlayerVM.kt:517-541`, `:522`, `:538-540` | `restoreTrackPreferences`, local `content`, the `if (subtitleIndex >= 0)` block | yes |
| master `PlayerVM.kt:543-549`, `:707-719`, `:712` | closing guard in `onAction`; `scheduleControlsHide`; `if (closing) return@launch` | yes |
| master `PlayerVM.kt:739-743`, `:745-758`, `:760-769`, `:792` | `holdEpisodesPanel`, `onPanelClosed`, `applyControlsState`, `applySubtitleSelection` | yes |
| master `PlayerVM.kt:856`, `:867`, `:911`, `:915-928`, `:1290-1307`, `:1327-1332`, `:1811-1830`, `:1848` | restore-flag resets, `switchEpisode` (StartFromBeginning), `resumeFromSavedPosition`, `retryPlayback`, `exitPlayer` (no hide-job cancel), `CONTROLS_HIDE_DELAY_MS` | yes |
| master `ControlsStateMachine.kt:8-19`, `:80-89`, `:159-163` | `State` with `playbackEnded`/`episodesPanelHeld`, `handleBack`, `holdEpisodesPanel` | yes |
| master `AudioTrackPreferenceResolver.kt:24-37`, `SubtitleTrackIdentity.kt:7-12`, `PlaybackController.kt:161` | `findSubtitleTrackIndex`, `stableSubtitleKey`, `pendingSubtitleTrack` | yes |
| master `PlayerVMTestFixture.kt:75`, `:99`, `:106-107`, `:155`, `:157`, `:229-238`, `:256`, `:267` | `callbackSlot`, factory stub, subtitle pref stubs, `startedVM`, `contentState`, `testSubtitleTracks`, `controlsVisible = true`, `selectedSubtitleIndex = 0` | yes |
| master `PlayerVMTrackSelectionTest.kt:117`, `:138-149`, `:147` | audio `tracks`, ambiguous-language test, `verify(exactly = 0)` | yes |
| master `PlayerVMOverlayAutoHideTest.kt:41-49`, `:171-191`, `:256-270`, `:272-292` | `vmTest`, the two tests to rewrite, the last-episode pattern | yes |
| master `PlayerVMEpisodeFlowTest.kt:108-126`, `ControlsStateMachineTest.kt:15-18`, `PlayerUIMapperTest` 73 lines | SelectEpisode pattern, `setUp()`, file length | yes |
| master `ContentStateFactory.kt:31-33`, `:39` (in `vm/`) | controls fields from resume dialog, `selectedSubtitleIndex = 0` | yes |
| master `KinoPubApiClient.kt:122-126`, `Models.kt:429-434`, `HistorySerializationTest.kt:14-19` | client `Json`, `SubtitleLink`, local `Json` style | yes |
| master `PlayerPreview.kt:36-42`, `:136`, `:193`; `PlayerUIModels.kt:27-32`; `app/build.gradle.kts:51` + `:223` | preview rows, uses, `SubtitleTrackUIState`, `applicationId` + dev `.stage` | yes |
| wt `ControlsStateMachine.kt:38-50`, `PlayerVM.kt:361-368`, `:762-768` | `initialize`, `publishPreparedContent`, `withControlsState` | yes |
| wt `SubtitleLabels.kt:17-40`, `:31`, `:35-39`, `:72-81`; `player.xml:44-47`; `values-en/strings.xml:246-249` | labels function, unknown position, collisions, lazy ISO-3 map, strings | yes |
| wt `PlaybackController.kt:383-392`, `:611`, `:871-887`, `:889-902` | text disabled at build, `SELECTION_FLAG_FORCED`, re-enable on pick, no positional fallback | yes |
| wt `ControlsStateMachineTest.kt:20-63`, `:41-53`; `PlayerVMPlaybackFlowTest.kt:59-69`; `SubtitleLabelsTest.kt:10-18`, `:20-21` | initialize section, reset test, fresh-media Back test, helpers | yes |

Task 1's file list equals `git status --short` in the worktree (10 modified, 2 new sources, the
evidence file and directory with 3 PNGs; `replay_pid1700735.log` and the plan excluded).
`tools/mobile-evidence-audit.sh` takes `<directory> [package]`, as the plan says.

#### 3. Build state and ordering

- Fast gate stays green after every task as planned.
- **Problem:** after Task 2 the unit suite has two known red tests in
  `PlayerVMOverlayAutoHideTest` (`resumeDialog_noHideTimerRunsWhileItIsUp`,
  `controlsTimerDuringExit_changesNothing`), and they stay red through Tasks 3 and 4 because
  their fix is Task 5. Task 5 consumes only `publishPreparedContent` (Tasks 1-2); it does not
  depend on Tasks 3 or 4. So Tasks 3 and 4 each end with a commit on a red suite for no
  dependency reason. The port commit itself being red is a consequence of owner Q1
  (commit in place, then rebase) and is acceptable if named.
- Otherwise the order follows dependencies: 1 -> 2 -> {3, 4, 5, 6, 7, 8} -> 9; Task 6 needs only
  Task 2.

#### 4. Scope and open questions

- No task touches side-loading, the merge model or restore keys (Out of scope runs
  subtitle-sideload-merge, subtitle-restore-model). D3 is inside AC-1 and was taken by the owner
  (Q4). Task 8 (preview rows) is the ux-designer nit named in the spec; its `Английский` instead
  of the spec's `English` stand content is stated and matches what the code renders in ru.
- Blocking questions Q3 and Q4 are answered; Q1, Q2, Q5, Q6 answered too.

#### Must fix

1. Move Task 5 (rewrite of the two master overlay-autohide tests) to run directly after Task 2,
   before the D3 and D7 tasks, so no task after the rebase ends with a commit on a red unit
   suite. In Task 2's Done-when (or Global constraints), name the rebased port commit as the only
   commit with the two known red tests, as the accepted consequence of Q1, and have the
   Implementation record state it.

#### Non-blocking notes for the implementer and reviewers

- T-M1: with the controls now hidden during the prompt, a stray hide timer armed during the
  prompt has no observable effect (and `resumeFromSavedPosition` cancels it via
  `scheduleControlsHide`). The "no timer while the prompt is up" half of the original intent is
  therefore no longer observable, like T-P2's limit. Say so under For the reviewers; the
  observable half (hidden with no focus during the prompt, 5.9 s / 6.1 s after resume plus
  `ShowControls`) is kept and is mutation-sensitive to reverting `publishPreparedContent`.
- T-L1 covers en only through the mapper; ru stays at the pure-function level (SubtitleLabelsTest,
  T-L2). Fine for AC-5, worth one line in the record.
- AC-2, AC-4, AC-6 (`SELECTION_FLAG_FORCED`) and AC-1's DEFAULT/FORCED manifest case stay without
  automated proof by owner decision Q2/Q3; the gate will carry them as device-only or
  NOT VERIFIED unless the smoke shows them.

### Readiness 2: subtitle-upstream-port

Judge: final-judge, mode readiness, round 2. Date: 2026-10-14.
Read: plan `docs/superpowers/plans/2026-10-14-subtitle-upstream-port.md` (wt, untracked, 571
lines), `readiness-1.md`, master be8395a4 via `git show`, and the worktree at 3eb98f96 (unchanged
since round 1: same uncommitted file set, `replay_pid1700735.log` still untracked).

**Verdict: READY**

#### 1. Round 1 must-fix

Must-fix 1 (move the D8 rewrite directly after the rebase; name the rebased port commit as the
only commit with the two known red tests): **resolved**.

- Task order is now 1 commit in place, 2 rebase, **3 D8** (rewrite of
  `resumeDialog_noHideTimerRunsWhileItIsUp` and `controlsTimerDuringExit_changesNothing`),
  4 D3, 5 D7, 6 T-P1/T-P2, 7 labels, 8 preview, 9 full gate and record.
- Task 2's run step says "Task 3 rewrites both, directly after this task"; its Done-when names the
  rebased port commit as the only commit whose unit suite carries the two known red tests, as the
  accepted consequence of owner Q1, and says every later task ends on a green suite.
- Task 3's Done-when requires the whole player test package green.
- Task 9 requires the Implementation record to state the red-commit fact and that Task 3 turned
  them green, and to carry T-M1's known limit under For the reviewers (round 1 note, now also
  written into Task 3).

#### 2. Regression check after the renumbering

- Every `Task N` cross-reference in the plan (lines 22, 27, 31, 33, 115, 134, 138, 145-146, 332,
  432, 536, 540-545) points at the right task under the new numbers. `test-design.md` and
  `state.md` hold no task-number references.
- Dependencies hold in the new order: Task 3 consumes `publishPreparedContent` (present after
  Task 2); Task 4 (D3) and Task 5 (D7) do not depend on Task 3; Task 6 adds T-P2 to
  `PlayerVMOverlayAutoHideTest` after Task 3 rewrote that file, so no edit collision.
- AC coverage is unchanged from round 1 with the new numbers: AC-1 Tasks 1, 4; AC-3 Tasks 1, 4;
  AC-5/6/7 Tasks 1, 7; AC-8 Tasks 1, 3, 5, 6; AC-9 Tasks 2, 3, 9; AC-2, AC-4 Task 1 (owner Q2/Q3).
- Scope unchanged: no task touches side-loading, the merge model or restore keys.

#### 3. Citation re-sample (master be8395a4)

| Citation | Claimed | Found |
|----------|---------|-------|
| `PlayerVM.kt:543-549` | `onAction` drops all but Back while `closing` | yes |
| `PlayerVM.kt:707-719`, `:712` | `scheduleControlsHide`, `if (closing) return@launch` | yes |
| `PlayerVM.kt:1244-1255` | `onPlaybackEnded`, `!hasNextEpisode` + no panel -> `exitPlayer()` | yes |
| `PlayerVM.kt:365-368`, `:926`, `:1327-1332` | `buildResumeDialog`, `StartFromBeginning` in `switchEpisode`, `retryPlayback` without hide-job cancel | yes |
| `PlayerVMOverlayAutoHideTest.kt:41-49`, `:171`, `:256`, `:272-281` | `vmTest`, the two tests to rewrite, last-episode pattern with `CompletableDeferred` save | yes |

#### Must fix

None.

#### Notes carried from round 1 (non-blocking)

- AC-2, AC-4, AC-6 (`SELECTION_FLAG_FORCED`) and AC-1's DEFAULT/FORCED manifest case have no
  automated proof by owner decision Q2/Q3; the gate will carry them as device-only or
  NOT VERIFIED unless the smoke shows them.
- T-L1 covers en through the mapper; ru stays at the pure-function level (SubtitleLabelsTest, T-L2).
