# Readiness 1: subtitle-upstream-port

Judge: final-judge, mode readiness, round 1. Date: 2026-10-14.
Read: spec `docs/superpowers/specs/2026-10-14-subtitle-upstream-port.md` (approved, wt),
plan `docs/superpowers/plans/2026-10-14-subtitle-upstream-port.md` (wt, untracked),
`state.md` (Q1-Q6 answered), `test-design.md`, master be8395a4 via `git show`, and the
worktree at 3eb98f96 with its uncommitted diff.

**Verdict: NOT READY** (one ordering fix; everything else holds).

## 1. AC coverage (plan task + Test design row)

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

### The three replaced recipes

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

## 2. Citation sample (all resolved against code, not summaries)

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

## 3. Build state and ordering

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

## 4. Scope and open questions

- No task touches side-loading, the merge model or restore keys (Out of scope runs
  subtitle-sideload-merge, subtitle-restore-model). D3 is inside AC-1 and was taken by the owner
  (Q4). Task 8 (preview rows) is the ux-designer nit named in the spec; its `Английский` instead
  of the spec's `English` stand content is stated and matches what the code renders in ru.
- Blocking questions Q3 and Q4 are answered; Q1, Q2, Q5, Q6 answered too.

## Must fix

1. Move Task 5 (rewrite of the two master overlay-autohide tests) to run directly after Task 2,
   before the D3 and D7 tasks, so no task after the rebase ends with a commit on a red unit
   suite. In Task 2's Done-when (or Global constraints), name the rebased port commit as the only
   commit with the two known red tests, as the accepted consequence of Q1, and have the
   Implementation record state it.

## Non-blocking notes for the implementer and reviewers

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
