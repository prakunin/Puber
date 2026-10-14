# Test design: subtitle-sideload-merge

Base `a97ee2b1`. Inputs: `brief.md` (AC-1..AC-11), `design-architect.md` (D1..D16, R1..R10, Q1..Q4). Test paths are
under `app/src/test/kotlin/com/kino/puber/` in the worktree. Levels: `unit` (JUnit 5, `MainDispatcherExtension`,
`FakeResourceProvider` for mapped strings), `device` (emulator, D-pad via `adb -s <serial> shell input keyevent`).
No instrumented level: the project has no instrumented `PlaybackController` tests (hermetic ones are
upstream-sync-triage P6). No Robolectric: `Uri`/`MediaItem.Builder` are not usable on the JVM, so Media3 types stay out
of unit tests. The controller (`PlaybackController`) logic that needs a real `ExoPlayer` is covered by the device
checklist, not by mocks.

Assumed answers: Q1 = architect's wording (identity, then full group id + index, then unambiguous language, never
position). Q2 = AC-9 by Media3 code trace (D15). D4 = merged rows on HLS only.

## Existing tests to reuse or adapt

- `ui/feature/player/vm/PlayerVMTestFixture.kt` (lines ~86 mapper mock, ~229-238 `testSubtitleTracks` uses `index =`).
  Adapt: drop `index`. A VM test that reaches `refreshStreamLinksOnce` must stub `mapper.mapSubtitleTracks`.
- `ui/feature/player/vm/PlayerVMTrackSelectionTest.kt` (AC-6/7 live here; the restore tests at lines 34-205 must stay green).
- `ui/feature/player/model/SubtitleLabelsTest.kt`: ported into `SubtitleLabelerTest` (the old file is deleted with
  `SubtitleLabels.kt`; every old assertion must survive as a case, with the same expected strings).
- `ui/feature/player/model/PlayerUIMapperTest.kt`: adapt (no `index`; API labels unchanged byte for byte).
- Upstream sources to port from `git show b755826c:app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/<F>.kt`:
  `SubtitleTrackMergerTest`, `SubtitleTrackSelectorTest`, `SubtitleLabelerTest`, `SubtitleTrackIdentityTest`,
  `PlayerVMSubtitleVariantTest` (see "Port notes" for what must change).
- Not ported (step 2 or out of scope): upstream `PlayerVMSubtitleVariantTest` cases about language-less/forced restore
  across recreation, "deferred until manifest tracks appear", "after eleven empty updates", `retryPlayback_restores...`.

## 1. Risk profile

Score = probability (1-3) x impact (1-3). Risk ids are the architect's R1..R10.

| Risk | P | I | Score | Test at the level where it fails |
|---|---|---|---|---|
| R-sel Selector picks wrong side-loaded track (all stripped group ids are "0"; positional or group-id-only match) | 3 | 3 | 9 | unit `SubtitleTrackSelectorTest` (identity beats group id; same-"0" groups; no coordinates) |
| R3 Child order / prefix changes on rebuild (refresh reorders, HLS to progressive fallback) | 3 | 3 | 9 | unit selector (reordered groups), resolver `findSameSubtitleTrackIndex`, VM re-find; device quality switch |
| R4 Manifest to API linking fails when `file` absent or path differs (url stays "") | 2 | 3 | 6 | unit merger (`file` vs `<file>/index.m3u8`, no `file`), resolver language fallback; device title where both offer a language |
| R-merge Wrong source per language / `fromManifest` miscount (manifest track with unmatched rendition counted as side-load) | 2 | 3 | 6 | unit `SubtitleTrackMergerTest` (D5 case is mandatory) |
| D9 Off detection: manifest-only row with `url == ""` turns subtitles off when picked | 3 | 3 | 9 | unit VM/controller-facing: `isOff` extension test + VM `selectSubtitle` delegates the row; device (AC-2 select) |
| R5 Re-send loop: VM re-sends current row inside `onTracksChanged`; must stop after one | 2 | 3 | 6 | unit VM (assert exactly one `selectSubtitle` after identical repeated reports) |
| R1 Focus drops from open Subtitles page when rows change | 2 | 3 | 6 | device (cannot be unit-tested: Compose focus); unit only asserts equal rows do not re-emit |
| AC-5 progressive regression (rows or side-load behaviour change) | 2 | 3 | 6 | unit VM (`subtitleTracks = null` keeps API rows); unit factory spec (progressive: no m3u8 mime, same subtitle specs); device |
| Restore (AC-7) regression: saved URL/lang no longer resolves over merged rows | 2 | 3 | 6 | unit `PlayerVMTrackSelectionTest` extension + resolver `sameSubtitleLanguage` |
| D1 HLS mime/side-load config wrong (API subtitles still dropped, or HLS guessed as progressive) | 2 | 3 | 6 | unit factory spec (pure); device AC-1 (only real proof) |
| R2 / AC-9 failing subtitle pauses loading or goes fatal | 1 | 3 | 3 | UNTESTABLE hermetically (Q2); Media3 trace D15; opportunistic device |
| Label regression for API rows (D10 relabel) | 2 | 2 | 4 | unit `SubtitleLabelerTest` (old cases ported), `PlayerUIMapperTest` |
| R6 Data-shape churn (`index` removed, `PlayerPreview` compile) | 2 | 1 | 2 | compile via `check.full`; accepted |
| R7 main-thread merge cost | 1 | 1 | 1 | accepted (n about 10), note only |
| R8 cache growth from selected side-loads | 1 | 1 | 1 | accepted |
| R9 debug "stream source" shows side-load host | 2 | 1 | 2 | accepted, deferred to playback-controller-split (Q3) |
| R10 baseline profile not regenerated | 1 | 1 | 1 | accepted, release procedure |
| D13/AC-10 TTFF or warm-cache regression | 1 | 2 | 2 | device observation only; accepted if the note is recorded |

Highest: 9 (selector, child order, D9). Every score of 6+ has a test at its failing level except R1 (device by nature)
and the AC-9 family (3, below threshold, Q2).

## 2. Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | `PlayerVMTrackSelectionTest.tracksUpdated_hlsApiOnlyLanguage_appearsInRowsAndIsSelectable` (new) | an API-only language that the player reports as a side-load (prefixed id `1:<key>`) is a picker row and `SelectSubtitle` delegates that row to the controller |
| AC-1 | unit | P0 | `PlaybackMediaItemSpecTest.hls_attachesEveryApiSubtitle_withStableKeyAsIdAndLabel` (new, pure spec) | every API subtitle becomes a configuration with id = label = `stableSubtitleKey()`, FORCED from `forced`, and `APPLICATION_M3U8` mime for `StreamType.HLS` (the input `DefaultMediaSourceFactory` needs) |
| AC-1 | device | P0 | checklist D-1 | the subtitle actually renders on HLS (only real proof of D1) |
| AC-2 | unit | P0 | `SubtitleTrackMergerTest.merge_carriesManifestOnlyLanguage_withLanguageNameLabel` and `merge_keepsSideLoadedTrack_whenItsLanguageIsMissingFromTheManifest` (port) | manifest-only row is listed with a language-name label, `url == ""`, not Off |
| AC-2 | unit | P0 | `SubtitleTrackUIStateTest.isOff_isFalse_forManifestRowWithEmptyUrl` (new) + `PlayerVMTrackSelectionTest.selectSubtitle_manifestOnlyRow_isDelegatedNotTreatedAsOff` | D9: an empty `url` no longer means Off |
| AC-2 | device | P0 | checklist D-2 | manifest-only row is selectable and renders |
| AC-3 | unit | P0 | `SubtitleTrackMergerTest`: `merge_usesLargerLanguageGroup_preferringHlsOnTies` (parameterised, port), `merge_choosesSourceIndependentlyForEachCanonicalLanguage`, `merge_keepsUnmatchedManifestAndSideLoadedTracks_whenLanguageIsMissing`, `merge_hidesSideLoadedCopies_whenManifestCoversTheirLanguages` | one source per language: more tracks wins, HLS on a tie, unknown-language rows kept unless proven duplicates |
| AC-3 | unit | P0 | `SubtitleTrackMergerTest.merge_countsManifestByFromManifestFlag_notByRenditionUri` (new, D5) | a manifest track whose rendition did not match (`playerTrackUri == null`, `fromManifest = true`) counts as manifest in the per-language count |
| AC-3 | unit | P1 | `merge_matchesApiToRenditionByFilePath_whenLanguageOrderDiffers`, `merge_matchesSideLoadedTrackByStableUrl_whenHostAndTokenChange` (port), `merge_withoutApiFile_keepsRenditionUrlEmpty` (new, R4), `merge_doesNotPairSameLanguageVariants_withoutExactIdentity`, `merge_doesNotTreatDisplayLabelAsTrackIdentity` | enrichment (url, sourceFile, forced) only on unique identity match |
| AC-3 | unit | P1 | `merge_groupsVariantsByLanguage_keepingManifestOrder`, `merge_marksPartialVariant_andOrdersItLastWithinItsLanguage`, `merge_exposesOnlyOff_whenPlayerReportsNoTracks` | ordering, forced last, Off first |
| AC-4 | unit | P0 | `SubtitleTrackSelectorTest.select_picksEachSideLoadedTrackByStrippedFormatId_whenAllStrippedGroupIdsAreZero` (new: three candidates with group ids `1:0`,`2:0`,`3:0`, format ids `1:<k1>`.. ) | Q1 core: prefix-stripped identity distinguishes side-loads; the stripped group id alone cannot |
| AC-4 | unit | P0 | `select_survivesTrackGroupsBeingReordered` (port) and `select_prefersIdentityOverFullGroupId_whenChildOrderChanged` (new) | R3: after a reorder, identity wins over a stale `groupId` + index |
| AC-4 | unit | P0 | `select_usesFullGroupIdAndIndex_whenIdentityIsAbsent`, `select_doesNotFallBackToCoordinates_whenNothingIdentifiesTheTrack` (port of `select_returnsNull_ratherThanGuessing...`; delete the port of `select_fallsBackToCoordinates...`) | order is identity, group id + index, unambiguous language; never position |
| AC-4 | unit | P0 | `select_matchesByLanguage_onlyWhenUnambiguous`, `select_rejectsReusedCoordinatesAndIds_withDifferentLanguage`, `select_rejectsForcedTrack_forFullSubtitleRow` (port) | semantic filter first; ambiguity yields null |
| AC-4 | unit | P1 | `SubtitleTrackIdentityTest`: `withoutMergedSourcePrefix_stripsOneLeadingNumberColon`, `..._keepsIdsWithoutPrefix`, `..._doesNotStripEmbeddedColons`; port of the three `stableSubtitleKey_*` plus `sameSubtitleIdentity_treatsFileAndIndexM3u8AsOne` | the shared strip helper and `<file>/index.m3u8` normalisation |
| AC-5 | unit | P0 | `PlayerVMTrackSelectionTest.tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows` (new) | `onTracksUpdated(subtitleTracks = null)` leaves rows equal to the API rows, no merge |
| AC-5 | unit | P0 | `PlaybackMediaItemSpecTest.progressive_keepsAllApiSubtitles_includingEmbedTrue_withoutM3u8Mime` (new) | D2: no `embed` filter, no HLS mime on progressive; same specs as master `PlaybackController.kt:606-635` |
| AC-5 | device | P1 | checklist D-5 | progressive title: Off by default, same rows, side-load renders |
| AC-6 | unit | P0 | `PlayerVMTrackSelectionTest.tracksUpdated_keepsSelectedRow_whenSameMergedRowStillExists` (new) | selection preserved by identity across a second report |
| AC-6 | unit | P0 | `..._selectedRowReordered_followsRowToNewIndex` and `..._selectedRowDisappears_goesOffAndDisablesText` (new) | re-find by identity; a dropped row ends at Off and `selectSubtitle(off)` is sent (picker never says Off while text plays) |
| AC-6 | unit | P0 | `..._identicalRepeatedReports_sendSingleReSelect` (new, R5) | exactly one `selectSubtitle` re-send, no loop; equal rows are not re-emitted (state emission count) |
| AC-6 | unit | P1 | `..._audioEmptyUpdate_keepsAudioRowsAndIndex`, `..._textOnlyUpdate_updatesSubtitleRows` (port of `tracksUpdated_keepsAudioTracks_whenUpdateCarriesOnlyTextTracks`) | D4 guard change: text-only report updates rows, audio kept |
| AC-6 | unit | P1 | `AudioTrackPreferenceResolverTest.findSameSubtitleTrackIndex_*`: off to off; by source identity; by full group id + index; by unambiguous canonical language; ambiguous gives Off | the new resolver method (no resolver tests exist today; new file under `vm/`) |
| AC-6 | unit | P1 | `PlayerVMTrackSelectionTest.refreshStreamLinks_recomputesApiRows_andMergesWithPlayerRows` (new; stub `mapper.mapSubtitleTracks`) | D11 `apiSubtitleTracks` recompute |
| AC-7 | unit | P0 | existing `tracksUpdated_restoresPreferredSubtitleByUrl_beforeLanguage`, `..._ByStableUrl_whenSignedUrlChanges`, `..._ambiguousSubtitleLanguage_withoutUrl_reappliesOffRow`, `..._reappliesOffRow_whenSavedSubtitleDoesNotResolve`, `..._restoreDoesNotRewritePreferencesFromIntermediateState` rerun over merged rows (feed `subtitleTracks` non-null in a parametrised variant) | master restore behaviour unchanged |
| AC-7 | unit | P0 | `PlayerVMTrackSelectionTest.tracksUpdated_restoresSavedUrl_onApiLinkedManifestRow` (new) | rows enriched with API `url` resolve a saved URL |
| AC-7 | unit | P1 | `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_matchesRuAgainstRus_viaSameSubtitleLanguage` (new, D12) and `..._stillAmbiguous_returnsNull` | canonical language comparison replaces `==` |
| AC-7 | unit | P2 | `SubtitleLinkTest` extension: payload without `file` parses to `file == null`; payload with `file` maps | D3 additive field |
| AC-8 | unit | P0 | `SubtitleLabelerTest` (new; absorbs every case of `SubtitleLabelsTest`: 3-letter and bibliographic codes, locale names, ordinals, forced qualifier, AI code, unknown "Subtitles N", Russian ISO-3 rule) with `FakeResourceProvider` | API-row labels byte-identical to master |
| AC-8 | unit | P1 | upstream `SubtitleLabelerTest` ports: `apply_neverShowsSubtitleFileNames`, `apply_neverShowsCdnChannelNumbering`, `apply_usesManifestLabels_whenTheyReadAsNamesAndSeparateTheRows`, `apply_keepsPartialMarker_whenAManifestLabelSeparatesTheRows`, `apply_doesNotQualifyRowsThatAlreadyReadDifferently`, `apply_preservesUnknownLanguageInformation_beforePositionalFallback` | manifest rows never show raw ids/file names; no raw ids |
| AC-8 | unit | P1 | `subtitleLabeler(resources)` factory test: strings come from `FakeResourceProvider` keys `player_subtitle_ai_generated/_forced/_variant_label/_unknown` | D10 wiring, locale from `resources.locale` |
| AC-8 | unit | P1 | `PlayerUIMapperTest` subtitle cases | mapper emits `isForced`, `sourceFile`, labels via labeler, Off row first |
| AC-8 | manual | P2 | wording `(owner judges)`, checklist D-2 label observation | final label text is the owner's call |
| AC-9 | UNTESTABLE | n/a | see below | Media3 trace D15 |
| AC-10 | device | P2 | checklist D-6 | TTFF and warm cache, observation |
| AC-11 | unit | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | gate; JUnit 5, `FakeResourceProvider` where strings are mapped; new classes listed above all exist |

Row counts: P0 = 20 (18 unit incl. the AC-11 gate, 2 device), P1 = 13 (12 unit, 1 device), P2 = 3 (1 unit, 1 manual, 1 device). P0 must exist before review.

## 3. Failing-first test (feature, not a bug fix)

Master drops API subtitles on HLS and the VM never sees player text tracks. Verified by reading base code:
`git -C {wt} show a97ee2b1:app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` lines 651-675
(`HlsMediaSource.Factory(...).createMediaSource`, subtitleConfigurations unread) and `PlayerVM.kt:211` (`onTracksUpdated`
takes audio only). Written first, in this order, each fails on base (does not compile or does not pass) and passes after:

1. `PlayerVMTrackSelectionTest.tracksUpdated_hlsApiOnlyLanguage_appearsInRowsAndIsSelectable` (AC-1/2 user-visible
   behaviour through VM state; on base the callback has no subtitle parameter, so rows can never change).
2. `SubtitleTrackSelectorTest.select_picksEachSideLoadedTrackByStrippedFormatId_whenAllStrippedGroupIdsAreZero`
   (the Q1 defect: the literal AC-4 rule cannot tell side-loads apart; base selector `findTextTrack` is positional-free
   but cannot see prefixed ids from a merged source, `PlaybackController.kt:866-942`).
3. `PlaybackMediaItemSpecTest.hls_attachesEveryApiSubtitle_...` (D1 input).

## 4. Test specifications and port notes

Builder helpers: copy `manifestTrack`, `apiTrack`, `playerTrack`, `offTrack` helpers from upstream tests and adjust to
`SubtitleTrackUIState` without `index`, with `fromManifest`. Do not mock the merger, selector, labeler or resolver in
VM tests: they are pure and stateless (D16). The controller is mocked (it is a collaborator, as in the existing fixture).

`PlaybackMediaItemSpecTest` (pure): `PlaybackMediaItemFactory` must expose the subtitle specs (id, label, language, mime,
selection flags, uri string) as a pure function so the test needs no `Uri`. Cases: HLS gets m3u8 mime; progressive does
not; id = label = `stableSubtitleKey(url)`; `forced = true` sets FORCED; `forced = null/false` does not; empty list;
`.srt`/`.vtt` mime by extension (reuse the old `subtitleMimeType` cases); `embed = true` row is kept on both transports.
If the implementer cannot expose such a function, this is a design defect to bounce, not a reason to drop the test.

Merger port notes: drop upstream cases that depend on `playerTrackUri != null` meaning manifest; add D5 case (manifest
row, `fromManifest = true`, no matched rendition). Add: unknown-language side-load identical (by identity) to a manifest
row is dropped, unknown-language non-duplicate kept. Add a determinism case: `merge(a, b) == merge(a, b)` (R5 guard).

Selector port notes: remove upstream `select_fallsBackToCoordinates_whenTheManifestExposesNoIdentity` and any
`matchByCoordinates` use (replace by its negation, listed above). Keep the AC-4 "never by position" assertion as an
explicit test: two same-language candidates, row without identity, same index as one of them, expected `null`.

VM re-send test (R5): after the first report call `verify(exactly = 1)`-style on the controller `selectSubtitle`, send the
same report again, assert still 1 and `state.subtitleTracks` emitted once (equal rows, no re-emit).

## 5. Q1 / Q2 / D4 sensitivity

- Q1 rejected (owner keeps literal "prefix-stripped group id"): the three selector P0 tests above that assert identity
  precedence change. Replace by a test that two side-loads (stripped group id "0" for both) are **not** distinguishable by
  group id alone and the selector must then return null or fall through to language, which would make AC-1/AC-2 with two
  same-language API subtitles non-selectable. That outcome contradicts AC-1 in practice; I would flag it back to the
  owner rather than weaken the tests. The identity-stripping tests in `SubtitleTrackIdentityTest` stay either way.
- Q2 rejected (owner wants a test, not a trace): needs Robolectric or the P6 instrumented fixtures; AC-9 becomes a
  P1 `instrumented` test `PlaybackControllerSubtitleFailureTest` (HLS fixture from `player-test-fixtures` plus a 404
  side-load, assert no `onPlayerError`, no CDN fallback, video position advances), run only through `make DEVICE=<serial> itest`.
  That moves work out of this run's M size and onto P6.
- D4 rejected (merge on progressive as upstream): AC-5 changes; delete `tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows`
  and the progressive factory case about unchanged rows, add the upstream container-track cases
  (`c798fdcb`, `5a43798a`) and a progressive-merged-rows device check. The `embed` filter question (D2) then reopens.

## 6. UNTESTABLE

- AC-9 (failing side-load 404/TLS does not break playback): needs a real `ExoPlayer` + failing source on the JVM
  (no Robolectric) and no hermetic title exists; evidence is the Media3 1.11.0 code trace in D15, recorded in the spec.
  Residual risk R2 (partial-then-failed subtitle stalling the merged period) is watched during the device pass only.
- Text-off default at build (`PlaybackController.kt:393`) and the parameter building in `selectSubtitle` /
  `applySubtitleTrackSelection`: need a real `ExoPlayer`; covered by device D-1..D-5 only, left to
  playback-controller-split / P6 for automation.
- Compose focus behaviour in `SubtitlesPage` (R1): no Compose UI test infrastructure for TV focus in this project;
  device only.
- `PlaybackController` warm-up shape (D13): unchanged code; observed only (D-6).

## 7. Manual / device checklist for qa-device

Preconditions: authenticated emulator, lease via `tools/emulator-resource-lock.sh`, worktree dev APK installed with
`tools/android-apk-install-preserve`, `adb -s <serial>` on every call. Owner supplies (Q6) before verify: title H1 =
HLS whose API subtitle list contains a language the manifest lacks AND a manifest language the API lacks; title H2 = HLS
where both offer one language (R4); optional H3 with a forced track; title P1 = progressive-only. Evidence files must
not include tokens or raw logcat (run `tools/mobile-evidence-audit.sh`).

D-1 (AC-1). Open H1 (HLS). Wait for playback; D-pad centre to open the overlay, move to Subtitles, open the page.
Expected: the API-only language is listed with a language-name label; select it; cues render within a few seconds.
Must not: raw id, file name or `1:xxx` text as a label; Off shown while cues render; duplicate rows for one language.
D-2 (AC-2, AC-8). On the same page select the manifest-only language. Expected: listed with a language name (not a
rendition id), selecting it renders cues and the row stays highlighted. Must not: subtitles turn off after the pick (D9);
picker jumps back to Off. Record the label text for the owner.
D-3 (AC-3). On H2: the shared language appears exactly once. Expected: one row per language. Must not: two rows for one
language unless they are distinct variants (forced, AI) with qualifiers.
D-4 (AC-4/6/R3). On H1 select an API-only language, then switch quality (OK, Quality, other quality) and then back.
Expected: the same language remains selected and rendering after each switch. Must not: a different-language subtitle
renders (positional mis-pick), picker says Off while cues show, or cues stop with the picker still showing a language.
D-5 (AC-5). Open P1 (progressive). Expected: subtitles Off by default; rows identical to the API list as on master; pick one,
it renders. Must not: extra container or manifest rows, changed order, changed labels.
D-6 (AC-10). On H1 cold start twice, with and without a warm episode (series next-episode prefetch if available). Expected:
time to first frame comparable to master build of the same title (note both numbers; subjective regression over about
half a second is a finding). Must not: visible extra buffering or a subtitle HTTP fetch before a subtitle is picked
(optional: `adb -s <serial> shell dumpsys netstats` is not needed; observing no regression is enough).
D-7 (R1). Open the Subtitles page immediately at start (during first report) and during a quality switch. Expected:
focus stays inside the panel, D-pad Up/Down still moves. Must not: focus leaves the panel, crash, empty page.
D-8 (AC-7). On H1 pick a subtitle, back out, reopen the same item. Expected: the saved subtitle is restored (same as master
behaviour). Must not: Off, or the wrong language.
D-9 (AC-9, optional). If a title with a dead subtitle URL turns up: play it. Expected: video plays, no error dialog, no
CDN fallback. Must not: fatal error, repeated rebuffering.

## Counts

See the row counts under the traceability table. Manual device steps: 9 (D-1..D-9; D-9 optional).
