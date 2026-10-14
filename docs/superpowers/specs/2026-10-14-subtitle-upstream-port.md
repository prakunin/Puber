# Bring upstream's subtitle fixes into this fork

Status: approved
Date: 2026-10-14
Slug: subtitle-upstream-port
Kind: improvement
Size: M
Tracks: ui

## Problem / Why

Port the first batch of upstream's subtitle fixes so that: subtitles are Off until chosen; HLS manifest tracks are never matched to API rows by position; the picker names languages, AI and forced rows readably; and Back on freshly prepared media hides the controls before leaving.

Risks named at triage:

- `setTrackTypeDisabled(TEXT)` at selector build: confirm the selection path re-enables text and sets the override, else subtitles never appear (partly seen working on device).
- Dropping the positional fallback may leave an API row with no effect on HLS until the side-load run (subtitle-sideload-merge) lands: a user picks a row and nothing shows. Accepted by the port, worth knowing.
- `SELECTION_FLAG_FORCED` on side-loaded configs may change Media3 default selection once text is enabled.
- `Locale.getAvailableLocales()` ISO-3 map is built lazily on first picker build (cost on TV hardware, unmeasured); Android locale data may name fewer languages than the JVM unit tests.
- `initialize()` in `publishPreparedContent` races with a pending hide job (cancelled there) and replaces the whole State, including master's new `episodesPanelHeld` (default false, fine).

Evidence from the old base:

Covered on emulator-5554 (old base 7ad23247, 1373 unit tests green): Off default on an episode, `AI-generated` label, selecting text, restored choice on next media (AC-1 partly, AC-3, AC-5 for `ai`), Back hides controls on fresh movie (AC-8), no subtitles by default on a movie.
Not covered on device: Off across rebuild (AC-2, unit tests only), a DEFAULT/FORCED manifest (AC-1's actual regression never reproduced on the old build), forced and variant labels (AC-6, AC-7, unit tests only), resume-prompt variant of AC-8, and everything on the rebased code: evidence is from a base 145 commits behind master, so it must be redone after the rebase (smoke at least). The pass also moved account watch progress.

Rebase assessment:

Master changed 15 player files via player-overlay-autohide and a PlaybackClock change. Overlap with this diff:
- `ControlsStateMachine.kt`: master edits the State class, `openPanel`, `closePanel`, replaces `applyControlsVisibility`; our `initialize` is inserted between `lastPanelOpener` and `showControls`, a region master leaves alone. Expected: clean textual merge. Semantic check: `initialize` builds `State(...)`, so it must also reset the new `episodesPanelHeld` (default false: OK) and `playbackEnded`.
- `PlayerVM.kt`: master hunks at ~201, 560, 708-736, 1245, 1831; ours at ~349 and ~756 (`applyControlsState`, near master's 736 `holdEpisodesPanel`). Expected: clean or at most a trivial context conflict near `applyControlsState`. The hide job now calls `applyControlsState()` then `processEffects`, unaffected by `withControlsState`.
- `PlayerUIMapper.kt`: master replaces `formatTime` and removes imports at the file end/top (`java.util.Locale` import and constants). Our hunk is in the subtitle block (~line 46): no overlap, but check that `Locale` is still imported if the mapper needs it (our code uses `context.resources.configuration.locales[0]`, no import needed).
- Tests: `ControlsStateMachineTest.kt` changed heavily on master (+151, rewritten applyControlsVisibility section); our +45 added tests likely conflict by adjacency, resolvable by keeping both. `PlayerUIMapperTest.kt` has master additions only; `PlayerVMMutationAndResultTest` / `PlayerVMPlaybackFlowTest` unchanged on master.
- `PlaybackController.kt`, `Models.kt`, `strings.xml`, `player.xml`: untouched on master, clean.
Overall: low-to-moderate; no semantic clash expected, but master's new episodes-panel auto-hide interacts with Back handling (`handleBack`), so rerun the overlay-autohide tests and re-smoke Back after rebase. Because all work is uncommitted, commit it on the branch first, then rebase (or apply onto a fresh worktree from master).

## Acceptance criteria

- AC-1 (Off by default, 6123fdbc). Given media whose HLS manifest marks a subtitle DEFAULT or FORCED and no saved choice, When playback starts, Then no text is rendered and the picker reads Off. (`PlaybackController.kt:387` `setTrackTypeDisabled(TEXT, true)`.)
- AC-2 (Off survives rebuild). Given Off is selected, When the player is rebuilt (buffer preset or DNS change), Then text stays disabled.
- AC-3 (explicit/restored choice still works). Given a saved or just-picked subtitle, When the next media starts or the user picks a row, Then that subtitle is rendered (text type re-enabled by the selection path).
- AC-4 (no positional match). Given an HLS stream whose manifest text-track order differs from the API list, When a row is selected, Then the track is matched only by url / stableKey / unambiguous language, and with no match no override is set (never "row N = track N").
- AC-5 (language names). Given API lang `rus`, `eng`, `ai`, a blank or an unresolvable code, Then labels are the localized language name titlecased ("Русский"), `AI-generated` / `Созданные ИИ`, uppercased code, and `Subtitles N` / `Субтитры N` respectively.
- AC-6 (forced). Given `SubtitleLink.forced == true`, Then the row label carries the qualifier (`forced` / `частичные`) and the media item gets `SELECTION_FLAG_FORCED`; a missing `forced` field parses as null without breaking deserialization.
- AC-7 (collisions). Given rows whose labels would read the same, Then each gets `<label> · variant N` / `вариант N` ordinal (numbered per label, not per raw language).
- AC-8 (Back on fresh media, f3eb3533). Given newly prepared media with no resume prompt, When Back is pressed first, Then the controls hide and playback continues; with a resume prompt the controls start hidden and the prompt owns focus; state left by previous media (panel, playbackEnded) is cleared.
- AC-9 (regressions). Given the full unit suite, detekt and lint, Then no new failures on the rebased branch, including master's player overlay-autohide tests.

## Out of scope

- Side-loading API subtitles on HLS and the player/API subtitle merge model: run subtitle-sideload-merge.
- Restoring the subtitle choice across player recreation with new preference keys: run subtitle-restore-model.

## Design

### Architect

Retroactive design. The implementation already sits uncommitted in the worktree
`/home/user1/orca/workspaces/Puber/subtitle-player-fixes` (base 7ad23247). This document assesses
that diff against master be8395a4, which it will be rebased onto, and names what must change.
Citations: `master:` = main checkout at be8395a4; `wt:` = the worktree with the uncommitted diff.
Every citation below was read.

### Decisions

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

### Changes by layer

#### API / repository
- `app/src/main/java/com/kino/puber/data/api/models/Models.kt:429-434` (`SubtitleLink`): adds `forced: Boolean? = null`. In the diff already. Master leaves this file alone, so the rebase is clean.

#### ViewModel state and actions
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt`: adds `initialize(resumeDialogVisible)` between `lastPanelOpener` (master:`:37`) and `showControls` (master:`:38`). Master leaves that region alone. It resets `state` to `State(controlsVisible = !resumeDialogVisible, focusTarget = Buttons unless resume)` and `lastPanelOpener = Buttons`. In the diff already.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt`:
  - `preparePlayback`: `updateViewState(PlayerViewState.Content(contentState))` at master:`:352` becomes `publishPreparedContent(contentState)`. The `scheduleControlsHide()` at master:`:357` stays, so the 6 s auto-hide from master (`CONTROLS_HIDE_DELAY_MS`, master:`:1848`) still runs for no-resume starts. In the diff already.
  - `applyControlsState` (master:`:760-769`) goes through the new `withControlsState` extension. In the diff already. Master's new `holdEpisodesPanel` (master:`:739-743`) and `onPanelClosed` sit between the two hunks, so expect a clean merge or a trivial context conflict.
  - **New (D3):** in `restoreTrackPreferences` (master:`:517-541`), the `if (subtitleIndex >= 0)` at master:`:538-540` gets an else branch that re-applies the current row. Equivalent form: `applySubtitleSelection(subtitleIndex.takeIf { it >= 0 } ?: content.selectedSubtitleIndex, persist = false)`.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` (playback, owned by the VM layer). Master leaves this file alone:
  - selector built text-disabled (wt:`:383-392`; master:`:383-389`).
  - `SELECTION_FLAG_FORCED` on side-loaded configs (wt:`:611`; master `buildMediaItem` at `:599-614`).
  - positional fallback removed (master:`:893`, `:899-912` deleted).

#### Screen and DI module
- None. No Koin or screen wiring changes. `ControlsStateMachine` is still constructed inside the VM (master:`PlayerVM.kt:173`).

#### Compose content
- None. `PlayerSettingsPanel` keeps rendering `SubtitleTrackUIState.label`. The visual delta (label wording, Off default, controls on Back) belongs to the ux-designer, not this document.

#### Resources
- `app/src/main/res/values/player.xml:44` (ru) and `app/src/main/res/values-en/strings.xml:246` (en): after `player_subtitle_variant_label` the diff adds `player_subtitle_ai_generated`, `player_subtitle_forced` and `player_subtitle_unknown` (`%1$d`). In the diff already. Master leaves both files alone.

#### Model (UI mapping)
- `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:35-70` (master `mapSubtitleTracks`): the inline duplicate-language block (master:`:44-67`) is replaced with a call to `subtitleLabels(...)`. Master's only change here is `formatTime` and the removed `java.util.Locale` import (master diff at the top and around `:210`). The new code needs no `Locale` import: it uses `context.resources.configuration.locales[0]`, which is API 24, and minSdk is 24. No overlap.
- New `app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLabels.kt` (wt, 81 lines).

#### Tests
Already in the diff:
- `ControlsStateMachineTest` adds 4 `initialize` tests.
- `PlayerVMPlaybackFlowTest` adds `backPressed_onFreshMedia_hidesTheControlsInsteadOfLeaving`. Its Back tests now press Back twice, or send `HideControls` first.
- `PlayerVMMutationAndResultTest` now presses Back twice in 4 tests.
- New `SubtitleLabelsTest` (6 tests).

Master leaves both PlayerVM test files alone. Master rewrote `ControlsStateMachineTest`, so expect an adjacency conflict there: keep both sides.

### Must change in the existing diff (for correctness or to fit master)

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

### Alternatives rejected

- Keep the positional fallback only for non-HLS sources: AC-4 forbids it, and the sideload run replaces matching wholesale.
- Clear `pendingSubtitleTrack` in `PlaybackController.prepare()`: that would also drop a live pick on a buffer or DNS rebuild (AC-3), and the controller cannot tell new media from a rebuild.
- Clear it in the controller when its URL is absent from the new subtitle list: it works, but the controller would be deciding picker semantics, and the controller has no unit tests.
- Make `ContentStateFactory` stop writing the controls fields and read them from the machine: a wider change for the same effect.
- Emit `PanelClosed` from `initialize`: it would re-run `onPanelClosed`, which handles `exitWhenPanelCloses` and the next-episode check (master:`PlayerVM.kt:745-758`), on a media switch.
- Port upstream's `SubtitleLabeler` now: that belongs to subtitle-sideload-merge (its Q2). Labels for API rows alone do not need it.
- Build the ISO-3 map eagerly at app start: it puts a cost on startup for a screen that may never open.

### Risks (highest first)

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

### Open questions

1. D3 adds a production line beyond the ported diff, to keep AC-1 true for the next media. Take it in this run? Recommended: yes. It is one line plus one test, it lives inside AC-1's scope, and the alternative is a known Off-picker-with-text defect. Blocks: no.
2. Rewriting the two master overlay-autohide tests (items 2 and 3) changes their assertions, not their intent. Is that acceptable as part of AC-9? Recommended: yes, the tests encoded the disagreement between content and machine that AC-8 removes. Blocks: no.

### UX designer

### Which stand governs

**No stand on master governs the subtitle picker.** It is a page of the player settings side
panel (`PlayerSettingsPanel.kt` `SubtitlesPage`, rows drawn by `PlayerPanelItem` in
`PlayerSidePanel.kt`).

- `docs/design/player-controls/` (master) covers the title, the seek bar and the button row.
  It does not cover the side panel. This change leaves every number in it alone.
- `docs/design/player-overlays/` exists only on the unmerged `player-overlays-port` branch
  (commit 92a9441d). It has a side-panel scene, but its README lists the panel as **"Not yet
  agreed"**, and its "as shipped" numbers come from source, not from the television. The resume
  prompt scene there *is* agreed (`ResumeDialog.kt`, bottom-left at 20 / 48 dp), but master still
  ships the old dialog.

This does **not** block planning. The change adds no layout, size, colour or material. It only
changes the text that existing rows show, plus when the controls are visible. The panel geometry
stays the shipped code values listed below. Nobody has dialled them, and this run must not retune
them. When the side-panel scene of `player-overlays` is dialled, it takes the new labels as
content (see "Content for the stand").

### Visual delta

| Where | Before (old base / master) | After |
|---|---|---|
| Subtitles page rows | raw API code (`rus`, `eng`, `ai`), `rus · вариант 2` | `Русский` / `Russian`, `Созданные ИИ` / `AI-generated`, `ENG` for an unresolved code, `Субтитры N` / `Subtitles N` for a blank one |
| Forced row | no marker | `<label> · частичные` / `· forced` |
| Rows that would read the same | numbered per raw language | `<label> · вариант N` / `· variant N`, numbered per final label |
| Root "Субтитры" door, trailing value | selected raw code, or `Выкл.` | selected label (same strings), `Выкл.` / `Off` by default |
| Fresh media with no resume prompt | controls up, first Back left the player | controls up on the buttons, first Back hides them and video keeps playing |
| Fresh media with a resume prompt | controls could show under the prompt | controls hidden, the prompt alone on screen |
| Subtitles on fresh media | could appear unasked (HLS DEFAULT/FORCED) | none until chosen |

Labels get longer: the worst case is `Русский · частичные · вариант 2`, about 31 characters.
The panel is content-sized (`.width(IntrinsicSize.Max)` between `PanelMinWidth` 240 dp and
`PanelMaxWidth` 420 dp), so a long label **widens the whole panel** on the Subtitles page.
`animateContentSize` animates the change between pages. This is the shipped behaviour, not a
regression, but it is the one place where the new strings show up as geometry.

### Numbers that apply (shipped in code, not agreed on a stand)

From `PlayerSidePanel.kt` / `PlayerSettingsPanel.kt` on master:

- Panel: min 240 dp, max 420 dp, padding 20 / 20 / 20 / 16 dp, corner 20 dp (left only),
  flush to the right edge. Title 14 sp / 18 sp, 12 dp below it.
- Row (`PlayerPanelItem`): min height 40 dp, padding 12 × 6 dp, corner 12 dp; label 14 sp /
  18 sp Medium, **maxLines 2, ellipsis**; trailing value 13 sp at 76 % alpha, **maxLines 1,
  ellipsis**, 16 dp before it; check icon 18 dp, 12 dp before it. Selected bed: primary at 16 %;
  focused bed: onSurface at 30 %.
- Page row spacing `PageSpacing` 2 dp; root door spacing `RootSpacing` 4 dp.

Checked against these: inside 420 dp minus padding (about 356 dp of row content), a 31-character
14 sp label fits on one line, and the 13 sp trailing value on the root door fits beside
`Субтитры`. Neither should wrap or ellipsize. This is calculated, not measured, so the device
pass must confirm it.

Still open (belongs to the player-overlays stand, not to this run): every side-panel number above.
The stand recommends 20 dp off the edge, all four corners 12 dp, a flat bed instead of glass,
34 dp / 13 sp rows and a 360 dp max. If that is agreed, the longest label must be re-checked
against 360 dp.

### Focus order and visibility

The change adds **no new focusable element**.

- Subtitles page: initial focus is `selectedSubtitleIndex`. With Off as the default, that is the
  first row, `Выкл.`, focused and selected (focused bed plus check). Order: Off, API rows in API
  order, `Аа Размер`. Unchanged.
- Fresh media without a resume prompt: focus target `Buttons`, so the play/pause pill must show
  the focused 20 % bed as soon as the controls appear. After the first Back, no control is visible
  or focused, and the next key shows the controls again.
- Fresh media with a resume prompt: focus target `null`, controls hidden. The prompt's own button
  holds the visible focus. No control pill may sit under the prompt.

### Shared constants: parameterise, do not retune

- `PlayerPanelItem`, `PlayerSidePanel`, `PanelMinWidth` / `PanelMaxWidth`: shared by every
  settings page, the About panel and the stream page. Do not shorten labels by tightening these,
  and do not drop `maxLines` to 1 for subtitle rows ("never hide data to protect a layout").
- Separator: `QUALIFIER_SEPARATOR = " · "` in `SubtitleLabels.kt` must stay identical to the
  ` · ` inside `player_subtitle_variant_label` (both locales), so that `Русский · частичные ·
  вариант 2` reads as one rhythm. A unit assertion on the composed string covers this.
- `PlayerPreview.kt:37-41` still shows the old content (`Русские`, `Узбекские · Созданы
  нейросетью`). Update it to the new shapes (`Русский`, `Созданные ИИ`, a forced row, a variant
  row) so the preview shows the strings that actually ship. This is a nit, not a blocker.

### Content for the stand

When the side-panel scene of `player-overlays` is dialled, its Subtitles page should carry
`Выкл.`, `Русский`, `Русский · частичные`, `English · вариант 1`, `English · вариант 2`,
`Созданные ИИ`, so that panel width is judged on the longest real label.

### What to check on device (rebased build, 1080p, dp = px / 2)

Note: the old evidence frames are 960 × 540, half scale, so 1 px = 1 dp there. They were taken
with the device in English. Retake them at full resolution.

1. Subtitles page with labels: the panel's right edge stays flush, every label is on one line with
   no ellipsis, and the check sits at the same x on every row. Measure the panel width and compare
   it with the content width (≤ 420 dp).
2. Root settings page: the `Субтитры` door shows `Выкл.` on fresh media, and the full selected
   label after a choice, with no ellipsis.
3. App in Russian at least once: `Русский`, `Созданные ИИ` titlecased. Android locale data names
   fewer languages than the JVM, so record any row that falls back to an uppercase code.
4. If the owner names a title with forced or duplicate-language subtitles: `· частичные` and
   `· вариант N` rendered, with the panel width measured. Otherwise unit coverage stands, and the
   review says `not measured` for those rows.
5. Fresh movie, no resume prompt: frame with the controls up and play/pause focused, then Back,
   then a frame with no controls and video still running.
6. Fresh media with a resume prompt: frame with the prompt alone, no control overlay under it,
   and focus visible on the prompt button.
7. Fresh media: no subtitle text in the first frame after start (Off by default).

## Test design

Base be8395a4 (after the rebase). Worktree `/home/user1/orca/workspaces/Puber/subtitle-player-fixes`.
Levels: `unit` (JUnit 5, `MainDispatcherExtension`, `FakeResourceProvider`, virtual time),
`device` (qa-device smoke on an emulator, evidence file), no `instrumented` tests.
Gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.

Key constraint: `PlaybackController` (AC-1, AC-2, AC-4 internals) has no unit tests and wraps ExoPlayer, so
its behaviour is covered by the VM contract (what the VM asks the controller to do) plus the device smoke.
Owner decisions Q2: DEFAULT/FORCED manifest and Off across rebuild are unit-only or accepted-untested; no device re-pass for them.

### Risk profile

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

### Traceability

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

### Tests that fail on the base today and pass after (written first)

Checked by reading the base code (`git -C {wt} show be8395a4:<path>`), not by checkout.

1. `PlayerVMPlaybackFlowTest.backPressed_onFreshMedia_hidesTheControlsInsteadOfLeaving` (AC-8). On base the machine starts with `controlsVisible=false` (`ControlsStateMachine.kt:8-19`) while the content says visible, so Back exits. Fails on base, passes with `initialize`.
2. T-D3a / T-D3b (AC-1, D3). On base `restoreTrackPreferences` (`PlayerVM.kt:538-540`) calls nothing when no match, so `selectSubtitle(Off row)` is never invoked. Fails on base and on the current diff, passes after the D3 line. Write it first, before the VM change.
3. `ControlsStateMachineTest.initialize_*` fail on base (no `initialize`, compile error counts as red).
4. `SubtitleLabelsTest` is new code, red by absence on base.

FAILING-FIRST TEST (primary): T-D3a, because it is the only one not yet backed by code.

### UNTESTABLE

- AC-1 DEFAULT/FORCED manifest regression: needs a real Media3 selector with an HLS manifest that sets DEFAULT/FORCED. `PlaybackController` has no unit tests and no fake exists. Owner Q2 accepts unit coverage only; the VM-level T-D3 and the device Off check (M1) are the proxy. Note in the review as `not reproduced`.
- AC-2 Off across rebuild: depends on `prepare()` building a text-disabled selector; same constraint. Optional device check M6.
- AC-4 matching internals (url, stable key, unambiguous language, no override): `applySubtitleTrackSelection` and `findUnambiguousTextTrackByLanguage` are private in an un-faked controller. Behaviour is observed on device (M2). Suggestion for the implementer, not a requirement: extracting the pure matching over a list of `(url?, language?)` would allow unit tests; out of scope unless the owner asks.
- AC-6 `SELECTION_FLAG_FORCED` on the media item: private `buildMediaItem`.

### Manual checks (qa-device smoke, rebased dev build, emulator, lease, `adb -s <serial>`, remote key events only)

Use `tools/android-apk-install-preserve`; no uninstall or data clear. Do not mutate account watch progress beyond what is needed; use a short seek-free play. Evidence in a new file under `docs/superpowers/evidence/`, audited by `tools/mobile-evidence-audit.sh`.

- M1 Off by default (AC-1). Steps: open a series episode or a movie with no saved subtitle choice; wait for first frame. Expect: no subtitle text rendered; open Settings, Subtitles door trailing value reads `Выкл.`/`Off`; Subtitles page first row focused and checked. Must not: text appears unasked.
- M2 Pick a row (AC-3, AC-4, AC-5). Steps: open Subtitles page (D-pad down/OK), read labels, pick a non-Off row. Expect: language names (`Русский`, `English`), `AI-generated`/`Созданные ИИ` if present, no raw codes unless unresolved; the pick shows text, or on an HLS title record whether it shows (known accepted limitation, R5); the root door shows the label. Must not: crash, focus loss, panel with ellipsised labels.
- M3 Restored choice and Russian (AC-3, AC-5, R7). Steps: after M2, go to the next episode (or reopen). Expect: the saved subtitle is rendered and the picker marks it; if the saved choice does not resolve, text is Off and the picker reads Off (D3). Repeat once with the app language Russian; record any row that falls back to an uppercase code. Must not: text under an Off picker.
- M4 Back on fresh media (AC-8). Steps: open a fresh movie without a resume prompt; take a frame with controls up and play/pause focused; press Back once. Expect: controls hide, video keeps playing, no screen change; second Back leaves the player. Must not: first Back exits.
- M5 Layout (R11). On the Subtitles page: right edge flush, labels single-line, panel width at most 420 dp, check icon at one x. Forced/variant rows only if the owner names a title; otherwise `not measured`.
- M6 (optional, AC-2). With Off selected, change the buffer preset in settings during playback. Expect: playback restarts, still no text.
- M7 Resume prompt (AC-8). Open a title with saved progress. Expect: prompt alone, no controls under it, focus on its button; choose resume; controls then follow normal behaviour. Must not: controls under the prompt.
- M8 Panel on media change (Q6). From the Episodes panel switch episode. Expect: panel closed, controls visible with focus on the buttons row, first Back hides them. Must not: panel left open over new media, focus lost.

### Priorities summary

P0: T-D3a, T-D3b, the T-D3 rewrite, `initialize_*` (4), T-D7, `backPressed_onFreshMedia`, T-M1, T-M2, `SubtitleLabelsTest` AC-5/6/7 cases (counted as 6), AC-9 gate.
P1: T-L1, T-L2, T-F1, T-P1, T-P2, M1, M2, AC-4 device.
P2: T-L3, M6, AC-6 device.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Rebase: commit in place then rebase, or re-apply on a fresh worktree? | no | answered | Commit in place (without replay_pid*.log), then rebase onto master |
| Q2 | Device re-pass after the rebase? | no | answered | Short smoke (Off default, picker labels, Back) plus unit tests; DEFAULT/FORCED manifest case by unit tests only |
| Q3 | Ship before subtitle-sideload-merge, accepting API-only rows may do nothing on HLS? | yes | answered | Yes, ship first; AC-4 stays as is |
| Q4 | Take D3? | yes | answered | Yes, in this run |
| Q5 | Rewrite the two master overlay-autohide tests broken by AC-8, keeping their intent? | no | answered | Yes |
| Q6 | initialize closes any open panel on media change, as upstream? | no | answered | Yes; checked in the smoke |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-13 | Commit the uncommitted work in place and rebase onto master be8395a4 | owner |
| 2026-10-13 | Device re-pass: smoke + unit tests only | owner |
| 2026-10-13 | Ship before subtitle-sideload-merge; AC-4 unchanged | owner |
| 2026-10-14 | Take D3 in this run | owner |
| 2026-10-14 | Rewrite the two master overlay-autohide tests with the same intent | owner |
| 2026-10-14 | Media change closes any panel and focuses the controls, as upstream | owner |
| 2026-10-14 | Gate 1: spec approved | owner |
