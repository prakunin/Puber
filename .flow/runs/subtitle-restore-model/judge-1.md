# Gate - subtitle-restore-model - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 2e8d4be596fdead27bceccf16106edbf39cf9bdc   **Base:** f351d988

## Done as asked

I read the production diff myself (`git diff f351d988...HEAD -- app/src/main`). It touches 6 production files.

- `domain/model/SubtitlePreference.kt` is new. D1 is followed.
- `PlayerPreferencesRepository` has one reader. Forced is read through `contains`. The four keys are put or removed in a single `edit {}` (D2, AC-3).
- `PlayerInteractor` has one getter and the save signature takes `SubtitlePreference` with no defaults (D3).
- `SubtitlePreferenceKey.kt` holds the key order, the identities and `toSubtitlePreference` (D4, D5).
  - The merge prefix is stripped only when `fromManifest` is true.
  - A side-load's positional group id is never stored or compared (AC-10).
- Resolver rules 0-5 (D6):
  - The URL rules run first over all rows, unfiltered.
  - Candidates are filtered by language and forced only. Per triage-1 #2 the label is now only a chooser, not a filter; this deviates from spec D7 and is recorded in the plan Implementation record.
- `PlayerVM`:
  - Intent-first rebuild restore (D9, AC-8).
  - `subtitlePickedThisMedia` and a `pickedSubtitlePreference` snapshot, both reset in `resetMediaSession` (AC-16, triage-1 #1, triage-2 #2).
  - The save writes only an explicit pick, otherwise the stored value passes through.

Out of scope was respected: `prepare()`, `PlaybackController`, the merger, the selector, `findSameSubtitleTrackIndex`, DI, UI and strings are untouched. Nothing else is left behind. The old `getPreferredSubtitleLang`/`Url` are gone everywhere (grep is empty), and `unambiguousSubtitleLanguageMatch` was removed, not orphaned. No commit carries a Co-Authored-By line.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | T-1a `pickManifestOnlyRow_savesUnprefixedGroupId...`, T-1b round trip, T-1c, T-1d, T-1e `newSession_restoresManifestOnlyRow...`; device M1/M2 | Names sampled in the test sources; JUnit XML 1637/0/0/0 at HEAD; evidence round 1 M1/M2, round 2 step 1 | PASS |
| AC-2 | T-2a `pickLanguageLessManifestRow_savesIdentityNotOff`, T-2b, T-2c, `newSession_restoresLanguageLessManifestRow...` | Unit only (no title known; owner accepted unit level, spec 6) | PASS |
| AC-3 | T-3a `subtitlePreference_noneClearsAllFourKeys`, T-3b `explicitOff_savesNoneAndClearsAllValues`, T-3c; device M6 | Unit and device round 1 M6, round 2 step 4 | PASS |
| AC-4 | Existing resolver tests at :115-126 unchanged (the diff deletes no line from that file); T-4a legacy keys; T-4b; T-4c; device rollout step | Unit and device round 1 rollout | PASS |
| AC-5 | T-5a `rejectsForcedOnlyReplacementForFullPreference`, T-5b `savedFullSubtitle_streamHasOnlyForcedRow_staysOff` | Unit only | PASS |
| AC-6 | T-6a, T-6b, T-6c, `labelChoosesBetweenSameLanguageRowsWithoutIdentity` (rule 4 only), `renamedLabel_stillRestoresSingleSameLanguageRow`, T-6d | Unit only; qa round 2 marks the label chooser NOT CHECKED on device (Outlander has one row per language) | PASS (unit); device NOT CHECKED, carried over |
| AC-7 | T-7a, T-7b | Unit | PASS |
| AC-8 | T-8a `rebuild_intentDiffersFromSaved_resolvesIntentFirst`, T-8b, T-8c, `rebuildFastDns_*` / `rebuildBufferPreset_*` | Unit | PASS |
| AC-9 | T-9 `rebuildFastDns_audioEmptyReportWithTextRows_reSendsIntent_andSavesNothing` | Unit | PASS |
| AC-10 | T-10a, T-10b `pickSideLoadedRow_savesUrlNotGroupId`, T-10c | Unit | PASS |
| AC-11 persistence, re-entry, clause (b) | Device M1, M2, 4, 4b, 4c, the stricter 720p exit variant; round 2 step 3 | Evidence file, rounds 1-2 (builds d538e15b and 630e2074) | PASS |
| AC-11 clause (a), manifest-only row after a quality switch | Device step 3 | No manual quality offers a manifest-only row (all play url.hls). The Psyren observation is not counted | NOT CHECKED (carried over) |
| AC-12 | check.full | I ran it at 2e8d4be5: BUILD SUCCESSFUL, exit 0. Tasks were up to date from the conductor's run on the same sources. JUnit XML: 1637 tests, 0 failures, 0 errors, 0 skipped | PASS |
| AC-16 | T-16a/b/c, `savedSubtitleRestoredBySubstitute_noPick_...`, `pickInFirstMedia_nextMediaSubstituteRestore_...`, `pickThenRemapToSameLanguageRow_audioChangeSavesThePick`; device round 2 step 2 (audio change keeps the stored subtitle) | Unit and device round 2 | PASS |

The last device pass ran on 630e2074. Commit 89c68077 (the pick snapshot) was not re-checked on a device. I read it: it changes only the value written after an explicit pick, and it is unit-pinned. I accept the skip but name it below.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| A substitute row is saved as the viewer's choice | edge-1 F1 | fixed | c81474e8; save gated on `subtitlePickedThisMedia`; test `savedSubtitleRestoredBySubstitute_noPick_...` |
| A label filter drops a renamed single row | blind-1 F1 | fixed | bccc8161; `renamedLabel_stillRestoresSingleSameLanguageRow` |
| No test is decided by rule 4 | tests-1 F1 | fixed | bccc8161; `labelChoosesBetweenSameLanguageRowsWithoutIdentity` |
| Forced and label pass-through not pinned | tests-1 F2 | fixed | c81474e8 (reviewed closed in tests-2) |
| The no-wipe negatives are too narrow | tests-1 F3 | fixed | c81474e8 (tests-2) |
| Positive verify uses `any()` | tests-1 F4 | fixed | c81474e8 (tests-2) |
| T-16c pins mock wiring | blind-1 F3 | fixed | c81474e8 |
| The empty-language Off rule is unreachable | blind-1 F2 | rejected (accepted) | Spot-checked: spec AC-7 second half requires this resolver contract, and the KDoc now says the app never stores it (diff of `findSubtitleTrackIndex` KDoc) |
| The rebuild remaps the intent to a same-language variant | codex-1 adversarial | rejected (accepted) | Spot-checked: `onTracksUpdated` lines 552-557 and `findSameSubtitleTrackIndex` are unchanged from base, which the spec Out of scope covers. Its save-side effect was later closed by 89c68077 |
| The per-media pick reset is not pinned (major) | tests-2 F1 | fixed | 89c68077; `pickInFirstMedia_nextMediaSubstituteRestore_...` (confirmed in tests-3) |
| An in-session remap is saved after a pick | codex-2 | fixed | 89c68077; `pickThenRemapToSameLanguageRow_audioChangeSavesThePick` |
| Round 3 edge and tests | review-edge-3, review-tests-3 | no findings | - |

No finding was silently dropped.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | The cost is negligible. The restore runs once per media, and so do its two extra in-memory SharedPreferences reads. The resolver does O(n) filtering over a handful of rows. There is no new coroutine and no Compose state. |
| Reliability | All four keys are written in one `edit {}` with null-removes, so no stale forced flag or label is left. Old prefs read as `(lang, url, null, null)`. Process death and retry go through `resetMediaSession`, and the pick was already persisted. An empty or unresolved restore keeps the stored value (AC-16). One open item: the unexplained Off at the start of qa round 2 (see Concerns). |
| Maintainability | The project conventions hold: Koin is untouched, tests use JUnit 5 and the fixture, and the save has no default parameters. The helpers live in one new file and nothing is dead. One drift: the spec Design D7 still says the readable label filters candidates, but the code (correctly, per triage-1 #2) uses it only as a chooser. The deviation is recorded in the plan Implementation record, not in the spec. |
| Security / privacy | No secrets or account data appear in code or evidence. Per qa, the evidence passed `mobile-evidence-audit.sh`. Installs were same-signer `install-preserve` (`compatible_replace`, `destructive_action: false`): nothing was uninstalled, cleared or downgraded. The account state changes are recorded (Outlander progress, the subtitle ending as Russian). |
| TV | There is no UI, focus or D-pad change. Device checks used D-pad only. |

## Must fix

none

## Concerns for the owner

1. AC-11 clause (a) is NOT CHECKED on device. No known title has a manual quality whose stream still offers a manifest-only row, because manual qualities play `url.hls`. The behaviour is covered only by the unchanged in-session re-finder and unit tests.
2. The label choosing among same-language rows (AC-6, triage-1 #2) is unit-only. Outlander has one row per language.
3. The qa round-2 open of Outlander S1E5 showed Subtitles Off, though round 1 had ended with Russian restored. This happened before any pick, and it was not reproduced or explained. The implementer found no defect in code. Every later re-entry restored correctly (English, Off and Russian each survived re-entry). If it shows up again in daily use, open a run with the log of the first track report.
4. The final production commit 89c68077 (save the pick snapshot rather than the remapped row) was not re-verified on a device. It is VM save-path only and pinned by `pickThenRemapToSameLanguageRow_audioChangeSavesThePick`.
5. Doc drift: spec Design D7 still describes a label filter, while the code uses the label only as a chooser. The deviation is recorded in the plan, not in the spec.
6. Process: review ran 3 rounds, past the cap of 2, under the owner's standing instruction. Gate 1 and this gate rest on that instruction, not on an explicit owner reply.
