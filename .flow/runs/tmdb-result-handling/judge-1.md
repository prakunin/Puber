# Gate - tmdb-result-handling - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 7e0b94f3   **Base:** e569e055

## Done as asked

Read `git diff e569e055...HEAD` directly. Production change is one file,
`app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`:

- private `apiResult` and the `kotlinx.coroutines.CancellationException` import are gone;
  all four public suspend methods are `= runCatchingCancellable { ... }` (`:40,:44,:53,:63`).
- one `private suspend fun find(imdbId)` (`:67-72`) builds the `find/{id}` request once and
  calls `requireSuccess()` once; `findTvByImdbId` and `findByImdbId` differ only in projection.
- `findByImdbId` non-2xx now gives `failure(TmdbApiException)` instead of `success(null)`
  (the approved contract change, Q2); KDoc on both find methods states the contract.
- `getTvDetails`/`getTvSeasonDetails` bodies unchanged; public signatures unchanged.

Not touched, as required: `SkipSegmentService.kt`, `EpisodeScheduleRepository.kt`, IntroDB
clients, DI, both privacy guard tests (`git diff --stat` on `*PrivacyTest*` is empty). No
`log(`/`Timber`/`println` added. `docs/architecture/api.md` gets the one planned sentence.
Out-of-scope items (logging, IntroDB policy) were left alone.

Small deviations from the plan, harmless: KDoc is two lines rather than one; the AC-3 test was
renamed `allCalls_wrapErrorsIntoFailure` when the fix round (7e0b94f3) extended it to all four
methods.

## Traceability

check.full was re-run by the judge at 7e0b94f3 (qa-report predates that commit):
`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`
-> BUILD SUCCESSFUL. Fresh XML (14:00:19): TmdbApiClientTest 9/9, SkipSegmentServiceTest 4/4,
SkipSegmentPrivacyTest 3/3, EpisodeSchedulePrivacyTest 1/1, 0 failures/errors.

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | grep + compile | judge grep: no `apiResult`, no kotlinx `CancellationException`; 4 `= runCatchingCancellable`; check.full green | PASS |
| AC-2 | `scheduleEndpoint_rethrowsCancellation`, `findByImdbId_rethrowsCancellation` | judge run, green; test body read (fails if nothing thrown) | PASS |
| AC-3 | `allCalls_wrapErrorsIntoFailure` (cause-chain identity, all 4 methods) | judge run, green | PASS |
| AC-4 | `findTvByImdbId_returnsFailureForHttpErrors` (429, 502) | judge run, green | PASS |
| AC-5 | `findByImdbId_prefersTvThenMovieAndFailsOnHttpError` (502 -> TmdbApiException 502) | judge run, green | PASS |
| AC-6 | same test (TV wins, movie fallback, empty -> null) + `findTvByImdbId_usesNormalizedIdAndOnlyTvResults`, `..._returnsNoMatchWithoutFallingBackToMovieResults` | judge run, green | PASS |
| AC-7 | code read | `TmdbApiClient.kt:67-72` single `find`, one `requireSuccess` for find | PASS |
| AC-8 | `SkipSegmentServiceTest.skipsPrimaryAndAsksFallback_whenTmdbLookupFails` | judge run, green; asserts fallback result, 0 calls to TheIntroDB and `saveTmdbId` | PASS |
| AC-9 | check.full | judge run green at 7e0b94f3; privacy tests unmodified | PASS |
| AC-10 | M1 schedule screen; M2 player skip prompt | evidence `docs/superpowers/evidence/2026-10-12-tmdb-result-handling-device.md` Round 2: M1 PASS; M2 playback starts/plays without crash, IntroDB.app skip prompt not observed | M1 PASS; M2 NOT CHECKED (carried from qa, not upgraded) |

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| `Error`s wrapped into `Result.failure` and swallowed by `getOrNull()` | review-blind-1 F1 (minor) | rejected | Accepted (spot-checked): AC-3 requires exactly this; design Risk 3 accepted at gate 1; same as other `runCatchingCancellable` callers (`CoroutinesErrorHandler.kt:37-45`) |
| Cancellation test also green on base | review-blind-1 F2 (nit) | rejected | Accepted (spot-checked): AC-2 asks for it; test-design section 3 lists it as a regression pin |
| No Error/failure test for `getTvDetails`/`getTvSeasonDetails` | review-tests-1 F1 (minor) | fixed | 7e0b94f3; read in diff: 502 assertion for `getTvSeasonDetails`, Error test covers all four calls; review-tests-2 confirms |
| AC-10 M2 skip prompt not seen on device | review-tests-1 F2, review-codex-1 (minor) | carried to owner | Not a code defect; kept NOT CHECKED in Traceability |
| Round 2 reviews (edge, tests, codex) | review-*-2 | no findings | triage-2 |

No blocker or major was raised in any review; nothing silently dropped.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No change: `runCatchingCancellable` is inline; one HTTP request per call as before; no new allocation of note. |
| Reliability | Cancellation still rethrown (tested). Non-2xx/Error on `findByImdbId` now a failure; `SkipSegmentService.resolveTmdbId` (`:56-65`) uses `getOrNull()`, so the player outcome is unchanged and a failure is not cached (tested). Schedule screen: `Error` now reaches the VM's existing `Throwable` catch as an error state instead of escaping. Fatal `Error`s are now hidden for TMDB calls (accepted Risk 3). |
| Maintainability | Duplicate private helper removed in favour of the shared one; request build deduplicated; no dead code left (`isSuccess`/`HttpResponse` imports still used by `requireSuccess`); detekt and lint green; doc updated. |
| Security / privacy | No logging added; privacy guards unmodified and green; evidence passed `tools/mobile-evidence-audit.sh` (judge re-ran: `evidence_audit_status=passed`). No secrets in diff. |
| TV | No UI change. M1: Back from the schedule returns focus to the play button, not the calendar button that opened it (observation, not caused by this diff). |

## Must fix

none

## Concerns for the owner

1. AC-10 M2 is NOT CHECKED: the IntroDB.app skip prompt was never observed on device (playback
   resumed mid-episode; seeking was avoided to not change watch progress). The fallback logic is
   covered only by `SkipSegmentServiceTest`. Accept as is, or authorize a re-run from the episode start.
2. Owner-authorized install (Q4): the conductor ran `adb -s emulator-5554 install -r` of the
   worktree dev APK over a corrupted on-device copy (`signer_unknown`); no uninstall or data clear.
3. Side effect of the smoke: a ~15 s resumed playback (Outlander S2E3) was reported to the
   account's watch progress.
4. Behaviour accepted at gate 1 but worth remembering: fatal `Error`s in TMDB calls now become a
   failed `Result` (schedule error state / no TheIntroDB segments) instead of crashing, and a TMDB
   failure remains invisible at runtime (no logging, option B).
5. Observation from M1, not attributable to this diff: after Back from the schedule screen focus
   lands on the play button rather than the calendar button.
