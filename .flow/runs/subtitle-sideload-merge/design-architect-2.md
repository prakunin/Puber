# Design delta 2 (architect): subtitle-sideload-merge, fix round 2

Scope: QA round 2 Defect 1, review-edge-2 F1 (major), review-codex-2 (minor, same issue) and review-tests-2 F1.
Worktree HEAD `1b79c661`. App paths are relative to `app/src/main/java/com/kino/puber/` and test paths to
`app/src/test/kotlin/com/kino/puber/` unless they start with `app/`. Every `path:line` below was read at that HEAD. Media3
citations are from the `1.11.0` tag (`libraries/exoplayer/...`, `libraries/exoplayer_hls/...`).

This delta replaces fix `36da8957`, which treats every text-less report as transient. It amends spec D11 and adds
D17-D20. The ACs are unchanged.

## 1. Where Outlander's subtitles come from

**Finding: on the manual-quality stream the item had no side-loads. Outlander's `Russian`/`English` rows come from
the `hls4` manifest only, so the 720p/480p `url.hls` stream really has no subtitles.** Confidence: high (about 90 %).
The deduction is from code plus the round-1 and round-2 device observations. The raw API list was not inspected.

How the deduction goes:

1. All three paths that rebuild the item pass the API subtitles and go through the factory: quality switch
   (`ui/feature/player/vm/PlayerVM.kt:419-427`), next-candidate fallback (`:440-447`) and link refresh (`:497`, with
   `refreshedMedia.subtitles` from `:469-473`). Each one calls `PlaybackController.switchStream`
   (`vm/PlaybackController.kt:501-521`), which builds the item with `mediaItemFactory.build(stream, subtitles)` (`:507`).
   `PlaybackMediaItemFactory.spec` attaches every link that has a url (`vm/PlaybackMediaItemFactory.kt:38-52`, filter
   `:78-79`). No path drops them, so no switch path has a bug.
2. When an item has subtitle configurations, `DefaultMediaSourceFactory` wraps it in a `MergingMediaSource`
   (`DefaultMediaSourceFactory.java:603-659`). `MergingMediaPeriod.onPrepared` publishes its track groups only after
   **every** child has prepared, and then publishes all of them at once (`MergingMediaPeriod.java:300-335`). The lazy
   side-load children prepare without I/O. So the first prepared `Tracks` of a side-loaded item always contains the
   side-load text groups.
3. Before that point the player masks the new item with an empty track set. `setMediaItem` changes the playing
   period, so `trackGroups = TrackGroupArray.EMPTY` (`ExoPlayerImpl.java:2823-2824`). The controller's guard
   (`PlaybackController.kt:822`) suppresses that report, because it has neither audio nor text groups.
4. HLS subtitle renditions are also known at preparation. `HlsMediaPeriod.buildAndPrepareSampleStreamWrappers` always
   builds them from the multivariant playlist, not only under chunkless preparation (`HlsMediaPeriod.java:559-560`,
   `:820-873` `prepareWithMultivariantPlaylistInfo`).
5. So **a report with audio groups and no text groups can happen only for an HLS item that has neither side-loads
   nor subtitle renditions**. The "transient" state that `36da8957` assumed does not exist in Media3 1.11.0.
6. The devices fit this exactly:
   - Round 1, 720p: the picker collapsed to `Off` and stayed there for 5-10 s. If side-loads had existed, their
     groups would have rebuilt the rows.
   - Round 2: re-picking `Russian` at 480p rendered nothing (0/27). The controller found no text group, so it set no
     override and only a preferred language (`PlaybackController.kt:849-867`).
   - Audio reads `Track 1` on `url.hls`. The label falls back from name and language to the index
     (`PlaybackController.kt:826`), so this playlist has no `EXT-X-MEDIA` metadata at all, subtitles included.

Consequences outside this fix:

- The evidence file lists Outlander as **H2** ("API lists Russian, English"). That was inferred, and is most likely
  wrong. Outlander is the **H1 manifest-only** case, so it is evidence for AC-2 (manifest row listed and renders),
  not for AC-1 / D-1 (side-load renders on HLS). AC-1 on a device is still open.
- Manual quality works as a discriminator on the device: rows that survive on `url.hls` are side-loads. QA can use
  it on Psyren S1E1 (9 API languages) for AC-1.
- Residual uncertainty: an API list whose every link has a blank `url` gives the same picture. The outcome is the
  same: no side-loads. QA can confirm with one count of `subtitles` for Outlander S1E5, without keeping the response.

Nothing in the side-load path is broken. The defect is the VM rule.

## 2. Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D17 | What does a report without text groups mean? | It is **definitive**: the stream has no subtitles. The VM no longer has any notion of a "transient" report. Remove `textlessReport` (`PlayerVM.kt:534-541`). | §1 points 2-5. Holding rows is what produced "`Russian` checked, nothing renders" (Defect 1, edge F1, codex). |
| D18 | Where is "not known yet" decided? | In the controller, through a pure function `subtitleReportKind(streamType, hasSideLoads, textGroupCount)` in `vm/PlayerTextTracks.kt`. It returns `API_ROWS` (progressive or unknown type; the callback gets `null`, as today `PlaybackController.kt:838-841`), `NOT_YET` (HLS and `hasSideLoads` and `textGroupCount == 0`: `notifyTracksUpdated` **returns without calling back**), or `PLAYER_ROWS` (otherwise, including an empty list). It is called in `notifyTracksUpdated` (`:817-843`) after the existing both-empty guard (`:822`). | Per §1 a side-loaded item cannot report audio without text, so `NOT_YET` is a defensive guard that is normally unreachable. If it ever fires, the next report carries everything. Being pure, it is unit-tested without ExoPlayer, and the controller's own logic becomes one call. |
| D19 | Where does the viewer's pick live? | **VM owns the intent**: a new `private var intendedSubtitleRow: SubtitleTrackUIState?` (null = Off) next to `apiSubtitleTracks` (`PlayerVM.kt:179`). It is not view state and is never persisted by itself. **The controller's `pendingSubtitleTrack` (`PlaybackController.kt:170`) only mirrors the row the picker shows.** It is set by `selectSubtitle` (`:567-581`), cleared by Off and by `prepare` (`:387`), and its semantics do not change. | One owner. The controller cannot tell "not on this stream" apart from "gone". The VM sees API rows, merges and media changes. A mirror that always equals the displayed row keeps D11's guarantee: the picker never says Off while text plays. |
| D20 | Single source for "has side-loads" (tests-2 F1) | Add `val hasSideLoads: Boolean get() = subtitles.isNotEmpty()` to `MediaItemSpec` (`vm/PlaybackMediaItemFactory.kt:21-26`). Change `build` to `fun build(spec: MediaItemSpec): MediaItem` (`:54-55`). In `prepare` (`PlaybackController.kt:436-440`) and `switchStream` (`:507-511`): `val spec = mediaItemFactory.spec(stream, subtitles)`, `setMediaItem(mediaItemFactory.build(spec)[, savedPosition])`, `currentItemHasSideLoads = spec.hasSideLoads`. The warm-up (`:701`) uses `build(spec(stream, subtitles))`. Remove the two `subtitles.sideLoadable().isNotEmpty()` expressions. | The flag comes from the spec that was actually attached, and the existing spec test covers it. |
| D11 (amended) | VM rule per report | Replace the D11 text with the rule in §3. The new part: a pick that cannot be re-found shows Off and sends Off once, but the **intent is kept** (not persisted) and re-applied when a later report contains the row again. | Edge F1 fix direction, without depending on a controller pending pick surviving across streams. |

## 3. The exact VM rule (`PlayerVM.onTracksUpdated`, replaces `PlayerVM.kt:527-567`)

New state, next to `tracksRestoredForCurrentMedia` (`PlayerVM.kt:163`):

- `intendedSubtitleRow: SubtitleTrackUIState? = null`.
- `subtitleRestoredForCurrentMedia = false`. From now on `tracksRestoredForCurrentMedia` covers **audio only**.
  - Both flags are reset together at `:917` (buffer preset rebuild), `:928` (Fast DNS rebuild) and `:972`
    (`resetMediaSession`).
  - `intendedSubtitleRow = null` **only** in `resetMediaSession` (`:957-974`, reached by episode switch `:981` and
    retry `:1391`). A rebuild of the same media keeps the intent.

On each report `(audio, audioIndex, reported)`:

1. `previousRow` = the displayed row (as today, `:532-533`).
2. `rows = reported?.let(::mergeSubtitleTracks) ?: apiSubtitleTracks`. A report with no text groups merges to
   `[Off]` (`vm/SubtitleTrackMerger.kt:23-25`). There is no special case for it.
3. `offIndex = rows.indexOfFirst { it.isOff }.coerceAtLeast(0)`.
   `shown = intendedSubtitleRow?.let { resolver.findSameSubtitleTrackIndex(rows, it) } ?: offIndex`.
   `findSameSubtitleTrackIndex` returns the Off index when nothing matches (`vm/AudioTrackPreferenceResolver.kt:48-65`).
4. If `shown != offIndex`, set `intendedSubtitleRow = rows[shown]`. This refreshes the identity, for example to
   refreshed URLs. When nothing matches, the intent stays as it was.
5. `updateContent`: audio exactly as today (`:547-552`), `subtitleTracks = rows`, `selectedSubtitleIndex = shown`.
6. If `!tracksRestoredForCurrentMedia`: set the flag, then run the **audio** half of `restoreTrackPreferences`
   (`:573-586`).
7. If `!subtitleRestoredForCurrentMedia`:
   - If `rows.none { !it.isOff }`, **return**. The restore stays pending, nothing is sent, and after `prepare` text is
     already disabled (`PlaybackController.kt:406`, `:387`).
   - Otherwise set the flag and run the **subtitle** half (`:588-599`, unchanged: the saved URL, then the stable key,
     then an unambiguous language; else re-send the displayed row; `persist = false`). Then return.
8. Otherwise `row = rows.getOrNull(shown)`. If `row != null && previousRow != null && row != previousRow`, call
   `playbackController.selectSubtitle(row)`. This is the existing re-send (`:562-566`), without persist and without
   touching the intent. **Only rows from the current `rows` are ever sent.**

Changes elsewhere in `PlayerVM`:

- `applySubtitleSelection` (`:853-863`) sets `intendedSubtitleRow = subtitle.takeUnless { it.isOff }`. When
  `persist` is true (an explicit viewer pick), it also sets `subtitleRestoredForCurrentMedia = true`, because an
  explicit choice supersedes a pending restore.
- `saveTrackPreferences` (`:1826-1837`) takes its subtitle part from the intent, not from the displayed index:
  - If `!subtitleRestoredForCurrentMedia`, it passes through `interactor.getPreferredSubtitleLang/Url(params.itemId)`
    unchanged.
  - Otherwise it saves `intendedSubtitleRow` (null means Off, which writes nulls exactly as today).
  - This is what stops an audio pick (`:843-851`) from persisting Off while the subtitles are collapsed or the
    restore is pending. The repository removes keys on null (`data/repository/PlayerPreferencesRepository.kt:28-56`).

How each case comes out:

| Case | Result |
|------|--------|
| Auto → 720p, Outlander | rows `[Off]`, Off shown, one `selectSubtitle(Off)`, nothing persisted, intent = `Russian`. Further text-less reports: no send. |
| 720p → Auto | rows rebuilt, intent re-found, `Russian` shown, one `selectSubtitle(Russian)`, cues return without a re-pick. |
| Stream has other text but not the pick | Off shown, Off sent (D11: no language fallback under an Off picker), intent kept and re-applied if the row returns. |
| Next episode, first report text-less | Audio restored. Subtitle restore pending, Off shown, nothing sent. An audio pick keeps the saved subtitle. The first report with text runs the restore. |
| Link refresh | `apiSubtitleTracks` recomputed (`:488`). The next report merges fresh rows and the intent is re-found by identity (`sameSubtitleIdentity`, `vm/SubtitleTrackIdentity.kt:13-23`: path, suffix, stable key). Only the fresh row is sent. A text-less report after a refresh shows `[Off]`, so stale rows are never shown or sent. |
| Rebuild (buffer, DNS) | Intent kept. The restore runs on the first report with text, as before (`prepare` cleared the controller mirror). |
| Viewer picks Off while collapsed | Intent null, Off persisted (explicit), restore marked done. Auto later stays Off. |
| Progressive | `reported == null` keeps the API rows (AC-5). Re-finding by intent equals re-finding by the displayed row. |

## 4. Changes by layer

### API / repository

- None.

### ViewModel state and actions

- `vm/PlaybackMediaItemFactory.kt:21-26, 54-55`: `MediaItemSpec.hasSideLoads`; `build(spec)` (D20).
- `vm/PlayerTextTracks.kt`: add `enum class SubtitleReportKind { API_ROWS, NOT_YET, PLAYER_ROWS }` and the pure
  `subtitleReportKind(...)` (D18).
- `vm/PlaybackController.kt`:
  - `:436-440` and `:507-511`: the spec drives the item and the flag (D20). `:701`: the warm-up uses
    `build(spec(...))`.
  - `:834-842`: `when (subtitleReportKind(currentStreamType, currentItemHasSideLoads, textGroups.size))`. `NOT_YET`
    returns; `API_ROWS` sends `null`; `PLAYER_ROWS` sends `buildSubtitleRows(...)` as today.
- `vm/PlayerVM.kt`:
  - `:163`: add `subtitleRestoredForCurrentMedia`. `:179`: add `intendedSubtitleRow`.
  - `:527-567`: the rule in §3. `:572-600`: split into `restoreAudioPreference()` and
    `restoreSubtitlePreference()`; the bodies are unchanged.
  - `:853-863`: set the intent and the explicit-pick flag.
  - `:917, :928, :972`: reset the new flag. `:957-974`: clear the intent.
  - `:1826-1837`: the subtitle part comes from the intent, or passes through while the restore is pending.
- `AudioTrackPreferenceResolver`, `SubtitleTrackMerger`, `SubtitleTrackSelector`: unchanged.

### Screen and DI module

- None.

### Compose content

- None. `component/PlayerSettingsPanel.kt:230-253` already clamps `focusIndex` with `coerceToIndex` and handles a
  one-row list.

### Resources

- None.

## 5. Unit tests (JUnit 5, `PlayerVMTestFixture`)

`ui/feature/player/vm/PlayerVMTrackSelectionTest.kt`:

- **Replace** `tracksUpdated_textlessReportAfterQualitySwitch_keepsRowsAndPick` (`:345-368`) with
  `tracksUpdated_textlessReport_collapsesToOff_sendsOffOnce_doesNotPersist`.
  - Steps: pick `rus.vtt`, `clearMocks`, then `report(emptyList())` twice.
  - Assert: rows `== [Off]`, index 0, exactly one `selectSubtitle(match { it.isOff })`, zero `saveTrackPreferences`.
  - Covers AC-6, edge F1 (a), codex.
- `tracksUpdated_textlessThenRowsReturn_reappliesIntendedRow`.
  - Steps: continue the case above with `report(rows)`.
  - Assert: the `rus.vtt` row is shown; exactly one `selectSubtitle(match { it.playerTrackId == "rus.vtt" })`; zero
    `saveTrackPreferences`.
  - Covers AC-6 and the Defect 1 return to Auto.
- Extend `tracksUpdated_selectedRowDisappears_goesOffAndDisablesText` (`:331-343`). Assert unchanged, then
  `report(original rows)` re-selects the Russian row exactly once (D11 amended).
- `selectAudio_whileSubtitlesCollapsed_persistsIntendedSubtitle`.
  - Steps: after the collapse, `SelectAudioTrack(1)`.
  - Assert: `saveTrackPreferences(42, any(), any(), "rus", <rus url>)`.
- `selectOff_whileCollapsed_forgetsIntent`.
  - Steps: collapse, `SelectSubtitle(0)`, `report(rows)`.
  - Assert: Off stays shown; no `selectSubtitle` of the Russian row after the Off pick; Off persisted.
- `nextMedia_textlessFirstReport_defersSubtitleRestore_keepsAudioRestore`.
  - Steps: saved `rus` and URL; `SelectEpisode(1, 2)`; `report(emptyList())` with audio.
  - Assert: `selectAudioTrack` was called; zero `selectSubtitle` calls; index 0.
  - Then `report(rows)`: the saved row is selected once with no persist.
  - Covers AC-7 and edge F1 (b).
- `nextMedia_textlessFirstReport_audioPick_keepsSavedSubtitlePreference`.
  - Assert: `saveTrackPreferences(42, any(), any(), "rus", savedUrl)`.
  - Covers AC-7 (no Off persisted).
- `refreshStreamLinks_textlessThenRows_sendsRefreshedRowOnly`.
  - Steps: pick a side-load with the old url; refresh with a same-path, new-host url (as at `:519-547`);
    `report(emptyList())`; then `report(listOf(sideLoad("eng", newUrl, 1)))`.
  - Assert: exactly one `selectSubtitle(match { it.url == newUrl })` and none with the old url after the refresh.
  - Covers edge F1 (c) and AC-6.
- P1: `rebuild_textlessFirstReport_audioPick_doesNotPersistOff` (buffer preset rebuild, `:908-919`).
- Must stay green unchanged: `:41-205` (restore), `:370-413` (re-send counts, R5), `:282-289` (progressive, AC-5),
  `:498-517`.

`ui/feature/player/vm/PlayerTextTracksTest.kt`, new `subtitleReportKind_*`:

- `progressiveOrNullType_isApiRows`.
- `hlsWithSideLoads_noTextGroups_isNotYet`.
- `hlsWithoutSideLoads_noTextGroups_isPlayerRows` (a real text-less stream).
- `hlsWithTextGroups_isPlayerRows_withOrWithoutSideLoads`.

`ui/feature/player/vm/PlaybackMediaItemSpecTest.kt`:

- `hasSideLoads_isFalse_whenEveryLinkHasBlankUrl_orListIsNullOrEmpty`.
- `hasSideLoads_isTrue_whenAnyLinkHasUrl`.
- These close tests-2 F1.

Device (qa-device), revised checklist D-4 on Outlander S1E5:

1. On Auto, `Russian` is rendering. Switch to 720p: the root reads `Subtitles Off` and the page lists only `Off`
   (checked).
2. Change the audio track once.
3. Back on Auto: `Russian` is checked and cues render without a re-pick.
4. Back out and reopen: `Russian` is restored, which proves Off was never persisted.
5. Optionally, 720p on Psyren S1E1: rows that survive are side-loads, which is the AC-1 discriminator from §1.

## 6. Alternatives rejected

- Keeping rows on every text-less report (`36da8957`): there is no end condition, and the picker lies on a stream
  that really has no text.
- A timer or "wait for STATE_READY" window to decide transience: per §1 there is no transient to wait for, and a
  timer adds a lifecycle to cancel.
- Keeping the pick only in the controller's `pendingSubtitleTrack` and not sending Off (edge F1's sketch): it leaves
  text enabled with a preferred language on the next stream, and the controller cannot handle a refresh or a new
  media. It would also be a second owner of the selection.
- Passing `null` for "not yet": `null` already means progressive or API rows (`PlaybackController.kt:68-76`), and
  would show API rows on HLS.
- Persisting Off when the rows collapse: it would erase the saved choice on a manual-quality switch (AC-7 on the next
  open).

## 7. Risks (highest first)

1. **Saved subtitle overwritten by an unrelated save.** Any `saveTrackPreferences` caller now depends on the intent
   or pass-through rule (`PlayerVM.kt:1826-1837`). Today the only callers are `:849` and `:861`. A future caller that
   reads `selectedSubtitleIndex` directly would persist Off again. Tests: the audio-pick cases above.
2. **Focus while the rows collapse under an open Subtitles page** (R1). Rows go from 3 to 1 under the focus
   (`component/PlayerSettingsPanel.kt:236-245`). `coerceToIndex` clamps the index. Round-1 D-7 saw this exact
   collapse with focus kept. Re-check on the device during the 720p switch.
3. **Re-send loop (R5).** Step 4 rewrites the intent on each report. It is deterministic (merger and labeler are
   pure), so the second identical report re-finds an equal row and sends nothing. Covered by `:370-413` plus the
   once-only counts above.
4. **`NOT_YET` hides audio rows** if a future Media3 version reports a side-loaded period without its text groups.
   It lives in `subtitleReportKind`. The impact is a stale audio picker, not a playback failure. It is pinned by
   §1's Media3 citations; re-check on a Media3 upgrade.
5. **Re-find by language on return** may land on a different same-language variant when only one remains
   (`AudioTrackPreferenceResolver.kt:60`). This is accepted under the existing rule.
6. **Refresh window.** Between the refresh's state update (`PlayerVM.kt:489-496`) and the next report, the picker
   still shows the old rows. A pick there sends an old-url row, and the selector matches by stable identity. Low
   impact, and the next report corrects it.

## 8. Open questions

1. **Q5 (scope; blocks: no).** Manual quality plays `url.hls` first (`domain/interactor/player/PlayerInteractor.kt:238-246`).
   On KinoPub that stream carries no subtitle or audio metadata, so manifest-only subtitles and named audio are lost
   on every manual quality. Recommended: a separate run that plays `hls4` with a max-video-size constraint for manual
   quality. Not in this run's ACs.
2. **Q6 (evidence; blocks: no).** Re-label Outlander in the device evidence as the manifest-only title (AC-2 / D-2
   evidence) and keep AC-1 / D-1 open until a side-load is proven, for example by Psyren on 720p. Recommended: yes,
   at the next QA pass, with one count of the API `subtitles` for Outlander S1E5 and no raw response stored.
3. No AC change is needed. D11 in the spec is amended as above. Checklist D-4 in `test-design.md` changes wording
   ("collapses to Off on a stream without text, returns on Auto"). That belongs to the test designer.
