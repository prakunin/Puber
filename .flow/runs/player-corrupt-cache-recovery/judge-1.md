# Gate - player-corrupt-cache-recovery - round 1

**Decision:** CONCERNS
**Head:** a8607ccc   **Base:** 8ffdbca1

## Done as asked

The diff (`git -C /home/dev/orca/workspaces/Puber/player-corrupt-cache-recovery diff 8ffdbca1...HEAD`) does plan tasks 1-2 and the device part of task 3 (brief.md#Plan): `PlaybackControllerResourcesTest.kt:59-80` takes a mark before `prepare`, waits on `corrupt.error`, asserts `StreamFailure(NEXT_URL, url)`, `!sawReady`, and no `PlaybackEvent.Error` after `releaseAndDrain()`. KDoc and `docs/testing/player-playback.md:87` updated, test name kept (Q2). No `app/src/main` change, option B not touched (Q1).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` | Diff read at a8607ccc; run folder `itest-fix1-1..3.xml` (3 tests, 0 failures each, at a8607ccc) | PASS |
| AC-2 | Class 3x on host.docker.internal:5555 | `docs/superpowers/evidence/2026-11-27-player-corrupt-cache-recovery-itest-1..3.xml` + `-itest-qa.xml` (at 4ce45e30); `itest-fix1-1..3.xml` (at a8607ccc) | PASS |
| AC-3 | Mutant without `writeCorruptCacheEntry(url)` | `-itest-mutant.xml`: fails at `PlaybackControllerResourcesTest.kt:59` "corrupt-cache failure: condition stayed false for 15000 ms"; line 59 is unchanged at a8607ccc, the fix only adds later assertions | PASS |
| AC-4 | Read KDoc `:38-42` and `docs/testing/player-playback.md:87` | Diff: both state `StreamFailure(NEXT_URL)` failover; no "not asserted", no run pointer; name unchanged | PASS |
| AC-5 | `check.full` | Re-run by the judge at a8607ccc (qa-report.md older than the commit): exit 0, tooling shell tests all PASS | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Late terminal `Error` unseen after the snapshot (minor) | review-blind-1 F1, review-tests-1 F1 (triage #1) | fixed | a8607ccc: `none { Error }` on `events.since(mark)` after `releaseAndDrain()` |
| `sawReady` not asserted false (nit) | review-tests-1 F2 (triage #2) | fixed | a8607ccc: `assertFalse(corrupt.sawReady)`, held in `itest-fix1-1..3.xml` |
| Test name says "Recovers" (nit) | review-blind-1 F2 (triage #3) | rejected, accepted | Owner answer Q2 (state.md); name maps upstream in `player-playback.md:87`, KDoc states the outcome |
| Antigravity review | config review_extra | skipped: quota | Owner rule: skip at quota, do not wait (state.md Decisions) |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | N/A: test-only; corrupt step about 1 s per run |
| Reliability | Waits bounded by `awaitCondition` (15 s); 7 green class runs across two heads |
| Maintainability | Follows the existing harness pattern (`mark`/`since`/`awaitCondition`); no dead code; detekt green |
| Security / privacy | Evidence holds JUnit XMLs only; `tools/mobile-evidence-audit.sh` on the evidence dir: passed |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

1. The merged evidence `docs/superpowers/evidence/2026-11-27-player-corrupt-cache-recovery-device.md` records build 4ce45e30 and the pre-fix AC-1 assertions; the runs at head a8607ccc (`itest-fix1-1..3.xml`) stay in the run folder and are not in the evidence dir. Before ship, add a row for those runs and copy the three XMLs there (plan task 3).
