# Design (architect): subtitle-upstream-port

Retroactive design. The implementation already sits uncommitted in the worktree
`/home/user1/orca/workspaces/Puber/subtitle-player-fixes` (base 7ad23247). This document assesses
that diff against master be8395a4, which it will be rebased onto, and names what must change.
Citations: `master:` = main checkout at be8395a4; `wt:` = the worktree with the uncommitted diff.
Every citation below was read.

## Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | How is Off by default enforced (AC-1, AC-2)? | Keep the diff: `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)` on the `DefaultTrackSelector` built in every `prepare()` (wt:`PlaybackController.kt:383-392`). | `prepare()` builds a new selector on every start and every rebuild (buffer preset, DNS: master:`PlayerVM.kt:856`, `:867` call `initializePlayer`), so Off holds for both. The only path that enables text is `applySubtitleTrackSelection` (wt:`PlaybackController.kt:871-887`, `setTrackTypeDisabled(TEXT, false)` at :879), reached only by an explicit or restored pick. Off clears it again at wt:`:546-556`. |
| D2 | Positional match (AC-4) | Keep the diff: `findTextTrackBySubtitleIndex` deleted. Matching is url, then stable key, then unambiguous language (wt:`PlaybackController.kt:889-902`). With no match, text is enabled with `setPreferredTextLanguage` only and no override. | Owner decision Q3 (2026-10-13): ship before subtitle-sideload-merge, AC-4 as written. |
| D3 | A pick from the previous media outliving it (AC-1, AC-3) | **Change required.** In `restoreTrackPreferences` (master:`PlayerVM.kt:517-541`), when `findSubtitleTrackIndex` returns no match, re-apply the picker's current row: `applySubtitleSelection(content.selectedSubtitleIndex, persist = false)`. | `pendingSubtitleTrack` (master:`PlaybackController.kt:161`) is not cleared by `release()` (master:`:558-574`) or `prepare()`, and `onTracksChanged` re-applies it (master:`:234-237`, `:862-864`). Take a next episode whose saved choice does not resolve: no URL match, and the language is ambiguous or missing (master:`AudioTrackPreferenceResolver.kt:24-37`). The VM then calls nothing (master:`PlayerVM.kt:538-540`), and the previous episode's pick turns text back on under a picker that reads Off (`selectedSubtitleIndex = 0`, master:`ContentStateFactory.kt:39`). For new media the current row is 0 (Off), so the controller drops the stale pick. On a rebuild it is the viewer's live pick, so nothing changes. One line in the VM, nothing in the controller. |
| D4 | Language, AI, forced and collision labels (AC-5..AC-7) | Keep `SubtitleLabels.kt` (wt, untracked) as a pure top-level function. Strings are injected (`aiGeneratedLabel`, `forcedQualifier`, `variantLabel`, `unknownLabel`), and `PlayerUIMapper.mapSubtitleTracks` (wt:`PlayerUIMapper.kt:38-71`) calls it. | It is pure Kotlin with no Android types, so JVM tests can cover it with no Context (`SubtitleLabelsTest.kt`). Collisions are counted on the final label, which AC-7 requires. |
| D5 | `forced` field (AC-6) | Keep `val forced: Boolean? = null` on `SubtitleLink` (master:`Models.kt:429-434`) and `SELECTION_FLAG_FORCED` on the side-loaded config (wt:`PlaybackController.kt:611`). | `KinoPubApiClient` JSON has `ignoreUnknownKeys = true` (master:`KinoPubApiClient.kt:123`). A missing field takes the default `null`. |
| D6 | Back on fresh media (AC-8) | Keep `ControlsStateMachine.initialize(resumeDialogVisible)` (wt:`ControlsStateMachine.kt:38-50`) and `PlayerVM.publishPreparedContent` (wt:`PlayerVM.kt:361-368`). The latter cancels the hide job, resets the machine and publishes content carrying the machine's controls fields through `withControlsState` (wt:`PlayerVM.kt:762-768`). | Today the content says the controls are visible (master:`ContentStateFactory.kt:31-33`) while the machine still holds its initial or previous state (master:`ControlsStateMachine.kt:8-19`). `handleBack` (master:`:80-89`) then exits instead of hiding. With this change the machine is the one source of truth. |
| D7 | Master's `episodesPanelHeld` and `playbackEnded` under `initialize` | No code change. `initialize` builds a fresh `State(...)`, so both fall back to their defaults (`false`, master:`ControlsStateMachine.kt:17-18`). Add one assertion to the existing `initialize_dropsWhatThePreviousMediaLeft` test: `holdEpisodesPanel(true)` before `initialize`, then `assertFalse(state.episodesPanelHeld)`. | The field is new on master. Without the assertion, nothing pins the reset. |
| D8 | Master tests broken by D6 after the rebase (AC-9) | **Change required** in two master tests, described under "Must change". | Both encode the old disagreement between content and machine that AC-8 removes. |
| D9 | Shape for the later `SubtitleLabeler` replacement (subtitle-sideload-merge Q2) | No reshaping now. Keep the single entry point `subtitleLabels(...)` with injected strings, called only from `PlayerUIMapper`. Keep the four string ids stable. Do not add callers of `canonicalSubtitleLanguage` / `subtitleLanguageDisplayName` outside the file. | The sideload run plans to keep upstream's `SubtitleLabeler`/`SubtitleLanguage`, fed with this batch's strings and its ISO-3 rule (sideload brief, Q2 and AC-8). One call site and stable string ids make that a swap inside `mapSubtitleTracks`. `SubtitleLabelsTest` cases double as the parity spec for the replacement. |
| D10 | Commit hygiene | Commit everything except `replay_pid1700735.log`. Run `tools/mobile-evidence-audit.sh` on `docs/superpowers/evidence/2026-10-10-subtitle-upstream-port/` before committing it. The evidence is from the old base and is kept as history. The post-rebase smoke gets a new evidence file. | Owner decisions Q1 and Q2. AGENTS.md evidence rule. |

## Changes by layer

### API / repository
- `app/src/main/java/com/kino/puber/data/api/models/Models.kt:429-434` (`SubtitleLink`): adds `forced: Boolean? = null`. In the diff already. Master leaves this file alone, so the rebase is clean.

### ViewModel state and actions
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt`: adds `initialize(resumeDialogVisible)` between `lastPanelOpener` (master:`:37`) and `showControls` (master:`:38`). Master leaves that region alone. It resets `state` to `State(controlsVisible = !resumeDialogVisible, focusTarget = Buttons unless resume)` and `lastPanelOpener = Buttons`. In the diff already.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt`:
  - `preparePlayback`: `updateViewState(PlayerViewState.Content(contentState))` at master:`:352` becomes `publishPreparedContent(contentState)`. The `scheduleControlsHide()` at master:`:357` stays, so the 6 s auto-hide from master (`CONTROLS_HIDE_DELAY_MS`, master:`:1848`) still runs for no-resume starts. In the diff already.
  - `applyControlsState` (master:`:760-769`) goes through the new `withControlsState` extension. In the diff already. Master's new `holdEpisodesPanel` (master:`:739-743`) and `onPanelClosed` sit between the two hunks, so expect a clean merge or a trivial context conflict.
  - **New (D3):** in `restoreTrackPreferences` (master:`:517-541`), the `if (subtitleIndex >= 0)` at master:`:538-540` gets an else branch that re-applies the current row. Equivalent form: `applySubtitleSelection(subtitleIndex.takeIf { it >= 0 } ?: content.selectedSubtitleIndex, persist = false)`.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` (playback, owned by the VM layer). Master leaves this file alone:
  - selector built text-disabled (wt:`:383-392`; master:`:383-389`).
  - `SELECTION_FLAG_FORCED` on side-loaded configs (wt:`:611`; master `buildMediaItem` at `:599-614`).
  - positional fallback removed (master:`:893`, `:899-912` deleted).

### Screen and DI module
- None. No Koin or screen wiring changes. `ControlsStateMachine` is still constructed inside the VM (master:`PlayerVM.kt:173`).

### Compose content
- None. `PlayerSettingsPanel` keeps rendering `SubtitleTrackUIState.label`. The visual delta (label wording, Off default, controls on Back) belongs to the ux-designer, not this document.

### Resources
- `app/src/main/res/values/player.xml:44` (ru) and `app/src/main/res/values-en/strings.xml:246` (en): after `player_subtitle_variant_label` the diff adds `player_subtitle_ai_generated`, `player_subtitle_forced` and `player_subtitle_unknown` (`%1$d`). In the diff already. Master leaves both files alone.

### Model (UI mapping)
- `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:35-70` (master `mapSubtitleTracks`): the inline duplicate-language block (master:`:44-67`) is replaced with a call to `subtitleLabels(...)`. Master's only change here is `formatTime` and the removed `java.util.Locale` import (master diff at the top and around `:210`). The new code needs no `Locale` import: it uses `context.resources.configuration.locales[0]`, which is API 24, and minSdk is 24. No overlap.
- New `app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLabels.kt` (wt, 81 lines).

### Tests
Already in the diff:
- `ControlsStateMachineTest` adds 4 `initialize` tests.
- `PlayerVMPlaybackFlowTest` adds `backPressed_onFreshMedia_hidesTheControlsInsteadOfLeaving`. Its Back tests now press Back twice, or send `HideControls` first.
- `PlayerVMMutationAndResultTest` now presses Back twice in 4 tests.
- New `SubtitleLabelsTest` (6 tests).

Master leaves both PlayerVM test files alone. Master rewrote `ControlsStateMachineTest`, so expect an adjacency conflict there: keep both sides.

## Must change in the existing diff (for correctness or to fit master)

1. **D3, stale subtitle pick across media.** Production change in `PlayerVM.restoreTrackPreferences` (master:`PlayerVM.kt:538-540`). Test changes:
   - The existing `tracksUpdated_doesNotRestoreAmbiguousSubtitleLanguage_whenUrlIsMissing` (master:`PlayerVMTrackSelectionTest.kt:138-149`) asserts `selectSubtitle` is called exactly 0 times. It must now assert `selectSubtitle(testSubtitleTracks[0])` (the Off row) and keep `selectedSubtitleIndex == 0` and no `saveTrackPreferences`.
   - Add a VM test. Pick a row, start the next media where the restore finds no match, and check that the Off row reaches the controller.
2. **Master test `resumeDialog_noHideTimerRunsWhileItIsUp`** (master:`PlayerVMOverlayAutoHideTest.kt:171-191`) will fail after the rebase. It asserts `controlsVisible == true` while the resume prompt is up (`:182`) and again 5.9 s after `ResumeFromPosition` (`:186`). The fixture content has `controlsVisible = true` (master:`PlayerVMTestFixture.kt:256`). The real factory sets it false when a prompt exists (master:`ContentStateFactory.kt:31`). AC-8 now makes the VM publish hidden. Keep the test's intent, "no hide timer runs while the prompt is up":
   - assert the controls are hidden with no focus target during the prompt, unchanged after 60 s;
   - after `ResumeFromPosition`, show the controls (`ShowControls(Buttons)`) and keep the 5.9 s / 6.1 s pair.
   - This also covers the resume half of AC-8 at VM level.
3. **Master test `controlsTimerDuringExit_changesNothing`** (master:`PlayerVMOverlayAutoHideTest.kt:256-270`) will fail after the rebase:
   - What breaks: the first Back now hides the controls instead of exiting, so `assertTrue(controlsVisible)` at `:267` fails.
   - Keep the intent, "a hide timer firing during exit changes nothing": `HideControls` (or a first Back), Back to start the exit with the save held, `ResetControlsTimer` to arm a timer, then `advanceTimeBy(6_100)` and assert the content equals the snapshot taken before.
4. **`ControlsStateMachineTest.initialize_dropsWhatThePreviousMediaLeft`**: add the `episodesPanelHeld` reset assertion (D7).
5. **Do not commit** `replay_pid1700735.log`. Audit the evidence directory before committing it (D10).

Every other master player test was checked against D6. The tests that open a panel, end playback or press OK before Back already start from a machine state that the panel, ended or OK path itself sets, so the initial state does not reach them (master:`PlayerVMOverlayAutoHideTest.kt:161-169`, `:272-292`, `:350-365`, `:396-431`; master:`PlayerControlsInteractionTest.kt:42-76`, `:148-170`). The full suite after the rebase is still the arbiter (AC-9).

## Alternatives rejected

- Keep the positional fallback only for non-HLS sources: AC-4 forbids it, and the sideload run replaces matching wholesale.
- Clear `pendingSubtitleTrack` in `PlaybackController.prepare()`: that would also drop a live pick on a buffer or DNS rebuild (AC-3), and the controller cannot tell new media from a rebuild.
- Clear it in the controller when its URL is absent from the new subtitle list: it works, but the controller would be deciding picker semantics, and the controller has no unit tests.
- Make `ContentStateFactory` stop writing the controls fields and read them from the machine: a wider change for the same effect.
- Emit `PanelClosed` from `initialize`: it would re-run `onPanelClosed`, which handles `exitWhenPanelCloses` and the next-episode check (master:`PlayerVM.kt:745-758`), on a media switch.
- Port upstream's `SubtitleLabeler` now: that belongs to subtitle-sideload-merge (its Q2). Labels for API rows alone do not need it.
- Build the ISO-3 map eagerly at app start: it puts a cost on startup for a screen that may never open.

## Risks (highest first)

1. **Correctness, API rows that do nothing on HLS (accepted, Q3).** With no url, stable-key or unambiguous-language match, no override is set (wt:`PlaybackController.kt:889-902`), and text is enabled with only `setPreferredTextLanguage(track.language)` (wt:`:880`).
   - `findUnambiguousTextTrackByLanguage` compares `format.language == language` raw (master:`:914-929`). Media3 normalizes `Format.language`, so an API `rus` may never equal the manifest's code. The language match may then almost never hit on HLS, and the picked row shows nothing.
   - The sideload run owns the fix. The smoke should record what a picked API row does on an HLS title.
2. **Focus and D-pad: Back and controls ownership on media switch.** `initialize` puts focus on `Buttons` and closes any panel. A switch from the Episodes row now lands on the controls with focus on Buttons, not on `EpisodesButton`. That is consistent with the content master already published (master:`ContentStateFactory.kt:32-33`), but the machine and the content now agree, so it is visible to the user. It lives in wt:`ControlsStateMachine.kt:38-50` and wt:`PlayerVM.kt:361-368`. The re-smoke of Back covers it.
3. **Lifecycle, stale subtitle pick across media.** Fixed by D3. If D3 is not taken, AC-1 fails for a next media whose saved choice does not resolve. Location: master:`PlaybackController.kt:161`, `:234-237`.
4. **Concurrency, hide job against `publishPreparedContent`.** The job is cancelled before `initialize` (wt:`PlayerVM.kt:363`), and the job body exits when `closing` (master:`PlayerVM.kt:711`). Both run on the VM's main scope, so no interleaving is possible inside the call. `exitWhenPanelCloses` is not reset by `initialize`. That is unchanged behaviour: it is cleared on resume (master:`PlayerVM.kt:702-705`).
5. **Data shape, `SELECTION_FLAG_FORCED`.** Text is disabled at selector build, so the flag only matters once the viewer enables text without an override. Media3 may then prefer the forced side-loaded track over the preferred-language full track. It lives in wt:`PlaybackController.kt:611`. Only progressive sources keep side-loaded configs; per the sideload research, HLS drops them.
6. **Performance, ISO-3 locale map.** `Locale.getAvailableLocales()` plus `isO3Language` per locale is built lazily on the first `mapSubtitleTracks` call, on the main dispatcher (`ContentStateFactory.build` is called from `preparePlayback`, master:`PlayerVM.kt:339`). It lives in wt:`SubtitleLabels.kt:72-81` and is unmeasured on TV hardware. Android ICU may also name fewer languages than the JVM used in unit tests; unresolved codes fall back to the uppercase code (AC-5).
7. **Rollout, rebase conflicts.** Expected only in `ControlsStateMachineTest` (adjacency) and maybe at `PlayerVM.applyControlsState` (context). The two master tests in "Must change" items 2 and 3 fail at runtime, not at merge time.
8. **Baseline profile.** No new startup path, so no regeneration is needed.

## Open questions

1. D3 adds a production line beyond the ported diff, to keep AC-1 true for the next media. Take it in this run? Recommended: yes. It is one line plus one test, it lives inside AC-1's scope, and the alternative is a known Off-picker-with-text defect. Blocks: no.
2. Rewriting the two master overlay-autohide tests (items 2 and 3) changes their assertions, not their intent. Is that acceptable as part of AC-9? Recommended: yes, the tests encoded the disagreement between content and machine that AC-8 removes. Blocks: no.
