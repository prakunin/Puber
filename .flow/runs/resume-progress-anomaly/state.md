---
slug: resume-progress-anomaly
title: "Find out why playback progress did not resume on emulator-5554"
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/resume-progress-anomaly
worktree: /home/user1/orca/workspaces/Puber/resume-progress-anomaly
base: 2fd851a4
spec: /home/user1/orca/workspaces/Puber/resume-progress-anomaly/docs/superpowers/specs/2026-11-22-resume-progress-anomaly.md
plan: /home/user1/orca/workspaces/Puber/resume-progress-anomaly/docs/superpowers/plans/2026-11-22-resume-progress-anomaly.md
rounds: {verify: 2, review: 1, judge: 1}
blocked_by: []
created: 2026-10-12
updated: 2026-11-22
---

## Next
Done. Follow-up: card-progress-watching-duration

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | setWatchingTime returns Result<Unit> or a MarkTimeResponse model? | no | answered | Result<Unit> (recommended) |
| Q2 | Progress-save failure log names the error class? | yes (AC-4) | answered | yes, simple class name only (recommended) |
| Q3 | Check a movie and a series episode on the box? | no | answered | yes; an episode-only failure becomes a new run (recommended) |
| Q4 | Fix card progress readers of watching.duration here too? | no | answered | no, separate idea run card-progress-watching-duration (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-22 | Design delta 2 (Details mapper takes the length from the media when watching carries none; movie reads the first video position first) approved as the fix for qa defect 1 | owner (standing instruction: recommended) |
| 2026-11-22 | Q4: card progress readers deferred to card-progress-watching-duration | owner (standing instruction: recommended) |
| 2026-11-22 | qa defect 1 (Details progress line ignores the saved position, movie and episode) stays in this run: it is the original C7 symptom ("no saved progress on the detail page") and AC-5/AC-7 require it; not an episode-only failure, so Q3's new-run rule does not apply | owner (standing instruction: recommended) |
| 2026-11-22 | Run end to end on recommended answers, gates and ship included | owner (standing instruction: recommended) |
| 2026-11-22 | codex: required on every round; caps lifted | owner |
| 2026-11-22 | Device work on the TV box 192.168.1.110:5555 only, emulator not used | owner |
| 2026-11-22 | Q1: Result<Unit> | owner (standing instruction: recommended) |
| 2026-11-22 | Q2: log the simple error class name only | owner (standing instruction: recommended) |
| 2026-11-22 | Q3: movie and series episode on the box | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 1: approve | owner (standing instruction: recommended) |

## Log
- 2026-10-12 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
- 2026-11-12 dependency-update-stable qa: `Playback progress save failed` (watching/marktime) twice per playback on emulator-5554, on base 4db9c0d4 and on the bumped build alike; PlayerProgressDiagnostics hides the cause (.flow/runs/dependency-update-stable/qa-report.md, AC-10)
- 2026-11-22 cache-targeted-invalidation: watching/marktime returns HTTP 200 but the body fails to decode (ApiError.Serialization: Malformed response) on emulator-5554 and on the owner's TV box 192.168.1.110:5555 alike, so every progress save counts as failed and its follow-ups (details stale mark, TV home refresh) never run; the server does store the position. Evidence: .flow/runs/cache-targeted-invalidation/baseline.md (Measurement shim), docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md (step 0)
- 2026-11-22 /task:run started from master 2fd851a4; researcher spawned
- 2026-11-22 research.md: marktime answers {"status":200} (docs), setWatchingTime decodes into WatchingStatus with required id; every save fails since 1.5.0 (1b7646af); explains no-resume via the 10 min details cache; option A recommended; analyst spawned
- 2026-11-22 brief.md: M (networking/API risk area), bug, no tracks, 8 ACs, 3 questions answered on recommendation
- 2026-11-22 Orca worktree /home/user1/orca/workspaces/Puber/resume-progress-anomaly from 2fd851a4; architect design spawned
- 2026-11-22 design-architect.md: 6 decisions, 5 risks, no questions; test-designer spawned
- 2026-11-22 test-design.md: 7 risks, P0 8, P1 4, MANUAL 3; spec assembled and committed (08740a15); gate 1 approved on standing instruction; architect plan spawned
- 2026-11-22 plan written (2 tasks + qa hand-off); readiness judge spawned
- 2026-11-22 readiness-1: READY (notes: device P0 row manual, Details progress may be NOT CHECKED on the box, no Out of scope section); plan committed 0995cebc; implementer spawned
- 2026-11-22 impl done: 83bb0b6c, 76db652b, 37c5c7af (record); check.fast green (conductor); all named tests present (3 existing guards in ApiRetryPolicyTest/PlayerVMInvalidationTest); 0 Co-Authored-By; check.full green per implementer; qa-device verify round 1 spawned
- 2026-11-22 qa-report round 1: check.full green; AC-1..4, 6, 8 PASS; AC-5, AC-7 FAIL (partial): 0 save failures, marktime 200 on every save, resume dialog at the new position, but Details still shows "Not started" (movie) / "Next - episode N" (series); evidence committed; architect design delta spawned
- 2026-11-22 design-architect-2.md: root cause watching.duration absent from item payloads (docs), timeLeft() null -> "Not started"/"Up next"; since 0192929e; mapper-only fix, 5 named tests; Q4 deferred as card-progress-watching-duration; test-designer delta spawned
- 2026-11-22 test-design delta: 6 new mapper tests (P0 5, P1 1); implementer fix (delta 2) spawned
- 2026-11-22 delta impl: da9eebc8 (spec), c185d417 (mapper fix, 6 tests, 5 red on base); check.fast green (conductor, exit 0); check.full green per implementer; qa-device round 2 spawned
- 2026-11-22 qa-report round 2: all AC-1..8 PASS on the box (Details "Stopped · 1 h 54 min left", episode time left, Home session); episode bar on screen not read (white frame), covered by mapper test; evidence f2e0f25f; review round 1 spawned at f2e0f25f
- 2026-11-22 review round 1: blind, edge, tests no findings, codex clean (plain + adversarial); triage-1: no fixes, 5 unproven notes rejected with reasons; final-judge spawned
- 2026-11-22 judge-1: PASS, no must-fix (manual, not an AC: episode bar on screen unseen, white frame). Squashed f2e0f25f into 3 commits (fd2a9bb3, 3c9b8a89, ce2c829b), tree unchanged; Co-Authored-By 0. Gate 2: ship on standing instruction
- 2026-11-22 shipped: merged into master as ad0c0af0 (--no-ff), Orca worktree removed
