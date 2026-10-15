# Readiness 1 - subtitle-restore-model

Judge: final-judge, mode readiness, round 1. Worktree HEAD `62d24119` (plan), code base `f351d988`.
`git diff --stat f351d988 HEAD -- app` is empty, so every `path:line` was checked against the base code.

## Verdict: NOT READY

One blocking gap: the device design for the reworded part of AC-11 is missing. Everything else is ready.

## AC coverage (plan task + Test design row)

| AC | Plan task | Test design rows | Status |
|----|-----------|------------------|--------|
| AC-1 | T1 (repository), T2 (key), T3 (resolver), T4 (VM) | T-1a..T-1e | covered |
| AC-2 | T2, T3, T4 | T-2a..T-2c, T-1e language-less variant | covered |
| AC-3 | T1, T4 | T-3a..T-3c | covered |
| AC-4 | T1, T3 | existing `:115-126`, T-4a..T-4c | covered |
| AC-5 | T3, T4 | T-5a, T-5b (base form per P-h) | covered |
| AC-6 | T2, T3 | T-6a..T-6e | covered |
| AC-7 | T3 | T-7a, T-7b | covered |
| AC-8 | T4 | T-8a..T-8c, existing `TST:401-457` | covered |
| AC-9 | T4 | T-9 | covered |
| AC-10 | T2, T3, T4 | T-10a..T-10c | covered |
| AC-11 | "After Task 4" (qa-device) | M1-M6, section 5 | **partial**: re-entry is designed (M1, M2, M4, M6); the reworded quality-switch clause is not (see fix 1) |
| AC-12 | T4 full gate | AC-12 row | covered |
| AC-16 | T4 | T-16a..T-16c | covered |

## Citations sampled (all resolve at `f351d988`)

- `PlayerVM.kt:165` `subtitleRestoredForCurrentMedia`; `:187` `intendedSubtitleRow`; `:545-550` intent re-find in `onTracksUpdated`; `:599-616` `restoreSubtitlePreference`; `:869-882` `applySubtitleSelection` (`:875` intent, `:879` flag); `:936-937`, `:948-949` flag resets on rebuild; `:994-995` in `resetMediaSession`; `:1849-1870` `saveTrackPreferences` (`:1857-1858` `takeIf { isNotEmpty }`).
- `AudioTrackPreferenceResolver.kt:26-39` `findSubtitleTrackIndex`; `:48-65` `findSameSubtitleTrackIndex`; `:67-69` `uniqueIndex`; `:98-104` exact URL (`:102` null-only guard, so P-a is real); `:106-112` stable key; `:151-158` `unambiguousSubtitleLanguageMatch`.
- `SubtitleTrackIdentity.kt:7-11` `stableSubtitleKey`, `:13-23` `sameSubtitleIdentity` (equality at `:14`), `:25-31` path rule, `:51` `withoutMergedSourcePrefix`.
- `PlayerPreferencesRepository.kt:16-22` getters, `:28-57` save, `:121-122` keys; `PlayerInteractor.kt:335-358`.
- `PlayerVMTestFixture.kt:107-108,116` stubs, `:156` `startedVM`; `PlayerVMTrackSelectionTest.kt:240-243`, `:245-247` (`report` takes `audio`), `:249-250`, `:394-398`; `collapsedAfterPick` clears mocks (`:360`), which T-9 relies on.
- P-d counts were re-counted with grep: 17 verifies at the listed lines, 15 stub pairs at the listed lines. No other caller of the two getters or `saveTrackPreferences` exists under `app/src`.
- `FakeSharedPreferences.kt:16,25-59` (contains, getBoolean, putBoolean, remove, apply); `PlayerPreferencesRepositoryTest.kt:33-44` fixture; `SubtitleLanguage.kt:8-11`; `PlayerTextTracks.kt:48`; `Models.kt:434`; `PlayerUIMapper.kt:47`; `PlaybackMediaItemFactory.kt:45,51-52`; `app/build.gradle.kts:340` (detektAll covers unit tests).
- Detekt: `detekt.yml` `ComplexCondition.allowedConditions: 3` (detekt `2.0.0-alpha.6`), `ReturnCount.max: 3` with `excludeGuardClauses: false`. The Task 4 sketch has 3 conditions and 3 returns: within limits.
- Small drift, not blocking: the spec cites `PlaybackController.kt:822` for the early return; `:822` is the text-group filter and the return is `:823`.

## Failing-first and ordering

- Base red is real: the merger keeps only player rows (`SubtitleTrackMerger.kt:16-30`), so T-5b base sees `[Off, forced rus]` and the legacy language rule picks the forced row; T-2a base writes null/null (G1); T-16a base sees two side-load rus rows, ambiguous, then writes null/null.
- Task order: T1 (signature + all callers + tests in one commit, behaviour-neutral) -> T2 (additive helpers) -> T3 (resolver with defaulted new params, VM still two-arg) -> T4 (VM). No task leaves compile or tests red.
- Scope: no change to `PlaybackController`/`prepare()`, merger, selector, `findSameSubtitleTrackIndex`, DI, UI or strings. Inside Out of scope.
- Open questions Q1-Q4 answered and recorded in Decisions.

## Must fix

1. **AC-11 quality-switch clause has no device design.** AC-11 was reworded at gate 1 (Q4): after a quality switch the row is on when the new stream offers it; on a stream without it the picker shows Off **without saving Off**, and the pick comes back on Auto and on re-entry. Test design section 5 still has the pre-rework M3, which treats the switch as "observation only" and says an Off result is "not a failure of this run". That lets an AC-11 failure through the gate. Update section 5 (and the plan's "After Task 4" pointer) with steps for: (a) a quality whose stream has the row -> still on; (b) a quality whose stream lacks it -> picker Off, then back to Auto -> row on again, then exit and re-enter -> row on (proves Off was not saved). Remove the "not a failure of this run" escape for the AC-11 clause. If no title with a stream that lacks the row can be found, say that the qa report must mark sub-clause (b) `NOT CHECKED` and give the reason. It must not be passed by observation.

## Advisory (not blocking; tests would catch them)

- Task 3 rule 4: skip it when the normalised preferred label is null. Otherwise `null == null` matches one unlabelled candidate whatever its identity, and T-7a and T-10c would return 1 instead of -1. Rule 3 states its guard; rule 4 should state the same kind of guard.
- T-8b and T-8c assert "zero saves" after an earlier explicit pick, which saves. They need `clearMocks(interactor, answers = false)` after the pick, as T-8a does.
