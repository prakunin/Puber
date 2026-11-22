# Gate - card-progress-watching-duration - round 1

**Decision:** CONCERNS
**Head:** 41b96bda   **Base:** c506d0bd

## Done as asked

The diff implements the spec's shared movie rule and nothing out of scope.

| Spec item | Where in the diff |
|---|---|
| `moviePosition`, `movieLength`, `lengthOr` helpers | `Models.kt:186-194` |
| Movie watched verdict reads `videos[0]` too | `Models.kt:166,178-183` (`isMovieWatchedAnywhere`) |
| `toWatchStateOrNull` is `internal`, has a wider gate for non-series items only, and takes movie numbers from the helpers | `WatchStateRepository.kt:499-521` |
| `toLocalWatchState` duplicate deleted; fallback calls `toWatchStateOrNull` | `VideoItemUIMapper.kt:111-112` |
| Details resume line delegates; `time <= 0` guard kept | `DetailsScreenUIMapper.kt:371,404-407` |
| Out of scope (DAO length guard, `resolve()` order) | untouched: no change under `data/db/`, `resolve` at `WatchStateRepository.kt:230` unchanged |

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | WSR `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` (`WatchStateRepositoryTest.kt:598`) | test results at 41b96bda (17:53): 2343 tests, 0 failures, 0 errors | PASS |
| AC-2 | VIM `mapShortItem_progress_forPartiallyWatchedMovie` (`:112`), WSR `resolve_readsAMoviesPositionAndLengthFromItsFirstVideo` (`:610`) | same run | PASS |
| AC-3 | VIM `:121`, `:131`, `:141`; WSR `recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength` (`:620`) | same run | PASS |
| AC-4 | IWS `:27`, `:33`; VIM `mapShortItem_progress_null_whenMoviesVideoIsWatched` (`:153`); WSR `:642` | same run | PASS |
| AC-5 | WSR `resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields` (`:660`) and the existing series guards | same run | PASS |
| AC-6 | DSM whole class | `git diff c506d0bd...HEAD` on DSM only adds two tests, with no line removed; class green | PASS |
| AC-7 | Manual check 1 (test-design.md#manual-checks) | evidence file `docs/superpowers/evidence/card-progress-watching-duration/2026-11-22-card-progress-watching-duration-device.md`: NOT CHECKED, no movie card on the box, playback outside standing authorization | NOT CHECKED |
| AC-8 | `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | re-run by judge at 41b96bda: exit 0, BUILD SUCCESSFUL | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| F1 major: AC-7 not observed; R5 not unit-tested | review-tests-1 (codex adversarial, same gap) | fixed (test part); the device part goes to the owner | 41b96bda adds `recordFromServer_aNotWatchedFirstVideoReplacesAWatchedHistoryRow` (`WatchStateRepositoryTest.kt:737`); AC-7 in Manual checks |
| F2 minor: Details watched-video verdict and zero-length fallback not pinned | review-tests-1 | fixed | 41b96bda, two DSM tests |
| Codex adversarial: AC-7 never exercised | review-codex-1 | rejected as a code defect, merged into F1 | accepted: a verification gap, handled in Manual checks |
| Codex: fake DAO does not prove Room | review-codex-2 | rejected | accepted: a scope note; the fake mirrors the `updatedAt` and pending rules (review-tests-2) |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Only `firstOrNull` reads were added to the mappers. A Details open of an untouched movie can now insert one index row (spec risk 5); repeat opens skip it. |
| Reliability | A movie with no length anywhere still writes a null `progressDuration` over a history length (blind/edge unproven). That matches base behaviour (it wrote 0) and is the deferred option B. A multi-part movie is judged by `videos[0]` (spec risk 4, accepted). |
| Maintainability | The duplicate rule was removed rather than patched twice. No dead code is left. The helpers sit next to `isFullyWatched`, following the API-model convention in AGENTS.md. |
| Security / privacy | The evidence holds no body, token or screenshot. `tools/mobile-evidence-audit.sh` re-run: `passed`. |

## Manual checks for the owner

AC-7 (stays NOT CHECKED above). Needs one movie playback on the owner's account on box `192.168.1.110:5555`, package `com.kino.puber.stage`. The stage APK built at db292ad0 is installed (production code is identical at 41b96bda).

1. Open a movie you have never watched, press Play, let it run past 5 min, then press BACK to leave the player.
2. Press BACK to Home and find the movie's card. Expect a progress bar at about the position you left, not a watched badge.
3. Open its Details, wait for the resume line, then press BACK. Expect the same bar, with focus still on the card.
4. Force-stop and relaunch `com.kino.puber.stage`. Expect the same bar.
5. If the bar is lost at step 3 or 4, open an idea run for the DAO length guard (decision Q3). Note whether `/v1/items/<id>` carries item-level `watching`/`watched` (field names only) for the Q4 KDoc fix.

## Must fix

none

## Concerns for the owner

1. The root cause is still `plausible`: no real `/v1/items/<id>` movie payload has been seen. The rule handles either payload shape, but R6 (a cached Details state beats a fresh local play) and the Q4 KDoc correction both wait on AC-7.
2. R5 is pinned as current behaviour, not decided: a newer Details read whose `videos[0].watching` is `{status 0, time 0}` replaces a watched history row (`WatchStateRepositoryTest.kt:737`). This is correct if the server is right. If `videos[0]` lags behind the history, it is a regression. Triage-1 handed this choice to the owner.
