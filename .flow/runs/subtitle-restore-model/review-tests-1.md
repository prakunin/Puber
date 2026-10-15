LENS: tests  ROUND: 1  BASE: f351d988  HEAD: d538e15b

Suite: check.full BUILD SUCCESSFUL; 1632 tests, 0 skipped/failures/errors (incl. PlayerVMSubtitlePreferenceTest 14, SubtitlePreferenceKeyTest 7, AudioTrackPreferenceResolverTest 25, PlayerPreferencesRepositoryTest 6, PlayerVMTrackSelectionTest 44).
Traceability: every P0 row has a test under its spec name (T-1a/b/c/e, T-2a/b, T-3a/b, T-4a/b + unchanged :115-126, T-5a/b, T-6a/b, T-7a/b, T-8a + migrated rebuild tests, T-9, T-10a/b, T-16a/b); P1 rows present. AC-11 by device checks M1-M4 as specified. Failing-first T-2a, T-1a, T-5b, T-16a, T-8a would fail on base; T-16c would fail without the reset in resetMediaSession.
Signature migration: positive verifies match on language and url (as strict as before); `exactly = 0 ... any()` negatives unchanged. Two slightly weaker, see F3/F4.

### F1 [minor] Resolver rule 4 (label match) never decides any test; deleting it keeps the suite green
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolverTest.kt:187-201 (production `subtitleLabelMatch` in AudioTrackPreferenceResolver.kt)
- Problem: T-6b/T-6c reach the answer through rule 3 (identity) after the label filter; T-6d and VM tests pass `label = null` or resolve by URL/language. No test depends on rule 4.
- Failure scenario: delete `{ subtitleLabelMatch(candidates, preferredLabel) }` or break its normalisation; suite green. In production the rule picks the row when the saved identity is gone (renamed group) and the label still matches one row while a label-less same-language row is also a candidate; without it the result is NO_MATCH (Off).
- Fix: one resolver case only rule 4 can decide, e.g. a manifest row labelled "Russian Full" plus a second label-less manifest row of the same language, `find(..., "rus", "subtitle:Renamed", label = "Russian Full")` expects the labelled row.

### F2 [minor] Pass-through of the stored forced flag and label (D8, AC-16) is not pinned
- Where: PlayerVMSubtitlePreferenceTest.kt:296-315, 336-345; PlayerVMTrackSelectionTest.kt:440, 492
- Problem: every pass-through test stubs the saved preference with `isForced = null, descriptiveLabel = null` and asserts only those nulls or language/url. D8 says all four values pass through unchanged.
- Failure scenario: pass-through rewritten as `SubtitlePreference(saved.language, saved.url, null, null)`; all green; in production an audio pick after an unresolved restore drops `forced = false`, so next session the forced-only row comes back (AC-5 lost).
- Fix: stub `isForced = false, descriptiveLabel = "Russian Full"` in `unresolvedSavedSubtitleVM()` and assert the exact `SubtitlePreference` saved.

### F3 [minor] Migrated "no wipe" negatives forbid only the exact NONE, not any null-language/null-url save
- Where: PlayerVMTrackSelectionTest.kt:388; PlayerVMSubtitlePreferenceTest.kt:37
- Problem: base forbade any save with null lang and null url; migrated version forbids only `SubtitlePreference.NONE`, so `(null, null, false, null)` (stale-key shape, risk R1) passes.
- Fix: `verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), match { it.language == null && it.url == null }) }`.

### F4 [nit] One positive saveTrackPreferences verify leaves the subtitle argument as any()
- Where: PlayerVMTrackSelectionTest.kt:99 (`selectTrack_savesLangToPrefs`)
- Problem: spec reviewer rule asks every positive verify to name the SubtitlePreference; this one passes `any()` (was `any(), any()` on base, so not weakened by the migration).
- Fix: 4th argument `SubtitlePreference.NONE`.
