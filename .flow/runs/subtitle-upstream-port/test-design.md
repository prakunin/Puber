# Test design: subtitle-upstream-port

Base be8395a4 (after the rebase). Worktree `/home/user1/orca/workspaces/Puber/subtitle-player-fixes`.
Levels: `unit` (JUnit 5, `MainDispatcherExtension`, `FakeResourceProvider`, virtual time),
`device` (qa-device smoke on an emulator, evidence file), no `instrumented` tests.
Gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.

Key constraint: `PlaybackController` (AC-1, AC-2, AC-4 internals) has no unit tests and wraps ExoPlayer, so
its behaviour is covered by the VM contract (what the VM asks the controller to do) plus the device smoke.
Owner decisions Q2: DEFAULT/FORCED manifest and Off across rebuild are unit-only or accepted-untested; no device re-pass for them.

## Risk profile

| # | Risk | P | I | Score | Where it fails | Test / handling |
|---|---|---|---|---|---|---|
| R1 | Stale pick from previous media re-enables text under an Off picker (D3) | 2 | 3 | 6 | VM unit | T-D3a, T-D3b (new, missing) |
| R2 | Back on fresh media leaves the player instead of hiding controls; panel / playbackEnded / episodesPanelHeld leak from previous media | 3 | 3 | 9 | machine + VM unit, device | `initialize_*` tests, `backPressed_onFreshMedia_*`, T-D7 (missing assertion), smoke M4 |
| R3 | The two rewritten master overlay-autohide tests lose their intent or pass vacuously | 2 | 3 | 6 | VM unit | T-M1, T-M2 (to rewrite); each must be red against the un-rewritten production change if intent is broken |
| R4 | Text disabled at selector build and never re-enabled: subtitles never appear | 2 | 3 | 6 | controller (no unit tests), device | M2 (select a row, text shows), M3 (restored choice); VM contract `selectSubtitle_*` |
| R5 | API row does nothing on HLS (positional fallback gone, raw `format.language` compare) | 3 | 2 | 6 | device only | Accepted (Q3). M2 records what a picked row does on HLS. Not a pass/fail |
| R6 | Label wording wrong or colliding (AC-5..AC-7), separator drift between code and `player_subtitle_variant_label` | 2 | 2 | 4 | unit | `SubtitleLabelsTest` (6) + missing T-L1..T-L3 |
| R7 | Android ICU names fewer languages than the JVM; ISO-3 map cost on TV | 2 | 2 | 4 | device only | M3 records fallbacks; cost stays `not measured` (no perf track) |
| R8 | `SELECTION_FLAG_FORCED` changes Media3 default selection for side-loaded configs | 1 | 2 | 2 | device only | Accepted with note; text is off until chosen |
| R9 | Rebase conflicts in `ControlsStateMachineTest` / `PlayerVM.applyControlsState` | 2 | 2 | 4 | build + suite | AC-9 full suite is the arbiter |
| R10 | `forced` field breaks deserialization when absent | 1 | 3 | 3 | unit | T-F1 (missing) |
| R11 | Long label wraps/ellipsizes or widens the panel past 420 dp | 2 | 1 | 2 | device only | M5, accepted if unmeasured for forced/variant rows |

## Traceability

Status: EXISTS = in the worktree diff; REWRITE = master test to change; MISSING = to write.

| AC | Level | Priority | Test | Proves | Status |
|---|---|---|---|---|---|
| AC-1 | unit | P0 | T-D3a `tracksUpdated_reappliesOffRow_whenSavedSubtitleDoesNotResolve` in `PlayerVMTrackSelectionTest` | VM sends the current (Off) row to the controller when the restore finds no match, so a stale pick is dropped; no `saveTrackPreferences` | MISSING |
| AC-1 | unit | P0 | T-D3b `tracksUpdated_nextMediaWithUnresolvedChoice_sendsOffRow`: pick row 2, start next media with an ambiguous / absent saved choice, assert `selectSubtitle(testSubtitleTracks[0])` and `selectedSubtitleIndex == 0` | The lifecycle defect behind R1 | MISSING |
| AC-1 | unit | P0 | Rewrite `tracksUpdated_doesNotRestoreAmbiguousSubtitleLanguage_whenUrlIsMissing` (`PlayerVMTrackSelectionTest:139-149`): replace `verify(exactly = 0)` with `verify(exactly = 1) { selectSubtitle(testSubtitleTracks[0]) }`, keep index 0 and no save | Same as T-D3a at the old test's fixture | REWRITE |
| AC-1 | device | P1 | M1 (Off default, no text in first frame) | `setTrackTypeDisabled(TEXT, true)` effective on real Media3 | MISSING (smoke) |
| AC-1 (DEFAULT/FORCED manifest) | - | - | UNTESTABLE as a regression (see below) | | |
| AC-2 | device/unit | P2 | Not reproducible by unit; optional smoke M6 (change buffer preset in settings during playback, Off stays) | Selector rebuilt text-disabled | MISSING (optional) |
| AC-3 | unit | P0 | Existing `tracksUpdated_restoresPreferredSubtitleByUrl_beforeLanguage`, `..ByStableUrl_whenSignedUrlChanges`, `selectSubtitle_updatesStateAndDelegates`, `tracksUpdated_restoreDoesNotRewritePreferencesFromIntermediateState` | VM routes explicit and restored picks to `selectSubtitle` (the path that re-enables text) | EXISTS (master) |
| AC-3 | device | P0 | M2 select a row, M3 restored choice on next media | Controller re-enables text on a pick | evidence on old base only, redo |
| AC-4 | device | P1 | M2 step on an HLS title: pick a row, observe whether text shows; record, no failure if nothing shows | No positional match; no crash with no override | MISSING (smoke). Controller matching helpers are private; no unit test (see UNTESTABLE) |
| AC-5 | unit | P0 | `SubtitleLabelsTest.threeLetterCodes_readAsLanguageNames`, `languageNames_followTheDisplayLocale`, `aiGeneratedSubtitles_getTheirOwnName`, `unresolvableCodes_fallBackToTheCode_andMissingOnesToAPosition` | rus/eng names titlecased, ai, uppercase code, `Subtitles N` | EXISTS |
| AC-5 | unit | P1 | T-L1 `PlayerUIMapperTest.mapSubtitleTracks_usesFakeResourceProviderStrings_forAiForcedVariantUnknown`: through `PlayerUIMapper.mapSubtitleTracks`, ru and en strings from `FakeResourceProvider`; Off row first; `selectedIndex` 0 by default | Public mapper wiring, not just the pure function | MISSING |
| AC-6 | unit | P0 | `SubtitleLabelsTest.forcedSubtitles_getAQualifier` | Qualifier on the label | EXISTS |
| AC-6 | unit | P1 | T-F1 `SubtitleLinkTest.missingForcedField_parsesAsNull`: decode JSON with the app's `Json { ignoreUnknownKeys = true }` without `forced`, with `true`, with `false` | Deserialization stays intact | MISSING |
| AC-6 | device | P2 | `SELECTION_FLAG_FORCED` on the media item: controller private, not testable (see UNTESTABLE) | | |
| AC-7 | unit | P0 | `SubtitleLabelsTest.rowsThatStillReadTheSame_getOrdinals` | Ordinals per final label | EXISTS |
| AC-7 | unit | P1 | T-L2 `composedLabel_usesTheSameSeparatorAsVariantString`: forced + collision yields `Русский · частичные · вариант 2` and the separator equals the ` · ` in `player_subtitle_variant_label` (read both locales' strings from the resource file in the test, or assert on the composed result via `FakeResourceProvider`) | UX separator rhythm (ux-designer note) | MISSING |
| AC-7 | unit | P2 | T-L3 ordinals per label not per raw language: `rus` + `ru` collide, `rus` forced does not | Counting on the final label | MISSING |
| AC-8 | unit | P0 | `ControlsStateMachineTest.initialize_withoutResumeDialog_showsControlsOnButtons`, `initialize_withResumeDialog_keepsControlsHidden`, `initialize_thenBack_hidesControlsInsteadOfExiting` | Machine state after initialize | EXISTS |
| AC-8 | unit | P0 | `ControlsStateMachineTest.initialize_dropsWhatThePreviousMediaLeft` plus T-D7: `holdEpisodesPanel(true)` before `initialize`, then `assertFalse(episodesPanelHeld)` | Reset of panel, playbackEnded and master's `episodesPanelHeld` | EXISTS, assertion MISSING |
| AC-8 | unit | P0 | `PlayerVMPlaybackFlowTest.backPressed_onFreshMedia_hidesTheControlsInsteadOfLeaving` | First Back hides controls, no exit, playback continues | EXISTS |
| AC-8 | unit | P0 | T-M1 rewrite `resumeDialog_noHideTimerRunsWhileItIsUp` (`PlayerVMOverlayAutoHideTest:171-191`): with the prompt controls hidden and no focus target, unchanged after 60 s; after `ResumeFromPosition` send `ShowControls(Buttons)`, visible at 5.9 s, hidden at 6.1 s | Resume half of AC-8 plus the original intent (no timer while the prompt is up) | REWRITE |
| AC-8 | unit | P0 | T-M2 rewrite `controlsTimerDuringExit_changesNothing` (`:256-270`): `HideControls`, Back to start exit with the save held, `ResetControlsTimer`, `advanceTimeBy(6_100)`, content equals the snapshot taken before | Original intent (a timer firing during exit changes nothing) | REWRITE |
| AC-8 | unit | P1 | T-P1 `publishPreparedContent_closesPanelLeftByPreviousMedia`: open a panel, switch media, assert panel closed and focus `Buttons` (owner Q6) | Panel closes on media change as upstream | MISSING |
| AC-8 | unit | P1 | T-P2 `publishPreparedContent_cancelsPendingHideJob`: arm the hide timer, publish new media, advance 6.1 s with the resume prompt up, controls stay hidden | Race in risk R2 / architect risk 4 | MISSING |
| AC-8 | device | P0 | M4 Back on fresh movie; M7 resume-prompt variant; M8 Episodes switch focus | Real focus and D-pad behaviour | evidence on old base only, redo |
| AC-9 | unit/static | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` on the rebased branch, including all of `PlayerVMOverlayAutoHideTest`, `PlayerControlsInteractionTest`, `PlayerUIMapperTest` | No regression | pending rebase |

The four overlay-autohide tests (T-M1, T-M2) must pass after the rewrite and the rewritten T-M1 must also fail if `publishPreparedContent` is reverted to `updateViewState(Content(contentState))` (check by reading the diff, no checkout).

## Tests that fail on the base today and pass after (written first)

Checked by reading the base code (`git -C {wt} show be8395a4:<path>`), not by checkout.

1. `PlayerVMPlaybackFlowTest.backPressed_onFreshMedia_hidesTheControlsInsteadOfLeaving` (AC-8). On base the machine starts with `controlsVisible=false` (`ControlsStateMachine.kt:8-19`) while the content says visible, so Back exits. Fails on base, passes with `initialize`.
2. T-D3a / T-D3b (AC-1, D3). On base `restoreTrackPreferences` (`PlayerVM.kt:538-540`) calls nothing when no match, so `selectSubtitle(Off row)` is never invoked. Fails on base and on the current diff, passes after the D3 line. Write it first, before the VM change.
3. `ControlsStateMachineTest.initialize_*` fail on base (no `initialize`, compile error counts as red).
4. `SubtitleLabelsTest` is new code, red by absence on base.

FAILING-FIRST TEST (primary): T-D3a, because it is the only one not yet backed by code.

## UNTESTABLE

- AC-1 DEFAULT/FORCED manifest regression: needs a real Media3 selector with an HLS manifest that sets DEFAULT/FORCED. `PlaybackController` has no unit tests and no fake exists. Owner Q2 accepts unit coverage only; the VM-level T-D3 and the device Off check (M1) are the proxy. Note in the review as `not reproduced`.
- AC-2 Off across rebuild: depends on `prepare()` building a text-disabled selector; same constraint. Optional device check M6.
- AC-4 matching internals (url, stable key, unambiguous language, no override): `applySubtitleTrackSelection` and `findUnambiguousTextTrackByLanguage` are private in an un-faked controller. Behaviour is observed on device (M2). Suggestion for the implementer, not a requirement: extracting the pure matching over a list of `(url?, language?)` would allow unit tests; out of scope unless the owner asks.
- AC-6 `SELECTION_FLAG_FORCED` on the media item: private `buildMediaItem`.

## Manual checks (qa-device smoke, rebased dev build, emulator, lease, `adb -s <serial>`, remote key events only)

Use `tools/android-apk-install-preserve`; no uninstall or data clear. Do not mutate account watch progress beyond what is needed; use a short seek-free play. Evidence in a new file under `docs/superpowers/evidence/`, audited by `tools/mobile-evidence-audit.sh`.

- M1 Off by default (AC-1). Steps: open a series episode or a movie with no saved subtitle choice; wait for first frame. Expect: no subtitle text rendered; open Settings, Subtitles door trailing value reads `Выкл.`/`Off`; Subtitles page first row focused and checked. Must not: text appears unasked.
- M2 Pick a row (AC-3, AC-4, AC-5). Steps: open Subtitles page (D-pad down/OK), read labels, pick a non-Off row. Expect: language names (`Русский`, `English`), `AI-generated`/`Созданные ИИ` if present, no raw codes unless unresolved; the pick shows text, or on an HLS title record whether it shows (known accepted limitation, R5); the root door shows the label. Must not: crash, focus loss, panel with ellipsised labels.
- M3 Restored choice and Russian (AC-3, AC-5, R7). Steps: after M2, go to the next episode (or reopen). Expect: the saved subtitle is rendered and the picker marks it; if the saved choice does not resolve, text is Off and the picker reads Off (D3). Repeat once with the app language Russian; record any row that falls back to an uppercase code. Must not: text under an Off picker.
- M4 Back on fresh media (AC-8). Steps: open a fresh movie without a resume prompt; take a frame with controls up and play/pause focused; press Back once. Expect: controls hide, video keeps playing, no screen change; second Back leaves the player. Must not: first Back exits.
- M5 Layout (R11). On the Subtitles page: right edge flush, labels single-line, panel width at most 420 dp, check icon at one x. Forced/variant rows only if the owner names a title; otherwise `not measured`.
- M6 (optional, AC-2). With Off selected, change the buffer preset in settings during playback. Expect: playback restarts, still no text.
- M7 Resume prompt (AC-8). Open a title with saved progress. Expect: prompt alone, no controls under it, focus on its button; choose resume; controls then follow normal behaviour. Must not: controls under the prompt.
- M8 Panel on media change (Q6). From the Episodes panel switch episode. Expect: panel closed, controls visible with focus on the buttons row, first Back hides them. Must not: panel left open over new media, focus lost.

## Priorities summary

P0: T-D3a, T-D3b, the T-D3 rewrite, `initialize_*` (4), T-D7, `backPressed_onFreshMedia`, T-M1, T-M2, `SubtitleLabelsTest` AC-5/6/7 cases (counted as 6), AC-9 gate.
P1: T-L1, T-L2, T-F1, T-P1, T-P2, M1, M2, AC-4 device.
P2: T-L3, M6, AC-6 device.
