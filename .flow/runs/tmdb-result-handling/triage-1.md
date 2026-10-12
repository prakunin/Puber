# Review triage - round 1

Run: tmdb-result-handling   Base: e569e055   Reviewed head: c2b93ed0

Sources: review-blind-1.md, review-edge-1.md (no findings), review-tests-1.md,
review-codex-1.md (ran: review, adversarial; model gpt-6-astra). No review_extra agent matched (track none, no Compose diff).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1 | minor | `TmdbApiClient.kt:40,44,53,63`; `TmdbApiClientTest.findLookups_wrapErrorsIntoFailure` | `Error`s are now wrapped into `Result.failure`, and `getOrNull()` in `SkipSegmentService` swallows them | Read the code: true, but it is the approved contract | reject | Spec AC-3 requires exactly this ("engine throws an `Error` -> `Result.failure`"), design Risk 3 accepts it, owner approved option B at gate 1. Matches the other 12 callers of `runCatchingCancellable`. |
| 2 | blind F2 | nit | `TmdbApiClientTest.findByImdbId_rethrowsCancellation` | Cancellation test passes on base too | Read the test and test-design: it is listed as a regression pin, not failing-first | reject | By design (test-design section 3: "characterisation or regression pins"); AC-2 asks for it. |
| 3 | tests F1 | minor | `TmdbApiClient.kt:44-57`; `TmdbApiClientTest.kt:102,179` | `getTvDetails`/`getTvSeasonDetails` have no Error-wrapping test; `getTvSeasonDetails` has no failure test at all | grep of the test file: `getTvSeasonDetails` appears only at `:94` (2xx decode); Error test covers only the two find methods | fix | fixed (7e0b94f3). Cheap: extend the Error test to all four methods; add a 502 assertion for `getTvSeasonDetails`. |
| 4 | tests F2, codex | minor | evidence `2026-10-12-tmdb-result-handling-device.md` Round 2; `qa-report.md` | AC-10 M2: IntroDB.app skip prompt never seen on device | Read the evidence: M2 PARTIAL, prompt NOT CHECKED; re-running needs a seek/restart that changes account watch progress | reject (as a code finding) | Not a code defect. Carried to gate 2 as NOT CHECKED exactly as qa reported; the owner decides there (accept, or authorize a re-run that changes watch progress). |

## Rejected codex claims

- None: the plain Codex review returned no findings; the adversarial finding is row 4.

## Next

- Fix round for row 3 (tests only): implementer in fix mode -> check.fast -> lens edge, lens tests and codex on `git diff c2b93ed0`. Runtime code unchanged, so no qa re-run.
