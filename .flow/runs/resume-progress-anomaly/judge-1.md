# Gate - resume-progress-anomaly - round 1

**Decision:** PASS
**Head:** f2e0f25f   **Base:** 2fd851a4

## Done as asked

The diff (`git diff 2fd851a4...f2e0f25f -- app`) does what the spec asks and nothing it excluded:

| Spec part | Diff |
|---|---|
| Q1: `setWatchingTime` returns `Result<Unit>` | `KinoPubApiClient.kt:456`; `WatchingStatus` and both imports deleted; `rg WatchingStatus app/src` finds only an unrelated test name |
| Q2: class simple name only | `PlayerProgressDiagnostics.kt:13` takes `KClass<out Throwable>`; sole caller `PlayerVM.kt:1932`; `CancellationException` rethrow kept |
| Design delta 2 (owner-approved, `state.md` Decisions) | `DetailsScreenUIMapper.kt` `timeLeft`/`progressFraction`/`length` fall back to the media length; movie reads `videos.first().watching` first, matching `PlayerInteractor.kt:116-125`; `Models.kt` KDoc only |
| Excluded: research options B and C, retry policy, card readers (Q4) | Untouched; Q4 deferred as run `card-progress-watching-duration` (stage idea, `source: resume-progress-anomaly`) |

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `KinoPubApiClientFailureTest`: `aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition` | Red on base (plan Implementation record); TEST xml 7/7 green at HEAD | PASS |
| AC-2 | `anEmptyMarkTimeAnswerIsASuccess`, `aMarkTimeAnswerWithExtraFieldsIsASuccess` | Same suite green | PASS |
| AC-3 | `aMarkTimeServerErrorIsAFailureAndIsSentOnce`, `ApiRetryPolicyTest` | Same suite green; `ApiRetryPolicyTest` 6/6 | PASS |
| AC-4 | `PlayerVMPlaybackFlowTest`: `failedFinalProgressSave_emitsIdentityFreeDiagnostic` | Asserts one entry, `Serialization` present, id/title/time/cause message/"Malformed response" absent; 32/32 green | PASS |
| AC-5 | Device steps 1-2 (box); `DetailsScreenUIMapperTest` delta-2 rows | Evidence Round 2 steps 1-2: 0 failure lines, marktime all 200, Details "Stopped - 1 h 54 min left" and episode "26 min left"; mapper 25/25 green | PASS |
| AC-6 | Device steps 1-2 | Round 2: dialogs "Continue from 8:58?", "Continue from 4:38?" | PASS |
| AC-7 | Device step 3 | Round 2 step 3: 0 failures, Details "season 1, episode 2 - 11 min left", dialog 4:36 to 12:48 | PASS |
| AC-8 | check.full | Run by the judge at f2e0f25f: exit 0, BUILD SUCCESSFUL (up to date with the reviewed run); `PlayerVMInvalidationTest` 11/11 | PASS |

Device evidence: `docs/superpowers/evidence/2026-11-22-resume-progress-anomaly-verify-device.md`; `tools/mobile-evidence-audit.sh` on the evidence dir: `evidence_audit_status=passed`.

## Findings

No blocker, major or minor in `review-*-1.md`; codex clean (plain + adversarial). Unproven notes from `triage-1.md`:

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| U1 video `duration: 0` or several videos skews the movie length | blind, edge | rejected, accepted | `Video.duration` is `Int? = null` (`Models.kt:262`), so an absent field falls back to `item.duration.total`; only an explicit 0 misbehaves; device round 2 time left matched the dialog |
| U2 `progressFraction` null instead of 0f at time 0 | blind, tests | rejected, accepted | Recorded deviation in the plan; empty bar either way |
| U3 movie prefers `videos.first().watching` | edge | rejected, accepted | Design-2 risk 1; covered by `a movie whose video carries no position reads the item-level one` |
| U4 any 2xx is success | edge | rejected, accepted | Q1 owner decision; same as the other `Result<Unit>` write calls |
| U5 no test for `watching.duration > 0` winning | tests | rejected, accepted | History path still reads it via `HistoryUIMapper.kt:24`, not this mapper; card readers in `card-progress-watching-duration` |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Mapper change is O(1); exit now marks details stale and refreshes TV home on every session (design R2), measured in the cache-targeted-invalidation verify-box evidence, AC-10 |
| Reliability | Non-2xx still fails (AC-3), marktime stays un-retried; cancellation rethrow kept; null/0 positions return null and keep the "Not started" line |
| Maintainability | Follows `Result<Unit>` pattern of other write calls; dead `WatchingStatus` removed; one length helper shared by line and bar; no Co-Authored-By in the run's commits |
| Security / privacy | Log carries only a class simple name (`KClass` boundary); evidence holds counts and UI text, no tokens or raw logcat; audit passed |
| TV | Focus kept on Play after BACK (round 2 step 1); no layout or D-pad change |

## Manual checks for the owner

1. Optional, not an AC: episode-card progress bar on a series Details (the box captures that screen as a white frame; covered by `an episode card fills its bar from the episode length when the position carries none`). Steps: on the stage build open a series with a started episode, focus the episode row, confirm the card bar is partly filled in proportion to the dialog position.

## Must fix

none

## Concerns for the owner

none
