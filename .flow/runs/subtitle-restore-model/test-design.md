# Test design: subtitle-restore-model (Part 1)

Base `f351d988`. Scope: AC-1..AC-12 (AC-13..15 live in `subtitle-switch-text-disable`). Inputs: `brief.md`,
`design-architect.md` (D1-D12, R1-R9). Test paths relative to `app/src/test/kotlin/com/kino/puber/`.
Levels: `unit` (JUnit 5, `MainDispatcherExtension`, mockk fakes as in `PlayerVMTestFixture`, `FakeSharedPreferences`),
`device` (emulator, D-pad, evidence file). No `instrumented` tests in this run.

## Pending gate-1 answers (the design assumes the architect's recommendations)

| Q | Assumed answer | Test consequence | If the owner answers otherwise |
|---|---|---|---|
| Q1 | AC-4 reworded: an old preference that resolved to a row resolves to the same row; a formerly ambiguous language may now resolve to the single manifest row | AC-4 tests pin only "resolved before => same row" plus the existing `AudioTrackPreferenceResolverTest.kt:115-126` unchanged. One test pins the new Off-to-manifest-row case (T-4c) | Original wording ("exactly as before"): T-4c flips to expect `NO_MATCH` and D6 rule 5 loses its manifest preference; the architect must change the design. T-4c is the only test affected |
| Q2 | New AC-16: saved preference kept after a restore that found nothing, until the viewer picks (`subtitlePickedThisMedia`) | Tests T-16a..c below (VM level) | Not adopted: drop T-16a..c; add a known-gap note: after AC-5's forced-only case the first audio change wipes the saved full subtitle. T-8c and the pass-through tests stay |
| Q3 | No readable-label rule in `findSameSubtitleTrackIndex` | No test; existing `findSameSubtitleTrackIndex_*` tests stay unchanged | Adopted label rule: add one resolver test for two same-language labelled rows in the in-session re-find |

AC-16 text used here: Given the saved subtitle did not resolve on this stream and the viewer made no subtitle pick, When
the audio track is changed (or any other save runs), Then the stored subtitle preference (all four values) is written back
unchanged; and Given the viewer then picks a subtitle or Off, Then the new value replaces it.

## 1. Risk profile

Score = probability (1-3) x impact (1-3). Score >= 6 has a test at the level where it fails.

| ID (design) | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Stale or partial keys: a forced flag or label survives Off and filters rows later | 2 | 3 | 6 | T-3a, T-3b (repository, real `FakeSharedPreferences`) |
| R2 | Identity collision / wrong row from a stored `subtitle:<NAME>`; side-load positional id `<n>:0` stored | 2 | 3 | 6 | T-1d, T-10a, T-10b, T-1e, T-2c (unit, resolver and key) |
| R3 | Saved preference wiped after a restore that found nothing (data loss, pre-existing) | 3 | 3 | 9 | T-16a..c (VM). Without Q2 it is accepted as a known gap |
| G1/G2 | Manifest-only row saved as Off, or language only (the bug itself) | 3 | 3 | 9 | T-1a, T-2a (VM save), T-1b (repository), device AC-11 |
| G3 | Forced-only row chosen for a full-subtitle viewer | 2 | 2 | 4 | T-5a, T-5b (accepted at unit level, below 6) |
| R4 | Over-strict label filter after a NAME rename gives Off | 2 | 2 | 4 | T-6d (tolerance: row without readable label passes). Rename itself is `unverified`, accepted with a note |
| R5 | Intent-first fallback overwrites the intent | 1 | 2 | 2 | T-8c pins the order only. Accepted |
| R6 | Old preferences change outcome | 2 | 3 | 6 | T-4a, T-4b, existing resolver tests, T-4c |
| R7 | Downgrade or upstream-written data | 1 | 2 | 2 | T-1d covers the prefixed-equality case. Accepted |
| R8 | Sequencing with `player-vm-split` and Part 2 (D9 re-send after `switchStream`) | 2 | 2 | 4 | No test; process risk. T-8a pins the D9 re-send that Part 2 depends on |
| R9 | Performance/concurrency/focus | 1 | 1 | 1 | Accepted, nothing material |
| New | Migration breakage of 47 mockk verifies (D3): a silently weaker `verify` | 2 | 3 | 6 | Compile-breaking signature (no defaults). Reviewer rule: every `saveTrackPreferences` verify must use the `SubtitlePreference` matcher. T-8b, T-9 keep `exactly = 0` negatives with `any()` on the new arity |
| New | Rebuild intent-first branch re-sends but also persists, or double-sends | 2 | 2 | 4 | `rebuildFastDns_*` / `rebuildBufferPreset_*` stay green (`exactly = 1` `selectSubtitle`, zero saves) |

## 2. Traceability

Priority: P0 must exist before review, P1 should, P2 nice to have.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | T-1a `PlayerVMTrackSelectionTest.pickManifestOnlyRow_savesUnprefixedGroupIdWithLanguageForcedAndLabel` | Picking a manifest-only row with language (group id `0:subtitle:Rus`) and then an audio pick stores `SubtitlePreference("rus", "subtitle:Rus", forced, label)`; no `0:` prefix |
| AC-1 | unit | P0 | T-1b `PlayerPreferencesRepositoryTest.subtitlePreference_roundTripsAllFourValues` | `saveTrackPreferences` then `getPreferredSubtitlePreference` through a new repository instance returns the same four values |
| AC-1 | unit | P0 | T-1c `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_matchesManifestRowByStoredGroupId` | New session with stored `subtitle:Rus` picks the manifest row |
| AC-1 | unit | P1 | T-1d `findSubtitleTrackIndex_matchesPrefixedGroupIdAgainstStoredUnprefixedAndViceVersa` | A stored `subtitle:Rus` matches `0:subtitle:Rus` row and a stored prefixed upstream value still matches by equality (R2, R7) |
| AC-1 | unit | P0 | T-1e `PlayerVMTrackSelectionTest.newSession_restoresManifestOnlyRowFromSavedPreference` | End to end through the VM: saved preference in the interactor stub, tracks reported, the intended row is `selectSubtitle`d with `persist=false` (zero saves) |
| AC-2 | unit | P0 | T-2a `PlayerVMTrackSelectionTest.pickLanguageLessManifestRow_savesIdentityNotOff` | Language-less manifest pick saves `language=null, url="subtitle:<NAME>"`, not `NONE` (fails on base: G1) |
| AC-2 | unit | P0 | T-2b `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_preservesLanguageLessTrackByIdentity` | Ported from f9c9ff38; the language-less row is chosen by identity among several rows |
| AC-2 | unit | P1 | T-2c `SubtitlePreferenceKeyTest.languageLessRow_givesNullLanguageNeverEmpty` | `toSubtitlePreference()` language is null, not `""` (guards D6 rule 0) |
| AC-3 | unit | P0 | T-3a `PlayerPreferencesRepositoryTest.subtitlePreference_noneClearsAllFourKeys` | After saving a full preference, saving `NONE` leaves `subtitle_lang_/url_/forced_/label_` missing; reader returns `NONE` with `isForced == null` (not `false`) |
| AC-3 | unit | P0 | T-3b `PlayerVMTrackSelectionTest.explicitOff_savesNoneAndClearsAllValues` | After picking a row, then picking Off, the next save is exactly `SubtitlePreference.NONE` |
| AC-3 | unit | P1 | T-3c `PlayerPreferencesRepositoryTest.subtitlePreference_partialValuesRemoveOnlyNullKeys` | Saving `(lang, null, null, null)` after a full value removes the three others (null removes the key) |
| AC-4 | unit | P0 | existing `findSubtitleTrackIndex_matchesRuAgainstRus_viaSameSubtitleLanguage` and `_stillAmbiguous_returnsNoMatch` (`:115-126`), unchanged | Old resolver outcomes via URL, language alias and ambiguity stay |
| AC-4 | unit | P0 | T-4a `PlayerPreferencesRepositoryTest.legacyKeysOnly_readAsLangUrlWithNullForcedAndLabel` | Seeding only `subtitle_lang_42`/`subtitle_url_42` reads `(lang, url, null, null)` |
| AC-4 | unit | P0 | T-4b `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_legacyPreference_resolvesToSameRowAsBefore` | Table over old cases: exact URL, stable key (different host or token), single-language row, with forced/label null; the index equals the base result. Includes a URL match on a row that the semantic filter would drop (rules 1-2 are unfiltered) |
| AC-4 | unit | P1 | T-4c `findSubtitleTrackIndex_legacyAmbiguousLanguage_nowResolvesToSingleManifestRow` | Q1 wording: two same-language rows, exactly one `fromManifest`, language-only preference gives the manifest row (was NO_MATCH). Two manifest rows stay NO_MATCH |
| AC-5 | unit | P0 | T-5a `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_rejectsForcedOnlyReplacementForFullPreference` | Ported from 7d5addd2: preference `(rus, null, forced=false)`, only a forced rus row gives NO_MATCH, not the forced row |
| AC-5 | unit | P0 | T-5b `PlayerVMTrackSelectionTest.savedFullSubtitle_streamHasOnlyForcedRow_staysOff` | VM: no `selectSubtitle` for the forced row, selected index is Off. Fails on base (G3: legacy language rule picks it). Note: the legacy lang-only preference (forced null) still picks the forced row; that is by design (AC-4) |
| AC-6 | unit | P0 | T-6a `findSubtitleTrackIndex_usesForcedFlagToChooseBetweenFullAndForcedSameLanguage` | Two rus rows, preference forced=false gives the full row, forced=true gives the forced row |
| AC-6 | unit | P0 | T-6b `findSubtitleTrackIndex_usesMeaningfulLabelToResolveSameLanguageVariants` | Ported from 7d5addd2: two labelled rus manifest variants; stored label chooses the right one |
| AC-6 | unit | P1 | T-6c `findSubtitleTrackIndex_labelComparisonIsTrimmedAndCaseInsensitive` | D6 rule 4 normalisation |
| AC-6 | unit | P1 | T-6d `findSubtitleTrackIndex_rowWithoutReadableLabelPassesLabelFilter` | D7 tolerance: preference with label, API/side-load row without a readable label is still matched by URL or language (progressive fallback, R4) |
| AC-6 | unit | P2 | T-6e `SubtitlePreferenceKeyTest.readableLabelOnly_fileKeyLabelIsNotStored` | A side-load whose label is the file key stores `descriptiveLabel = null` |
| AC-7 | unit | P0 | T-7a `findSubtitleTrackIndex_doesNotRestoreOffWhenLanguageLessIdentityDisappears` | Ported from f9c9ff38: language null, identity absent gives NO_MATCH, not the Off index |
| AC-7 | unit | P0 | T-7b `findSubtitleTrackIndex_emptyLanguageWithoutIdentity_meansOff` | Empty language, null/empty url gives the Off index; this is rule 0 |
| AC-8 | unit | P0 | T-8a `PlayerVMTrackSelectionTest.rebuild_intentDiffersFromSaved_resolvesIntentFirst` | Saved says eng, live intent is rus; after a Fast DNS rebuild and report, `selectSubtitle(rus)` once, no eng, zero saves |
| AC-8 | unit | P0 | existing `PlayerVMTrackSelectionTest.kt:401-457` (`rebuildFastDns_*`, `rebuildBufferPreset_*`), migrated to the new signatures only | Intent order and no persist stay |
| AC-8 | unit | P1 | T-8c `rebuild_intentRowMissing_fallsBackToSavedPreference` | Intent row absent from the report, saved resolves: restored with `persist=false`; pins R5 behaviour |
| AC-8 | unit | P1 | T-8b `newMedia_intentIsNull_usesSavedPreferenceOnly` | After `resetMediaSession` the saved preference alone is used |
| AC-9 | unit | P0 | T-9 `rebuildFastDns_audioEmptyReportWithTextRows_reSendsIntent_andSavesNothing` | Rebuild, a report with empty audio and text rows: one `selectSubtitle(pick)`, zero saves, audio rows kept. No production change expected, so it passes on base (regression pin) |
| AC-10 | unit | P0 | T-10a `SubtitlePreferenceKeyTest.sideLoadRow_neverStoresPositionalGroupId` | Side-load with group id `0:0`/`1:0`: key is `url`, else `sourceFile`, else stable `playerTrackId`, never `<n>:0` |
| AC-10 | unit | P0 | T-10b `PlayerVMTrackSelectionTest.pickSideLoadedRow_savesUrlNotGroupId` | Through the VM; saved url equals the API URL, no `<n>:0` anywhere in the saved preference |
| AC-10 | unit | P1 | T-10c `findSubtitleTrackIndex_positionalSideLoadGroupIdNeverMatches` | A stored `0:0` does not match a side-load row that happens to have that group id (identities exclude it for non-manifest rows) |
| AC-10 | unit | P1 | `SubtitlePreferenceKeyTest.keyOrder_urlThenSourceFileThenGroupIdThenTrackUriThenTrackId` | D4 order, and all-empty gives null never `""` |
| AC-11 | device | P0 | Manual checks M1-M4 (steps 1-2 persistence and re-entry; step 3 quality switch, row present; step 4 4b/4c quality switch, row absent) | Persistence, playback and re-entry on a real HLS title; after a quality switch the row is on when the new stream has it; when it lacks it the picker shows Off, Off is not saved, Auto and re-entry bring the row back. Clause (b) is NOT CHECKED, with the reason, if no such stream is found |
| AC-12 | unit | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | Whole gate green. The 47 migrated `saveTrackPreferences` verifies compile and keep their negative cases |
| AC-16 (Q2) | unit | P0 | T-16a `PlayerVMTrackSelectionTest.savedSubtitleUnresolved_noPick_audioChangeKeepsStoredPreference` | Stored preference unmatched (forced-only stream or missing row), intent null, `SelectAudioTrack`: the save carries the stored `SubtitlePreference` unchanged, not `NONE`. Fails on base (the base writes null/null at `PlayerVM.kt:1856-1858`) |
| AC-16 (Q2) | unit | P0 | T-16b `..._pickAfterUnresolved_replacesStored` | After the viewer picks a row, the save carries the new preference |
| AC-16 (Q2) | unit | P1 | T-16c `..._explicitOffAfterUnresolved_clearsStored` | Explicit Off sets the flag, so `NONE` is written; also the flag resets on new media (`resetMediaSession`) so the next title's unresolved restore passes through again |

Failing-first selection among the traceability rows: see section 3.

## 3. Bugs (tests that fail on the base commit today)

Base read with `git -C <wt> show f351d988:<path>`; not checked out.

- G1 (language-less manifest row saved as Off): `PlayerVM.kt:1849-1871` writes `language`/`url` only when non-empty, so a manifest row with `url=""` and no language writes null/null, which removes both keys. T-2a fails today.
- G2 (manifest row with language stores only the language): T-1a fails today.
- G3 (forced-only row chosen for a full viewer): `AudioTrackPreferenceResolver.kt` rule `unambiguousSubtitleLanguageMatch` ignores forced, and `PlayerVM.kt:606-608` applies it with `persist=false`. T-5b fails today.
- R3 (wipe after an unresolved restore): T-16a fails today.

**Failing-first test: T-2a `PlayerVMTrackSelectionTest.pickLanguageLessManifestRow_savesIdentityNotOff`**, written first.
Compile caveat: the tests use the new `SubtitlePreference` signature, which does not exist on base. To show a red run on the base
commit, first write T-2a (and T-5b) as a throwaway against the base signature
(`saveTrackPreferences(42, any(), any(), null, null)` is called, so `verify(exactly=0)` of that null/null call fails) and run it
once red. After the signature migration, the same test is rewritten against `SubtitlePreference` and must be red against a stub
that returns `NONE`, then green. Order for the implementer: T-2a and T-5b red on base, then the model, then green.

Tests that pass on base and are regression pins: T-9, existing `:401-457`, T-4a/b (as a table of old outcomes), T-7b.

## 4. Test-fixture notes

- `PlayerVMTestFixture.kt:107-108,116`: replace the two `getPreferredSubtitleLang/Url` stubs with
  `every { interactor.getPreferredSubtitlePreference(any()) } returns SubtitlePreference.NONE`, and the save stub with
  a 4-argument `any()`. Add a helper `stubSavedSubtitle(SubtitlePreference)` to avoid per-test mockk wiring (the same helper name
  upstream calls `stubPreferredSubtitle`).
- Add a `manifestRow(lang, groupId, forced, label)` builder in the fixture or beside `AudioTrackPreferenceResolverTest.kt:13-19`
  (upstream `manifestTrack(...)` in `PlayerVMSubtitleVariantTest` is the model).
- Port sources (read with `git show <sha>:app/src/test/...`, port by intent, keep fork names):
  - 7d5addd2 `AudioTrackPreferenceResolverTest`: `rejectsForcedOnlyReplacementForFullPreference`, `usesMeaningfulLabelToResolveSameLanguageVariants`,
    `doesNotUseCoordinatesAcrossMediaSources` (it asserts the absence of a coordinate rule, which holds here: no coordinates), `rejectsReusedIdentityWithDifferentLanguage`
    (check against the fork's D7 language clause: row language blank is tolerated, a different language is not).
  - 7d5addd2 `PlayerPreferencesRepositoryTest.subtitlePreferenceMetadata_persistsAndClearsSemanticIdentity` becomes T-1b plus T-3a.
  - f9c9ff38 `preservesLanguageLessTrackByIdentity`, `doesNotRestoreOffWhenLanguageLessIdentityDisappears` become T-2b, T-7a.
  - `PlayerVMSubtitleRestoreTest` (7d5addd2): port its rebuild/pass-through cases only where Part 1 owns the behaviour (T-8a, T-9, T-16). Its `qualityChange_*` and `switchStream_*` cases belong to `subtitle-switch-text-disable` (they assume the text disable in `switchStream`) and are not ported here.
  - Do not port: `PlaybackControllerSubtitleDefaultsTest`, `PlaybackControllerNetworkTracksTest` (instrumented), `PlayerScreenE2ETest`.
- Tests assert through the public surface: the resolver's return value, the repository reader, `interactor.saveTrackPreferences` arguments, `playbackController.selectSubtitle` calls, and VM content state. The class under test (`PlayerVM`, `PlayerPreferencesRepository`) is never mocked; the interactor, controller and mapper are the fakes, as in the existing fixture.
- `SubtitlePreferenceKey.kt` helpers are `internal`, called directly by `SubtitlePreferenceKeyTest`; they are pure functions.

## 5. Manual checks (AC-11, device)

Owner decision: HLS title with a manifest-only row (Psyren S1E1 or Outlander). Lease an emulator via
`tools/emulator-resource-lock.sh`, `adb -s <serial>` for every adb call, install the worktree's own dev APK with
`tools/android-apk-install-preserve`. No uninstall, no data clear. Evidence goes to the run's evidence file, with
`tools/mobile-evidence-audit.sh` run before sharing. Forced and language-less cases are unit-only (no title is known).

1. Open the title, start playback on HLS (the debug overlay or stream info shows `.m3u8`). Open the subtitle picker; the list shows the manifest-only row (no API URL; marked by the debug label or log). Select it.
   Expected: the subtitle renders cues. Must not: the picker shows Off after the pick, or the pick selects another language.
2. Press Back, leave the player, and open the same episode again.
   Expected: the same row is already selected in the picker and cues render without any pick (M1, core of AC-11). Must not: Off, a different row, or the API variant of the same language.
3. (AC-11 clause a) Same session, pick the manifest-only row first, then change quality to one whose stream still offers that row (settings, another HLS quality), wait for tracks.
   Expected: the same row is on after the switch and cues render. Must not: Off, or a different row. A brief flash of cues going off and on is not a defect. Off or a wrong row after the switch FAILS AC-11; it is a failure of this run, not an observation.
4. (AC-11 clause b) Switch to a quality whose stream lacks the row. Manual quality plays `url.hls` with no manifest subtitles (e.g. Outlander S1E5 at 720p; confirm via the stream info that the manifest has no subtitle row). Wait for tracks.
   Expected: the picker shows Off, and the Off is not saved. Then (4b) select Auto (or the quality that has the row): the row comes back on. Then (4c) Back out of the player and re-enter the episode: the row is on again, which proves Off was not saved. Must not: the row stays Off after Auto; re-entry shows Off; the saved preference becomes NONE (check the player's subtitle-restore log line for the save, filtered by tag).
   If no title with a stream that lacks the row is found, the qa report marks clause (b) `NOT CHECKED` with the reason (titles tried, what the stream info showed). It must not be passed by observation or by clause (a).
5. (Rollout) With a stale old-style preference on the device from before the install (the install preserves data), open a title where the viewer previously picked a subtitle.
   Expected: the previous pick still resolves (AC-4), or a title that used to be Off because of ambiguity may now show the single manifest row (Q1). Must not: a crash, or a forced-only row for a full viewer.
6. Pick Off explicitly, leave, re-enter.
   Expected: Off stays. Must not: a subtitle returns.

Logs: only the player's own subtitle-restore lines, filtered by tag; no broad logcat in evidence.

## 6. UNTESTABLE

- AC-11 on forced and language-less rows by device: no title with a forced or language-less manifest row is known (sideload-merge AC-2/5/9 NOT CHECKED). Covered by unit tests T-2a, T-2b, T-5a, T-5b only; the unit level is the accepted evidence by the owner's decision.
- R4 NAME stability across sessions on the CDN: `unverified`, cannot be asserted offline. Device observation M1 over two sessions is the only (weak) evidence.
- The Media3 1.11.0 group id shapes (`subtitle:<NAME>` and `0:subtitle:<NAME>`) are asserted in unit tests from `research.md: 5` fixtures, not from a live ExoPlayer; a live check needs the instrumented fixtures of P6-A (Part 2). M1 on the device covers the real id on one title.
- Downgrade behaviour (R7): device downgrades are not authorised; only the equality case is unit-tested.

Perf track: off (tracks: none). No metric, baseline, or threshold.
